@file:OptIn(ExperimentalMaterial3Api::class)

package com.newdubai.shop.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.newdubai.shop.data.*
import com.newdubai.shop.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/* ---------------- reports ---------------- */

@Composable
fun ReportsScreen(vm: AppVM) {
    val orders by vm.orders.collectAsState(); val pays by vm.payments.collectAsState(); val sales by vm.sales.collectAsState()
    val purchases by vm.purchases.collectAsState(); val expenses by vm.expenses.collectAsState()
    val cs = MaterialTheme.colorScheme
    val now = LocalDate.now()
    var from by remember { mutableStateOf(now.withDayOfMonth(1).toString()) }
    var to by remember { mutableStateOf(now.toString()) }
    var preset by remember { mutableStateOf("month") }
    val s = Calc.stats(from, to, orders, pays, sales, purchases, expenses)
    val rows = Calc.series(from, to, pays, sales, purchases, expenses)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("today", "month", "year", "custom")) { k ->
                    FilterChip(selected = preset == k, onClick = {
                        preset = k
                        when (k) {
                            "today" -> { from = now.toString(); to = now.toString() }
                            "month" -> { from = now.withDayOfMonth(1).toString(); to = now.toString() }
                            "year" -> { from = now.withDayOfYear(1).toString(); to = now.toString() }
                        }
                    }, label = { Text(tr(k)) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(tr("from"), from, { from = it; preset = "custom" }, Modifier.weight(1f))
                DateField(tr("to"), to, { to = it; preset = "custom" }, Modifier.weight(1f))
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                KV(tr("inc"), money(s.cash)); KV(tr("tail"), money(s.tailoring)); KV(tr("fabinc"), money(s.fabricIncome))
                KV(tr("fabexp"), money(s.purchases), cs.error); KV(tr("othexp"), money(s.other), cs.error)
                KV(tr("debts"), money(Calc.totalDebt(orders, pays)), cs.error)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                KV(tr("net"), money(s.net), if (s.net < 0) cs.error else cs.tertiary)
                Text(tr("basis"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Text("${tr("inc")} / ${tr("exp")}", color = cs.primary)
                BarChart(rows)
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Donut(listOf(s.tailoring to cs.primary, s.fabricIncome to cs.secondary, s.purchases to cs.error, s.other to Color(0xFFE8A33D)))
                    Column {
                        Text("■ ${tr("tail")}", color = cs.primary); Text("■ ${tr("fabinc")}", color = cs.secondary)
                        Text("■ ${tr("fabexp")}", color = cs.error); Text("■ ${tr("othexp")}", color = Color(0xFFE8A33D))
                    }
                }
            }
        }
    }
}

/* ---------------- backup & restore ---------------- */

private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

@Composable
fun PasswordDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    Sheet(title, onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(tr("cancel")) }
        Button(onClick = { if (pw.length >= 4) { onOk(pw); onDismiss() } }) { Text(tr("ok")) }
    }) {
        OutlinedTextField(pw, { pw = it }, label = { Text(tr("bk_pw")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
    }
}

@Composable
fun BackupScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var askPw by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var needPwData by remember { mutableStateOf<ByteArray?>(null) }
    var confirmSnap by remember { mutableStateOf<Snapshot?>(null) }
    var driveAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var autoOn by remember { mutableStateOf(vm.prefs.autoBackup) }
    val sOk = tr("bk_ok"); val sBad = tr("badbk"); val sRes = tr("restored"); val sDrive = tr("drive_ok"); val sNone = tr("drive_none")
    val today = LocalDate.now().toString()

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val b = pending
        if (uri != null && b != null) scope.launch(Dispatchers.IO) {
            try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) }; status = sOk } catch (e: Exception) { status = e.message ?: sBad }
        }
    }

    fun export(zip: Boolean, pw: String?) {
        scope.launch(Dispatchers.Default) {
            var b = BackupIO.toJson(vm.snapshot()).toByteArray()
            if (pw != null) b = BackupIO.encrypt(b, pw)
            if (zip) b = BackupIO.zip(b, if (pw != null) "backup.ndenc" else "backup.json")
            pending = b
            withContext(Dispatchers.Main) { saver.launch("NEW_DUBAI_$today." + if (zip) "zip" else "ndbak") }
        }
    }

    fun handleBytes(raw: ByteArray, pw: String?) {
        scope.launch(Dispatchers.Default) {
            try {
                var b = raw
                if (BackupIO.isZip(b)) b = BackupIO.unzip(b)
                if (BackupIO.isEncrypted(b)) {
                    if (pw == null) { needPwData = b; return@launch }
                    b = BackupIO.decrypt(b, pw)
                }
                confirmSnap = BackupIO.fromJson(String(b))
            } catch (e: Exception) { status = sBad }
        }
    }

    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val b = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (b != null) handleBytes(b, null)
        }
    }

    val driveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try {
            val res = Identity.getAuthorizationClient(ctx as Activity).getAuthorizationResultFromIntent(r.data)
            val tk = res.accessToken
            if (tk != null) driveAction?.invoke(tk) else status = sBad
        } catch (e: Exception) { status = e.message ?: sBad }
    }

    fun withDriveToken(action: (String) -> Unit) {
        driveAction = action
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()
        Identity.getAuthorizationClient(ctx as Activity).authorize(req)
            .addOnSuccessListener { res ->
                val pi = res.pendingIntent
                if (res.hasResolution() && pi != null) driveLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                else res.accessToken?.let(action)
            }
            .addOnFailureListener { status = it.message ?: sBad }
    }

    fun driveUpload(token: String, pw: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                var b = BackupIO.toJson(vm.snapshot()).toByteArray()
                if (pw != null) b = BackupIO.encrypt(b, pw)
                DriveClient(token).upload(b); status = sDrive
            } catch (e: Exception) { status = e.message ?: sBad }
        }
    }

    fun driveDownload(token: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val b = DriveClient(token).downloadLatest()
                if (b == null) status = sNone else handleBytes(b, null)
            } catch (e: Exception) { status = e.message ?: sBad }
        }
    }

    val autos = remember(status) { Snapshots.backupDir(ctx).listFiles()?.sortedByDescending { it.name }?.toList() ?: emptyList() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (status.isNotBlank()) item { Panel(Modifier.fillMaxWidth()) { Text(status) } }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle(tr("bak"))
                Button(onClick = { export(false, null) }, Modifier.fillMaxWidth()) { Text(tr("bk_local")) }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { askPw = { pw -> export(false, pw) } }, Modifier.fillMaxWidth()) { Text(tr("bk_enc")) }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { askPw = { pw -> export(true, pw) } }, Modifier.fillMaxWidth()) { Text("${tr("bk_zip")} + ${tr("bk_pw")}") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { export(true, null) }, Modifier.fillMaxWidth()) { Text(tr("bk_zip")) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { opener.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text(tr("restore")) }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle(tr("bk_drive"))
                Button(onClick = { askPw = { pw -> withDriveToken { t -> driveUpload(t, pw) } } }, Modifier.fillMaxWidth()) { Text("${tr("drive_up")} (${tr("bk_pw")})") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { withDriveToken { t -> driveUpload(t, null) } }, Modifier.fillMaxWidth()) { Text(tr("drive_up")) }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { withDriveToken { t -> driveDownload(t) } }, Modifier.fillMaxWidth()) { Text(tr("drive_down")) }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("bk_auto"))
                    Switch(autoOn, { autoOn = it; vm.prefs.autoBackup = it })
                }
                SectionTitle(tr("autolist"))
                autos.forEach { f ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { handleBytes(f.readBytes(), null) }) { Text(tr("restore")) }
                    }
                }
            }
        }
    }

    askPw?.let { cb -> PasswordDialog(tr("bk_pw"), { askPw = null }) { cb(it) } }
    needPwData?.let { d -> PasswordDialog(tr("bk_pw"), { needPwData = null }) { pw -> needPwData = null; handleBytes(d, pw) } }
    confirmSnap?.let { snap ->
        AlertDialog(
            onDismissRequest = { confirmSnap = null }, title = { Text(tr("restore")) },
            text = { Text(tr("restore_ask") + "\n\n${snap.created}\n${tr("cust")}: ${snap.customers.size} · ${tr("orders")}: ${snap.orders.size}") },
            confirmButton = {
                Button(onClick = { confirmSnap = null; vm.restore(snap) { r -> status = if (r == null) sRes else sBad } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(tr("restore")) }
            },
            dismissButton = { TextButton(onClick = { confirmSnap = null }) { Text(tr("cancel")) } }
        )
    }
}

/* ---------------- settings ---------------- */

@Composable
fun SettingsScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var lock by remember { mutableStateOf(vm.prefs.lockMinutes.toString()) }
    var low by remember { mutableStateOf(Calc.fmt(vm.prefs.lowStock.toDouble())) }
    var bio by remember { mutableStateOf(vm.prefs.bioEnabled) }
    var cur by remember { mutableStateOf("") }; var npw by remember { mutableStateOf("") }; var npin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }; var ok by remember { mutableStateOf(false) }
    val canBio = BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                Text(tr("lang")); LangChips(vm)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("unit"))
                    FilterChip(selected = !vm.inch, onClick = { vm.changeInch(false) }, label = { Text(tr("cm")) })
                    FilterChip(selected = vm.inch, onClick = { vm.changeInch(true) }, label = { Text(tr("inch")) })
                }
                TField(tr("lowth"), low, { low = it; it.toFloatOrNull()?.let { v -> vm.prefs.lowStock = v } }, kb = KeyboardType.Decimal)
                TField(tr("lockmin"), lock, { lock = it; it.toIntOrNull()?.takeIf { v -> v in 1..60 }?.let { v -> vm.prefs.lockMinutes = v } }, kb = KeyboardType.Number)
                if (canBio) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("bio")); Switch(bio, { bio = it; vm.prefs.bioEnabled = it })
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("${tr("pw")} / ${tr("pin")}")
                OutlinedTextField(cur, { if (it.length <= 6 && it.all(Char::isDigit)) cur = it }, label = { Text(tr("curpin")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(npw, { npw = it }, label = { Text(tr("chpw")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(npin, { if (it.length <= 6 && it.all(Char::isDigit)) npin = it }, label = { Text(tr("chpin")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                msg?.let { Text(tr(it), color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                Button(onClick = {
                    if (!vm.prefs.verify(cur, true)) { ok = false; msg = "wrong" }
                    else if ((npw.isNotEmpty() && npw.length < 6) || (npin.isNotEmpty() && npin.length != 6)) { ok = false; msg = "pwmin" }
                    else if (npw.isEmpty() && npin.isEmpty()) { ok = false; msg = "req" }
                    else { vm.prefs.setCreds(npw.ifEmpty { null }, npin.ifEmpty { null }); ok = true; msg = "saved"; cur = ""; npw = ""; npin = "" }
                }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("save")) }
            }
        }
        item { Button(onClick = { vm.locked = true }, Modifier.fillMaxWidth()) { Text(tr("lockn")) } }
    }
}
