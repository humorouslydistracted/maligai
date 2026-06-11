package com.maligai.app

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/* ----------------------------------------------------------------------------
 * Constants
 * ------------------------------------------------------------------------- */

object UnitType {
    const val WEIGHT = "WEIGHT" // kg / g, price per kg
    const val VOLUME = "VOLUME" // litre / ml, price per litre
    const val COUNT = "COUNT"   // piece / packet, price per piece
}

object BillStatus {
    const val DRAFT = "DRAFT"
    const val COMPLETE = "COMPLETE"
}

object ReceiptNameMode {
    const val ENGLISH = "ENGLISH"
    const val LOCAL_IMAGE = "LOCAL_IMAGE"
    /** Legacy value stored in DB before multi-language support */
    const val TAMIL_IMAGE = "TAMIL_IMAGE"
}

object ThemeMode {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

/* ----------------------------------------------------------------------------
 * Entities
 * ------------------------------------------------------------------------- */

@Entity(tableName = "items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nameTamil")
    val nameLocal: String,
    val nameLatin: String = "",
    val unitType: String = UnitType.COUNT,
    val unitLabel: String = "piece",
    val pricePerUnit: Double = 0.0,
    val available: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val status: String = BillStatus.DRAFT,
    val subtotal: Double = 0.0,
    val cgst: Double = 0.0,
    val sgst: Double = 0.0,
    val total: Double = 0.0,
    val isLoan: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "bill_items")
data class BillItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val itemName: String,
    val itemNameLatin: String = "",
    val unitType: String = UnitType.COUNT,
    val unitLabel: String = "piece",
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = ""
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val customerId: Long,
    val amount: Double,
    val outstanding: Double,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "loan_payments")
data class LoanPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amount: Double,
    val paidAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shop_spends")
data class ShopSpend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val spentAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val adminPinHash: String = "",
    val securityQuestion: String = "",
    val securityAnswerHash: String = "",
    val gstEnabled: Boolean = false,
    val gstPercent: Double = 0.0,
    val cgstPercent: Double = 0.0,
    val sgstPercent: Double = 0.0,
    val receiptNameMode: String = ReceiptNameMode.ENGLISH,
    val footerText: String = "Thank you! Visit Again",
    val paperWidthMm: Int = 80,
    val receiptDotsTop: Int = 1,
    val receiptDotsBottom: Int = 2,
    val rupeeFix: Boolean = false,
    val printerMac: String = "",
    val printerName: String = "",
    val primaryScriptTag: String = ScriptLanguages.DEFAULT_TAG,
    @ColumnInfo(defaultValue = "'en'") val uiLocaleTag: String = "en",
    val biometricUnlockEnabled: Boolean = false,
    val themeMode: String = ThemeMode.SYSTEM,
    val setupComplete: Boolean = false,
    /** Next bill tab number (Bill 1, Bill 2, …). Resets daily at midnight and via manual reset. */
    @ColumnInfo(defaultValue = "1") val nextBillNumber: Int = 1,
    /** Local midnight millis for the day [nextBillNumber] applies to. */
    @ColumnInfo(defaultValue = "0") val billCounterDay: Long = 0
)

@Entity(tableName = "receipt_fields")
data class ReceiptField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val value: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(tableName = "recognition_corrections")
data class RecognitionCorrection(
    @PrimaryKey val recognizedText: String,
    val itemId: Long,
    @ColumnInfo(defaultValue = "1") val hitCount: Int = 1
)

/* ----------------------------------------------------------------------------
 * Relations / projections
 * ------------------------------------------------------------------------- */

data class CustomerOutstanding(
    val customerId: Long,
    val name: String,
    val phone: String,
    val outstanding: Double
)

data class ItemSale(
    val itemName: String,
    val totalQty: Double,
    val totalRevenue: Double
)

/* ----------------------------------------------------------------------------
 * DAOs
 * ------------------------------------------------------------------------- */

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY sortOrder ASC, nameTamil ASC")
    fun observeAll(): Flow<List<MenuItem>>

    @Query("SELECT * FROM items WHERE available = 1 ORDER BY sortOrder ASC, nameTamil ASC")
    suspend fun getAvailable(): List<MenuItem>

    @Query("SELECT * FROM items ORDER BY sortOrder ASC, nameTamil ASC")
    suspend fun getAll(): List<MenuItem>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): MenuItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MenuItem): Long

    @Update
    suspend fun update(item: MenuItem)

    @Delete
    suspend fun delete(item: MenuItem)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM items")
    suspend fun maxSortOrder(): Int
}

@Dao
interface BillDao {
    @Insert
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBill(id: Long): Bill?

    @Query("SELECT * FROM bills WHERE status = :status ORDER BY createdAt ASC")
    fun observeByStatus(status: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<Bill>

    @Query("SELECT * FROM bills WHERE status = :status AND completedAt BETWEEN :from AND :to ORDER BY completedAt DESC")
    fun observeCompletedBetween(from: Long, to: Long, status: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE status = :status AND completedAt BETWEEN :from AND :to ORDER BY completedAt DESC")
    suspend fun getCompletedBetween(from: Long, to: Long, status: String): List<Bill>

    @Insert
    suspend fun insertItem(item: BillItem): Long

    @Insert
    suspend fun insertItems(items: List<BillItem>)

    @Update
    suspend fun updateItem(item: BillItem)

    @Delete
    suspend fun deleteItem(item: BillItem)

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY id ASC")
    fun observeItems(billId: Long): Flow<List<BillItem>>

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY id ASC")
    suspend fun getItems(billId: Long): List<BillItem>

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    suspend fun deleteItemsForBill(billId: Long)

    @Query("SELECT COALESCE(SUM(total), 0) FROM bills WHERE status = :status AND completedAt BETWEEN :from AND :to")
    suspend fun revenueBetween(from: Long, to: Long, status: String): Double

    @Query(
        """
        SELECT bi.itemName AS itemName, SUM(bi.quantity) AS totalQty, SUM(bi.lineTotal) AS totalRevenue
        FROM bill_items bi
        INNER JOIN bills b ON b.id = bi.billId
        WHERE b.status = :status AND b.completedAt BETWEEN :from AND :to
        GROUP BY bi.itemName
        ORDER BY totalQty DESC
        """
    )
    suspend fun itemSalesBetween(from: Long, to: Long, status: String): List<ItemSale>
}

@Dao
interface CustomerLoanDao {
    @Insert
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getCustomers(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomer(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun findCustomerByName(name: String): Customer?

    @Insert
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    @Delete
    suspend fun deleteLoan(loan: Loan)

    @Query("SELECT * FROM loans WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getLoansForCustomer(customerId: Long): List<Loan>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoan(id: Long): Loan?

    @Query("SELECT * FROM loans WHERE billId = :billId LIMIT 1")
    suspend fun getLoanByBillId(billId: Long): Loan?

    @Query("DELETE FROM loans WHERE billId = :billId")
    suspend fun deleteLoansForBill(billId: Long)

    @Insert
    suspend fun insertPayment(payment: LoanPayment): Long

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId ORDER BY paidAt DESC")
    suspend fun getPayments(loanId: Long): List<LoanPayment>

    @Query("SELECT COALESCE(SUM(outstanding), 0) FROM loans")
    fun observeTotalOutstanding(): Flow<Double>

    @Query("SELECT COALESCE(SUM(outstanding), 0) FROM loans")
    suspend fun totalOutstanding(): Double

    @Query(
        """
        SELECT c.id AS customerId, c.name AS name, c.phone AS phone,
               COALESCE(SUM(l.outstanding), 0) AS outstanding
        FROM customers c
        LEFT JOIN loans l ON l.customerId = c.id
        GROUP BY c.id
        HAVING outstanding > 0
        ORDER BY outstanding DESC
        """
    )
    fun observeCustomerOutstanding(): Flow<List<CustomerOutstanding>>

    @Query(
        """
        SELECT c.id AS customerId, c.name AS name, c.phone AS phone,
               COALESCE(SUM(l.outstanding), 0) AS outstanding
        FROM customers c
        LEFT JOIN loans l ON l.customerId = c.id
        GROUP BY c.id
        HAVING outstanding > 0
        ORDER BY outstanding DESC
        """
    )
    suspend fun getCustomerOutstanding(): List<CustomerOutstanding>
}

@Dao
interface SpendDao {
    @Insert
    suspend fun insert(spend: ShopSpend): Long

    @Update
    suspend fun update(spend: ShopSpend)

    @Delete
    suspend fun delete(spend: ShopSpend)

    @Query("SELECT * FROM shop_spends ORDER BY spentAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ShopSpend>>

    @Query("SELECT * FROM shop_spends WHERE spentAt BETWEEN :from AND :to ORDER BY spentAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<ShopSpend>>

    @Query("SELECT * FROM shop_spends WHERE spentAt BETWEEN :from AND :to ORDER BY spentAt DESC")
    suspend fun getBetween(from: Long, to: Long): List<ShopSpend>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM shop_spends WHERE spentAt BETWEEN :from AND :to")
    suspend fun totalBetween(from: Long, to: Long): Double

    @Query("SELECT * FROM shop_spends ORDER BY spentAt DESC")
    suspend fun getAll(): List<ShopSpend>

    @Query("DELETE FROM shop_spends")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettings)

    // Receipt fields
    @Query("SELECT * FROM receipt_fields ORDER BY sortOrder ASC")
    fun observeReceiptFields(): Flow<List<ReceiptField>>

    @Query("SELECT * FROM receipt_fields ORDER BY sortOrder ASC")
    suspend fun getReceiptFields(): List<ReceiptField>

    @Insert
    suspend fun insertReceiptField(field: ReceiptField): Long

    @Update
    suspend fun updateReceiptField(field: ReceiptField)

    @Delete
    suspend fun deleteReceiptField(field: ReceiptField)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM receipt_fields")
    suspend fun maxReceiptFieldOrder(): Int
}

@Dao
interface CorrectionDao {
    @Query("SELECT * FROM recognition_corrections WHERE recognizedText = :text LIMIT 1")
    suspend fun get(text: String): RecognitionCorrection?

    @Query("SELECT * FROM recognition_corrections")
    suspend fun getAll(): List<RecognitionCorrection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(correction: RecognitionCorrection)

    @Query("UPDATE recognition_corrections SET hitCount = hitCount + 1, itemId = :itemId WHERE recognizedText = :text")
    suspend fun bump(text: String, itemId: Long)

    @Query("DELETE FROM recognition_corrections WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    @Query("DELETE FROM recognition_corrections")
    suspend fun deleteAll()
}

/* ----------------------------------------------------------------------------
 * Database
 * ------------------------------------------------------------------------- */

@Database(
    entities = [
        MenuItem::class,
        Bill::class,
        BillItem::class,
        Customer::class,
        Loan::class,
        LoanPayment::class,
        ShopSpend::class,
        AppSettings::class,
        ReceiptField::class,
        RecognitionCorrection::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun billDao(): BillDao
    abstract fun customerLoanDao(): CustomerLoanDao
    abstract fun spendDao(): SpendDao
    abstract fun settingsDao(): SettingsDao
    abstract fun correctionDao(): CorrectionDao
}
