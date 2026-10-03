package dev.mitul.upibudget.ingest

import dev.mitul.upibudget.budget.*
import dev.mitul.upibudget.categorize.*
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import dev.mitul.upibudget.parse.ParsedMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface Outcome {
    data object Skipped : Outcome
    data class Debit(val txnId: Long, val amount: Paise, val payee: String, val subName: String, val asked: Boolean,
                     val guesses: List<Long>, val summary: Summary?) : Outcome
    data class Credit(val txnId: Long, val amount: Paise, val sender: String, val answered: CreditAnswer?, val summary: Summary?) : Outcome
    data class AskFrequency(val recurringId: Long, val merchant: String, val amount: Paise) : Outcome
}

class Ingestor(private val db: AppDatabase, private val today: () -> LocalDate = { LocalDate.now() }) {
    private val budget = BudgetRepo(db, today)
    private val lock = Mutex()
    @Volatile private var cached: Categorizer? = null

    fun invalidate() { cached = null }

    private suspend fun categorizer(): Categorizer = cached ?: run {
        val kws = db.rules().allKeywords()
        fun entries(builtin: Boolean) = kws.filter { it.builtin == builtin }.map { KeywordEntry(it.word, it.subId, it.weak, it.ord) }
        Categorizer(db.rules().allNameRules().associate { it.payeeNorm to it.subId }, entries(false), entries(true)).also { cached = it }
    }

    private suspend fun uncategorized() = db.categories().subIdByName("Uncategorized")!!
    private suspend fun subName(id: Long) = db.categories().all().firstOrNull { it.id == id }?.name ?: "Uncategorized"

    private suspend fun monthFor(date: LocalDate): Long =
        db.months().all().lastOrNull { it.startDay <= date.toEpochDay() }?.id ?: 0L

    suspend fun handle(parsed: ParsedMessage, source: Source, rawText: String, receivedAt: LocalDateTime): Outcome = lock.withLock {
        when (parsed) {
            is ParsedMessage.Ignored -> Outcome.Skipped
            is ParsedMessage.Payment -> payment(parsed, source, rawText, receivedAt)
            is ParsedMessage.MandateNotice -> {
                upsertRecurring(parsed.merchant, parsed.amount, parsed.dueDate, RecurringKind.SUBSCRIPTION, updateAmount = true)
                Outcome.Skipped
            }
            is ParsedMessage.MandateCreated -> {
                val r = upsertRecurring(parsed.merchant, parsed.amount, parsed.start, RecurringKind.SUBSCRIPTION, updateAmount = true, end = parsed.end)
                Outcome.AskFrequency(r.id, parsed.merchant, parsed.amount)
            }
            is ParsedMessage.MandateRevoked -> {
                val norm = Normalizer.name(parsed.merchant)
                db.recurring().all().filter { Recurring.matches(it.merchantNorm, norm) && it.active }.forEach { db.recurring().update(it.copy(active = false)) }
                Outcome.Skipped
            }
            is ParsedMessage.RdBooked -> {
                val date = receivedAt.toLocalDate()
                upsertRecurring("RD " + parsed.account, parsed.amount, date, RecurringKind.SAVING, updateAmount = false,
                    end = date.plusMonths(parsed.tenureMonths.toLong()))
                Outcome.Skipped
            }
        }
    }

    private suspend fun upsertRecurring(merchant: String, amount: Paise, due: LocalDate, kind: RecurringKind,
                                        updateAmount: Boolean, end: LocalDate? = null): RecurringEntity {
        val norm = Normalizer.name(merchant)
        val existing = db.recurring().all().firstOrNull { Recurring.matches(it.merchantNorm, norm) }
        if (existing != null) {
            val upd = existing.copy(amount = if (updateAmount) amount else existing.amount, active = true,
                endDate = end?.toEpochDay() ?: existing.endDate, nextDue = existing.nextDue ?: due.toEpochDay())
            if (upd != existing) db.recurring().update(upd)
            return upd
        }
        val r = RecurringEntity(merchantNorm = norm, amount = amount, frequency = Frequency.MONTHLY, dueDay = due.dayOfMonth,
            nextDue = due.toEpochDay(), endDate = end?.toEpochDay(), active = true, kind = kind)
        return r.copy(id = db.recurring().insert(r))
    }

    private suspend fun accountOk(p: ParsedMessage.Payment): Boolean {
        val acct = p.account ?: return true
        val known = db.misc().setting("account")?.takeIf { it.isNotBlank() }
        if (known == null) {
            if (p.direction == Direction.DEBIT && p.kind == Kind.UPI) db.misc().putSetting(SettingEntity("account", acct))
            return true
        }
        return known == acct
    }

    private suspend fun payment(p: ParsedMessage.Payment, source: Source, raw: String, receivedAt: LocalDateTime): Outcome {
        if (!accountOk(p)) return Outcome.Skipped
        val time = p.time ?: receivedAt
        val incoming = Candidate(0, time, p.amount, p.direction, source, p.ref, raw, false)
        val nearby = db.txns().inWindow(time.minusDays(1).toStore(), time.plusDays(1).toStore()).map { it.toCandidate() }
        Dedupe.findDuplicate(incoming, nearby)?.let { merge(it, p, source, raw, time); return Outcome.Skipped }
        return if (p.direction == Direction.DEBIT) debit(p, source, raw, time) else credit(p, source, raw, time)
    }

    private suspend fun merge(existing: Candidate, p: ParsedMessage.Payment, source: Source, raw: String, time: LocalDateTime) {
        val e = db.txns().byId(existing.id) ?: return
        if (source == Source.SMS && e.source != Source.SMS) {
            db.txns().update(e.copy(time = time.toStore(), ref = p.ref ?: e.ref, rawText = raw, source = Source.SMS,
                payeeRaw = p.payee.ifBlank { e.payeeRaw }, merged = true))
        } else if (e.source != source) {
            db.txns().update(e.copy(merged = true, ref = e.ref ?: p.ref))
        }
    }

    private suspend fun debit(p: ParsedMessage.Payment, source: Source, raw: String, time: LocalDateTime): Outcome {
        val norm = Normalizer.name(p.payee)
        val unc = uncategorized()
        val cat = if (norm.isEmpty()) CategoryResult.Unknown else categorizer().categorize(norm)
        val (sub, ask) = when (cat) {
            is CategoryResult.Known -> cat.subId to false
            is CategoryResult.Hint -> cat.subId to true
            CategoryResult.Unknown -> unc to true
        }
        val id = db.txns().insert(TxnEntity(time = time.toStore(), amount = p.amount, direction = Direction.DEBIT,
            payeeRaw = p.payee, payeeNorm = norm, payeeType = p.payeeType, subId = sub, needsSorting = ask, creditAs = null,
            source = source, ref = p.ref, rawText = raw, kind = p.kind, monthId = monthFor(time.toLocalDate())))
        if (p.kind == Kind.AUTOPAY && norm.isNotEmpty() && db.recurring().all().none { Recurring.matches(it.merchantNorm, norm) }) {
            val saving = db.categories().all().firstOrNull { it.id == sub }?.isSavings == true
            upsertRecurring(p.payee, p.amount, time.toLocalDate(), if (saving) RecurringKind.SAVING else RecurringKind.SUBSCRIPTION, true)
        }
        val guesses = if (ask) guesses(norm, p.payeeType) else emptyList()
        return Outcome.Debit(id, p.amount, p.payee.ifBlank { "payment" }, subName(sub), ask, guesses, budget.current()?.summary)
    }

    /** Category ids to offer as one-tap answers: keyword hit first, then the user's most used. A person with no match gets Friends & Family first. */
    suspend fun guesses(norm: String, type: PayeeType, count: Int = 2): List<Long> {
        val unc = uncategorized()
        val used = db.txns().mostUsedSubs(3).filter { it != unc }
        val base = categorizer().bestGuess(norm, used, count)
        if (type != PayeeType.P2A || (norm.isNotEmpty() && categorizer().categorize(norm) != CategoryResult.Unknown)) return base
        val friends = db.categories().subIdByName("Friends & Family")
        return (listOfNotNull(friends) + base).distinct().take(count)
    }

    private suspend fun credit(p: ParsedMessage.Payment, source: Source, raw: String, time: LocalDateTime): Outcome {
        val norm = Normalizer.name(p.payee)
        val cat = if (norm.isEmpty()) CategoryResult.Unknown else categorizer().categorize(norm)
        val fromSavings = cat is CategoryResult.Known && cat.via == Via.BUILTIN && db.categories().all().firstOrNull { it.id == cat.subId }?.isSavings == true
        val answer: CreditAnswer? = if (fromSavings) CreditAnswer.SAVINGS
            else if (cat is CategoryResult.Known && cat.via == Via.BUILTIN) CreditAnswer.REFUND
            else if (norm.isNotEmpty()) db.rules().creditRule(norm)?.answer else null
        // Money coming back from an RD/SIP means that item is closed: stop setting money aside for it.
        if (fromSavings) db.recurring().all().filter { it.kind == RecurringKind.SAVING && Recurring.matches(it.merchantNorm, norm) && it.active }
            .forEach { db.recurring().update(it.copy(active = false)) }
        val id = db.txns().insert(TxnEntity(time = time.toStore(), amount = p.amount, direction = Direction.CREDIT,
            payeeRaw = p.payee, payeeNorm = norm, payeeType = p.payeeType, subId = uncategorized(), needsSorting = answer == null,
            creditAs = answer, source = source, ref = p.ref, rawText = raw, kind = Kind.CREDIT, monthId = monthFor(time.toLocalDate())))
        if (answer == CreditAnswer.ALLOWANCE) applyAllowance(id)
        return Outcome.Credit(id, p.amount, p.payee.ifBlank { "unknown sender" }, answer, budget.current()?.summary)
    }

    suspend fun teach(txnId: Long, subId: Long): Outcome.Debit? = lock.withLock {
        val t = db.txns().byId(txnId) ?: return@withLock null
        db.txns().update(t.copy(subId = subId, needsSorting = false))
        if (t.payeeNorm.isNotEmpty()) {
            db.rules().upsertNameRule(NameRuleEntity(t.payeeNorm, subId))
            db.txns().applyToSorting(t.payeeNorm, subId)
            invalidate()
        }
        Outcome.Debit(t.id, t.amount, t.payeeRaw, subName(subId), false, emptyList(), budget.current()?.summary)
    }

    suspend fun answerCredit(txnId: Long, answer: CreditAnswer) = lock.withLock {
        val t = db.txns().byId(txnId) ?: return@withLock
        // Money you resolve now should show up now: a payback/refund answered after the month it arrived in has closed counts in the current month.
        val latest = db.months().latest()
        val month = if ((answer == CreditAnswer.PAYBACK || answer == CreditAnswer.REFUND || answer == CreditAnswer.SAVINGS) && latest != null) latest.id else t.monthId
        db.txns().update(t.copy(creditAs = answer, needsSorting = false, monthId = month))
        if (t.payeeNorm.isNotEmpty()) db.rules().upsertCreditRule(CreditRuleEntity(t.payeeNorm, answer))
        if (answer == CreditAnswer.ALLOWANCE) applyAllowance(txnId)
    }

    suspend fun addAllowance(amount: Paise, date: LocalDate) = lock.withLock {
        val id = db.txns().insert(TxnEntity(time = date.atTime(9, 0).toStore(), amount = amount, direction = Direction.CREDIT,
            payeeRaw = "Allowance (manual)", payeeNorm = "ALLOWANCE MANUAL", payeeType = PayeeType.UNKNOWN, subId = uncategorized(),
            needsSorting = false, creditAs = CreditAnswer.ALLOWANCE, source = Source.MANUAL, ref = null, rawText = "manual",
            kind = Kind.CREDIT, monthId = 0))
        applyAllowance(id)
    }

    private suspend fun applyAllowance(txnId: Long) {
        val t = db.txns().byId(txnId) ?: return
        val date = t.time.toLocalDateTime().toLocalDate()
        val latest = db.months().latest()
        if (BudgetEngine.shouldStartNewMonth(latest?.toInfo(), date)) {
            val newId = db.months().insert(BudgetMonthEntity(startDay = date.toEpochDay(), carryOver = 0))
            val olds = listOfNotNull(0L, latest?.id)
            db.txns().moveToMonth(date.atStartOfDay().toStore(), newId, olds)
            val carry = latest?.let { BudgetEngine.carryOverOf(it.toInfo(), budget.txsOf(it.id)) } ?: 0L
            db.months().update(BudgetMonthEntity(newId, date.toEpochDay(), carry))
            db.txns().update(db.txns().byId(txnId)!!.copy(monthId = newId))
        } else {
            // Backfilled/older allowance: join the month it falls in, or the newest month if it predates them all, so it is never silently dropped.
            db.txns().update(t.copy(monthId = monthFor(date).takeIf { it != 0L } ?: latest!!.id))
        }
    }

    suspend fun reassignMonths() = lock.withLock {
        for (m in db.months().all()) db.txns().moveToMonth(m.startDay.toLocalDate().atStartOfDay().toStore(), m.id, listOf(0L))
    }

    suspend fun setFrequency(recurringId: Long, f: Frequency) = lock.withLock {
        val r = db.recurring().all().firstOrNull { it.id == recurringId } ?: return@withLock
        val start = r.nextDue?.toLocalDate() ?: today()
        var next = start
        if (f == Frequency.YEARLY) while (next.plusDays(BudgetEngine.MISSED_AFTER_DAYS.toLong()).isBefore(today())) next = next.plusYears(1)
        db.recurring().update(r.copy(frequency = f, dueDay = start.dayOfMonth, nextDue = next.toEpochDay()))
    }

    suspend fun stopRecurring(recurringId: Long) = lock.withLock {
        db.recurring().all().firstOrNull { it.id == recurringId }?.let { db.recurring().update(it.copy(active = false)) }
    }
}
