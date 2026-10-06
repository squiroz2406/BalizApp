package com.example.balizapp.ui.components

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
import java.util.Locale
import kotlin.math.abs

private val zone: ZoneId get() = ZoneId.systemDefault()
private val spanish = Locale.forLanguageTag("es-AR")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", spanish)
private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", spanish)

fun Timestamp.toMillis(): Long = toDate().time

/** "16:00" si es hoy, "mañana 16:00", o "mar 30 sept 16:00". */
fun formatDateTime(millis: Long): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val time = dateTime.format(timeFormat)
    return when (dateTime.toLocalDate()) {
        today -> time
        today.plusDays(1) -> "mañana $time"
        today.minusDays(1) -> "ayer $time"
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
        diff > 60_000 -> "Quedan ${formatDuration(diff)}"
        diff > 0 -> "Queda menos de 1 min"
        else -> "Venció hace ${formatDuration(diff)}"
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
