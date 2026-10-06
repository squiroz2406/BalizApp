package com.example.balizapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Avisos de vencimiento (RF7) con alarmas locales.
 *
 * Por qué locales y no push (FCM): el aviso depende de una hora que el usuario fija en su
 * teléfono, no de un evento del servidor. Una alarma local no necesita backend y suena
 * aunque no haya internet ni la app esté abierta.
 *
 * Cada estacionamiento activo con vencimiento tiene dos alarmas:
 * - WARNING: N minutos antes (N = reminderMinutes del perfil).
 * - EXPIRED: en el momento del vencimiento.
 * Lo programado se guarda en SharedPreferences para reprogramarlo si el teléfono se reinicia
 * (Android borra todas las alarmas al apagarse).
 */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = ContextCompat.getSystemService(appContext, AlarmManager::class.java)!!
    private val prefs = appContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    /** Lo necesario para programar y mostrar los avisos de un estacionamiento. */
    data class Spec(
        val parkingId: String,
        val expiresAt: Long,
        val reminderMinutes: Int,
        val vehicleName: String,
        val place: String?,
    )

    enum class Kind { WARNING, EXPIRED }

    /**
     * Deja programados exactamente los avisos de [specs]: agrega o actualiza los que cambiaron
     * y cancela los de estacionamientos que ya no están (retirados, borrados u otra sesión).
     */
    @Synchronized
    fun sync(specs: List<Spec>) {
        val previous = loadStored().associateBy { it.parkingId }
        val next = specs.associateBy { it.parkingId }
        (previous.keys - next.keys).forEach(::cancelAlarms)
        next.values.forEach { spec -> if (previous[spec.parkingId] != spec) setAlarms(spec) }
        store(next.values.toList())
    }

    /** Vuelve a registrar todo lo guardado (después de reiniciar o de conceder alarmas exactas). */
    @Synchronized
    fun rescheduleAll() {
        val stillValid = loadStored().filter { it.expiresAt > System.currentTimeMillis() }
        stillValid.forEach(::setAlarms)
        store(stillValid)
    }

    /** Al cerrar sesión: no deben sonar avisos de la cuenta anterior. */
    @Synchronized
    fun cancelAll() {
        loadStored().forEach { cancelAlarms(it.parkingId) }
        store(emptyList())
    }

    /** Android 12+ pide un permiso especial para alarmas exactas; sin él, el aviso puede demorarse. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Aviso de prueba en [seconds] segundos, para verificar permisos y la demo. */
    fun scheduleTest(seconds: Int = 10) {
        val spec = Spec(TEST_ID, System.currentTimeMillis() + 15 * 60_000L, 15, "Prueba", "Aviso de prueba")
        setAlarm(System.currentTimeMillis() + seconds * 1000L, pendingIntent(spec, Kind.WARNING))
    }

    private fun setAlarms(spec: Spec) {
        cancelAlarms(spec.parkingId)
        val now = System.currentTimeMillis()
        if (spec.expiresAt <= now) return
        val warnAt = spec.expiresAt - spec.reminderMinutes * 60_000L
        // Si al guardar ya queda menos tiempo que el aviso previo, solo se avisa al vencer.
        if (warnAt > now) setAlarm(warnAt, pendingIntent(spec, Kind.WARNING))
        setAlarm(spec.expiresAt, pendingIntent(spec, Kind.EXPIRED))
    }

    private fun setAlarm(triggerAt: Long, operation: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            // Respaldo sin permiso de alarmas exactas: Android puede demorarla algunos minutos.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun cancelAlarms(parkingId: String) {
        Kind.entries.forEach { kind ->
            val intent = Intent(appContext, ReminderReceiver::class.java).setAction(actionFor(kind))
            PendingIntent.getBroadcast(
                appContext, requestCode(parkingId, kind), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun pendingIntent(spec: Spec, kind: Kind): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java)
            .setAction(actionFor(kind))
            .putExtra(ReminderReceiver.EXTRA_PARKING_ID, spec.parkingId)
            .putExtra(ReminderReceiver.EXTRA_EXPIRES_AT, spec.expiresAt)
            .putExtra(ReminderReceiver.EXTRA_VEHICLE, spec.vehicleName)
            .putExtra(ReminderReceiver.EXTRA_PLACE, spec.place)
        return PendingIntent.getBroadcast(
            appContext, requestCode(spec.parkingId, kind), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionFor(kind: Kind) = "com.example.balizapp.REMINDER_${kind.name}"

    private fun requestCode(parkingId: String, kind: Kind) = "$parkingId/${kind.name}".hashCode()

    // --- Persistencia simple en SharedPreferences (JSON) ---

    private fun store(specs: List<Spec>) {
        val array = JSONArray()
        specs.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.parkingId)
                    .put("expiresAt", s.expiresAt)
                    .put("minutes", s.reminderMinutes)
                    .put("vehicle", s.vehicleName)
                    .put("place", s.place ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_SPECS, array.toString()).apply()
    }

    private fun loadStored(): List<Spec> {
        val raw = prefs.getString(KEY_SPECS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Spec(
                    parkingId = o.getString("id"),
                    expiresAt = o.getLong("expiresAt"),
                    reminderMinutes = o.getInt("minutes"),
                    vehicleName = o.getString("vehicle"),
                    place = if (o.isNull("place")) null else o.getString("place"),
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_SPECS = "specs"
        const val TEST_ID = "prueba"
    }
}
