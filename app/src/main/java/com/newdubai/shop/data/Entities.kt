package com.newdubai.shop.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String, val lastName: String = "", val phone: String,
    val address: String = "", val notes: String = "", val regDate: String
)

@Entity(tableName = "orders")
data class TailorOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val no: String, val customerId: Long, val type: String,
    val fabricSource: String, val fabricId: Long = 0, val meters: Double = 0.0,
    val fabricPrice: Double = 0.0, val tailorPrice: Double,
    val orderDate: String, val deliveryDate: String, val status: String,
    val notes: String = "", val measurements: String = "", val unit: String = "cm"
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long, val customerId: Long, val amount: Double,
    val date: String, val kind: String
)

@Entity(tableName = "fabrics")
data class Fabric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val fabricType: String = "", val buyPrice: Double,
    val sellPrice: Double, val stock: Double = 0.0, val photo: String = "",
    @ColumnInfo(defaultValue = "'#FFFFFF'") val color: String = "#FFFFFF"
)

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fabricId: Long, val meters: Double, val pricePerMeter: Double, val date: String
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fabricId: Long, val meters: Double, val pricePerMeter: Double,
    val date: String, val customerId: Long = 0
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val amount: Double, val date: String,
    val category: String, val notes: String = ""
)

@Entity(tableName = "measurements")
data class MeasurementRec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long, val grp: String, val type: String, val date: String,
    val vals: String, val unit: String, val orderNo: String = ""
)

interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: T): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(l: List<T>)
    @Delete suspend fun delete(e: T)
}

@Dao interface CustomerDao : BaseDao<Customer> {
    @Query("SELECT * FROM customers ORDER BY id DESC") fun flow(): Flow<List<Customer>>
    @Query("SELECT * FROM customers") suspend fun list(): List<Customer>
    @Query("DELETE FROM customers") suspend fun clear()
}
@Dao interface OrderDao : BaseDao<TailorOrder> {
    @Query("SELECT * FROM orders ORDER BY id DESC") fun flow(): Flow<List<TailorOrder>>
    @Query("SELECT * FROM orders") suspend fun list(): List<TailorOrder>
    @Query("DELETE FROM orders") suspend fun clear()
}
@Dao interface PaymentDao : BaseDao<Payment> {
    @Query("SELECT * FROM payments ORDER BY date DESC, id DESC") fun flow(): Flow<List<Payment>>
    @Query("SELECT * FROM payments") suspend fun list(): List<Payment>
    @Query("DELETE FROM payments") suspend fun clear()
}
@Dao interface FabricDao : BaseDao<Fabric> {
    @Query("SELECT * FROM fabrics ORDER BY name") fun flow(): Flow<List<Fabric>>
    @Query("SELECT * FROM fabrics") suspend fun list(): List<Fabric>
    @Query("DELETE FROM fabrics") suspend fun clear()
}
@Dao interface PurchaseDao : BaseDao<Purchase> {
    @Query("SELECT * FROM purchases ORDER BY date DESC, id DESC") fun flow(): Flow<List<Purchase>>
    @Query("SELECT * FROM purchases") suspend fun list(): List<Purchase>
    @Query("DELETE FROM purchases") suspend fun clear()
}
@Dao interface SaleDao : BaseDao<Sale> {
    @Query("SELECT * FROM sales ORDER BY date DESC, id DESC") fun flow(): Flow<List<Sale>>
    @Query("SELECT * FROM sales") suspend fun list(): List<Sale>
    @Query("DELETE FROM sales") suspend fun clear()
}
@Dao interface ExpenseDao : BaseDao<Expense> {
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC") fun flow(): Flow<List<Expense>>
    @Query("SELECT * FROM expenses") suspend fun list(): List<Expense>
    @Query("DELETE FROM expenses") suspend fun clear()
}
@Dao interface MeasurementDao : BaseDao<MeasurementRec> {
    @Query("SELECT * FROM measurements ORDER BY date DESC, id DESC") fun flow(): Flow<List<MeasurementRec>>
    @Query("SELECT * FROM measurements") suspend fun list(): List<MeasurementRec>
    @Query("DELETE FROM measurements") suspend fun clear()
}

@Database(
    entities = [Customer::class, TailorOrder::class, Payment::class, Fabric::class,
        Purchase::class, Sale::class, Expense::class, MeasurementRec::class],
    version = 2, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun orders(): OrderDao
    abstract fun payments(): PaymentDao
    abstract fun fabrics(): FabricDao
    abstract fun purchases(): PurchaseDao
    abstract fun sales(): SaleDao
    abstract fun expenses(): ExpenseDao
    abstract fun measurements(): MeasurementDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE fabrics ADD COLUMN color TEXT NOT NULL DEFAULT '#FFFFFF'"
                )
            }
        }

        @Volatile private var inst: AppDb? = null

        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(
                c.applicationContext,
                AppDb::class.java,
                "new_dubai.db"
            ).addMigrations(MIGRATION_1_2)
                .build().also { inst = it }
        }
    }
}
