package com.newdubai.shop

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.newdubai.shop.ui.AppVM
import com.newdubai.shop.ui.AuthScreen
import com.newdubai.shop.ui.MainShell
import com.newdubai.shop.ui.NewDubaiTheme
import com.newdubai.shop.util.LocalLang
import com.newdubai.shop.util.Tr

class MainActivity : FragmentActivity() {
    private val vm: AppVM by viewModels()
    private var stoppedAt = 0L

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun canBio() = BiometricManager.from(this)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun showBio() {
        if (!canBio()) return
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(Tr.get("bio_prompt", vm.lang))
            .setNegativeButtonText(Tr.get("cancel", vm.lang))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.locked = false }
        }).authenticate(info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            NewDubaiTheme(isSystemInDarkTheme()) {
                CompositionLocalProvider(
                    LocalLang provides vm.lang,
                    LocalLayoutDirection provides (if (vm.lang == "en") LayoutDirection.Ltr else LayoutDirection.Rtl)
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Crossfade(targetState = vm.locked, label = "lock") { locked ->
                            if (locked) AuthScreen(vm, canBio()) { showBio() } else MainShell(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() { super.onStop(); stoppedAt = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && vm.prefs.hasCreds &&
            SystemClock.elapsedRealtime() - stoppedAt > vm.prefs.lockMinutes * 60_000L) vm.locked = true
    }
}
