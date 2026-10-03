package dev.mitul.upibudget.gmail

import android.content.Context
import androidx.work.*
import dev.mitul.upibudget.App
import dev.mitul.upibudget.data.SettingEntity
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class GmailWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = App.get(applicationContext)
        app.awaitReady()
        val misc = app.db.misc()
        runCatching { app.catchUpSms() }
        runCatching { app.missedChargesCheck() }
        if (misc.setting("gmail_connected") != "1") return Result.success()
        return try {
            val auth = GmailAuth.authorize(applicationContext)
            val token = auth.accessToken
            if (auth.hasResolution() || token == null) {
                misc.putSetting(SettingEntity("gmail_status", "Needs sign-in again (open Settings)"))
                return Result.success()
            }
            val n = GmailSync.run(HttpGmailApi { token }, app.db, app.router)
            misc.putSetting(SettingEntity("gmail_status", "OK ${LocalTime.now().withNano(0)} ($n new)"))
            Result.success()
        } catch (e: Exception) {
            misc.putSetting(SettingEntity("gmail_status", "Error: ${e.message}"))
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "gmail-sync"
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<GmailWorker>(15, TimeUnit.MINUTES).setConstraints(net).build())

        fun runNow(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniqueWork(
            "$NAME-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<GmailWorker>().setConstraints(net).build())
    }
}
