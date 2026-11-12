package com.newdubai.shop

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.newdubai.shop.work.DailyBackupWorker
import com.newdubai.shop.work.ReminderWorker
import com.newdubai.shop.work.createChannel
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniquePeriodicWork("daily_backup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyBackupWorker>(24, TimeUnit.HOURS).build())
        wm.enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build())
    }
}
