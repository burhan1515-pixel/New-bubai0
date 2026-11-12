@file:OptIn(ExperimentalMaterial3Api::class)

package com.newdubai.shop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.newdubai.shop.util.tr
import kotlin.math.roundToInt

@Composable
fun LangChips(vm: AppVM) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ps" to "پښتو", "fa" to "دری", "en" to "English").forEach { (k, n) ->
            FilterChip(selected = vm.lang == k, onClick = { vm.changeLang(k) }, label = { Text(n) })
        }
    }
}

@Composable
fun AuthScreen(vm: AppVM, canBio: Boolean, onBio: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val setup = !vm.prefs.hasCreds
    var pin by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var usePw by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(tick) { if (tick > 0) { shake.snapTo(26f); shake.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 320f)) } }
    LaunchedEffect(canBio, setup) { if (canBio && !setup && vm.prefs.bioEnabled) onBio() }

    fun fail(e: String) { err = e; tick++; pin = ""; pw = "" }
    fun attempt(secret: String, isPin: Boolean) { vm.tryUnlock(secret, isPin)?.let { fail(it) } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LapisDeep, cs.background)))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            LangChips(vm)
            Spacer(Modifier.height(18.dp))
            Text("NEW DUBAI", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Saffron, letterSpacing = 3.sp)
            StitchThread(Modifier.width(220.dp).height(26.dp))
            Spacer(Modifier.height(10.dp))
            Text(tr(if (setup) "setup" else "greet"), color = cs.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))

            Column(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (setup) {
                    TField(tr("pw"), pw, { pw = it })
                    TField(tr("pin"), newPin, { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it }, kb = KeyboardType.NumberPassword)
                    Button(onClick = {
                        if (pw.length < 6 || newPin.length != 6) fail("pwmin")
                        else { vm.prefs.setCreds(pw, newPin); vm.locked = false }
                    }) { Text(tr("save")) }
                } else if (usePw) {
                    OutlinedTextField(pw, { pw = it }, label = { Text(tr("pw")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { attempt(pw, false) }) { Text(tr("login")) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(6) { i ->
                            val c by animateColorAsState(if (i < pin.length) Saffron else cs.outline.copy(alpha = .5f), label = "dot")
                            Box(Modifier.size(16.dp).clip(CircleShape).background(c))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            row.forEach { k ->
                                Box(
                                    Modifier.size(68.dp).clip(CircleShape).background(cs.surface.copy(alpha = if (k == "bio" && !canBio) 0f else 1f))
                                        .clickable(enabled = !(k == "bio" && !canBio)) {
                                            when (k) {
                                                "del" -> pin = pin.dropLast(1)
                                                "bio" -> if (vm.prefs.bioEnabled) onBio()
                                                else -> if (pin.length < 6) {
                                                    pin += k
                                                    if (pin.length == 6) attempt(pin, true)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (k) {
                                        "del" -> Icon(Icons.Default.Backspace, null)
                                        "bio" -> if (canBio) Icon(Icons.Default.Fingerprint, null, tint = Saffron)
                                        else -> Text(k, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ErrText(err)
            if (!setup) TextButton(onClick = { usePw = !usePw; err = null; pin = ""; pw = "" }) { Text(tr(if (usePw) "usepin" else "usepw")) }
        }
    }
}
