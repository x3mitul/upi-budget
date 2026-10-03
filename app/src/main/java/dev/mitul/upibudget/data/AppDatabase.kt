package dev.mitul.upibudget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CategoryEntity::class, TxnEntity::class, NameRuleEntity::class, KeywordRuleEntity::class, CreditRuleEntity::class,
        RecurringEntity::class, BudgetMonthEntity::class, SeenEmailEntity::class, DebugLogEntity::class, SettingEntity::class],
    version = 1, exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun txns(): TxnDao
    abstract fun rules(): RuleDao
    abstract fun months(): MonthDao
    abstract fun recurring(): RecurringDao
    abstract fun misc(): MiscDao

    companion object {
        fun build(ctx: Context): AppDatabase = Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "upibudget.db").build()
    }
}
