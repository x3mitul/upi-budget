package dev.mitul.upibudget.core

typealias Paise = Long

enum class Direction { DEBIT, CREDIT }
enum class Kind { UPI, AUTOPAY, RD, UNKNOWN, CREDIT }
enum class Source { SMS, NOTIFICATION, GMAIL, MANUAL }
enum class PayeeType { P2M, P2A, UNKNOWN }
enum class CreditAnswer { ALLOWANCE, PAYBACK, IGNORE, REFUND, SAVINGS }   // SAVINGS: money coming back from an RD/FD/SIP; spendable again
enum class Frequency { MONTHLY, YEARLY, ONE_TIME }
enum class RecurringKind { SUBSCRIPTION, SAVING }
