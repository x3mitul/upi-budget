package dev.mitul.upibudget.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.mitul.upibudget.core.*

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val parentId: Long?,
    val color: Int, val isSavings: Boolean, val builtin: Boolean, val sortOrder: Int,
)

@Entity(tableName = "txn", indices = [Index("monthId"), Index("ref"), Index("payeeNorm"), Index("time")])
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val time: Long, val amount: Long, val direction: Direction,
    val payeeRaw: String, val payeeNorm: String, val payeeType: PayeeType, val subId: Long, val needsSorting: Boolean,
    val creditAs: CreditAnswer?, val source: Source, val ref: String?, val rawText: String, val kind: Kind,
    val monthId: Long, val merged: Boolean = false,
)

@Entity(tableName = "name_rule") data class NameRuleEntity(@PrimaryKey val payeeNorm: String, val subId: Long)

@Entity(tableName = "keyword_rule", indices = [Index("word")])
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val word: String, val subId: Long,
    val weak: Boolean, val builtin: Boolean, val ord: Int,
)

@Entity(tableName = "credit_rule") data class CreditRuleEntity(@PrimaryKey val senderNorm: String, val answer: CreditAnswer)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val merchantNorm: String, val amount: Long,
    val frequency: Frequency, val dueDay: Int, val nextDue: Long?, val endDate: Long?, val active: Boolean, val kind: RecurringKind,
)

@Entity(tableName = "budget_month")
data class BudgetMonthEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val startDay: Long, val carryOver: Long)

@Entity(tableName = "seen_email") data class SeenEmailEntity(@PrimaryKey val messageId: String)
@Entity(tableName = "debug_log") data class DebugLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val time: Long, val tag: String, val text: String)
@Entity(tableName = "setting") data class SettingEntity(@PrimaryKey val key: String, val value: String)
