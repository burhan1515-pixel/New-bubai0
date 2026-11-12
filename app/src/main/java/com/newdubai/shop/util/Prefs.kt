package com.newdubai.shop.util

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("nd_prefs", Context.MODE_PRIVATE)

    var lang: String get() = sp.getString("lang", "ps") ?: "ps"; set(v) { sp.edit().putString("lang", v).apply() }
    var unit: String get() = sp.getString("unit", "cm") ?: "cm"; set(v) { sp.edit().putString("unit", v).apply() }
    var lockMinutes: Int get() = sp.getInt("lock", 2); set(v) { sp.edit().putInt("lock", v).apply() }
    var lowStock: Float get() = sp.getFloat("low", 10f); set(v) { sp.edit().putFloat("low", v).apply() }
    var bioEnabled: Boolean get() = sp.getBoolean("bio", false); set(v) { sp.edit().putBoolean("bio", v).apply() }
    var autoBackup: Boolean get() = sp.getBoolean("autobk", true); set(v) { sp.edit().putBoolean("autobk", v).apply() }

    val hasCreds: Boolean get() = sp.contains("pin")

    fun setCreds(pw: String?, pin: String?) {
        val salt = sp.getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        val e = sp.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        if (pw != null) e.putString("pw", hash(pw, salt))
        if (pin != null) e.putString("pin", hash(pin, salt))
        e.apply()
    }

    fun verify(secret: String, isPin: Boolean): Boolean {
        val salt = sp.getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val stored = sp.getString(if (isPin) "pin" else "pw", null) ?: return false
        return MessageDigest.isEqual(hash(secret, salt).toByteArray(), stored.toByteArray())
    }

    private fun hash(s: String, salt: ByteArray): String {
        val spec = PBEKeySpec(s.toCharArray(), salt, 120_000, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(k, Base64.NO_WRAP)
    }
}
