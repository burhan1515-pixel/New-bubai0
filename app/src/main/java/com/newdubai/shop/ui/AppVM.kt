package com.newdubai.shop.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.newdubai.shop.data.*
import com.newdubai.shop.util.Prefs
import com.newdubai.shop.util.Snapshot
import com.newdubai.shop.util.Snapshots
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppVM(app: Application) : AndroidViewModel(app) {
    val db = AppDb.get(app)
    val prefs = Prefs(app)

    var lang by mutableStateOf(prefs.lang)
    var inch by mutableStateOf(prefs.unit == "in")
    var locked by mutableStateOf(true)
    var fails = 0
    var lockedUntil = 0L

    private fun <T> flowState(f: kotlinx.coroutines.flow.Flow<List<T>>) =
        f.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customers = flowState(db.customers().flow())
    val orders = flowState(db.orders().flow())
    val payments = flowState(db.payments().flow())
    val fabrics = flowState(db.fabrics().flow())
    val purchases = flowState(db.purchases().flow())
    val sales = flowState(db.sales().flow())
    val expenses = flowState(db.expenses().flow())
    val measurements = flowState(db.measurements().flow())

    init {
        // daily automatic backup on app start (in addition to the scheduled worker)
        viewModelScope.launch {
            runCatching { if (prefs.autoBackup) Snapshots.writeAuto(app, Snapshots.build(db)) }
        }
    }

    fun changeLang(l: String) { lang = l; prefs.lang = l }
    fun changeInch(b: Boolean) { inch = b; prefs.unit = if (b) "in" else "cm" }

    fun tryUnlock(secret: String, isPin: Boolean): String? {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedUntil) return "lockwait"
        if (prefs.verify(secret, isPin)) { fails = 0; locked = false; return null }
        fails++
        if (fails >= 5) { fails = 0; lockedUntil = now + 30_000; return "lockwait" }
        return "wrong"
    }

    private fun tx(done: (String?) -> Unit = {}, block: suspend () -> String?) {
        viewModelScope.launch {
            val r = try { db.withTransaction { block() } } catch (e: Exception) { "err_db" }
            done(r)
        }
    }

    /* customers */
    fun saveCustomer(c: Customer, done: (String?) -> Unit) = tx(done) {
        if (c.firstName.isBlank()) return@tx "req"
        if (!Regex("^[0-9+\\s-]{7,16}$").matches(c.phone)) return@tx "badphone"
        db.customers().upsert(c); null
    }

    fun deleteCustomer(c: Customer, done: (String?) -> Unit) = tx(done) {
        if (db.orders().list().any { it.customerId == c.id }) return@tx "hasord"
        db.measurements().list().filter { it.customerId == c.id }.forEach { db.measurements().delete(it) }
        db.customers().delete(c); null
    }

    /* orders */
    fun saveOrder(o: TailorOrder, advance: Double, meas: MeasurementRec?, done: (String?) -> Unit) = tx(done) {
        if (o.customerId == 0L) return@tx "customer"
        if (o.tailorPrice < 0 || o.fabricPrice < 0 || advance < 0) return@tx "req"
        val old = if (o.id != 0L) db.orders().list().find { it.id == o.id } else null
        val fabs = db.fabrics().list().associateBy { it.id }.toMutableMap()
        if (old != null && old.fabricSource == "shop") {
            fabs[old.fabricId]?.let { fabs[it.id] = it.copy(stock = it.stock + old.meters) }
        }
        var order = o
        if (o.fabricSource == "shop") {
            val f = fabs[o.fabricId] ?: return@tx "fname"
            if (o.meters <= 0) return@tx "meters"
            if (f.stock < o.meters - 1e-9) return@tx "stockerr"
            fabs[f.id] = f.copy(stock = f.stock - o.meters)
        } else order = o.copy(fabricId = 0, meters = 0.0, fabricPrice = 0.0)
        val total = order.fabricPrice + order.tailorPrice
        val pays = db.payments().list()
        if (old != null) { if (total < Calc.paid(old, pays) - 1e-9) return@tx "overpay" }
        else if (advance > total + 1e-9) return@tx "overpay"
        fabs.values.forEach { db.fabrics().upsert(it) }
        val no = old?.no ?: run {
            val n = (db.orders().list().maxOfOrNull { it.no.removePrefix("ND-").toIntOrNull() ?: 0 } ?: 0) + 1
            "ND-" + n.toString().padStart(4, '0')
        }
        val id = db.orders().upsert(order.copy(no = no))
        val oid = if (order.id != 0L) order.id else id
        if (old == null && advance > 0) {
            db.payments().upsert(Payment(orderId = oid, customerId = order.customerId, amount = advance, date = order.orderDate, kind = "adv"))
        }
        if (meas != null) db.measurements().upsert(meas.copy(orderNo = no))
        null
    }

    fun deleteOrder(o: TailorOrder) = tx {
        if (o.fabricSource == "shop") {
            db.fabrics().list().find { it.id == o.fabricId }?.let { db.fabrics().upsert(it.copy(stock = it.stock + o.meters)) }
        }
        db.payments().list().filter { it.orderId == o.id }.forEach { db.payments().delete(it) }
        db.orders().delete(o); null
    }

    fun setStatus(o: TailorOrder, s: String) = tx { db.orders().upsert(o.copy(status = s)); null }

    fun addPayment(orderId: Long, amount: Double, date: String, done: (String?) -> Unit) = tx(done) {
        val o = db.orders().list().find { it.id == orderId } ?: return@tx "err_db"
        if (amount <= 0) return@tx "amt"
        val rem = Calc.balance(o, db.payments().list())
        if (amount > rem + 0.001) return@tx "overpay"
        db.payments().upsert(Payment(orderId = o.id, customerId = o.customerId, amount = amount, date = date,
            kind = if (rem - amount < 0.01) "full" else "part"))
        null
    }

    /* measurements */
    fun saveMeasurement(m: MeasurementRec, done: (String?) -> Unit) = tx(done) {
        if (m.customerId == 0L) return@tx "customer"
        db.measurements().upsert(m); null
    }
    fun deleteMeasurement(m: MeasurementRec) = tx { db.measurements().delete(m); null }

    /* fabric */
    fun saveFabric(f: Fabric, initialStock: Double, done: (String?) -> Unit) = tx(done) {
        if (f.name.isBlank()) return@tx "req"
        if (f.buyPrice < 0 || f.sellPrice < 0 || f.stock < 0) return@tx "req"
        val id = db.fabrics().upsert(f)
        if (f.id == 0L && initialStock > 0) {
            db.purchases().upsert(Purchase(fabricId = id, meters = initialStock, pricePerMeter = f.buyPrice, date = LocalDate.now().toString()))
        }
        null
    }

    fun deleteFabric(f: Fabric, done: (String?) -> Unit) = tx(done) {
        if (db.orders().list().any { it.fabricId == f.id }) return@tx "hasord"
        db.purchases().list().filter { it.fabricId == f.id }.forEach { db.purchases().delete(it) }
        db.sales().list().filter { it.fabricId == f.id }.forEach { db.sales().delete(it) }
        db.fabrics().delete(f); null
    }

    fun buyFabric(f: Fabric, meters: Double, ppm: Double, date: String, done: (String?) -> Unit) = tx(done) {
        if (meters <= 0 || ppm < 0) return@tx "meters"
        val cur = db.fabrics().list().find { it.id == f.id } ?: return@tx "err_db"
        db.fabrics().upsert(cur.copy(stock = cur.stock + meters, buyPrice = ppm))
        db.purchases().upsert(Purchase(fabricId = f.id, meters = meters, pricePerMeter = ppm, date = date)); null
    }

    fun sellFabric(f: Fabric, meters: Double, ppm: Double, date: String, done: (String?) -> Unit) = tx(done) {
        if (meters <= 0 || ppm < 0) return@tx "meters"
        val cur = db.fabrics().list().find { it.id == f.id } ?: return@tx "err_db"
        if (cur.stock < meters - 1e-9) return@tx "stockerr"
        db.fabrics().upsert(cur.copy(stock = cur.stock - meters))
        db.sales().upsert(Sale(fabricId = f.id, meters = meters, pricePerMeter = ppm, date = date)); null
    }

    /* expenses */
    fun saveExpense(e: Expense, done: (String?) -> Unit) = tx(done) {
        if (e.title.isBlank()) return@tx "req"
        if (e.amount <= 0) return@tx "amt"
        db.expenses().upsert(e); null
    }
    fun deleteExpense(e: Expense) = tx { db.expenses().delete(e); null }

    /* backup */
    suspend fun snapshot(): Snapshot = Snapshots.build(db)

    fun restore(s: Snapshot, done: (String?) -> Unit) {
        viewModelScope.launch {
            val r = try {
                Snapshots.writeAuto(getApplication(), Snapshots.build(db), "pre_restore")
                db.withTransaction {
                    db.customers().clear(); db.orders().clear(); db.payments().clear(); db.fabrics().clear()
                    db.purchases().clear(); db.sales().clear(); db.expenses().clear(); db.measurements().clear()
                    db.customers().insertAll(s.customers); db.orders().insertAll(s.orders)
                    db.payments().insertAll(s.payments); db.fabrics().insertAll(s.fabrics)
                    db.purchases().insertAll(s.purchases); db.sales().insertAll(s.sales)
                    db.expenses().insertAll(s.expenses); db.measurements().insertAll(s.measurements)
                }
                null
            } catch (e: Exception) { "err_db" }
            done(r)
        }
    }
}
