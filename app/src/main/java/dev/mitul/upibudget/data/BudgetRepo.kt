package dev.mitul.upibudget.data

import dev.mitul.upibudget.budget.*
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.ingest.Candidate
import java.time.LocalDate

data class CurrentBudget(val month: MonthInfo, val summary: Summary)

fun TxnEntity.toLite(cats: Map<Long, CategoryEntity>) = TxLite(
    time.toLocalDateTime(), amount, direction, kind, payeeNorm, cats[subId]?.isSavings == true, creditAs,
)
fun TxnEntity.toCandidate() = Candidate(id, time.toLocalDateTime(), amount, direction, source, ref, rawText, merged)
fun RecurringEntity.toLite() = RecurringLite(
    id, merchantNorm, amount, frequency, dueDay, nextDue?.toLocalDate(), endDate?.toLocalDate(), active, kind,
)
fun BudgetMonthEntity.toInfo() = MonthInfo(id, startDay.toLocalDate(), carryOver)

class BudgetRepo(private val db: AppDatabase, private val today: () -> LocalDate) {
    suspend fun txsOf(monthId: Long): List<TxLite> {
        val cats = db.categories().all().associateBy { it.id }
        return db.txns().inMonth(monthId).map { it.toLite(cats) }
    }

    suspend fun current(): CurrentBudget? {
        val m = db.months().latest()?.toInfo() ?: return null
        val rec = db.recurring().all().map { it.toLite() }
        return CurrentBudget(m, BudgetEngine.summarize(m, txsOf(m.id), rec, today()))
    }

    /** Recurring items whose charge is >7 days late. Each is returned once per budget month. */
    suspend fun missedCharges(): List<RecurringEntity> {
        val m = db.months().latest()?.toInfo() ?: return emptyList()
        val txs = txsOf(m.id)
        val out = ArrayList<RecurringEntity>()
        for (r in db.recurring().all()) {
            if (BudgetEngine.missedDue(r.toLite(), m, txs, today()) == null) continue
            val key = "missed_${r.id}_${m.id}"
            if (db.misc().setting(key) != null) continue
            db.misc().putSetting(SettingEntity(key, "1"))
            out += r
        }
        return out
    }
}
