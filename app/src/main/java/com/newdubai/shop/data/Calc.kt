package com.newdubai.shop.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Stats(
    val cash: Double, val tailoring: Double, val fabricIncome: Double,
    val purchases: Double, val other: Double
) { val net: Double get() = tailoring + fabricIncome - purchases - other }

data class Alert(val kind: String, val text: String)

val TailorOrder.total: Double get() = fabricPrice + tailorPrice
fun Customer.fullName(): String = "$firstName $lastName".trim()

object Calc {
    val STATUSES = listOf("new", "wait", "prog", "done", "deliv")
    val MEASURES = listOf(
        "قد", "اوږه / شانه", "لستوڼی", "آستین سور", "یخن / غاړه", "چپاتي",
        "بغل", "دامن", "تمبان", "پاچه", "پرتوګ", "پایڅې"
    )
    val MEN = listOf(
        "m_sq", "m_wc", "m_ct", "m_sh", "m_tr", "m_ot",
        "m_in_kurta", "m_in_sherwani", "m_in_achkan", "m_in_dhoti", "m_in_bandhgala",
        "m_pk_kurta", "m_pk_pathani", "m_pk_prince",
        "m_cn_tang", "m_cn_changshan", "m_cn_hanfu",
        "m_ar_thobe", "m_ar_kandura",
        "m_af_perahan", "m_af_chapan", "m_af_sadri",
        "m_ir_qaba", "m_kr_hanbok",
        "m_west_suit", "m_jacket", "m_uniform"
    )
    val KIDS = listOf(
        "c_sq", "c_wc", "c_st", "c_ot",
        "c_in_kurta", "c_in_sherwani", "c_pk_suit", "c_cn_tang", "c_af_perahan", "c_west_suit", "c_uniform"
    )
    val EXP_CATS = listOf("rent", "elec", "sup", "trn", "oth")

    fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
    fun fmt(v: Double): String {
        val r = round2(v)
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }
    fun cmToIn(v: Double) = v / 2.54
    fun inToCm(v: Double) = v * 2.54
    fun isMen(type: String) = type.startsWith("m_")

    fun paid(o: TailorOrder, p: List<Payment>) = p.filter { it.orderId == o.id }.sumOf { it.amount }
    fun balance(o: TailorOrder, p: List<Payment>) = round2(o.total - paid(o, p))
    fun customerDebt(cid: Long, orders: List<TailorOrder>, pays: List<Payment>) =
        round2(orders.filter { it.customerId == cid }.sumOf { balance(it, pays) })
    fun totalDebt(orders: List<TailorOrder>, pays: List<Payment>) =
        round2(orders.sumOf { balance(it, pays) })

    fun parseMeasures(s: String): List<Double?> {
        val l = s.split(",")
        return List(12) { l.getOrNull(it)?.toDoubleOrNull() }
    }
    fun joinMeasures(l: List<Double?>): String = l.joinToString(",") { it?.toString() ?: "" }

    fun stats(
        from: String, to: String, orders: List<TailorOrder>, pays: List<Payment>,
        sales: List<Sale>, purchases: List<Purchase>, expenses: List<Expense>
    ): Stats {
        fun inr(d: String) = d >= from && d <= to
        val po = orders.filter { inr(it.orderDate) }
        val ps = sales.filter { inr(it.date) }
        return Stats(
            cash = pays.filter { inr(it.date) }.sumOf { it.amount } + ps.sumOf { it.meters * it.pricePerMeter },
            tailoring = po.sumOf { it.tailorPrice },
            fabricIncome = po.sumOf { it.fabricPrice } + ps.sumOf { it.meters * it.pricePerMeter },
            purchases = purchases.filter { inr(it.date) }.sumOf { it.meters * it.pricePerMeter },
            other = expenses.filter { inr(it.date) }.sumOf { it.amount }
        )
    }

    fun series(
        from: String, to: String, pays: List<Payment>, sales: List<Sale>,
        purchases: List<Purchase>, expenses: List<Expense>
    ): List<Triple<String, Double, Double>> {
        val a = LocalDate.parse(from); val b = LocalDate.parse(to)
        if (b.isBefore(a)) return emptyList()
        val monthly = ChronoUnit.DAYS.between(a, b) > 62
        fun key(d: String) = if (monthly) d.take(7) else d
        val inc = HashMap<String, Double>(); val out = HashMap<String, Double>()
        fun add(m: HashMap<String, Double>, d: String, v: Double) {
            if (d >= from && d <= to) m[key(d)] = (m[key(d)] ?: 0.0) + v
        }
        pays.forEach { add(inc, it.date, it.amount) }
        sales.forEach { add(inc, it.date, it.meters * it.pricePerMeter) }
        expenses.forEach { add(out, it.date, it.amount) }
        purchases.forEach { add(out, it.date, it.meters * it.pricePerMeter) }
        val keys: List<String> = if (monthly) (inc.keys + out.keys).toSortedSet().toList()
        else (0..ChronoUnit.DAYS.between(a, b)).map { a.plusDays(it).toString() }
        return keys.map { k ->
            Triple(if (monthly) k.substring(2) else k.takeLast(2), inc[k] ?: 0.0, out[k] ?: 0.0)
        }
    }

    fun fabricProfit(f: Fabric, orders: List<TailorOrder>, sales: List<Sale>): Double =
        orders.filter { it.fabricSource == "shop" && it.fabricId == f.id }
            .sumOf { it.fabricPrice - it.meters * f.buyPrice } +
            sales.filter { it.fabricId == f.id }.sumOf { (it.pricePerMeter - f.buyPrice) * it.meters }

    fun alerts(
        today: String, customers: List<Customer>, orders: List<TailorOrder>,
        pays: List<Payment>, fabrics: List<Fabric>, low: Double
    ): List<Alert> {
        val res = mutableListOf<Alert>()
        val soon = LocalDate.parse(today).plusDays(2).toString()
        fun name(id: Long) = customers.find { it.id == id }?.fullName() ?: "-"
        orders.filter { it.status != "deliv" }.forEach { o ->
            if (o.deliveryDate.isNotBlank()) {
                if (o.deliveryDate < today) res.add(Alert("a_over", "${o.no} · ${name(o.customerId)}"))
                else if (o.deliveryDate <= soon) res.add(Alert("a_soon", "${o.no} · ${o.deliveryDate}"))
            }
            if (o.status == "done") res.add(Alert("a_ready", "${o.no} · ${name(o.customerId)}"))
        }
        fabrics.filter { it.stock < low }.forEach { res.add(Alert("low", "${it.name} (${fmt(it.stock)} m)")) }
        customers.forEach {
            val d = customerDebt(it.id, orders, pays)
            if (d > 0.005) res.add(Alert("a_debt", "${it.fullName()} · ${fmt(d)} AFN"))
        }
        return res
    }
}
