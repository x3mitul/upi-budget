package dev.mitul.upibudget.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.mitul.upibudget.App
import dev.mitul.upibudget.core.CreditAnswer
import dev.mitul.upibudget.core.Frequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val app = App.get(ctx)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.awaitReady()
                runCatching {
                when (i.action) {
                    Notifier.ACT_TEACH -> app.ingestor.teach(i.getLongExtra(Notifier.EXTRA_TXN, -1), i.getLongExtra(Notifier.EXTRA_SUB, -1))
                    Notifier.ACT_CREDIT -> app.ingestor.answerCredit(i.getLongExtra(Notifier.EXTRA_TXN, -1), CreditAnswer.valueOf(i.getStringExtra(Notifier.EXTRA_ANSWER)!!))
                    Notifier.ACT_FREQ -> app.ingestor.setFrequency(i.getLongExtra(Notifier.EXTRA_REC, -1), Frequency.valueOf(i.getStringExtra(Notifier.EXTRA_FREQ)!!))
                    Notifier.ACT_STOP -> app.ingestor.stopRecurring(i.getLongExtra(Notifier.EXTRA_REC, -1))
                }
                }   // a stale or malformed action must never crash the app
                ctx.getSystemService(NotificationManager::class.java).cancel(i.getIntExtra(Notifier.EXTRA_NID, 0))
            } finally { pending.finish() }
        }
    }
}
