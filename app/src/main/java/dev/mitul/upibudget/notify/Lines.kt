package dev.mitul.upibudget.notify

import dev.mitul.upibudget.budget.Summary
import dev.mitul.upibudget.core.CreditAnswer
import dev.mitul.upibudget.core.Money
import dev.mitul.upibudget.core.prettyName
import dev.mitul.upibudget.ingest.Outcome

object Lines {
    fun status(s: Summary?): String = when {
        s == null -> ""
        s.left < 0 -> "Over by ${Money.whole(-s.left)}"
        else -> "${Money.whole(s.left)} left · ${Money.whole(s.perDay)}/day"
    }

    fun debitTitle(o: Outcome.Debit): String =
        if (o.asked) "${Money.format(o.amount)} to ${prettyName(o.payee)}" else "${Money.format(o.amount)} · ${prettyName(o.payee)} · ${o.subName}"

    fun creditTitle(o: Outcome.Credit): String = when (val a = o.answered) {
        null -> "${Money.format(o.amount)} received from ${prettyName(o.sender)}"
        else -> "${Money.format(o.amount)} ${if (a == CreditAnswer.SAVINGS) "From savings" else a.name.lowercase().replaceFirstChar { it.uppercase() }} · ${prettyName(o.sender)}"
    }
}
