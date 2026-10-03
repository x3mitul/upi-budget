package dev.mitul.upibudget.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

fun LocalDateTime.toStore(): Long = toEpochSecond(ZoneOffset.UTC)
fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)
fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
