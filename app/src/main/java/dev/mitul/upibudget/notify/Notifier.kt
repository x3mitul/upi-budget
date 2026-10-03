package dev.mitul.upibudget.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.mitul.upibudget.MainActivity
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.AppDatabase
import dev.mitul.upibudget.data.RecurringEntity
import dev.mitul.upibudget.ingest.Outcome

class Notifier(private val ctx: Context, private val db: AppDatabase) {
    companion object {
        const val CH_PAYMENTS = "payments"
        const val CH_QUESTIONS = "questions"
        const val EXTRA_TXN = "txn"
        const val ACT_TEACH = "dev.mitul.upibudget.TEACH"
        const val ACT_CREDIT = "dev.mitul.upibudget.CREDIT"
        const val ACT_FREQ = "dev.mitul.upibudget.FREQ"
        const val ACT_STOP = "dev.mitul.upibudget.STOP"
        const val ACT_DISMISS = "dev.mitul.upibudget.DISMISS"
        const val EXTRA_SUB = "sub"; const val EXTRA_ANSWER = "answer"; const val EXTRA_REC = "rec"; const val EXTRA_FREQ = "freq"; const val EXTRA_NID = "nid"
    }

    fun createChannels() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PAYMENTS, "Payments", NotificationManager.IMPORTANCE_LOW).apply { description = "Status line after every payment" })
        nm.createNotificationChannel(NotificationChannel(CH_QUESTIONS, "Questions", NotificationManager.IMPORTANCE_HIGH).apply { description = "When the app needs your answer" })
    }

    private fun broadcast(action: String, nid: Int, vararg extras: Pair<String, Any>): PendingIntent {
        val i = Intent(action).setPackage(ctx.packageName).putExtra(EXTRA_NID, nid)
        extras.forEach { (k, v) -> if (v is Long) i.putExtra(k, v) else i.putExtra(k, v.toString()) }
        return PendingIntent.getBroadcast(ctx, (action + extras.joinToString { "${it.first}${it.second}" } + nid).hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openApp(txnId: Long): PendingIntent =
        PendingIntent.getActivity(ctx, txnId.toInt(), Intent(ctx, MainActivity::class.java).putExtra(EXTRA_TXN, txnId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun post(id: Int, b: NotificationCompat.Builder) {
        // The runtime permission only exists on Android 13+; older versions need no check.
        if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    private fun builder(channel: String, title: String, text: String) =
        NotificationCompat.Builder(ctx, channel).setSmallIcon(dev.mitul.upibudget.R.drawable.ic_stat).setColor(0xFF2F6B57.toInt()).setContentTitle(title)
            .setContentText(text).setAutoCancel(true)

    suspend fun show(o: Outcome) {
        when (o) {
            Outcome.Skipped -> Unit
            is Outcome.Debit -> {
                val nid = o.txnId.toInt()
                val b = builder(if (o.asked) CH_QUESTIONS else CH_PAYMENTS, Lines.debitTitle(o), Lines.status(o.summary)).setContentIntent(openApp(o.txnId))
                if (o.asked) {
                    val names = db.categories().all().associate { it.id to it.name }
                    o.guesses.forEach { sub -> b.addAction(0, names[sub] ?: "?", broadcast(ACT_TEACH, nid, EXTRA_TXN to o.txnId, EXTRA_SUB to sub)) }
                    b.addAction(0, "More…", openApp(o.txnId))
                } else b.addAction(0, "Change", openApp(o.txnId))
                post(nid, b)
            }
            is Outcome.Credit -> {
                val nid = o.txnId.toInt()
                val b = builder(if (o.answered == null) CH_QUESTIONS else CH_PAYMENTS, Lines.creditTitle(o), Lines.status(o.summary)).setContentIntent(openApp(o.txnId))
                if (o.answered == null) {
                    listOf(CreditAnswer.ALLOWANCE to "Allowance", CreditAnswer.PAYBACK to "Payback", CreditAnswer.IGNORE to "Ignore").forEach { (a, label) ->
                        b.addAction(0, label, broadcast(ACT_CREDIT, nid, EXTRA_TXN to o.txnId, EXTRA_ANSWER to a.name))
                    }
                } else b.addAction(0, "Change", openApp(o.txnId))
                post(nid, b)
            }
            is Outcome.AskFrequency -> {
                val nid = 1_000_000 + o.recurringId.toInt()
                val b = builder(CH_QUESTIONS, "New AutoPay: ${o.merchant} ${Money.format(o.amount)}", "How often does it charge?")
                listOf(Frequency.MONTHLY to "Monthly", Frequency.YEARLY to "Yearly", Frequency.ONE_TIME to "One-time").forEach { (f, label) ->
                    b.addAction(0, label, broadcast(ACT_FREQ, nid, EXTRA_REC to o.recurringId, EXTRA_FREQ to f.name))
                }
                post(nid, b)
            }
        }
    }

    fun showMissed(r: RecurringEntity) {
        val nid = 2_000_000 + r.id.toInt()
        post(nid, builder(CH_QUESTIONS, "${dev.mitul.upibudget.core.prettyName(r.merchantNorm)} didn't charge this month", "Cancelled?")
            .addAction(0, "Yes, stop", broadcast(ACT_STOP, nid, EXTRA_REC to r.id))
            .addAction(0, "Keep", broadcast(ACT_DISMISS, nid)))
    }
}
