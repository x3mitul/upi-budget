package dev.mitul.upibudget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mitul.upibudget.App
import dev.mitul.upibudget.budget.*
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TxnRow(val txn: TxnEntity, val subName: String)
data class DashboardUi(
    val summary: Summary?, val monthStart: LocalDate?, val groups: List<GroupTotal>, val lastGroups: List<GroupTotal>,
    val recent: List<TxnRow>, val toSortCount: Int, val saved: Paise, val reserved: List<Pair<String, Paise>>,
    val bars: List<DayBar> = emptyList(), val pace: Paise = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = App.get(app)
    private val db = a.db

    val categories = db.categories().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val toSort = db.txns().toSort().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val tick = MutableStateFlow(0)
    /** Called when the app comes to the front, so numbers that depend on the date (days left, "Today") never go stale overnight. */
    fun refresh() { tick.value++ }

    val ui: StateFlow<DashboardUi?> = combine(db.months().observeLatest(), db.txns().recent(1), db.recurring().observeAll(), db.categories().observeAll(), db.txns().toSort()) { _, _, _, _, _ -> }.combine(tick) { _, _ -> }
        .map { build() }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun build(): DashboardUi {
        a.awaitReady()
        val cats = db.categories().all()
        val byId = cats.associateBy { it.id }
        val cur = BudgetRepo(db) { LocalDate.now() }.current()
        val recent = db.txns().latest(40).map { TxnRow(it, byId[it.subId]?.name ?: "?") }
        val sortCount = db.txns().countToSort()
        if (cur == null) return DashboardUi(null, null, emptyList(), emptyList(), recent, sortCount, 0, emptyList())
        val months = db.months().all()
        val prev = months.lastOrNull { it.id != cur.month.id && it.startDay < cur.month.start.toEpochDay() }
        val txns = db.txns().inMonth(cur.month.id)
        val rec = db.recurring().all().map { it.toLite() }
        val txLite = txns.map { it.toLite(byId) }
        val reserved = rec.mapNotNull { r -> Recurring.reservation(r, cur.month, txLite, LocalDate.now()).takeIf { it > 0 }?.let { r.merchantNorm to it } }
        val s = cur.summary
        return DashboardUi(s, cur.month.start, Breakdown.byGroup(txns, cats),
            prev?.let { Breakdown.byGroup(db.txns().inMonth(it.id), cats) } ?: emptyList(), recent, sortCount, s.saved, reserved,
            DaySpend.bars(txLite, cur.month.start, LocalDate.now()), maxOf(0L, s.income - s.saved - s.reserved) / BudgetEngine.MONTH_DAYS)
    }

    fun teach(txnId: Long, subId: Long) = viewModelScope.launch(Dispatchers.IO) { a.ingestor.teach(txnId, subId) }
    fun answerCredit(txnId: Long, answer: CreditAnswer) = viewModelScope.launch(Dispatchers.IO) { a.ingestor.answerCredit(txnId, answer) }
    fun addAllowance(amount: Paise, date: LocalDate) = viewModelScope.launch(Dispatchers.IO) { a.ingestor.addAllowance(amount, date) }
    fun addKeyword(word: String, subId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val w = Normalizer.name(word)
        if (w.isNotEmpty()) { db.rules().insertKeyword(KeywordRuleEntity(word = w, subId = subId, weak = false, builtin = false, ord = 0)); a.ingestor.invalidate() }
    }
    suspend fun txn(id: Long): TxnEntity? = db.txns().byId(id)
    suspend fun guessesFor(t: TxnEntity): List<CategoryEntity> {
        val byId = db.categories().all().associateBy { it.id }
        return a.ingestor.guesses(t.payeeNorm, t.payeeType, 3).mapNotNull { byId[it] }
    }

    // ---- settings ----
    val userKeywords = MutableStateFlow<List<KeywordRuleEntity>>(emptyList())
    val nameRules = MutableStateFlow<List<NameRuleEntity>>(emptyList())
    val recurring = db.recurring().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs = db.misc().observeLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val gmailStatus: StateFlow<String> = flow {
        while (true) {
            emit(db.misc().setting("gmail_status") ?: if (db.misc().setting("gmail_connected") == "1") "Connected" else "Not connected")
            kotlinx.coroutines.delay(2000)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, "…")
    val account: StateFlow<String> = flow {
        while (true) { emit(db.misc().setting("account").orEmpty()); kotlinx.coroutines.delay(2000) }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val repo = CategoryRepo(db)
    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block(); a.ingestor.invalidate() }

    fun reloadRules() = viewModelScope.launch(Dispatchers.IO) {
        userKeywords.value = db.rules().allKeywords().filter { !it.builtin }
        nameRules.value = db.rules().allNameRules()
    }
    fun deleteKeyword(id: Long) = io { db.rules().deleteKeyword(id); reloadRules() }
    fun deleteNameRule(norm: String) = io { db.rules().deleteNameRule(norm); reloadRules() }
    fun saveRecurring(r: RecurringEntity) = io { if (r.id == 0L) db.recurring().insert(r) else db.recurring().update(r) }
    fun deleteRecurring(id: Long) = io { db.recurring().delete(id) }
    fun addGroup(name: String, color: Int) = io { runCatching { repo.addGroup(name, color) } }
    fun addSub(name: String, parentId: Long) = io { runCatching { repo.addSub(name, parentId) } }
    fun rename(id: Long, name: String) = io { runCatching { repo.rename(id, name) } }
    fun recolor(id: Long, color: Int) = io { repo.recolor(id, color) }
    fun deleteCategory(id: Long) = io { repo.deleteCategory(id) }
    fun setAccount(suffix: String) = io { db.misc().putSetting(SettingEntity("account", suffix.trim().uppercase())) }
    suspend fun exportBackup(): String = Backup.export(db)
    suspend fun importBackup(json: String) { Backup.import(db, json); a.ingestor.invalidate() }
    fun clearLogs() = viewModelScope.launch(Dispatchers.IO) { db.misc().clearLogs() }

    // ---- onboarding ----
    suspend fun isOnboarded(): Boolean { a.awaitReady(); return db.misc().setting("onboarded") == "1" }
    suspend fun runImport(ctx: android.content.Context): Int {
        a.awaitReady()
        val quiet = dev.mitul.upibudget.ingest.Router(db, a.ingestor) { }
        return dev.mitul.upibudget.sms.SmsImporter.import(dev.mitul.upibudget.sms.SmsImporter.read(ctx), quiet)
    }
    /** Re-reads 60 days of SMS after the parsers improve. Safe to repeat: known messages are skipped, unreadable leftovers are retried. */
    suspend fun rereadSms(ctx: android.content.Context): Int {
        a.awaitReady()
        db.txns().deleteUnreadSms()
        return runImport(ctx)
    }
    suspend fun allowanceGuess() = dev.mitul.upibudget.ingest.AllowanceGuess.suggestTxn(db)
    /** Finish setup. The allowance is optional: with none, the app asks when money arrives and starts the month then. */
    suspend fun finishOnboarding(amount: Paise?, date: LocalDate?, guessTxnId: Long?) {
        when {
            guessTxnId != null -> a.ingestor.answerCredit(guessTxnId, CreditAnswer.ALLOWANCE)
            amount != null && date != null -> a.ingestor.addAllowance(amount, date)
        }
        a.ingestor.reassignMonths()
        db.misc().putSetting(SettingEntity("onboarded", "1"))
    }
}
