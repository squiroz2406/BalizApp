package com.example.balizapp.ui.components

import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFormat get() = DateTimeFormatter.ofPattern("HH:mm", AppLocale.current().locale)
private val dateFormat get() = DateTimeFormatter.ofPattern("EEE d MMM", AppLocale.current().locale)

fun Timestamp.toMillis(): Long = toDate().time

/** "16:00" si es hoy, "mañana 16:00", o "mar 30 sept 16:00". */
fun formatDateTime(millis: Long): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val time = dateTime.format(timeFormat)
    return when (dateTime.toLocalDate()) {
        today -> time
        today.plusDays(1) -> AppLocale.str(R.string.time_tomorrow, time)
        today.minusDays(1) -> AppLocale.str(R.string.time_yesterday, time)
        else -> "${dateTime.format(dateFormat)} $time"
    }
}

fun formatDateTime(timestamp: Timestamp): String = formatDateTime(timestamp.toMillis())

/** Duración legible: "45 min", "1 h 05 min", "2 h". */
fun formatDuration(millis: Long): String {
    val totalMinutes = abs(millis) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h %02d min".format(minutes)
    }
}

/** Cuenta regresiva hasta [expiresAt]: "Quedan 1 h 05 min" o "Venció hace 10 min". */
fun formatRemaining(expiresAt: Long, now: Long): String {
    val diff = expiresAt - now
    return when {
        diff > 60_000 -> AppLocale.str(R.string.time_remaining, formatDuration(diff))
        diff > 0 -> AppLocale.str(R.string.time_less_than_minute)
        else -> AppLocale.str(R.string.time_expired_ago, formatDuration(diff))
    }
}

/** Hora actual que se actualiza cada [intervalMs]; sirve para las cuentas regresivas. */
@Composable
fun rememberNow(intervalMs: Long = 1_000): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
    return now
}
