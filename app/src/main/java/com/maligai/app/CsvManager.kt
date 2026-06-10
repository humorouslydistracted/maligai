package com.maligai.app

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports Room data to CSV. Writes to two locations (app-internal + app-specific external)
 * as dated monthly archives plus an always-updated "_latest" snapshot.
 *
 * Room is the source of truth — the app works fully even if these CSVs are deleted.
 */
@Singleton
class CsvManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billDao: BillDao,
    private val spendDao: SpendDao,
    private val itemDao: ItemDao,
    private val customerLoanDao: CustomerLoanDao
) {
    private val monthFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun internalDir(): File =
        File(context.filesDir, BACKUP_DIR).apply { mkdirs() }

    private fun externalDir(): File? =
        context.getExternalFilesDir(null)?.let { File(it, BACKUP_DIR).apply { mkdirs() } }

    private fun targetDirs(): List<File> = listOfNotNull(internalDir(), externalDir())

    suspend fun healthCheck(): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        val internalOk = try { internalDir().canWrite() } catch (e: Exception) { false }
        val externalOk = try { externalDir()?.canWrite() ?: false } catch (e: Exception) { false }
        internalOk to externalOk
    }

    suspend fun exportAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            val bills = billDao.getCompletedBetween(0, Long.MAX_VALUE, BillStatus.COMPLETE)
            val allItems = bills.associateWith { billDao.getItems(it.id) }
            val spends = spendDao.getAll()
            val menu = itemDao.getAll()
            val outstanding = customerLoanDao.getCustomerOutstanding()

            writeBills(bills)
            writeBillItems(allItems)
            writeSpends(spends)
            writeLoans(outstanding)
            writeMenu(menu)
            writeConsolidated(bills, spends)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun monthOf(ts: Long): String = monthFmt.format(Date(ts))

    private fun write(prefix: String, header: String, rowsByMonth: Map<String, List<String>>, allRows: List<String>) {
        for (dir in targetDirs()) {
            // monthly archives
            for ((month, rows) in rowsByMonth) {
                File(dir, "${prefix}_$month.csv").writeText(buildString {
                    appendLine(header)
                    rows.forEach { appendLine(it) }
                })
            }
            // latest snapshot
            File(dir, "${prefix}_latest.csv").writeText(buildString {
                appendLine(header)
                allRows.forEach { appendLine(it) }
            })
        }
    }

    private fun esc(value: String): String {
        val v = value.replace("\"", "\"\"")
        return "\"$v\""
    }

    private fun money(d: Double): String = String.format(Locale.US, "%.2f", d)

    private fun writeBills(bills: List<Bill>) {
        val header = "BillId,Name,DateTime,Subtotal,CGST,SGST,Total,IsLoan"
        val rows = bills.map { b ->
            listOf(
                b.id.toString(),
                esc(b.name),
                esc(dateTimeFmt.format(Date(b.completedAt ?: b.createdAt))),
                money(b.subtotal), money(b.cgst), money(b.sgst), money(b.total),
                if (b.isLoan) "Yes" else "No"
            ).joinToString(",")
        }
        val byMonth = bills.indices.groupBy { monthOf(bills[it].completedAt ?: bills[it].createdAt) }
            .mapValues { (_, idxs) -> idxs.map { rows[it] } }
        write("maligai_bills", header, byMonth, rows)
    }

    private fun writeBillItems(map: Map<Bill, List<BillItem>>) {
        val header = "BillId,DateTime,Item,Qty,Unit,UnitPrice,LineTotal"
        val allRows = mutableListOf<String>()
        val byMonth = HashMap<String, MutableList<String>>()
        for ((bill, items) in map) {
            val month = monthOf(bill.completedAt ?: bill.createdAt)
            for (it in items) {
                val row = listOf(
                    bill.id.toString(),
                    esc(dateTimeFmt.format(Date(bill.completedAt ?: bill.createdAt))),
                    esc(it.itemName),
                    money(it.quantity),
                    esc(it.unitLabel),
                    money(it.unitPrice),
                    money(it.lineTotal)
                ).joinToString(",")
                allRows.add(row)
                byMonth.getOrPut(month) { mutableListOf() }.add(row)
            }
        }
        write("maligai_bill_items", header, byMonth, allRows)
    }

    private fun writeSpends(spends: List<ShopSpend>) {
        val header = "Id,Name,Amount,DateTime"
        val rows = spends.map { s ->
            listOf(s.id.toString(), esc(s.name), money(s.amount), esc(dateTimeFmt.format(Date(s.spentAt)))).joinToString(",")
        }
        val byMonth = spends.indices.groupBy { monthOf(spends[it].spentAt) }
            .mapValues { (_, idxs) -> idxs.map { rows[it] } }
        write("maligai_spending", header, byMonth, rows)
    }

    private fun writeLoans(outstanding: List<CustomerOutstanding>) {
        val header = "CustomerId,Name,Phone,Outstanding"
        val rows = outstanding.map { c ->
            listOf(c.customerId.toString(), esc(c.name), esc(c.phone), money(c.outstanding)).joinToString(",")
        }
        // loans are a current snapshot, no monthly split
        write("maligai_loans", header, emptyMap(), rows)
    }

    private fun writeMenu(menu: List<MenuItem>) {
        val header = "Id,NameLocal,NameLatin,UnitType,Unit,PricePerUnit,Available"
        val rows = menu.map { m ->
            listOf(
                m.id.toString(), esc(m.nameLocal), esc(m.nameLatin), esc(m.unitType),
                esc(m.unitLabel), money(m.pricePerUnit), if (m.available) "Yes" else "No"
            ).joinToString(",")
        }
        write("maligai_items", header, emptyMap(), rows)
    }

    private fun writeConsolidated(bills: List<Bill>, spends: List<ShopSpend>) {
        val header = "Type,Reference,DateTime,Amount"
        val billRows = bills.map { b ->
            listOf("SALE", esc(b.name), esc(dateTimeFmt.format(Date(b.completedAt ?: b.createdAt))), money(b.total)).joinToString(",")
        }
        val spendRows = spends.map { s ->
            listOf("SPEND", esc(s.name), esc(dateTimeFmt.format(Date(s.spentAt))), money(-s.amount)).joinToString(",")
        }
        val all = billRows + spendRows
        val byMonth = HashMap<String, MutableList<String>>()
        bills.forEach { b ->
            byMonth.getOrPut(monthOf(b.completedAt ?: b.createdAt)) { mutableListOf() }
                .add(listOf("SALE", esc(b.name), esc(dateTimeFmt.format(Date(b.completedAt ?: b.createdAt))), money(b.total)).joinToString(","))
        }
        spends.forEach { s ->
            byMonth.getOrPut(monthOf(s.spentAt)) { mutableListOf() }
                .add(listOf("SPEND", esc(s.name), esc(dateTimeFmt.format(Date(s.spentAt))), money(-s.amount)).joinToString(","))
        }
        write("maligai_consolidated", header, byMonth, all)
    }

    fun backupLocations(): List<String> = targetDirs().map { it.absolutePath }

    fun listBackupFiles(): List<String> = targetDirs()
        .flatMap { dir -> dir.listFiles()?.filter { it.extension == "csv" }?.map { it.name } ?: emptyList() }
        .distinct()
        .sorted()

    /* --------------------------------------------------------------------- Import */

    enum class ImportSection { ITEMS, SPENDING }

    data class SectionImportResult(
        val success: Boolean,
        val message: String,
        val rowsImported: Int = 0
    )

    private val ITEMS_HEADERS = listOf(
        "Id", "NameLocal", "NameLatin", "UnitType", "Unit", "PricePerUnit", "Available"
    )
    private val ITEMS_HEADERS_LEGACY = listOf(
        "Id", "NameTamil", "NameLatin", "UnitType", "Unit", "PricePerUnit", "Available"
    )
    private val SPENDING_HEADERS = listOf("Id", "Name", "Amount", "DateTime")
    private val ITEMS_FILENAME = Regex("""(?i)maligai_items(_latest|_\d{4}-\d{2})\.csv""")
    private val SPENDING_FILENAME = Regex("""(?i)maligai_spending(_latest|_\d{4}-\d{2})\.csv""")

    fun expectedFilenameHint(section: ImportSection): String = when (section) {
        ImportSection.ITEMS -> "maligai_items_latest.csv or maligai_items_YYYY-MM.csv"
        ImportSection.SPENDING -> "maligai_spending_latest.csv or maligai_spending_YYYY-MM.csv"
    }

    fun validateFilename(fileName: String, section: ImportSection): Boolean {
        val name = fileName.substringAfterLast('/')
        return when (section) {
            ImportSection.ITEMS -> ITEMS_FILENAME.matches(name)
            ImportSection.SPENDING -> SPENDING_FILENAME.matches(name)
        }
    }

    suspend fun importSection(
        file: java.io.File,
        fileName: String,
        section: ImportSection
    ): SectionImportResult = withContext(Dispatchers.IO) {
        try {
            if (!validateFilename(fileName, section)) {
                return@withContext SectionImportResult(
                    success = false,
                    message = "Expected filename: ${expectedFilenameHint(section)}"
                )
            }
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return@withContext SectionImportResult(false, "File is empty.")
            }
            val headers = parseCsvLine(lines.first()).map { it.trim() }
            val expectedOptions = when (section) {
                ImportSection.ITEMS -> listOf(ITEMS_HEADERS, ITEMS_HEADERS_LEGACY)
                ImportSection.SPENDING -> listOf(SPENDING_HEADERS)
            }
            val headersLower = headers.map { it.lowercase() }
            if (expectedOptions.none { opt -> headersLower == opt.map { h -> h.lowercase() } }) {
                val hint = when (section) {
                    ImportSection.ITEMS -> "${ITEMS_HEADERS.joinToString(", ")} (NameTamil accepted for older exports)"
                    ImportSection.SPENDING -> SPENDING_HEADERS.joinToString(", ")
                }
                return@withContext SectionImportResult(
                    success = false,
                    message = "Invalid columns. Expected: $hint"
                )
            }
            val rows = lines.drop(1)
            val imported = when (section) {
                ImportSection.ITEMS -> importItems(rows)
                ImportSection.SPENDING -> importSpending(rows)
            }
            SectionImportResult(true, "Imported $imported row(s).", imported)
        } catch (e: Exception) {
            SectionImportResult(false, e.message ?: "Import failed")
        }
    }

    private suspend fun importItems(rows: List<String>): Int {
        itemDao.deleteAll()
        var order = 0
        for (row in rows) {
            val cols = parseCsvLine(row)
            if (cols.size < 7) continue
            val nameLocal = unesc(cols[1])
            if (nameLocal.isBlank()) continue
            itemDao.insert(
                MenuItem(
                    nameLocal = nameLocal,
                    nameLatin = unesc(cols[2]),
                    unitType = unesc(cols[3]).ifBlank { UnitType.COUNT },
                    unitLabel = unesc(cols[4]).ifBlank { defaultUnitLabel(unesc(cols[3])) },
                    pricePerUnit = cols[5].toDoubleOrNull() ?: 0.0,
                    available = unesc(cols[6]).equals("yes", ignoreCase = true),
                    sortOrder = ++order
                )
            )
        }
        return order
    }

    private suspend fun importSpending(rows: List<String>): Int {
        spendDao.deleteAll()
        var count = 0
        for (row in rows) {
            val cols = parseCsvLine(row)
            if (cols.size < 4) continue
            val name = unesc(cols[1])
            val amount = cols[2].toDoubleOrNull() ?: continue
            if (name.isBlank() || amount <= 0) continue
            val spentAt = parseDateTime(unesc(cols[3])) ?: System.currentTimeMillis()
            spendDao.insert(ShopSpend(name = name, amount = amount, spentAt = spentAt))
            count++
        }
        return count
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else inQuotes = false
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun unesc(value: String): String =
        value.trim().removeSurrounding("\"").replace("\"\"", "\"")

    private fun parseDateTime(raw: String): Long? = try {
        dateTimeFmt.parse(raw)?.time
    } catch (_: Exception) {
        null
    }

    companion object {
        const val BACKUP_DIR = "MaligaiBackup"
    }
}
