package com.example.balizapp.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.balizapp.MainActivity
import com.example.balizapp.R

/** Recibe la alarma y muestra la notificación. Tocarla abre "Volver al auto". */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val parkingId = intent.getStringExtra(EXTRA_PARKING_ID) ?: return
        val expired = intent.action?.endsWith(ReminderScheduler.Kind.EXPIRED.name) == true
        val vehicle = intent.getStringExtra(EXTRA_VEHICLE) ?: "Tu vehículo"
        val place = intent.getStringExtra(EXTRA_PLACE)
        val expiresAt = intent.getLongExtra(EXTRA_EXPIRES_AT, 0L)

        val minutesLeft = ((expiresAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        val title = if (expired) "Venció el estacionamiento de $vehicle"
        else "Al estacionamiento de $vehicle le quedan $minutesLeft min"
        val text = listOfNotNull(place, "Tocá para volver al vehículo").joinToString(" · ")

        Notifications.show(context, parkingId, title, text, highPriority = expired)
    }

    companion object {
        const val EXTRA_PARKING_ID = "parkingId"
        const val EXTRA_EXPIRES_AT = "expiresAt"
        const val EXTRA_VEHICLE = "vehicle"
        const val EXTRA_PLACE = "place"
    }
}

/** Reprograma los avisos cuando Android los borra (reinicio, actualización de la app) o cuando se concede el permiso de alarmas exactas. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> ReminderScheduler(context).rescheduleAll()
        }
    }
}

object Notifications {
    private const val CHANNEL_ID = "vencimientos"

    /** El canal se crea una vez; el usuario puede ajustarlo en los Ajustes del teléfono. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vencimiento del estacionamiento",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Aviso antes de que venza el tiempo de estacionamiento y al vencer" }
        ContextCompat.getSystemService(context, NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** true si la app puede mostrar notificaciones (Android 13+ pide permiso explícito). */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // canPost() verifica el permiso
    fun show(context: Context, parkingId: String, title: String, text: String, highPriority: Boolean) {
        if (!canPost(context)) return
        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_RETURN_TO_CAR)
            .putExtra(MainActivity.EXTRA_PARKING_ID, parkingId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, parkingId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .addAction(0, "Volver al auto", contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            // Mismo id por estacionamiento: el aviso de vencido reemplaza al previo.
            NotificationManagerCompat.from(context).notify(parkingId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Se revocó el permiso entre la verificación y el aviso: no hay nada que mostrar.
        }
    }
}
