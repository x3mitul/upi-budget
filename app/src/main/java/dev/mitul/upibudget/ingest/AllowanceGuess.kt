package dev.mitul.upibudget.ingest

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.AppDatabase
import java.time.LocalDate

object AllowanceGuess {
    /** Biggest unanswered/allowance-like imported credit; the txn id is returned too so it can be answered. */
    suspend fun suggestTxn(db: AppDatabase) =
        db.txns().all()
            .filter { it.direction == Direction.CREDIT && it.source != Source.MANUAL && (it.creditAs == null || it.creditAs == CreditAnswer.ALLOWANCE) }
            .maxWithOrNull(compareBy({ it.amount }, { it.time }))

    suspend fun suggest(db: AppDatabase): Pair<Paise, LocalDate>? =
        suggestTxn(db)?.let { it.amount to it.time.toLocalDateTime().toLocalDate() }
}
