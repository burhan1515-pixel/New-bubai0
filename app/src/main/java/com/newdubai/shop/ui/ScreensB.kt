@file:OptIn(ExperimentalMaterial3Api::class)

package com.newdubai.shop.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.newdubai.shop.data.*
import com.newdubai.shop.util.tr
import java.time.LocalDate

private val FABRIC_PALETTE = listOf(
    "#FFFFFF", "#F5F5F5", "#E8E8E8", "#D0D0D0", "#A9A9A9", "#808080", "#555555", "#222222",
    "#000000", "#FFF8E7", "#F5DEB3", "#DEB887", "#D2B48C", "#C19A6B", "#A67B5B", "#8B5A2B",
    "#5C4033", "#7B3F00", "#B22222", "#8B0000", "#DC143C", "#FF0000", "#FF6347", "#FF7F50",
    "#E9967A", "#FA8072", "#FF69B4", "#FF1493", "#C71585", "#DB7093", "#DDA0DD", "#DA70D6",
    "#BA55D3", "#9932CC", "#800080", "#4B0082", "#6A5ACD", "#7B68EE", "#9370DB", "#B0C4DE",
    "#E6E6FA", "#F0F8FF", "#ADD8E6", "#87CEEB", "#00BFFF", "#1E90FF", "#4169E1", "#0000CD",
    "#00008B", "#191970", "#0F52BA", "#0047AB", "#008080", "#20B2AA", "#48D1CC", "#00CED1",
    "#40E0D0", "#7FFFD4", "#66CDAA", "#3CB371", "#2E8B57", "#006400", "#008000", "#32CD32",
    "#7CFC00", "#ADFF2F", "#9ACD32", "#6B8E23", "#556B2F", "#808000", "#BDB76B", "#DAA520",
    "#FFD700", "#FFFF00", "#F0E68C", "#EEE8AA", "#FFE4B5", "#FFDAB9", "#FFA500", "#FF8C00",
    "#A0522D", "#CD853F", "#D2691E", "#F4A460", "#BC8F8F", "#E0B0FF", "#C3B1E1", "#B76E79",
    "#E5AA70", "#C9A227", "#708090", "#2F4F4F", "#36454F", "#800020", "#722F37", "#4A0404"
)

/* ---------------- measurements ---------------- */


@Composable
fun MeasCard(m: MeasurementRec, inch: Boolean, onDelete: (() -> Unit)? = null) {
    val v = Calc.parseMeasures(m.vals)
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${tr(if (m.grp == "M") "men" else "kid")} · ${tr(m.type)}", fontWeight = FontWeight.Bold)
                Text("${m.date} ${m.orderNo}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
        }
        Calc.MEASURES.forEachIndexed { i, l ->
            v[i]?.let { KV(l, Calc.fmt(if (inch) Calc.cmToIn(it) else it) + if (inch) " in" else " cm") }
        }
    }
}

@Composable
fun MeasurementsScreen(vm: AppVM) {
    val customers by vm.customers.collectAsState(); val meas by vm.measurements.collectAsState()
    var q by remember { mutableStateOf("") }; var form by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<MeasurementRec?>(null) }
    val names = customers.associate { it.id to it.fullName() }
    val list = meas.filter { (names[it.customerId] ?: "").contains(q, ignoreCase = true) }
    ListScreen(q, { q = it }, { form = true }) {
        if (list.isEmpty()) item { Empty() }
        items(list) { m ->
            Column {
                Text(names[m.customerId] ?: "-", color = MaterialTheme.colorScheme.primary)
                MeasCard(m, vm.inch) { del = m }
            }
        }
    }
    if (form) MeasurementForm(vm) { form = false }
    del?.let { d -> ConfirmDialog({ vm.deleteMeasurement(d) }, { del = null }) }
}

@Composable
fun MeasurementForm(vm: AppVM, onDismiss: () -> Unit) {
    val customers by vm.customers.collectAsState(); val meas by vm.measurements.collectAsState()
    var cust by remember { mutableStateOf(0L) }; var type by remember { mutableStateOf("m_sq") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }; var inch by remember { mutableStateOf(vm.inch) }
    val vals = remember { mutableStateListOf<String>().apply { repeat(12) { add("") } } }
    var err by remember { mutableStateOf<String?>(null) }
    val grp = if (Calc.isMen(type)) "M" else "C"
    val typeOpts = Calc.MEN.map { it to "${tr("men")} · ${tr(it)}" } + Calc.KIDS.map { it to "${tr("kid")} · ${tr(it)}" }
    Sheet(tr("newmeas"), onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = {
            val cm = formToCm(vals, inch)
            if (cm.none { it != null }) err = "req"
            else vm.saveMeasurement(MeasurementRec(customerId = cust, grp = grp, type = type, date = date,
                vals = Calc.joinMeasures(cm), unit = if (inch) "in" else "cm")) { r -> if (r == null) onDismiss() else err = r }
        }) { Text(tr("save")) }
    }) {
        Select(tr("customer"), customers.map { it.id.toString() to it.fullName() }, cust.toString(), { cust = it.toLong() })
        Select(tr("type"), typeOpts, type, { type = it })
        DateField(tr("date"), date, { date = it })
        OutlinedButton(onClick = {
            val p = meas.filter { it.customerId == cust && it.grp == grp }.maxWithOrNull(compareBy({ it.date }, { it.id }))
            if (p != null) { vals.clear(); vals.addAll(measStrings(p.vals, inch)) } else err = "nomeas"
        }) { Text(tr("useprev")) }
        MeasureFields(vals, inch) { t -> convertVals(vals, t); inch = t }
        ErrText(err)
    }
}

/* ---------------- fabric shop ---------------- */

@Composable
fun FabricScreen(vm: AppVM) {
    val fabrics by vm.fabrics.collectAsState(); val orders by vm.orders.collectAsState(); val sales by vm.sales.collectAsState()
    var q by remember { mutableStateOf("") }; var form by remember { mutableStateOf(false) }; var detail by remember { mutableStateOf<Long?>(null) }
    val low = vm.prefs.lowStock
    ListScreen(q, { q = it }, { form = true }) {
        val list = fabrics.filter { "${it.name} ${it.fabricType} ${it.color}".contains(q, ignoreCase = true) }
        if (list.isEmpty()) item { Empty() }
        items(list) { f ->
            Panel(Modifier.fillMaxWidth(), onClick = { detail = f.id }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val img = remember(f.photo) { if (f.photo.isBlank()) null else decodePhoto(f.photo) }
                    if (img != null) Image(img, null, Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Store, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.Bold); Text(f.fabricType, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${money(f.sellPrice)} / m")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${Calc.fmt(f.stock)} m", color = if (f.stock < low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        if (f.stock < low) Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (form) FabricForm(vm, null) { form = false }
    detail?.let { FabricDetail(vm, it) { detail = null } }
}

@Composable
fun FabricForm(vm: AppVM, f: Fabric?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(f?.name ?: "") }; var type by remember { mutableStateOf(f?.fabricType ?: "") }
    var buy by remember { mutableStateOf(f?.buyPrice?.let { Calc.fmt(it) } ?: "") }
    var sell by remember { mutableStateOf(f?.sellPrice?.let { Calc.fmt(it) } ?: "") }
    var stock by remember { mutableStateOf("") }; var photo by remember { mutableStateOf(f?.photo ?: "") }
    var color by remember { mutableStateOf(f?.color ?: "#FFFFFF") }
    var err by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? -> if (u != null) encodePhoto(ctx, u)?.let { photo = it } }
    Sheet(tr("fabric"), onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = {
            val b = buy.replace(',', '.').toDoubleOrNull(); val s = sell.replace(',', '.').toDoubleOrNull()
            if (b == null || s == null) err = "req" else {
                val init = stock.replace(',', '.').toDoubleOrNull() ?: 0.0
                val rec = (f ?: Fabric(name = "", buyPrice = 0.0, sellPrice = 0.0))
                    .copy(name = name.trim(), fabricType = type.trim(), buyPrice = b, sellPrice = s, photo = photo,
                        stock = if (f == null) init else f.stock, color = color)
                vm.saveFabric(rec, if (f == null) init else 0.0) { r -> if (r == null) onDismiss() else err = r }
            }
        }) { Text(tr("save")) }
    }) {
        TField(tr("fname"), name, { name = it })
        TField(tr("ftype"), type, { type = it })
        SectionTitle(tr("color"))
        Text(color, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(FABRIC_PALETTE) { hex ->
                val selected = color == hex
                Box(
                    Modifier
                        .size(if (selected) 38.dp else 32.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                        .clickable { color = hex }
                )
            }
        }
        TField(tr("buyp"), buy, { buy = it }, kb = KeyboardType.Decimal)
        TField(tr("sellp"), sell, { sell = it }, kb = KeyboardType.Decimal)
        if (f == null) TField(tr("stock"), stock, { stock = it }, kb = KeyboardType.Decimal)
        val img = remember(photo) { if (photo.isBlank()) null else decodePhoto(photo) }
        if (img != null) Image(img, null, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text(tr("photo"))
        }
        ErrText(err)
    }
}

@Composable
fun FabricDetail(vm: AppVM, id: Long, onDismiss: () -> Unit) {
    val fabrics by vm.fabrics.collectAsState(); val orders by vm.orders.collectAsState(); val sales by vm.sales.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val f = fabrics.find { it.id == id }
    var edit by remember { mutableStateOf(false) }; var del by remember { mutableStateOf(false) }
    var buy by remember { mutableStateOf(false) }; var sell by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    if (f == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val cs = MaterialTheme.colorScheme
    val profit = Calc.fabricProfit(f, orders, sales)
    Sheet(f.name, onDismiss, actions = {
        IconButton(onClick = { del = true }) { Icon(Icons.Default.Delete, null, tint = cs.error) }
        IconButton(onClick = { edit = true }) { Icon(Icons.Default.Edit, null) }
        TextButton(onClick = onDismiss) { Text(tr("close")) }
    }) {
        val img = remember(f.photo) { if (f.photo.isBlank()) null else decodePhoto(f.photo) }
        if (img != null) Image(img, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        KV(tr("ftype"), f.fabricType); KV(tr("color"), f.color)
        KV(tr("buyp"), money(f.buyPrice)); KV(tr("sellp"), money(f.sellPrice))
        KV(tr("stock"), Calc.fmt(f.stock), if (f.stock < vm.prefs.lowStock) cs.error else cs.tertiary)
        KV(tr("profit"), money(profit), if (profit < 0) cs.error else cs.tertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { buy = true }) { Text(tr("buy")) }
            Button(onClick = { sell = true }, colors = ButtonDefaults.buttonColors(containerColor = cs.secondary)) { Text(tr("sell")) }
        }
        ErrText(err)
        SectionTitle(tr("purchases"))
        purchases.filter { it.fabricId == id }.forEach { KV("${it.date} · ${Calc.fmt(it.meters)} m", money(it.meters * it.pricePerMeter)) }
        SectionTitle(tr("sales"))
        sales.filter { it.fabricId == id }.forEach { KV("${it.date} · ${Calc.fmt(it.meters)} m", money(it.meters * it.pricePerMeter)) }
    }
    if (edit) FabricForm(vm, f) { edit = false }
    if (del) ConfirmDialog({ vm.deleteFabric(f) { r -> if (r == null) onDismiss() else err = r } }, { del = false })
    if (buy) TradeDialog(vm, f, true) { buy = false }
    if (sell) TradeDialog(vm, f, false) { sell = false }
}

@Composable
fun TradeDialog(vm: AppVM, f: Fabric, isBuy: Boolean, onDismiss: () -> Unit) {
    var m by remember { mutableStateOf("") }
    var p by remember { mutableStateOf(Calc.fmt(if (isBuy) f.buyPrice else f.sellPrice)) }
    var d by remember { mutableStateOf(LocalDate.now().toString()) }; var err by remember { mutableStateOf<String?>(null) }
    Sheet(tr(if (isBuy) "buy" else "sell"), onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = {
            val mm = m.replace(',', '.').toDoubleOrNull(); val pp = p.replace(',', '.').toDoubleOrNull()
            if (mm == null || pp == null) err = "req"
            else if (isBuy) vm.buyFabric(f, mm, pp, d) { r -> if (r == null) onDismiss() else err = r }
            else vm.sellFabric(f, mm, pp, d) { r -> if (r == null) onDismiss() else err = r }
        }) { Text(tr("save")) }
    }) {
        TField(tr("meters"), m, { m = it }, kb = KeyboardType.Decimal)
        TField(tr(if (isBuy) "buyp" else "sellp"), p, { p = it }, kb = KeyboardType.Decimal)
        KV(tr("total"), money((m.toDoubleOrNull() ?: 0.0) * (p.toDoubleOrNull() ?: 0.0)))
        DateField(tr("date"), d, { d = it }); ErrText(err)
    }
}

/* ---------------- cash & debts ---------------- */

private data class CashRow(val date: String, val label: String, val amount: Double)

@Composable
fun CashScreen(vm: AppVM) {
    val pays by vm.payments.collectAsState(); val sales by vm.sales.collectAsState()
    val customers by vm.customers.collectAsState(); val orders by vm.orders.collectAsState(); val fabrics by vm.fabrics.collectAsState()
    val cs = MaterialTheme.colorScheme
    val rows = (pays.map { p ->
        CashRow(p.date, "${customers.find { it.id == p.customerId }?.fullName() ?: "-"} · ${orders.find { it.id == p.orderId }?.no ?: ""}", p.amount)
    } + sales.map { CashRow(it.date, "${tr("cashsale")} · ${fabrics.find { f -> f.id == it.fabricId }?.name ?: ""}", it.meters * it.pricePerMeter) })
        .sortedByDescending { it.date }
    val today = LocalDate.now().toString(); val month = today.take(7)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                KV(tr("today"), money(rows.filter { it.date == today }.sumOf { it.amount }), cs.tertiary)
                KV(tr("month"), money(rows.filter { it.date.startsWith(month) }.sumOf { it.amount }), cs.tertiary)
                KV(tr("inc"), money(rows.sumOf { it.amount }), cs.primary)
            }
        }
        if (rows.isEmpty()) item { Empty() }
        items(rows) { r -> Panel(Modifier.fillMaxWidth()) { Text(r.date, color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium); KV(r.label, money(r.amount), cs.tertiary) } }
    }
}

@Composable
fun DebtScreen(vm: AppVM, open: (Long) -> Unit) {
    val customers by vm.customers.collectAsState(); val orders by vm.orders.collectAsState(); val pays by vm.payments.collectAsState()
    var payFor by remember { mutableStateOf<Long?>(null) }
    val cs = MaterialTheme.colorScheme
    val rows = customers.map { it to Calc.customerDebt(it.id, orders, pays) }.filter { it.second > 0.005 }.sortedByDescending { it.second }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Panel(Modifier.fillMaxWidth()) { KV(tr("debts"), money(Calc.totalDebt(orders, pays)), cs.error) } }
        if (rows.isEmpty()) item { Empty() }
        items(rows) { (c, d) ->
            Panel(Modifier.fillMaxWidth(), onClick = { open(c.id) }) {
                KV(c.fullName(), money(d), cs.error)
                orders.filter { it.customerId == c.id && Calc.balance(it, pays) > 0.005 }.forEach { o ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${o.no}: ${money(Calc.balance(o, pays))}", color = cs.onSurfaceVariant)
                        TextButton(onClick = { payFor = o.id }) { Text(tr("pay")) }
                    }
                }
            }
        }
    }
    payFor?.let { PaymentDialog(vm, it) { payFor = null } }
}

/* ---------------- expenses ---------------- */

@Composable
fun ExpensesScreen(vm: AppVM) {
    val expenses by vm.expenses.collectAsState()
    var q by remember { mutableStateOf("") }; var edit by remember { mutableStateOf<Expense?>(null) }
    var form by remember { mutableStateOf(false) }; var del by remember { mutableStateOf<Expense?>(null) }
    val list = expenses.filter { "${it.title} ${it.notes} ${tr(it.category)}".contains(q, ignoreCase = true) }
    ListScreen(q, { q = it }, { form = true }) {
        item { Text("${tr("exp")}: ${money(list.sumOf { it.amount })}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        if (list.isEmpty()) item { Empty() }
        items(list) { e ->
            Panel(Modifier.fillMaxWidth(), onClick = { edit = e }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(e.title, fontWeight = FontWeight.Bold); Text("${e.date} · ${tr(e.category)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(money(e.amount), color = MaterialTheme.colorScheme.error)
                    IconButton(onClick = { del = e }) { Icon(Icons.Default.Delete, null) }
                }
            }
        }
    }
    if (form) ExpenseForm(vm, null) { form = false }
    edit?.let { ExpenseForm(vm, it) { edit = null } }
    del?.let { d -> ConfirmDialog({ vm.deleteExpense(d) }, { del = null }) }
}

@Composable
fun ExpenseForm(vm: AppVM, e: Expense?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(e?.title ?: "") }; var amt by remember { mutableStateOf(e?.amount?.let { Calc.fmt(it) } ?: "") }
    var date by remember { mutableStateOf(e?.date ?: LocalDate.now().toString()) }; var cat by remember { mutableStateOf(e?.category ?: "oth") }
    var notes by remember { mutableStateOf(e?.notes ?: "") }; var err by remember { mutableStateOf<String?>(null) }
    Sheet(tr("exp"), onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = {
            val a = amt.replace(',', '.').toDoubleOrNull()
            if (a == null) err = "amt" else vm.saveExpense((e ?: Expense(title = "", amount = 0.0, date = date, category = cat))
                .copy(title = title.trim(), amount = a, date = date, category = cat, notes = notes)) { r -> if (r == null) onDismiss() else err = r }
        }) { Text(tr("save")) }
    }) {
        TField(tr("title"), title, { title = it }); TField(tr("amt"), amt, { amt = it }, kb = KeyboardType.Decimal)
        DateField(tr("date"), date, { date = it }); Select(tr("cat"), Calc.EXP_CATS.map { it to tr(it) }, cat, { cat = it })
        TField(tr("notes"), notes, { notes = it }, lines = 2); ErrText(err)
    }
}
