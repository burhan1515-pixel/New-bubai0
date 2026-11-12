package com.newdubai.shop.util

import android.content.Context
import com.google.gson.Gson
import com.newdubai.shop.data.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class Snapshot(
    val version: Int = 1, val created: String = "",
    val customers: List<Customer> = emptyList(), val orders: List<TailorOrder> = emptyList(),
    val payments: List<Payment> = emptyList(), val fabrics: List<Fabric> = emptyList(),
    val purchases: List<Purchase> = emptyList(), val sales: List<Sale> = emptyList(),
    val expenses: List<Expense> = emptyList(), val measurements: List<MeasurementRec> = emptyList()
)

object Snapshots {
    suspend fun build(db: AppDb) = Snapshot(
        created = java.time.LocalDateTime.now().toString(),
        customers = db.customers().list(), orders = db.orders().list(), payments = db.payments().list(),
        fabrics = db.fabrics().list(), purchases = db.purchases().list(), sales = db.sales().list(),
        expenses = db.expenses().list(), measurements = db.measurements().list()
    )

    fun backupDir(ctx: Context) = File(ctx.filesDir, "backups").also { it.mkdirs() }

    /** Writes a private automatic backup and keeps the newest 7. */
    fun writeAuto(ctx: Context, snap: Snapshot, tag: String = "auto") {
        val f = File(backupDir(ctx), "${tag}_${LocalDate.now()}.ndbak")
        f.writeBytes(BackupIO.toJson(snap).toByteArray())
        backupDir(ctx).listFiles { x -> x.name.startsWith("auto_") }
            ?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
    }
}

object BackupIO {
    private val gson = Gson()
    private val MAGIC = "NDE1".toByteArray()

    fun toJson(s: Snapshot): String = gson.toJson(s)
    fun fromJson(j: String): Snapshot {
        val s = gson.fromJson(j, Snapshot::class.java)
        require(s != null && s.version > 0)
        // Backward compatibility: backups created before fabric colors were added
        // do not contain this field, so normalize it before Room restore.
        return s.copy(
            fabrics = s.fabrics.map { fabric ->
                if (fabric.color.isNullOrBlank()) fabric.copy(color = "#FFFFFF") else fabric
            }
        )
    }

    private fun key(pw: String, salt: ByteArray): SecretKeySpec {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pw.toCharArray(), salt, 120_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(plain: ByteArray, pw: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, pw: String): ByteArray {
        require(isEncrypted(data))
        val salt = data.copyOfRange(4, 20); val iv = data.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
        return c.doFinal(data, 32, data.size - 32)
    }

    fun isEncrypted(b: ByteArray) = b.size > 32 && String(b, 0, 4) == "NDE1"
    fun isZip(b: ByteArray) = b.size > 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    fun zip(inner: ByteArray, name: String): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            z.putNextEntry(ZipEntry(name)); z.write(inner); z.closeEntry()
            z.putNextEntry(ZipEntry("README.txt"))
            z.write("NEW DUBAI backup. Restore it from the Backup screen in the app.".toByteArray())
            z.closeEntry()
        }
        return bo.toByteArray()
    }

    fun unzip(b: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(b)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name.startsWith("backup")) return z.readBytes()
                e = z.nextEntry
            }
        }
        throw IllegalArgumentException("no backup entry")
    }
}

/** Real Google Drive REST client (appDataFolder). Needs an OAuth access token. */
class DriveClient(private val token: String) {
    private val name = "new_dubai_backup.ndbak"

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 20000; c.readTimeout = 60000
        return c
    }

    private fun check(c: HttpURLConnection) {
        if (c.responseCode >= 400) {
            val msg = c.errorStream?.bufferedReader()?.readText() ?: ""
            throw java.io.IOException("Drive ${c.responseCode}: $msg")
        }
    }

    fun listIds(): List<String> {
        val q = URLEncoder.encode("name='$name'", "UTF-8")
        val c = open("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q" +
            "&fields=files(id)&orderBy=modifiedTime%20desc", "GET")
        check(c)
        val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("files")
        return List(arr.length()) { arr.getJSONObject(it).getString("id") }
    }

    fun upload(data: ByteArray) {
        val old = listIds()
        val boundary = "ndb" + System.nanoTime()
        val meta = "{\"name\":\"$name\",\"parents\":[\"appDataFolder\"]}"
        val c = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        c.outputStream.use { o ->
            o.write(("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray())
            o.write(data)
            o.write("\r\n--$boundary--".toByteArray())
        }
        check(c); c.inputStream.readBytes()
        old.forEach { id ->
            val d = open("https://www.googleapis.com/drive/v3/files/$id", "DELETE")
            runCatching { d.responseCode }
        }
    }

    fun downloadLatest(): ByteArray? {
        val id = listIds().firstOrNull() ?: return null
        val c = open("https://www.googleapis.com/drive/v3/files/$id?alt=media", "GET")
        check(c)
        return c.inputStream.readBytes()
    }
}
