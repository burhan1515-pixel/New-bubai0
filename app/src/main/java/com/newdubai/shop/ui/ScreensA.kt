@file:OptIn(ExperimentalMaterial3Api::class)

package com.newdubai.shop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.newdubai.shop.data.*
import com.newdubai.shop.util.tr
import java.time.LocalDate

private data class Kpi(val label: String, val value: Double, val kind: Int, val icon: ImageVector, val color: Color)

@Composable
fun DashScreen(vm: AppVM) {
    val customers by vm.customers.collectAsState(); val orders by vm.orders.collectAsState()
    val pays by vm.payments.collectAsState(); val fabrics by vm.fabrics.collectAsState()
    val purchases by vm.purchases.collectAsState(); val sales by vm.sales.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now().toString()
    val td = Calc.stats(today, today, orders, pays, sales, purchases, expenses)
    val all = Calc.stats("0000-01-01", "9999-12-31", orders, pays, sales, purchases, expenses)
    val rows = Calc.series(LocalDate.now().minusDays(6).toString(), today, pays, sales, purchases, expenses)
    val alerts = Calc.alerts(today, customers, orders, pays, fabrics, vm.prefs.lowStock.toDouble())
    val kpis = listOf(
        Kpi("tord", orders.count { it.orderDate == today }.toDouble(), 1, Icons.Default.ContentCut, cs.secondary),
        Kpi("tinc", td.cash, 0, Icons.Default.Payments, cs.tertiary),
        Kpi("texp", td.other + td.purchases, 0, Icons.Default.Receipt, cs.error),
        Kpi("ncust", customers.size.toDouble(), 1, Icons.Default.People, cs.primary),
        Kpi("debts", Calc.totalDebt(orders, pays), 0, Icons.Default.CreditCard, cs.error),
        Kpi("inprog", orders.count { it.status == "prog" }.toDouble(), 1, Icons.Default.Build, cs.primary),
        Kpi("done", orders.count { it.status == "done" || it.status == "deliv" }.toDouble(), 1, Icons.Default.CheckCircle, cs.tertiary),
        Kpi("ready", orders.count { it.status == "done" }.toDouble(), 1, Icons.Default.LocalShipping, cs.secondary),
        Kpi("invent", fabrics.sumOf { it.stock }, 2, Icons.Default.Inventory2, cs.primary),
        Kpi("net", all.net, 0, Icons.Default.TrendingUp, if (all.net < 0) cs.error else cs.tertiary)
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AuroraBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text(tr("greet"), color = Color.White.copy(alpha = .8f))
                    Text("NEW DUBAI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Saffron)
                    StitchThread(Modifier.fillMaxWidth().height(28.dp))
                    Text(today, color = Color.White.copy(alpha = .8f))
                }
            }
        }
        itemsIndexed(kpis.chunked(2)) { i, row ->
            StaggerIn(i) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { k ->
                        Panel(Modifier.weight(1f)) {
                            Icon(k.icon, null, tint = k.color)
                            Spacer(Modifier.height(6.dp))
                            Text(tr(k.label), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            CountUp(k.value, MaterialTheme.typography.titleMedium, k.color,
                                fmt = { if (k.kind == 0) money(it) else Calc.fmt(it) })
                        }
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle(tr("week"))
                BarChart(rows)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text("■ ${tr("inc")}", color = cs.primary, style = MaterialTheme.typography.labelSmall)
                    Text("■ ${tr("exp")}", color = cs.secondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { SectionTitle("${tr("alerts")} (${alerts.size})") }
        if (alerts.isEmpty()) item { Empty() }
        items(alerts.take(30)) { a ->
            Panel(Modifier.fillMaxWidth()) {
                Text(tr(a.kind), color = cs.primary, style = MaterialTheme.typography.labelMedium)
                Text(a.text)
            }
        }
    }
}

/* ---------------- customers ---------------- */

@Composable
fun CustomersScreen(vm: AppVM, open: (Long) -> Unit) {
    val customers by vm.customers.collectAsState(); val orders by vm.orders.collectAsState(); val pays by vm.payments.collectAsState()
    var q by remember { mutableStateOf("") }
    var form by remember { mutableStateOf(false) }
    val list = customers.filter { "${it.fullName()} ${it.phone}".contains(q, ignoreCase = true) }
    ListScreen(q, { q = it }, { form = true }) {
        if (list.isEmpty()) item { Empty() }
        itemsIndexed(list) { i, c ->
            val debt = Calc.customerDebt(c.id, orders, pays)
            Panel(Modifier.fillMaxWidth(), onClick = { open(c.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(c.fullName())
                    Column(Modifier.weight(1f)) { Text(c.fullName(), fontWeight = FontWeight.Bold); Text(c.phone, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (debt > 0) Text(money(debt), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (form) CustomerForm(vm, null) { form = false }
}

@Composable
fun CustomerForm(vm: AppVM, c: Customer?, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(c?.firstName ?: "") }; var l by remember { mutableStateOf(c?.lastName ?: "") }
    var p by remember { mutableStateOf(c?.phone ?: "") }; var a by remember { mutableStateOf(c?.address ?: "") }
    var n by remember { mutableStateOf(c?.notes ?: "") }; var err by remember { mutableStateOf<String?>(null) }
    Sheet(tr("cust"), onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = {
            val rec = (c ?: Customer(firstName = "", phone = "", regDate = LocalDate.now().toString()))
                .copy(firstName = f.trim(), lastName = l.trim(), phone = p.trim(), address = a.trim(), notes = n.trim())
            vm.saveCustomer(rec) { r -> if (r == null) onDismiss() else err = r }
        }) { Text(tr("save")) }
    }) {
        TField(tr("first"), f, { f = it }); TField(tr("last"), l, { l = it })
        TField(tr("phone"), p, { p = it }, kb = KeyboardType.Phone); TField(tr("addr"), a, { a = it })
        TField(tr("notes"), n, { n = it }, lines = 3); ErrText(err)
    }
}

@Composable
fun CustomerProfile(vm: AppVM, id: Long, back: () -> Unit) {
    val customers by vm.customers.collectAsState(); val orders by vm.orders.collectAsState()
    val pays by vm.payments.collectAsState(); val meas by vm.measurements.collectAsState()
    val c = customers.find { it.id == id }
    var edit by remember { mutableStateOf(false) }; var del by remember { mutableStateOf(false) }
    var newOrder by remember { mutableStateOf(false) }; var detail by remember { mutableStateOf<Long?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    if (c == null) { Empty(); return }
    val cs = MaterialTheme.colorScheme
    val debt = Calc.customerDebt(id, orders, pays)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(c.fullName()); Column { Text(c.fullName(), style = MaterialTheme.typography.titleLarge); Text(c.phone) }
                }
                if (c.address.isNotBlank()) KV(tr("addr"), c.address)
                if (c.notes.isNotBlank()) KV(tr("notes"), c.notes)
                KV(tr("regd"), c.regDate)
                KV(tr("debt"), money(debt), if (debt > 0) cs.error else cs.tertiary)
                ErrText(err)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { newOrder = true }) { Text(tr("orders")) }
                    OutlinedButton(onClick = { edit = true }) { Icon(Icons.Default.Edit, null) }
                    OutlinedButton(onClick = { del = true }) { Icon(Icons.Default.Delete, null, tint = cs.error) }
                }
            }
        }
        item { SectionTitle(tr("orders")) }
        val mine = orders.filter { it.customerId == id }
        if (mine.isEmpty()) item { Empty() }
        items(mine) { o ->
            Panel(Modifier.fillMaxWidth(), onClick = { detail = o.id }) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("${o.no} · ${tr(o.type)}"); StatusChip(o.status) }
                KV(tr("total"), money(o.total)); KV(tr("rem"), money(Calc.balance(o, pays)))
            }
        }
        item { SectionTitle("${tr("meas")} · ${tr("history")}") }
        items(meas.filter { it.customerId == id }) { m -> MeasCard(m, vm.inch) }
        item { SectionTitle(tr("cash")) }
        items(pays.filter { it.customerId == id }) { p -> Panel(Modifier.fillMaxWidth()) { KV(p.date, money(p.amount)) } }
    }
    if (edit) CustomerForm(vm, c) { edit = false }
    if (del) ConfirmDialog({ vm.deleteCustomer(c) { r -> if (r == null) back() else err = r } }, { del = false })
    if (newOrder) OrderFormDialog(vm, null, id) { newOrder = false }
    detail?.let { OrderDetailDialog(vm, it) { detail = null } }
}

/* ---------------- orders ---------------- */

@Composable
fun OrdersScreen(vm: AppVM) {
    val customers by vm.customers.collectAsState(); val orders by vm.orders.collectAsState(); val pays by vm.payments.collectAsState()
    var q by remember { mutableStateOf("") }; var st by remember { mutableStateOf("") }
    var form by remember { mutableStateOf(false) }; var detail by remember { mutableStateOf<Long?>(null) }
    val names = customers.associate { it.id to it.fullName() }
    val list = orders.filter { (st.isEmpty() || it.status == st) && "${it.no} ${names[it.customerId] ?: ""}".contains(q, ignoreCase = true) }
        .sortedBy { it.deliveryDate }
    ListScreen(q, { q = it }, { form = true }, header = {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("") + Calc.STATUSES) { s ->
                FilterChip(selected = st == s, onClick = { st = s }, label = { Text(if (s.isEmpty()) tr("all") else tr(s)) })
            }
        }
    }) {
        if (list.isEmpty()) item { Empty() }
        items(list) { o ->
            val bal = Calc.balance(o, pays)
            val late = o.status != "deliv" && o.deliveryDate < LocalDate.now().toString()
            Panel(Modifier.fillMaxWidth(), onClick = { detail = o.id }) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(o.no, fontWeight = FontWeight.Bold); StatusChip(o.status)
                }
                Text("${names[o.customerId] ?: "-"} · ${tr(o.type)}")
                Text("${tr("ddate")}: ${o.deliveryDate}", color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                KV(tr("total"), money(o.total))
                KV(tr("rem"), money(bal), if (bal > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
            }
        }
    }
    if (form) {
        if (customers.isEmpty()) AlertDialog(onDismissRequest = { form = false }, confirmButton = { TextButton(onClick = { form = false }) { Text(tr("ok")) } }, title = { Text(tr("cust")) })
        else OrderFormDialog(vm, null, 0L) { form = false }
    }
    detail?.let { OrderDetailDialog(vm, it) { detail = null } }
}

@Composable
fun MeasureFields(vals: androidx.compose.runtime.snapshots.SnapshotStateList<String>, inch: Boolean, onInch: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("unit"))
        FilterChip(selected = !inch, onClick = { if (inch) onInch(false) }, label = { Text(tr("cm")) })
        FilterChip(selected = inch, onClick = { if (!inch) onInch(true) }, label = { Text(tr("inch")) })
    }
    Calc.MEASURES.chunked(2).forEachIndexed { r, pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEachIndexed { j, label ->
                val idx = r * 2 + j
                TField(label, vals[idx], { vals[idx] = it }, Modifier.weight(1f), KeyboardType.Decimal)
            }
        }
    }
}

fun convertVals(vals: androidx.compose.runtime.snapshots.SnapshotStateList<String>, toInch: Boolean) {
    for (i in vals.indices) {
        val v = vals[i].replace(',', '.').toDoubleOrNull() ?: continue
        vals[i] = Calc.fmt(if (toInch) Calc.cmToIn(v) else Calc.inToCm(v))
    }
}

fun measStrings(csv: String, inch: Boolean): List<String> =
    Calc.parseMeasures(csv).map { v -> v?.let { Calc.fmt(if (inch) Calc.cmToIn(it) else it) } ?: "" }

fun formToCm(vals: List<String>, inch: Boolean): List<Double?> =
    vals.map { s -> s.replace(',', '.').toDoubleOrNull()?.let { Calc.round2(if (inch) Calc.inToCm(it) else it) } }

@Composable
fun OrderFormDialog(vm: AppVM, existing: TailorOrder?, presetCustomer: Long, onDismiss: () -> Unit) {
    val customers by vm.customers.collectAsState(); val fabrics by vm.fabrics.collectAsState(); val meas by vm.measurements.collectAsState()
    var cust by remember { mutableStateOf(existing?.customerId ?: presetCustomer) }
    var type by remember { mutableStateOf(existing?.type ?: "m_sq") }
    var src by remember { mutableStateOf(existing?.fabricSource ?: "own") }
    var fabId by remember { mutableStateOf(existing?.fabricId ?: 0L) }
    var meters by remember { mutableStateOf(existing?.meters?.takeIf { it > 0 }?.let { Calc.fmt(it) } ?: "") }
    var fprice by remember { mutableStateOf(existing?.fabricPrice?.takeIf { it > 0 }?.let { Calc.fmt(it) } ?: "") }
    var tprice by remember { mutableStateOf(existing?.tailorPrice?.let { Calc.fmt(it) } ?: "") }
    var adv by remember { mutableStateOf("") }
    var od by remember { mutableStateOf(existing?.orderDate ?: LocalDate.now().toString()) }
    var dd by remember { mutableStateOf(existing?.deliveryDate ?: LocalDate.now().plusDays(7).toString()) }
    var status by remember { mutableStateOf(existing?.status ?: "new") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var inch by remember { mutableStateOf(existing?.unit?.let { it == "in" } ?: vm.inch) }
    var err by remember { mutableStateOf<String?>(null) }
    val vals = remember { mutableStateListOf<String>().apply { addAll(measStrings(existing?.measurements ?: "", inch)) } }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(fabId, meters, src) {
        if (first) { first = false; return@LaunchedEffect }
        if (src == "shop") {
            val f = fabrics.find { it.id == fabId }; val m = meters.toDoubleOrNull()
            if (f != null && m != null) fprice = Calc.fmt(m * f.sellPrice)
        }
    }
    val total = (if (src == "shop") fprice.toDoubleOrNull() ?: 0.0 else 0.0) + (tprice.toDoubleOrNull() ?: 0.0)
    val typeOpts = Calc.MEN.map { it to "${tr("men")} · ${tr(it)}" } + Calc.KIDS.map { it to "${tr("kid")} · ${tr(it)}" }
    val grp = if (Calc.isMen(type)) "M" else "C"

    Sheet(tr("order"), onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = {
            val tp = tprice.replace(',', '.').toDoubleOrNull()
            if (tp == null) { err = "req"; return@Button }
            val cm = formToCm(vals, inch)
            val joined = Calc.joinMeasures(cm)
            val order = TailorOrder(
                id = existing?.id ?: 0, no = existing?.no ?: "", customerId = cust, type = type, fabricSource = src,
                fabricId = if (src == "shop") fabId else 0, meters = meters.replace(',', '.').toDoubleOrNull() ?: 0.0,
                fabricPrice = if (src == "shop") fprice.replace(',', '.').toDoubleOrNull() ?: 0.0 else 0.0,
                tailorPrice = tp, orderDate = od, deliveryDate = dd, status = status, notes = notes,
                measurements = joined, unit = if (inch) "in" else "cm"
            )
            val rec = if (cm.any { it != null } && (existing == null || existing.measurements != joined))
                MeasurementRec(customerId = cust, grp = grp, type = type, date = od, vals = joined, unit = if (inch) "in" else "cm") else null
            vm.saveOrder(order, adv.replace(',', '.').toDoubleOrNull() ?: 0.0, rec) { r -> if (r == null) onDismiss() else err = r }
        }) { Text(tr("save")) }
    }) {
        Select(tr("customer"), customers.map { it.id.toString() to it.fullName() }, cust.toString(), { cust = it.toLong() })
        Select(tr("type"), typeOpts, type, { type = it })
        Select(tr("fsrc"), listOf("own" to tr("own"), "shop" to tr("shop")), src, { src = it })
        if (src == "shop") {
            Select(tr("fname"), fabrics.map { it.id.toString() to "${it.name} (${Calc.fmt(it.stock)} m)" }, fabId.toString(), { fabId = it.toLong() })
            TField(tr("meters"), meters, { meters = it }, kb = KeyboardType.Decimal)
            TField(tr("fprice"), fprice, { fprice = it }, kb = KeyboardType.Decimal)
        }
        TField(tr("tprice"), tprice, { tprice = it }, kb = KeyboardType.Decimal)
        if (existing == null) TField(tr("adv"), adv, { adv = it }, kb = KeyboardType.Decimal)
        KV(tr("total"), money(total), MaterialTheme.colorScheme.primary)
        if (existing == null) KV(tr("rem"), money(total - (adv.toDoubleOrNull() ?: 0.0)))
        DateField(tr("odate"), od, { od = it }); DateField(tr("ddate"), dd, { dd = it })
        Select(tr("status"), Calc.STATUSES.map { it to tr(it) }, status, { status = it })
        TField(tr("notes"), notes, { notes = it }, lines = 2)
        SectionTitle(tr("meas"))
        OutlinedButton(onClick = {
            val prev = meas.filter { it.customerId == cust && it.grp == grp }.maxWithOrNull(compareBy({ it.date }, { it.id }))
            if (prev != null) { vals.clear(); vals.addAll(measStrings(prev.vals, inch)) } else err = "nomeas"
        }) { Text(tr("useprev")) }
        MeasureFields(vals, inch) { toInch -> convertVals(vals, toInch); inch = toInch }
        ErrText(err)
    }
}

@Composable
fun OrderDetailDialog(vm: AppVM, orderId: Long, onDismiss: () -> Unit) {
    val orders by vm.orders.collectAsState(); val pays by vm.payments.collectAsState()
    val customers by vm.customers.collectAsState(); val fabrics by vm.fabrics.collectAsState()
    val o = orders.find { it.id == orderId }
    var edit by remember { mutableStateOf(false) }; var del by remember { mutableStateOf(false) }; var pay by remember { mutableStateOf(false) }
    if (o == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val cs = MaterialTheme.colorScheme
    val bal = Calc.balance(o, pays)
    Sheet(o.no, onDismiss, actions = {
        IconButton(onClick = { del = true }) { Icon(Icons.Default.Delete, null, tint = cs.error) }
        IconButton(onClick = { edit = true }) { Icon(Icons.Default.Edit, null) }
        TextButton(onClick = onDismiss) { Text(tr("close")) }
    }) {
        KV(tr("customer"), customers.find { it.id == o.customerId }?.fullName() ?: "-")
        KV(tr("type"), tr(o.type)); KV(tr("fsrc"), tr(o.fabricSource))
        if (o.fabricSource == "shop") KV(tr("fname"), "${fabrics.find { it.id == o.fabricId }?.name ?: "-"} · ${Calc.fmt(o.meters)} m")
        KV(tr("fprice"), money(o.fabricPrice)); KV(tr("tprice"), money(o.tailorPrice))
        KV(tr("total"), money(o.total)); KV(tr("paid"), money(Calc.paid(o, pays)))
        KV(tr("rem"), money(bal), if (bal > 0) cs.error else cs.tertiary)
        KV(tr("odate"), o.orderDate); KV(tr("ddate"), o.deliveryDate)
        if (o.notes.isNotBlank()) KV(tr("notes"), o.notes)
        Text(tr("status"), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Calc.STATUSES) { s -> FilterChip(selected = o.status == s, onClick = { vm.setStatus(o, s) }, label = { Text(tr(s)) }) }
        }
        val mv = Calc.parseMeasures(o.measurements)
        if (mv.any { it != null }) {
            SectionTitle(tr("meas"))
            Calc.MEASURES.forEachIndexed { i, l -> mv[i]?.let { KV(l, Calc.fmt(if (o.unit == "in") Calc.cmToIn(it) else it) + " " + o.unit) } }
        }
        SectionTitle(tr("cash"))
        pays.filter { it.orderId == o.id }.forEach { KV(it.date, money(it.amount)) }
        if (bal > 0) Button(onClick = { pay = true }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("pay")) }
    }
    if (edit) OrderFormDialog(vm, o, o.customerId) { edit = false }
    if (del) ConfirmDialog({ vm.deleteOrder(o); onDismiss() }, { del = false })
    if (pay) PaymentDialog(vm, o.id) { pay = false }
}

@Composable
fun PaymentDialog(vm: AppVM, orderId: Long, onDismiss: () -> Unit) {
    val orders by vm.orders.collectAsState(); val pays by vm.payments.collectAsState()
    val o = orders.find { it.id == orderId }
    if (o == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val bal = Calc.balance(o, pays)
    var amt by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var err by remember { mutableStateOf<String?>(null) }
    Sheet(tr("pay"), onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = {
            val a = amt.replace(',', '.').toDoubleOrNull()
            if (a == null) err = "amt" else vm.addPayment(orderId, a, date) { r -> if (r == null) onDismiss() else err = r }
        }) { Text(tr("save")) }
    }) {
        KV(o.no, "${tr("rem")}: ${money(bal)}")
        TField(tr("amt"), amt, { amt = it }, kb = KeyboardType.Decimal)
        OutlinedButton(onClick = { amt = Calc.fmt(bal) }) { Text(tr("payfull")) }
        DateField(tr("date"), date, { date = it }); ErrText(err)
    }
}
