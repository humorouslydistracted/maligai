package com.maligai.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import com.maligai.app.localization.AppStrings
import com.maligai.app.localization.StringKey
import com.maligai.app.localization.UiLocales
import javax.inject.Inject
import javax.inject.Singleton

/* ----------------------------------------------------------------------------
 * Utilities
 * ------------------------------------------------------------------------- */

object Security {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

object Period {
    fun todayRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to (cal.timeInMillis - 1)
    }

    fun weekRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -diff)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 7)
        return start to (cal.timeInMillis - 1)
    }

    fun monthRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to (cal.timeInMillis - 1)
    }

    fun allTimeRange(): Pair<Long, Long> = 0L to Long.MAX_VALUE
}

enum class PeriodTab { TODAY, WEEK, MONTH, ALL }

fun PeriodTab.range(): Pair<Long, Long> = when (this) {
    PeriodTab.TODAY -> Period.todayRange()
    PeriodTab.WEEK -> Period.weekRange()
    PeriodTab.MONTH -> Period.monthRange()
    PeriodTab.ALL -> Period.allTimeRange()
}

/* ----------------------------------------------------------------------------
 * Repositories
 * ------------------------------------------------------------------------- */

@Singleton
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao,
    private val correctionDao: CorrectionDao
) {
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<MenuItem>> = itemDao.observeAll()
    suspend fun getAvailable(): List<MenuItem> = itemDao.getAvailable()
    suspend fun getAll(): List<MenuItem> = itemDao.getAll()
    suspend fun getById(id: Long): MenuItem? = itemDao.getById(id)

    suspend fun add(item: MenuItem): Long {
        val order = itemDao.maxSortOrder() + 1
        return itemDao.insert(item.copy(sortOrder = if (item.sortOrder == 0) order else item.sortOrder))
    }

    suspend fun update(item: MenuItem) = itemDao.update(item)

    suspend fun delete(item: MenuItem) {
        correctionDao.deleteForItem(item.id)
        itemDao.delete(item)
    }

    suspend fun reorder(items: List<MenuItem>) {
        items.forEachIndexed { index, item -> itemDao.update(item.copy(sortOrder = index)) }
    }

    suspend fun corrections(): Map<String, Long> =
        correctionDao.getAll().associate { it.recognizedText to it.itemId }

    suspend fun clearCorrections() = correctionDao.deleteAll()

    suspend fun findCatalogDuplicate(
        nameLocal: String,
        nameLatin: String,
        unitType: String,
        unitLabel: String
    ): MenuItem? {
        val local = nameLocal.trim()
        val latin = nameLatin.trim()
        val label = unitLabel.trim().lowercase(Locale.US)
        if (local.isBlank() && latin.isBlank()) return null
        return getAll().find { item ->
            item.unitType == unitType &&
                item.unitLabel.trim().lowercase(Locale.US) == label &&
                (namesMatch(item.nameLocal, local, latin) || namesMatch(item.nameLatin, local, latin))
        }
    }

    private fun namesMatch(existing: String, local: String, latin: String): Boolean {
        val e = existing.trim()
        if (e.isBlank()) return false
        return (local.isNotBlank() && e.equals(local, ignoreCase = true)) ||
            (latin.isNotBlank() && e.equals(latin, ignoreCase = true))
    }

    suspend fun learn(recognizedText: String, itemId: Long) {
        val key = recognizedText.trim()
        if (key.isEmpty()) return
        val existing = correctionDao.get(key)
        if (existing == null) {
            correctionDao.upsert(RecognitionCorrection(key, itemId, 1))
        } else {
            correctionDao.bump(key, itemId)
        }
    }
}

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao
) {
    fun observeDrafts() = billDao.observeByStatus(BillStatus.DRAFT)
    fun observeItems(billId: Long) = billDao.observeItems(billId)
    suspend fun getDrafts() = billDao.getByStatus(BillStatus.DRAFT)
    suspend fun getBill(id: Long) = billDao.getBill(id)
    suspend fun getItems(billId: Long) = billDao.getItems(billId)

    suspend fun createDraft(name: String): Long =
        billDao.insertBill(Bill(name = name, status = BillStatus.DRAFT))

    suspend fun renameBill(bill: Bill, name: String) = billDao.updateBill(bill.copy(name = name))

    suspend fun addItem(item: BillItem): Long = billDao.insertItem(item)
    suspend fun updateItem(item: BillItem) = billDao.updateItem(item)
    suspend fun removeItem(item: BillItem) = billDao.deleteItem(item)

    suspend fun deleteDraft(bill: Bill) {
        billDao.deleteItemsForBill(bill.id)
        billDao.deleteBill(bill)
    }

    suspend fun deleteBill(bill: Bill) {
        billDao.deleteItemsForBill(bill.id)
        billDao.deleteBill(bill)
    }

    suspend fun reopenBill(billId: Long): Bill? {
        val bill = billDao.getBill(billId) ?: return null
        if (bill.status != BillStatus.COMPLETE) return bill
        val reopened = bill.copy(status = BillStatus.DRAFT, completedAt = null)
        billDao.updateBill(reopened)
        return reopened
    }

    suspend fun completeBill(
        billId: Long,
        gstEnabled: Boolean,
        cgstPercent: Double,
        sgstPercent: Double,
        isLoan: Boolean
    ): Bill? {
        val bill = billDao.getBill(billId) ?: return null
        val items = billDao.getItems(billId)
        val gross = items.sumOf { it.lineTotal }
        // GST-inclusive: split the gross into base + cgst + sgst
        val (subtotal, cgst, sgst) = if (gstEnabled && (cgstPercent + sgstPercent) > 0) {
            val rate = (cgstPercent + sgstPercent) / 100.0
            val base = gross / (1 + rate)
            val c = base * (cgstPercent / 100.0)
            val s = base * (sgstPercent / 100.0)
            Triple(base, c, s)
        } else {
            Triple(gross, 0.0, 0.0)
        }
        val updated = bill.copy(
            status = BillStatus.COMPLETE,
            subtotal = subtotal,
            cgst = cgst,
            sgst = sgst,
            total = gross,
            isLoan = isLoan,
            completedAt = System.currentTimeMillis()
        )
        billDao.updateBill(updated)
        return updated
    }

    fun observeCompleted(from: Long, to: Long) = billDao.observeCompletedBetween(from, to, BillStatus.COMPLETE)
    suspend fun getCompleted(from: Long, to: Long) = billDao.getCompletedBetween(from, to, BillStatus.COMPLETE)
    suspend fun revenue(from: Long, to: Long) = billDao.revenueBetween(from, to, BillStatus.COMPLETE)
    suspend fun itemSales(from: Long, to: Long) = billDao.itemSalesBetween(from, to, BillStatus.COMPLETE)
}

@Singleton
class LoanRepository @Inject constructor(
    private val dao: CustomerLoanDao
) {
    suspend fun getCustomers() = dao.getCustomers()
    suspend fun findOrCreateCustomer(name: String, phone: String): Long {
        val existing = dao.findCustomerByName(name.trim())
        return existing?.id ?: dao.insertCustomer(Customer(name = name.trim(), phone = phone.trim()))
    }

    suspend fun createLoan(billId: Long, customerId: Long, amount: Double): Long =
        dao.insertLoan(Loan(billId = billId, customerId = customerId, amount = amount, outstanding = amount))

    suspend fun getLoanByBillId(billId: Long) = dao.getLoanByBillId(billId)

    suspend fun updateLoanAmount(loan: Loan, newAmount: Double) {
        val delta = newAmount - loan.amount
        val newOutstanding = (loan.outstanding + delta).coerceAtLeast(0.0)
        dao.updateLoan(loan.copy(amount = newAmount, outstanding = newOutstanding))
    }

    suspend fun deleteLoansForBill(billId: Long) = dao.deleteLoansForBill(billId)

    fun observeCustomerOutstanding() = dao.observeCustomerOutstanding()
    fun observeTotalOutstanding() = dao.observeTotalOutstanding()
    suspend fun totalOutstanding() = dao.totalOutstanding()
    suspend fun getLoansForCustomer(customerId: Long) = dao.getLoansForCustomer(customerId)
    suspend fun getCustomer(id: Long) = dao.getCustomer(id)
    suspend fun getPayments(loanId: Long) = dao.getPayments(loanId)
    suspend fun getCustomerOutstanding() = dao.getCustomerOutstanding()

    // Apply a payment across that customer's loans, oldest first.
    suspend fun recordPayment(customerId: Long, amount: Double) {
        var remaining = amount
        val loans = dao.getLoansForCustomer(customerId)
            .filter { it.outstanding > 0 }
            .sortedBy { it.createdAt }
        for (loan in loans) {
            if (remaining <= 0) break
            val pay = minOf(remaining, loan.outstanding)
            dao.insertPayment(LoanPayment(loanId = loan.id, amount = pay))
            dao.updateLoan(loan.copy(outstanding = loan.outstanding - pay))
            remaining -= pay
        }
    }
}

@Singleton
class SpendRepository @Inject constructor(
    private val dao: SpendDao
) {
    fun observeRecent(limit: Int = 10) = dao.observeRecent(limit)
    fun observeBetween(from: Long, to: Long) = dao.observeBetween(from, to)
    suspend fun getBetween(from: Long, to: Long) = dao.getBetween(from, to)
    suspend fun total(from: Long, to: Long) = dao.totalBetween(from, to)
    suspend fun getAll() = dao.getAll()
    suspend fun add(spend: ShopSpend) = dao.insert(spend)
    suspend fun update(spend: ShopSpend) = dao.update(spend)
    suspend fun delete(spend: ShopSpend) = dao.delete(spend)
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dao: SettingsDao
) {
    fun observe() = dao.observe()

    /** Inserts the default settings row on first launch so observe() is not stuck on null. */
    suspend fun ensureInitialized() {
        if (dao.get() == null) dao.upsert(AppSettings())
    }

    suspend fun get(): AppSettings {
        ensureInitialized()
        return dao.get()!!
    }

    suspend fun save(settings: AppSettings) = dao.upsert(settings)

    fun observeReceiptFields() = dao.observeReceiptFields()
    suspend fun getReceiptFields() = dao.getReceiptFields()
    suspend fun addReceiptField(label: String, value: String) {
        val order = dao.maxReceiptFieldOrder() + 1
        dao.insertReceiptField(ReceiptField(label = label, value = value, sortOrder = order))
    }
    suspend fun updateReceiptField(field: ReceiptField) = dao.updateReceiptField(field)
    suspend fun deleteReceiptField(field: ReceiptField) = dao.deleteReceiptField(field)
}

/* ----------------------------------------------------------------------------
 * Matching helper (item text -> scored inventory items)
 * ------------------------------------------------------------------------- */

object Matcher {
    data class ScoredMatch(val item: MenuItem, val score: Int)

    /**
     * Scores [itemText] (the item/weight portion only, never the amount) against
     * every available [MenuItem].  Returns up to [limit] best matches, sorted by
     * descending score.
     */
    fun match(
        itemText: String,
        items: List<MenuItem>,
        corrections: Map<String, Long>,
        limit: Int = 5
    ): List<ScoredMatch> {
        if (items.isEmpty() || itemText.isBlank()) return emptyList()
        val cand = itemText.trim()
        val scored = mutableListOf<ScoredMatch>()

        for (item in items) {
            val score = scoreItem(cand, item, corrections)
            if (score > 0) scored.add(ScoredMatch(item, score))
        }

        return scored.sortedByDescending { it.score }.take(limit)
    }

    private fun scoreItem(
        cand: String,
        item: MenuItem,
        corrections: Map<String, Long>
    ): Int {
        var score = 0
        val local = item.nameLocal.trim()
        val en = item.nameLatin.trim()
        val combined1 = "$local $en".trim()
        val combined2 = "$en $local".trim()

        // 1) Learned correction — highest priority
        if (corrections[cand] == item.id) score += 100

        // 2) Exact match
        if (local.equals(cand, ignoreCase = true)) score += 50
        if (en.equals(cand, ignoreCase = true)) score += 50

        // 3) Contains match
        if (local.isNotEmpty() && (local.contains(cand, ignoreCase = true) || cand.contains(local, ignoreCase = true))) score += 30
        if (en.isNotEmpty() && (en.contains(cand, ignoreCase = true) || cand.contains(en, ignoreCase = true))) score += 30

        // 4) Combined forms
        if (combined1.isNotBlank() && combined1.contains(cand, ignoreCase = true)) score += 20
        if (combined2.isNotBlank() && combined2.contains(cand, ignoreCase = true)) score += 20

        // 5) Prefix match — single script/Latin char or first 2+ chars
        if (cand.length == 1) {
            if (local.isNotEmpty() && local.startsWith(cand)) score += 12
            if (en.isNotEmpty() && en.startsWith(cand, ignoreCase = true)) score += 12
        } else if (cand.length >= 2) {
            if (local.isNotEmpty() && local.startsWith(cand, ignoreCase = true)) score += 10
            if (en.isNotEmpty() && en.startsWith(cand, ignoreCase = true)) score += 10
        }

        // 6) Token overlap for mixed names
        val candTokens = cand.split(Regex("\\s+")).filter { it.length >= 2 }
        val enTokens = en.split(Regex("\\s+")).filter { it.length >= 2 }
        val localTokens = local.split(Regex("\\s+")).filter { it.length >= 2 }
        for (ct in candTokens) {
            for (et in enTokens) {
                if (et.contains(ct, ignoreCase = true) || ct.contains(et, ignoreCase = true)) score += 15
            }
            for (tt in localTokens) {
                if (tt.contains(ct, ignoreCase = true) || ct.contains(tt, ignoreCase = true)) score += 15
            }
        }

        // 7) Fuzzy edit-distance for Latin text — catches OCR confusions like "soep"→"soap"
        //    Only fires when no other score was earned (score == 0) to avoid noise on
        //    already-matched items. Requires both candidate and catalog name to be Latin
        //    and at least 4 chars to avoid short-string false positives.
        if (score == 0 && isMostlyLatin(cand) && cand.length >= 4) {
            if (en.isNotEmpty() && isMostlyLatin(en) && levenshtein(cand.lowercase(), en.lowercase()) <= 2) score += 20
        }

        return score
    }

    /** Standard DP edit distance (Levenshtein). O(m×n) time, O(n) space. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1]
                          else 1 + minOf(prev[j], curr[j - 1], prev[j - 1])
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return curr[b.length]
    }
}

/* ----------------------------------------------------------------------------
 * Hilt module
 * ------------------------------------------------------------------------- */

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE app_settings ADD COLUMN uiLocaleTag TEXT NOT NULL DEFAULT 'en'"
        )
        db.execSQL(
            "UPDATE app_settings SET uiLocaleTag = primaryScriptTag WHERE uiLocaleTag = 'en'"
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Enable bitmap/native-script receipts for all regional handwriting languages.
        db.execSQL(
            """
            UPDATE app_settings
            SET receiptNameMode = 'LOCAL_IMAGE'
            WHERE primaryScriptTag != 'en' AND receiptNameMode = 'ENGLISH'
            """.trimIndent()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "maligai.db")
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()
    @Provides fun provideBillDao(db: AppDatabase): BillDao = db.billDao()
    @Provides fun provideCustomerLoanDao(db: AppDatabase): CustomerLoanDao = db.customerLoanDao()
    @Provides fun provideSpendDao(db: AppDatabase): SpendDao = db.spendDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideCorrectionDao(db: AppDatabase): CorrectionDao = db.correctionDao()
}

/* ----------------------------------------------------------------------------
 * ViewModels
 * ------------------------------------------------------------------------- */

/**
 * A suggestion chip shown after handwriting recognition.
 * [item] is the matched inventory item (null when no inventory match but
 * a valid displayText was parsed).
 * [parsed] carries the verbatim displayText and lineTotal that will be stored
 * on the bill — never replaced by item.nameLocal.
 */
/** [isLatinScript] true when this new-item chip came from the English/Latin recognizer output. */
data class Suggestion(
    val item: MenuItem?,
    val parsed: ParsedLine,
    val isLatinScript: Boolean = false,
    /** True when both parsedQuantity and lineTotal are present — enables one-tap green chip. */
    val isDirectAdd: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val billRepo: BillRepository,
    private val itemRepo: ItemRepository,
    private val loanRepo: LoanRepository,
    private val settingsRepo: SettingsRepository,
    private val csvManager: CsvManager,
    private val printer: PrinterManager,
    val recognizer: Recognizer
) : ViewModel() {

    val drafts: StateFlow<List<Bill>> = billRepo.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeBillId = MutableStateFlow<Long?>(null)
    val activeBillId: StateFlow<Long?> = _activeBillId.asStateFlow()

    val activeItems: StateFlow<List<BillItem>> = _activeBillId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else billRepo.observeItems(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val grandTotal: StateFlow<Double> = activeItems
        .map { items -> items.sumOf { it.lineTotal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val catalogItems: StateFlow<List<MenuItem>> = itemRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<AppSettings?> = settingsRepo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun findMenuItemForLine(line: BillItem, catalog: List<MenuItem>): MenuItem? =
        catalog.find {
            it.nameLocal == line.itemName ||
                (line.itemNameLatin.isNotBlank() && it.nameLatin == line.itemNameLatin)
        }

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    private val _recognizing = MutableStateFlow(false)
    val recognizing: StateFlow<Boolean> = _recognizing.asStateFlow()

    private val _modelReady = MutableStateFlow(false)
    val modelReady: StateFlow<Boolean> = _modelReady.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepo.get()
            recognizer.setPrimaryScriptTag(settings.primaryScriptTag)
            applyBillDayReset(settings)
            val existing = billRepo.getDrafts()
            val id = existing.firstOrNull()?.id
                ?: billRepo.createDraft("Bill ${settingsRepo.get().nextBillNumber}")
            _activeBillId.value = id
            _modelReady.value = recognizer.ensureReady()
        }
    }

    /** Reset counter at local midnight; rename active draft to Bill 1 when the day rolls over. */
    private suspend fun applyBillDayReset(settings: AppSettings): AppSettings {
        val today = startOfLocalDayMillis()
        if (settings.billCounterDay == today) return settings
        val updated = settings.copy(nextBillNumber = 1, billCounterDay = today)
        settingsRepo.save(updated)
        _activeBillId.value?.let { id ->
            billRepo.getBill(id)?.let { billRepo.renameBill(it, "Bill 1") }
        }
        return updated
    }

    private suspend fun settingsWithBillDay(): AppSettings =
        applyBillDayReset(settingsRepo.get())

    fun resetBillCounter() {
        viewModelScope.launch {
            val today = startOfLocalDayMillis()
            settingsRepo.save(
                settingsRepo.get().copy(nextBillNumber = 1, billCounterDay = today)
            )
            _activeBillId.value?.let { id ->
                billRepo.getBill(id)?.let { billRepo.renameBill(it, "Bill 1") }
            }
        }
    }

    fun ensureBillDayReset() {
        viewModelScope.launch { settingsWithBillDay() }
    }

    fun selectBill(id: Long) { _activeBillId.value = id; _suggestions.value = emptyList() }

    fun reopenForEdit(billId: Long, onReady: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val reopened = billRepo.reopenBill(billId)
            if (reopened != null) {
                _activeBillId.value = reopened.id
                _suggestions.value = emptyList()
                onReady(true)
            } else {
                onReady(false)
            }
        }
    }

    fun printCompletedBill(billId: Long, onResult: (PrintResult) -> Unit) {
        viewModelScope.launch {
            val bill = billRepo.getBill(billId) ?: return@launch
            val items = billRepo.getItems(billId)
            val settings = settingsRepo.get()
            val fields = settingsRepo.getReceiptFields()
            onResult(printer.printReceipt(bill, items, settings, fields, settings.uiLocaleTag))
        }
    }

    fun newBill() {
        viewModelScope.launch {
            val settings = settingsWithBillDay()
            val drafts = billRepo.getDrafts()
            val maxDraft = drafts.mapNotNull { billNumberFromName(it.name) }.maxOrNull() ?: 0
            val next = maxOf(settings.nextBillNumber, maxDraft + 1)
            val id = billRepo.createDraft("Bill $next")
            _activeBillId.value = id
            _suggestions.value = emptyList()
        }
    }

    fun renameActive(name: String) {
        val id = _activeBillId.value ?: return
        viewModelScope.launch {
            billRepo.getBill(id)?.let { billRepo.renameBill(it, name.ifBlank { it.name }) }
        }
    }

    fun recognize(strokes: List<List<TimedPoint>>) {
        viewModelScope.launch {
            _recognizing.value = true
            val candidates = recognizer.recognize(strokes)
            if (candidates.isEmpty()) { _recognizing.value = false; return@launch }

            val items = itemRepo.getAvailable()
            val corrections = itemRepo.corrections()
            val scriptTag = settingsRepo.get().primaryScriptTag

            // Try EVERY ML candidate against inventory (not just one parsed line)
            val byItem = LinkedHashMap<Long, Pair<Suggestion, Int>>()
            val unmatched = LinkedHashMap<String, ParsedLine>()

            for (raw in candidates) {
                val parsed = LineParser.parse(raw, scriptTag)
                val matchText = (parsed.matchHint ?: parsed.displayText).trim()
                if (matchText.isBlank()) {
                    if (parsed.displayText.isNotBlank() || parsed.lineTotal != null) {
                        putUnmatched(unmatched, parsed)
                    }
                    continue
                }
                val allMatches = Matcher.match(matchText, items, corrections)
                val matched = allMatches.filter { isStrongCatalogMatch(matchText, it) }
                if (matched.isEmpty()) {
                    putUnmatched(unmatched, parsed)
                } else {
                    val directAdd = parsed.parsedQuantity != null || parsed.lineTotal != null
                    for (m in matched) {
                        val existing = byItem[m.item.id]
                        val better = existing == null ||
                            m.score > existing.second ||
                            (m.score == existing.second &&
                                parsed.confidence.ordinal < existing.first.parsed.confidence.ordinal)
                        if (better) byItem[m.item.id] = Suggestion(m.item, parsed, isDirectAdd = directAdd) to m.score
                    }
                    // English/Latin lines that don't exactly hit catalog still get a + New chip
                    if (isMostlyLatin(matchText) && matched.none {
                            it.item.nameLatin.trim().equals(matchText, ignoreCase = true)
                        }) {
                        putUnmatched(unmatched, parsed)
                    }
                }
            }

            val catalogSuggestions = byItem.values
                .sortedByDescending { it.second }
                .map { it.first }
                .take(3)

            // Split unmatched by script: regional vs Latin/English.
            // Filter Latin to ≥3 chars to remove single-char garbage from the English model
            // interpreting Tamil strokes (e.g. "&", "D", "a" — confirmed in logs).
            val unmatchedRegional = unmatched.filter { !isMostlyLatin(it.key) }
            val unmatchedLatin = unmatched.filter { isMostlyLatin(it.key) && it.key.length >= 3 }

            val newSuggestionsRegional = unmatchedRegional.values
                .sortedBy { it.confidence.ordinal }
                .take(2)
                .map { Suggestion(null, it, isLatinScript = false) }
            val newSuggestionsLatin = unmatchedLatin.values
                .sortedBy { it.confidence.ordinal }
                .take(3)
                .map { Suggestion(null, it, isLatinScript = true) }

            val finalSuggestions = catalogSuggestions + newSuggestionsRegional + newSuggestionsLatin
            _suggestions.value = finalSuggestions

            _recognizing.value = false
        }
    }

    fun clearSuggestions() { _suggestions.value = emptyList() }

    private fun unmatchedKey(parsed: ParsedLine): String {
        val text = parsed.displayText.ifBlank { parsed.raw }.trim().lowercase(Locale.US)
        return if (text.isNotBlank()) text else parsed.raw.trim().lowercase(Locale.US)
    }

    private fun putUnmatched(unmatched: LinkedHashMap<String, ParsedLine>, parsed: ParsedLine) {
        val key = unmatchedKey(parsed)
        if (key.isNotBlank()) unmatched.putIfAbsent(key, parsed)
    }

    /** Parses canvas strokes into [ParsedLine]s (for Add Item tab / prefilling forms). */
    fun parseStrokes(strokes: List<List<TimedPoint>>, onResult: (List<ParsedLine>) -> Unit) {
        viewModelScope.launch {
            _recognizing.value = true
            val candidates = recognizer.recognize(strokes)
            val scriptTag = settingsRepo.get().primaryScriptTag
            _recognizing.value = false
            onResult(candidates.map { LineParser.parse(it, scriptTag) })
        }
    }

    /** Adds a new row to the shop catalog (homepage Add Item tab or admin). */
    fun addCatalogItem(
        nameLocal: String,
        nameLatin: String,
        unitType: String,
        unitLabel: String,
        pricePerUnit: Double,
        learnKey: String? = null,
        onResult: (Boolean, String?, MenuItem?) -> Unit
    ) {
        viewModelScope.launch {
            val local = nameLocal.trim()
            val latin = nameLatin.trim()
            val label = unitLabel.trim().ifBlank { defaultUnitLabel(unitType) }
            if (local.isBlank() && latin.isBlank()) {
                onResult(false, "Enter a name", null)
                return@launch
            }
            if (pricePerUnit <= 0) {
                onResult(false, "Enter a price", null)
                return@launch
            }
            val catalogLocal = local.ifBlank { latin.ifBlank { "item" } }
            val catalogLatin = latin.ifBlank { if (isMostlyLatin(catalogLocal)) catalogLocal else "" }
            val dup = itemRepo.findCatalogDuplicate(catalogLocal, catalogLatin, unitType, label)
            if (dup != null) {
                val key = learnKey?.trim().orEmpty()
                if (key.isNotBlank()) itemRepo.learn(key, dup.id)
                onResult(true, null, dup)
                return@launch
            }
            val newId = itemRepo.add(
                MenuItem(
                    nameLocal = catalogLocal,
                    nameLatin = catalogLatin,
                    unitType = unitType,
                    unitLabel = label,
                    pricePerUnit = pricePerUnit
                )
            )
            val key = learnKey?.trim().orEmpty()
            if (key.isNotBlank()) itemRepo.learn(key, newId)
            val saved = itemRepo.getById(newId)
            onResult(true, null, saved)
        }
    }

    /**
     * Adds a bill row from a parsed handwritten line.
     * [displayText] is stored verbatim — never substituted with catalog name.
     * [matchedItem] is used only to record a correction for future matching.
     */
    /**
     * Full handwritten line with `- amount`: stores [parsed.displayText] verbatim.
     */
    fun addParsedLine(parsed: ParsedLine, matchedItem: MenuItem?) {
        val id = _activeBillId.value ?: return
        val total = parsed.lineTotal ?: return
        viewModelScope.launch {
            val line = BillItem(
                billId = id,
                itemName = parsed.displayText.ifBlank { "item" },
                itemNameLatin = "",
                unitType = UnitType.COUNT,
                unitLabel = "",
                quantity = 1.0,
                unitPrice = total,
                lineTotal = total
            )
            billRepo.addItem(line)
            if (matchedItem != null) {
                val learnKey = (parsed.matchHint ?: parsed.displayText).trim()
                if (learnKey.isNotBlank()) itemRepo.learn(learnKey, matchedItem.id)
            }
            _suggestions.value = emptyList()
        }
    }

    /**
     * Adds a handwritten line that is not in the catalog. Optionally saves a new [MenuItem].
     * Bill line is always added; catalog save is skipped when duplicate is found.
     */
    fun addNewHandwrittenItem(
        nameLocal: String,
        nameLatin: String,
        unitType: String,
        unitLabel: String,
        quantity: Double,
        lineTotal: Double,
        saveToCatalog: Boolean,
        learnKey: String,
        onResult: (AddItemResult) -> Unit = {}
    ) {
        val billId = _activeBillId.value ?: return
        if (lineTotal <= 0) return
        viewModelScope.launch {
            val local = nameLocal.trim()
            val latin = nameLatin.trim()
            val label = unitLabel.trim().ifBlank { defaultUnitLabel(unitType) }
            val qty = quantity.coerceAtLeast(1.0)
            val useQtyBreakdown = label.isNotBlank() && qty > 0 && unitType != UnitType.COUNT

            var catalogDuplicate = false
            var savedItem: MenuItem? = null
            if (saveToCatalog) {
                val dup = itemRepo.findCatalogDuplicate(local, latin, unitType, label)
                if (dup != null) {
                    catalogDuplicate = true
                } else {
                    val catalogLocal = local.ifBlank { latin.ifBlank { "item" } }
                    val catalogLatin = latin.ifBlank { if (isMostlyLatin(catalogLocal)) catalogLocal else "" }
                    val newId = itemRepo.add(
                        MenuItem(
                            nameLocal = catalogLocal,
                            nameLatin = catalogLatin,
                            unitType = unitType,
                            unitLabel = label,
                            pricePerUnit = lineTotal / qty
                        )
                    )
                    savedItem = itemRepo.getById(newId)
                }
            }

            val displayName = local.ifBlank { latin.ifBlank { "item" } }
            val line = if (useQtyBreakdown) {
                BillItem(
                    billId = billId,
                    itemName = displayName,
                    itemNameLatin = latin,
                    unitType = unitType,
                    unitLabel = label,
                    quantity = qty,
                    unitPrice = lineTotal / qty,
                    lineTotal = lineTotal
                )
            } else {
                BillItem(
                    billId = billId,
                    itemName = displayName,
                    itemNameLatin = latin,
                    unitType = UnitType.COUNT,
                    unitLabel = "",
                    quantity = 1.0,
                    unitPrice = lineTotal,
                    lineTotal = lineTotal
                )
            }
            billRepo.addItem(line)

            if (savedItem != null) {
                val key = learnKey.trim().ifBlank { local.ifBlank { latin } }
                if (key.isNotBlank()) itemRepo.learn(key, savedItem.id)
            }
            _suggestions.value = emptyList()
            onResult(if (catalogDuplicate) AddItemResult.DuplicateCatalog else AddItemResult.Success)
        }
    }

    /**
     * Picked from inventory chip without a handwritten amount: uses catalog name,
     * unit type, and pricePerUnit × quantity.
     */
    fun addFromCatalog(item: MenuItem, quantity: Double, learnKey: String?) {
        val id = _activeBillId.value ?: return
        if (quantity <= 0) return
        viewModelScope.launch {
            val lineTotal = quantity * item.pricePerUnit
            val line = BillItem(
                billId = id,
                itemName = item.nameLocal,
                itemNameLatin = item.nameLatin,
                unitType = item.unitType,
                unitLabel = item.unitLabel,
                quantity = quantity,
                unitPrice = item.pricePerUnit,
                lineTotal = lineTotal
            )
            billRepo.addItem(line)
            learnKey?.trim()?.takeIf { it.isNotBlank() }?.let { itemRepo.learn(it, item.id) }
            _suggestions.value = emptyList()
        }
    }

    /** Add from catalog with a custom line total (shopkeeper overrides default price).
     *  When [amountOnly] is true, quantity is derived from catalog unit price (e.g. ₹20 at ₹50/kg → 400 g). */
    fun addFromCatalogWithTotal(
        item: MenuItem,
        quantity: Double,
        lineTotal: Double,
        learnKey: String?,
        amountOnly: Boolean = false
    ) {
        val id = _activeBillId.value ?: return
        if (lineTotal <= 0) return
        viewModelScope.launch {
            val line = buildCatalogBillItem(id, item, lineTotal, quantity, amountOnly)
            billRepo.addItem(line)
            learnKey?.trim()?.takeIf { it.isNotBlank() }?.let { itemRepo.learn(it, item.id) }
            _suggestions.value = emptyList()
        }
    }

    /**
     * One-tap direct add for partially or fully parsed catalog lines.
     * Skips the confirmation dialog — used by the green direct-add chip.
     * Routes by what was handwritten: qty+amount, qty-only (catalog price), or amount-only (qty=1).
     */
    fun addDirectFromCatalog(suggestion: Suggestion) {
        val item = suggestion.item ?: return
        val parsed = suggestion.parsed
        val learnKey = (parsed.matchHint ?: parsed.displayText).trim().ifBlank { null }
        when {
            parsed.parsedQuantity != null && parsed.lineTotal != null ->
                addFromCatalogWithTotal(item, parsed.parsedQuantity, parsed.lineTotal, learnKey)
            parsed.parsedQuantity != null ->
                addFromCatalog(item, parsed.parsedQuantity, learnKey)
            parsed.lineTotal != null ->
                addFromCatalogWithTotal(item, 1.0, parsed.lineTotal, learnKey, amountOnly = true)
            else -> return
        }
    }

    /**
     * Adds a catalog line using [lineTotal]. Optionally updates catalog [pricePerUnit]
     * to lineTotal / quantity when [updateCatalog] is true.
     */
    fun addFromCatalogUpdatingPrice(
        item: MenuItem,
        quantity: Double,
        lineTotal: Double,
        learnKey: String?,
        updateCatalog: Boolean
    ) {
        val id = _activeBillId.value ?: return
        if (lineTotal <= 0 || quantity <= 0) return
        viewModelScope.launch {
            val qty = quantity.coerceAtLeast(1.0)
            val unitPrice = lineTotal / qty
            val catalogItem = if (updateCatalog) {
                val updated = item.copy(pricePerUnit = unitPrice)
                itemRepo.update(updated)
                updated
            } else {
                item
            }
            val line = BillItem(
                billId = id,
                itemName = catalogItem.nameLocal,
                itemNameLatin = catalogItem.nameLatin,
                unitType = catalogItem.unitType,
                unitLabel = catalogItem.unitLabel,
                quantity = qty,
                unitPrice = unitPrice,
                lineTotal = lineTotal
            )
            billRepo.addItem(line)
            learnKey?.trim()?.takeIf { it.isNotBlank() }?.let { itemRepo.learn(it, catalogItem.id) }
            _suggestions.value = emptyList()
        }
    }

    fun updateLineItem(item: BillItem, name: String, lineTotal: Double, quantity: Double? = null) {
        viewModelScope.launch {
            if (lineTotal <= 0) {
                billRepo.removeItem(item)
            } else if (quantity == null) {
                billRepo.updateItem(
                    item.copy(
                        itemName = name.trim(),
                        unitLabel = "",
                        quantity = 1.0,
                        unitPrice = lineTotal,
                        lineTotal = lineTotal
                    )
                )
            } else if (item.unitLabel.isBlank()) {
                billRepo.updateItem(item.copy(itemName = name.trim(), lineTotal = lineTotal, unitPrice = lineTotal))
            } else {
                val qty = quantity.takeIf { it > 0 } ?: item.quantity
                billRepo.updateItem(
                    item.copy(itemName = name.trim(), quantity = qty, lineTotal = lineTotal)
                )
            }
        }
    }

    fun updateCatalogItem(item: MenuItem, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            itemRepo.update(item)
            onResult(true, null)
        }
    }

    /** Sync catalog entry then add handwritten line to the active bill. */
    fun addNewItemToBill(
        catalogItem: MenuItem?,
        nameLocal: String,
        nameLatin: String,
        unitType: String,
        unitLabel: String,
        unitPrice: Double,
        quantity: Double,
        lineTotal: Double,
        learnKey: String,
        amountOnly: Boolean = false,
        onResult: (AddItemResult) -> Unit = {}
    ) {
        val billId = _activeBillId.value ?: return
        if (lineTotal <= 0) return
        viewModelScope.launch {
            val local = nameLocal.trim()
            val latin = nameLatin.trim()
            val label = unitLabel.trim().ifBlank { defaultUnitLabel(unitType) }
            if (catalogItem != null) {
                itemRepo.update(
                    catalogItem.copy(
                        nameLocal = local.ifBlank { catalogItem.nameLocal },
                        nameLatin = latin.ifBlank { catalogItem.nameLatin },
                        unitType = unitType,
                        unitLabel = label,
                        pricePerUnit = unitPrice
                    )
                )
            }
            val displayName = local.ifBlank { latin.ifBlank { "item" } }
            val line = buildHandwrittenBillItem(
                billId = billId,
                nameLocal = displayName,
                nameLatin = latin,
                unitType = unitType,
                unitLabel = label,
                unitPrice = unitPrice,
                quantity = quantity,
                lineTotal = lineTotal,
                amountOnly = amountOnly
            )
            billRepo.addItem(line)
            if (catalogItem != null) {
                val key = learnKey.trim().ifBlank { local.ifBlank { latin } }
                if (key.isNotBlank()) itemRepo.learn(key, catalogItem.id)
            }
            _suggestions.value = emptyList()
            onResult(AddItemResult.Success)
        }
    }

    fun updateLineFromCatalog(line: BillItem, item: MenuItem, qty: Double?, lineTotal: Double) {
        viewModelScope.launch {
            if (lineTotal <= 0) {
                billRepo.removeItem(line)
            } else if (qty != null) {
                billRepo.updateItem(
                    line.copy(
                        itemName = item.nameLocal,
                        itemNameLatin = item.nameLatin,
                        unitType = item.unitType,
                        unitLabel = item.unitLabel,
                        quantity = qty,
                        unitPrice = item.pricePerUnit,
                        lineTotal = lineTotal
                    )
                )
            } else {
                billRepo.updateItem(
                    line.copy(
                        itemName = item.nameLocal,
                        itemNameLatin = item.nameLatin,
                        unitType = item.unitType,
                        unitLabel = "",
                        quantity = 1.0,
                        unitPrice = item.pricePerUnit,
                        lineTotal = lineTotal
                    )
                )
            }
        }
    }

    fun updateQuantity(item: BillItem, quantity: Double) {
        viewModelScope.launch {
            if (quantity <= 0) billRepo.removeItem(item)
            else billRepo.updateItem(item.copy(quantity = quantity, lineTotal = quantity * item.unitPrice))
        }
    }

    fun removeItem(item: BillItem) {
        viewModelScope.launch { billRepo.removeItem(item) }
    }

    fun clearActive() {
        val id = _activeBillId.value ?: return
        viewModelScope.launch {
            val drafts = billRepo.getDrafts()
            billRepo.getBill(id)?.let { billRepo.deleteDraft(it) }
            val remaining = drafts.filter { it.id != id }
            _activeBillId.value = remaining.firstOrNull()?.id ?: billRepo.createDraft("Bill 1")
            _suggestions.value = emptyList()
        }
    }

    /**
     * Completes the active bill (optionally as a loan), optionally prints a receipt,
     * exports CSV, and opens a fresh draft. [onResult] reports the completed bill and
     * any print outcome (null when not printing).
     */
    fun finalizeBill(
        asLoan: Boolean,
        customerName: String,
        customerPhone: String,
        print: Boolean,
        onResult: (Bill?, PrintResult?) -> Unit
    ) {
        val id = _activeBillId.value ?: return
        viewModelScope.launch {
            val settings = settingsRepo.get()
            val items = billRepo.getItems(id)
            if (items.isEmpty()) { onResult(null, null); return@launch }
            val draftBill = billRepo.getBill(id)
            val effectiveLoan = asLoan || (draftBill?.isLoan == true)
            val existingLoan = loanRepo.getLoanByBillId(id)
            val bill = billRepo.completeBill(
                billId = id,
                gstEnabled = settings.gstEnabled,
                cgstPercent = settings.cgstPercent,
                sgstPercent = settings.sgstPercent,
                isLoan = effectiveLoan
            )
            if (bill != null && effectiveLoan) {
                if (existingLoan != null) {
                    loanRepo.updateLoanAmount(existingLoan, bill.total)
                } else if (customerName.isNotBlank()) {
                    val customerId = loanRepo.findOrCreateCustomer(customerName, customerPhone)
                    loanRepo.createLoan(bill.id, customerId, bill.total)
                }
                if (customerName.isNotBlank() && draftBill != null &&
                    (draftBill.name.startsWith("Bill ") || draftBill.name.isBlank())
                ) {
                    billRepo.renameBill(bill, customerName.trim())
                }
            }
            var printResult: PrintResult? = null
            if (print && bill != null) {
                val fields = settingsRepo.getReceiptFields()
                printResult = printer.printReceipt(bill, items, settings, fields, settings.uiLocaleTag)
            }
            csvManager.exportAll()
            val billSettings = settingsWithBillDay()
            val nextNum = billSettings.nextBillNumber + 1
            settingsRepo.save(billSettings.copy(nextBillNumber = nextNum))
            _activeBillId.value = billRepo.createDraft("Bill $nextNum")
            _suggestions.value = emptyList()
            onResult(bill, printResult)
        }
    }
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemRepo: ItemRepository,
    private val settingsRepo: SettingsRepository,
    private val csvManager: CsvManager
) : ViewModel() {

    val items: StateFlow<List<MenuItem>> = itemRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings?> = settingsRepo.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val receiptFields: StateFlow<List<ReceiptField>> = settingsRepo.observeReceiptFields()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addItem(item: MenuItem, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val label = item.unitLabel.trim().ifBlank { defaultUnitLabel(item.unitType) }
            val dup = itemRepo.findCatalogDuplicate(
                item.nameLocal, item.nameLatin, item.unitType, label
            )
            if (dup != null && dup.id != item.id) {
                onResult(false, "An item with this name and unit already exists.")
                return@launch
            }
            itemRepo.add(item.copy(unitLabel = label))
            onResult(true, null)
        }
    }
    fun updateItem(item: MenuItem) { viewModelScope.launch { itemRepo.update(item) } }
    fun deleteItem(item: MenuItem) { viewModelScope.launch { itemRepo.delete(item) } }
    fun moveItem(from: Int, to: Int) {
        val list = items.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val moved = list.removeAt(from)
        list.add(to, moved)
        viewModelScope.launch { itemRepo.reorder(list) }
    }

    fun saveSettings(settings: AppSettings) { viewModelScope.launch { settingsRepo.save(settings) } }

    fun addReceiptField(label: String, value: String) {
        viewModelScope.launch { settingsRepo.addReceiptField(label, value) }
    }
    fun updateReceiptField(field: ReceiptField) { viewModelScope.launch { settingsRepo.updateReceiptField(field) } }
    fun deleteReceiptField(field: ReceiptField) { viewModelScope.launch { settingsRepo.deleteReceiptField(field) } }

    fun exportCsv(onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(csvManager.exportAll()) }
    }

    fun csvHealth(onResult: (Boolean, Boolean) -> Unit) {
        viewModelScope.launch {
            val (internal, external) = csvManager.healthCheck()
            onResult(internal, external)
        }
    }

    fun importCsv(
        section: CsvManager.ImportSection,
        uri: Uri,
        onResult: (CsvManager.SectionImportResult) -> Unit
    ) {
        viewModelScope.launch {
            val name = uri.lastPathSegment ?: "import.csv"
            val temp = java.io.File(context.cacheDir, "csv_import_${System.currentTimeMillis()}.csv")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: run {
                    onResult(CsvManager.SectionImportResult(false, "Could not read file."))
                    return@launch
                }
                onResult(csvManager.importSection(temp, name, section))
            } catch (e: Exception) {
                onResult(CsvManager.SectionImportResult(false, e.message ?: "Import failed"))
            } finally {
                temp.delete()
            }
        }
    }

    fun backupPaths(): List<String> = csvManager.backupLocations()
}

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val loanRepo: LoanRepository
) : ViewModel() {

    private val _tab = MutableStateFlow(PeriodTab.TODAY)
    val tab: StateFlow<PeriodTab> = _tab.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val bills: StateFlow<List<Bill>> = _tab
        .flatMapLatest { t -> val (from, to) = t.range(); billRepo.observeCompleted(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val revenue: StateFlow<Double> = bills
        .map { list -> list.sumOf { it.total } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setTab(t: PeriodTab) { _tab.value = t }

    private val _detailItems = MutableStateFlow<List<BillItem>>(emptyList())
    val detailItems: StateFlow<List<BillItem>> = _detailItems.asStateFlow()

    fun loadDetail(billId: Long) {
        viewModelScope.launch { _detailItems.value = billRepo.getItems(billId) }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            loanRepo.deleteLoansForBill(bill.id)
            billRepo.deleteBill(bill)
        }
    }
}

data class LoanBillDetail(
    val loan: Loan,
    val customerName: String,
    val bill: Bill?,
    val items: List<BillItem>
)

@HiltViewModel
class LoanViewModel @Inject constructor(
    private val loanRepo: LoanRepository,
    private val billRepo: BillRepository
) : ViewModel() {

    val customers: StateFlow<List<CustomerOutstanding>> = loanRepo.observeCustomerOutstanding()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalOutstanding: StateFlow<Double> = loanRepo.observeTotalOutstanding()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _loans = MutableStateFlow<List<Loan>>(emptyList())
    val loans: StateFlow<List<Loan>> = _loans.asStateFlow()

    private val _payments = MutableStateFlow<List<LoanPayment>>(emptyList())
    val payments: StateFlow<List<LoanPayment>> = _payments.asStateFlow()

    private val _loanBillDetail = MutableStateFlow<LoanBillDetail?>(null)
    val loanBillDetail: StateFlow<LoanBillDetail?> = _loanBillDetail.asStateFlow()

    fun loadCustomer(customerId: Long) {
        viewModelScope.launch {
            val loans = loanRepo.getLoansForCustomer(customerId)
            _loans.value = loans
            _payments.value = loans.flatMap { loanRepo.getPayments(it.id) }.sortedByDescending { it.paidAt }
        }
    }

    fun loadLoanBillDetail(loan: Loan, customerName: String) {
        viewModelScope.launch {
            val bill = billRepo.getBill(loan.billId)
            val items = if (bill != null) billRepo.getItems(loan.billId) else emptyList()
            _loanBillDetail.value = LoanBillDetail(loan, customerName, bill, items)
        }
    }

    fun clearLoanBillDetail() {
        _loanBillDetail.value = null
    }

    fun recordPayment(customerId: Long, amount: Double) {
        viewModelScope.launch {
            loanRepo.recordPayment(customerId, amount)
            loadCustomer(customerId)
        }
    }
}

@HiltViewModel
class SpendingViewModel @Inject constructor(
    private val spendRepo: SpendRepository,
    private val csvManager: CsvManager
) : ViewModel() {

    private val _tab = MutableStateFlow(PeriodTab.TODAY)
    val tab: StateFlow<PeriodTab> = _tab.asStateFlow()

    val recent: StateFlow<List<ShopSpend>> = spendRepo.observeRecent(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val spends: StateFlow<List<ShopSpend>> = _tab
        .flatMapLatest { t -> val (from, to) = t.range(); spendRepo.observeBetween(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val total: StateFlow<Double> = spends
        .map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setTab(t: PeriodTab) { _tab.value = t }

    fun add(name: String, amount: Double, spentAt: Long) {
        viewModelScope.launch {
            spendRepo.add(ShopSpend(name = name, amount = amount, spentAt = spentAt))
            csvManager.exportAll()
        }
    }

    fun update(spend: ShopSpend) { viewModelScope.launch { spendRepo.update(spend); csvManager.exportAll() } }
    fun delete(spend: ShopSpend) { viewModelScope.launch { spendRepo.delete(spend); csvManager.exportAll() } }
}

data class AnalysisState(
    val revenue: Double = 0.0,
    val spending: Double = 0.0,
    val net: Double = 0.0,
    val outstanding: Double = 0.0,
    val itemSales: List<ItemSale> = emptyList()
)

@HiltViewModel
class PrinterViewModel @Inject constructor(
    private val printer: PrinterManager,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val connected: StateFlow<Boolean> = printer.connected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val settings: StateFlow<AppSettings?> = settingsRepo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun bluetoothEnabled(): Boolean = printer.bluetoothEnabled()
    fun pairedDevices(): List<PrinterDevice> = printer.pairedDevices()

    fun connect(device: PrinterDevice, onResult: (PrintResult) -> Unit) {
        viewModelScope.launch {
            val s = settingsRepo.get()
            val result = printer.connect(device.mac, s.uiLocaleTag)
            if (result.ok) {
                settingsRepo.save(s.copy(printerMac = device.mac, printerName = device.name))
            }
            onResult(result)
        }
    }

    fun reconnectSaved() {
        viewModelScope.launch {
            val s = settingsRepo.get()
            if (s.printerMac.isNotBlank()) printer.reconnect(s.printerMac)
        }
    }

    fun forget() {
        viewModelScope.launch {
            printer.disconnect()
            val s = settingsRepo.get()
            settingsRepo.save(s.copy(printerMac = "", printerName = ""))
        }
    }

    fun savePaperWidth(mm: Int) {
        viewModelScope.launch { settingsRepo.save(settingsRepo.get().copy(paperWidthMm = mm)) }
    }

    fun saveRupeeFix(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.save(settingsRepo.get().copy(rupeeFix = enabled)) }
    }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val itemRepo: ItemRepository,
    private val recognizer: Recognizer
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepo.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedUiLocale = MutableStateFlow(UiLocales.DEFAULT_TAG)
    val selectedUiLocale: StateFlow<String> = _selectedUiLocale.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<ScriptLanguage?>(null)
    val selectedLanguage: StateFlow<ScriptLanguage?> = _selectedLanguage.asStateFlow()

    private val _enModelDownloaded = MutableStateFlow(false)
    val enModelDownloaded: StateFlow<Boolean> = _enModelDownloaded.asStateFlow()

    private val _regionalModelDownloaded = MutableStateFlow(false)
    val regionalModelDownloaded: StateFlow<Boolean> = _regionalModelDownloaded.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _downloadPhase = MutableStateFlow<String?>(null)
    val downloadPhase: StateFlow<String?> = _downloadPhase.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.ensureInitialized()
            refreshDownloadStatus()
        }
    }

    /** Seeds DB defaults before the UI reads settings (prevents infinite loading spinner). */
    fun ensureReady() {
        viewModelScope.launch { settingsRepo.ensureInitialized() }
    }

    fun refreshDownloadStatus() {
        viewModelScope.launch {
            val s = settingsRepo.get()
            val tag = s.primaryScriptTag
            recognizer.setPrimaryScriptTag(tag)
            _selectedUiLocale.value = s.uiLocaleTag
            _selectedLanguage.value = ScriptLanguages.byMlKitTag(tag)
            _enModelDownloaded.value = recognizer.isEnModelDownloaded()
            _regionalModelDownloaded.value = recognizer.isRegionalModelDownloaded(tag)
        }
    }

    fun checkRegionalPackDownloaded(mlKitTag: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(recognizer.isRegionalModelDownloaded(mlKitTag))
        }
    }

    fun selectUiLocale(tag: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val resolved = UiLocales.resolveTag(tag)
            settingsRepo.save(settingsRepo.get().copy(uiLocaleTag = resolved))
            _selectedUiLocale.value = resolved
            onDone()
        }
    }

    fun selectLanguage(language: ScriptLanguage, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val s = settingsRepo.get()
            settingsRepo.save(
                s.copy(
                    primaryScriptTag = language.mlKitTag,
                    receiptNameMode = preferredReceiptMode(s.receiptNameMode, language.mlKitTag)
                )
            )
            recognizer.setPrimaryScriptTag(language.mlKitTag)
            _selectedLanguage.value = language
            _regionalModelDownloaded.value = recognizer.isRegionalModelDownloaded(language.mlKitTag)
            onDone()
        }
    }

    private fun preferredReceiptMode(current: String, scriptTag: String): String =
        when {
            ScriptLanguages.supportsLocalScriptReceipt(scriptTag) &&
                (current == ReceiptNameMode.ENGLISH || current == ReceiptNameMode.TAMIL_IMAGE) ->
                ReceiptNameMode.LOCAL_IMAGE
            !ScriptLanguages.supportsLocalScriptReceipt(scriptTag) -> ReceiptNameMode.ENGLISH
            else -> current
        }

    fun savePin(pin: String, question: String, answer: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val s = settingsRepo.get()
            val uiLocale = if (s.uiLocaleTag == UiLocales.DEFAULT_TAG) {
                UiLocales.defaultForDevice()
            } else s.uiLocaleTag
            settingsRepo.save(
                s.copy(
                    adminPinHash = Security.sha256(pin),
                    securityQuestion = question,
                    securityAnswerHash = Security.sha256(answer.lowercase().trim()),
                    uiLocaleTag = uiLocale
                )
            )
            _selectedUiLocale.value = uiLocale
            onDone()
        }
    }

    fun downloadAllModels(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _downloading.value = true
            _downloadError.value = null
            val appSettings = settingsRepo.get()
            val tag = appSettings.primaryScriptTag
            val localeTag = appSettings.uiLocaleTag
            recognizer.setPrimaryScriptTag(tag)

            _downloadPhase.value = "English"
            val enOk = try {
                recognizer.downloadEnModel()
            } catch (e: Exception) {
                _downloadError.value = e.message ?: AppStrings.get(StringKey.EnglishPackFailed, localeTag)
                false
            }
            _enModelDownloaded.value = enOk || recognizer.isEnModelDownloaded()

            if (_enModelDownloaded.value) {
                _downloadPhase.value = ScriptLanguages.displayNameForTag(tag)
                val regOk = try {
                    recognizer.downloadRegionalModel(tag)
                } catch (e: Exception) {
                    _downloadError.value = e.message ?: AppStrings.get(StringKey.RegionalPackFailed, localeTag)
                    false
                }
                _regionalModelDownloaded.value = regOk || recognizer.isRegionalModelDownloaded(tag)
            } else if (_downloadError.value == null) {
                _downloadError.value = AppStrings.get(StringKey.CouldNotDownloadEnglish, localeTag)
            }

            if (!_regionalModelDownloaded.value && _enModelDownloaded.value && _downloadError.value == null) {
                _downloadError.value =
                    AppStrings.get(
                        StringKey.CouldNotDownloadRegional,
                        localeTag,
                        ScriptLanguages.displayNameForTag(tag)
                    )
            }

            _downloading.value = false
            _downloadPhase.value = null
            onDone(_enModelDownloaded.value && _regionalModelDownloaded.value)
        }
    }

    fun changePrimaryLanguage(language: ScriptLanguage, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val prevTag = settingsRepo.get().primaryScriptTag
            if (language.mlKitTag == prevTag) {
                onDone(true)
                return@launch
            }
            val s = settingsRepo.get()
            settingsRepo.save(
                s.copy(
                    primaryScriptTag = language.mlKitTag,
                    receiptNameMode = preferredReceiptMode(s.receiptNameMode, language.mlKitTag)
                )
            )
            recognizer.setPrimaryScriptTag(language.mlKitTag)
            itemRepo.clearCorrections()
            _selectedLanguage.value = language
            _regionalModelDownloaded.value = recognizer.isRegionalModelDownloaded(language.mlKitTag)
            onDone(_regionalModelDownloaded.value)
        }
    }

    fun finishSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = settingsRepo.get()
            settingsRepo.save(
                s.copy(
                    setupComplete = true,
                    receiptNameMode = preferredReceiptMode(s.receiptNameMode, s.primaryScriptTag)
                )
            )
            onDone()
        }
    }
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val spendRepo: SpendRepository,
    private val loanRepo: LoanRepository
) : ViewModel() {

    private val _tab = MutableStateFlow(PeriodTab.TODAY)
    val tab: StateFlow<PeriodTab> = _tab.asStateFlow()

    private val _state = MutableStateFlow(AnalysisState())
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    fun setTab(t: PeriodTab) { _tab.value = t; refresh() }

    fun refresh() {
        viewModelScope.launch {
            val (from, to) = _tab.value.range()
            val revenue = billRepo.revenue(from, to)
            val spending = spendRepo.total(from, to)
            val outstanding = loanRepo.totalOutstanding()
            val sales = billRepo.itemSales(from, to)
            _state.value = AnalysisState(revenue, spending, revenue - spending, outstanding, sales)
        }
    }
}
