package dev.mitul.upibudget

import android.app.Application
import android.content.Context
import dev.mitul.upibudget.data.AppDatabase
import dev.mitul.upibudget.data.Seeder
import dev.mitul.upibudget.ingest.Ingestor
import dev.mitul.upibudget.ingest.Router
import dev.mitul.upibudget.notify.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var db: AppDatabase
    lateinit var ingestor: Ingestor
    lateinit var notifier: Notifier
    lateinit var router: Router
    private val ready = CompletableDeferred<Unit>()

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        ingestor = Ingestor(db)
        notifier = Notifier(this, db).also { it.createChannels() }
        router = Router(db, ingestor) { notifier.show(it) }
        dev.mitul.upibudget.gmail.GmailWorker.schedule(this)   // also runs the daily-ish missed-charge check
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Seeder(db).seed(assets.open("categories.json").bufferedReader().use { it.readText() })
                ingestor.invalidate()
            } finally { ready.complete(Unit) }
        }
    }

    suspend fun awaitReady() = ready.await()

    /**
     * Self-healing: re-reads the last few days of Axis SMS from the inbox. Anything the live receiver missed (app force-stopped,
     * killed by the phone's battery manager, phone off) is recovered; duplicates are dropped by the normal dedupe. Quiet: no notifications.
     */
    suspend fun catchUpSms(days: Int = 3): Int {
        if (checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return 0
        if (db.misc().setting("onboarded") != "1") return 0
        val quiet = Router(db, ingestor) { }
        return dev.mitul.upibudget.sms.SmsImporter.import(dev.mitul.upibudget.sms.SmsImporter.read(this, days), quiet)
    }

    /** Posts a "didn't charge — cancelled?" question for recurring items that are >7 days late (once per month each). */
    suspend fun missedChargesCheck() {
        dev.mitul.upibudget.data.BudgetRepo(db) { java.time.LocalDate.now() }.missedCharges().forEach { notifier.showMissed(it) }
    }

    companion object { fun get(ctx: Context): App = ctx.applicationContext as App }
}
