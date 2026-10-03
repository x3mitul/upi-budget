package dev.mitul.upibudget.gmail

import dev.mitul.upibudget.data.AppDatabase
import dev.mitul.upibudget.data.SeenEmailEntity
import dev.mitul.upibudget.ingest.Router

object GmailSync {
    const val QUERY = "from:(axisbank.com OR axis.bank.in) newer_than:2d"

    suspend fun run(api: GmailApi, db: AppDatabase, router: Router): Int {
        var n = 0
        for (id in api.listIds(QUERY)) {
            if (db.misc().seenEmail(id)) continue
            try {
                val m = api.get(id)
                router.email(m.id, m.subject, m.body, m.at)
                db.misc().markEmailSeen(SeenEmailEntity(id))
                n++
            } catch (e: Exception) {
                // not marked seen: retried on the next run; ages out of "newer_than:2d".
            }
        }
        return n
    }
}
