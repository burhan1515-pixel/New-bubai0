package com.newdubai.shop.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.newdubai.shop.data.AppDb
import com.newdubai.shop.data.Calc
import com.newdubai.shop.util.Prefs
import com.newdubai.shop.util.Snapshots
import com.newdubai.shop.util.Tr
import java.time.LocalDate

const val CHANNEL = "nd_alerts"

fun createChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 26) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "NEW DUBAI", NotificationManager.IMPORTANCE_DEFAULT))
    }
}

class DailyBackupWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        if (!Prefs(applicationContext).autoBackup) return Result.success()
        return try {
            Snapshots.writeAuto(applicationContext, Snapshots.build(AppDb.get(applicationContext)))
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val db = AppDb.get(c); val prefs = Prefs(c)
        val alerts = Calc.alerts(
            LocalDate.now().toString(), db.customers().list(), db.orders().list(),
            db.payments().list(), db.fabrics().list(), prefs.lowStock.toDouble()
        )
        if (alerts.isEmpty()) return Result.success()
        createChannel(c)
        val lines = alerts.take(5).joinToString("\n") { Tr.get(it.kind, prefs.lang) + ": " + it.text }
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NEW DUBAI (${alerts.size})")
            .setContentText(Tr.get(alerts[0].kind, prefs.lang) + ": " + alerts[0].text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify(1001, n)
        return Result.success()
    }
}
