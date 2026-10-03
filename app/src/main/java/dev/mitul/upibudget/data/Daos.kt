package dev.mitul.upibudget.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface CategoryDao {
    @Insert suspend fun insert(c: CategoryEntity): Long
    @Update suspend fun update(c: CategoryEntity)
    @Query("SELECT * FROM category ORDER BY sortOrder, id") suspend fun all(): List<CategoryEntity>
    @Query("SELECT * FROM category ORDER BY sortOrder, id") fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT COUNT(*) FROM category") suspend fun count(): Int
    @Query("DELETE FROM category WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT id FROM category WHERE name = :name AND parentId IS NOT NULL LIMIT 1") suspend fun subIdByName(name: String): Long?
}

@Dao interface TxnDao {
    @Insert suspend fun insert(t: TxnEntity): Long
    @Update suspend fun update(t: TxnEntity)
    @Query("SELECT * FROM txn WHERE id = :id") suspend fun byId(id: Long): TxnEntity?
    @Query("SELECT * FROM txn ORDER BY time DESC") suspend fun all(): List<TxnEntity>
    @Query("SELECT * FROM txn WHERE monthId = :monthId ORDER BY time DESC") suspend fun inMonth(monthId: Long): List<TxnEntity>
    @Query("SELECT * FROM txn WHERE monthId = :monthId ORDER BY time DESC") fun observeInMonth(monthId: Long): Flow<List<TxnEntity>>
    @Query("SELECT * FROM txn ORDER BY time DESC LIMIT :limit") fun recent(limit: Int): Flow<List<TxnEntity>>
    @Query("SELECT * FROM txn WHERE needsSorting = 1 ORDER BY time DESC") fun toSort(): Flow<List<TxnEntity>>
    @Query("SELECT * FROM txn WHERE time BETWEEN :from AND :to") suspend fun inWindow(from: Long, to: Long): List<TxnEntity>
    @Query("UPDATE txn SET subId = :to WHERE subId = :from") suspend fun repoint(from: Long, to: Long)
    @Query("SELECT subId FROM txn WHERE direction = 'DEBIT' GROUP BY subId ORDER BY COUNT(*) DESC LIMIT :limit") suspend fun mostUsedSubs(limit: Int): List<Long>
    @Query("SELECT * FROM txn ORDER BY time DESC LIMIT :n") suspend fun latest(n: Int): List<TxnEntity>
    @Query("SELECT COUNT(*) FROM txn WHERE needsSorting = 1") suspend fun countToSort(): Int
    /** Leftovers from messages an older version could not read (no payee). Removed before a re-read so they can be parsed again. */
    @Query("DELETE FROM txn WHERE source = 'SMS' AND kind = 'UNKNOWN' AND payeeRaw = ''") suspend fun deleteUnreadSms()
    @Query("DELETE FROM txn") suspend fun deleteAll()
    @Query("UPDATE txn SET subId = :sub, needsSorting = 0 WHERE payeeNorm = :norm AND needsSorting = 1") suspend fun applyToSorting(norm: String, sub: Long)
    @Query("UPDATE txn SET monthId = :to WHERE time >= :from AND monthId IN (:olds)") suspend fun moveToMonth(from: Long, to: Long, olds: List<Long>)
}

@Dao interface RuleDao {
    @Query("SELECT * FROM name_rule") suspend fun allNameRules(): List<NameRuleEntity>
    @Upsert suspend fun upsertNameRule(r: NameRuleEntity)
    @Query("DELETE FROM name_rule WHERE payeeNorm = :norm") suspend fun deleteNameRule(norm: String)
    @Query("UPDATE name_rule SET subId = :to WHERE subId = :from") suspend fun repointNameRules(from: Long, to: Long)
    @Query("SELECT * FROM keyword_rule ORDER BY ord") suspend fun allKeywords(): List<KeywordRuleEntity>
    @Insert suspend fun insertKeywords(list: List<KeywordRuleEntity>)
    @Insert suspend fun insertKeyword(k: KeywordRuleEntity): Long
    @Query("DELETE FROM keyword_rule WHERE builtin = 1") suspend fun deleteBuiltinKeywords()
    @Query("DELETE FROM keyword_rule WHERE builtin = 1 AND subId IN (:ids)") suspend fun deleteBuiltinKeywordsOf(ids: List<Long>)
    @Query("DELETE FROM keyword_rule WHERE id = :id") suspend fun deleteKeyword(id: Long)
    @Query("UPDATE keyword_rule SET subId = :to WHERE subId = :from") suspend fun repointKeywords(from: Long, to: Long)
    @Query("SELECT * FROM credit_rule WHERE senderNorm = :senderNorm") suspend fun creditRule(senderNorm: String): CreditRuleEntity?
    @Upsert suspend fun upsertCreditRule(r: CreditRuleEntity)
    @Query("SELECT * FROM credit_rule") suspend fun allCreditRules(): List<CreditRuleEntity>
    @Query("DELETE FROM name_rule") suspend fun deleteAllNameRules()
    @Query("DELETE FROM credit_rule") suspend fun deleteAllCreditRules()
    @Query("DELETE FROM keyword_rule WHERE builtin = 0") suspend fun deleteUserKeywords()
}

@Dao interface MonthDao {
    @Insert suspend fun insert(m: BudgetMonthEntity): Long
    @Update suspend fun update(m: BudgetMonthEntity)
    @Query("SELECT * FROM budget_month ORDER BY startDay DESC, id DESC LIMIT 1") suspend fun latest(): BudgetMonthEntity?
    @Query("SELECT * FROM budget_month ORDER BY startDay DESC, id DESC LIMIT 1") fun observeLatest(): Flow<BudgetMonthEntity?>
    @Query("SELECT * FROM budget_month WHERE id = :id") suspend fun byId(id: Long): BudgetMonthEntity?
    @Query("SELECT * FROM budget_month ORDER BY startDay") suspend fun all(): List<BudgetMonthEntity>
    @Query("DELETE FROM budget_month") suspend fun deleteAll()
}

@Dao interface RecurringDao {
    @Insert suspend fun insert(r: RecurringEntity): Long
    @Update suspend fun update(r: RecurringEntity)
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM recurring") suspend fun all(): List<RecurringEntity>
    @Query("SELECT * FROM recurring") fun observeAll(): Flow<List<RecurringEntity>>
    @Query("DELETE FROM recurring") suspend fun deleteAll()
}

@Dao interface MiscDao {
    @Query("SELECT value FROM setting WHERE `key` = :key") suspend fun setting(key: String): String?
    @Upsert suspend fun putSetting(s: SettingEntity)
    @Query("SELECT COUNT(*) > 0 FROM seen_email WHERE messageId = :id") suspend fun seenEmail(id: String): Boolean
    @Upsert suspend fun markEmailSeen(e: SeenEmailEntity)
    @Insert suspend fun addLog(l: DebugLogEntity)
    @Query("SELECT COUNT(*) > 0 FROM debug_log WHERE tag = :tag AND text = :text") suspend fun hasLog(tag: String, text: String): Boolean
    @Query("SELECT * FROM debug_log ORDER BY id DESC LIMIT 300") fun observeLogs(): Flow<List<DebugLogEntity>>
    @Query("DELETE FROM debug_log WHERE id NOT IN (SELECT id FROM debug_log ORDER BY id DESC LIMIT :keep)") suspend fun pruneLogs(keep: Int)
    @Query("DELETE FROM debug_log") suspend fun clearLogs()
}
