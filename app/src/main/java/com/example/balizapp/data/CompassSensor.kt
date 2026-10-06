package com.example.balizapp.data

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Lectura de la brújula: hacia dónde apunta la parte superior del teléfono. */
data class CompassReading(
    /** Grados respecto del norte MAGNÉTICO (0 = norte, 90 = este). */
    val azimuthDegrees: Float,
    /** true si el sistema indica que el magnetómetro necesita calibrarse. */
    val unreliable: Boolean,
)

/**
 * Brújula con magnetómetro + acelerómetro (RF6, sensor).
 * El acelerómetro da la dirección de la gravedad y el magnetómetro la del campo magnético;
 * con ambos, SensorManager calcula la orientación del teléfono (getRotationMatrix + getOrientation).
 * Se aplica un filtro pasa-bajos para que la flecha no tiemble.
 */
class CompassSensor(context: Context) {
    private val sensorManager = ContextCompat.getSystemService(context, SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** Los emuladores no siempre tienen magnetómetro: en ese caso se muestra solo el rumbo en texto. */
    val isAvailable: Boolean get() = accelerometer != null && magnetometer != null

    fun readings(): Flow<CompassReading> = callbackFlow {
        val manager = sensorManager
        if (manager == null || accelerometer == null || magnetometer == null) {
            close()
            return@callbackFlow
        }
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var unreliable = false
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> { lowPass(event.values, gravity, hasGravity); hasGravity = true }
                    Sensor.TYPE_MAGNETIC_FIELD -> { lowPass(event.values, geomagnetic, hasGeomagnetic); hasGeomagnetic = true }
                }
                if (hasGravity && hasGeomagnetic &&
                    SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)
                ) {
                    SensorManager.getOrientation(rotation, orientation)
                    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    trySend(CompassReading(normalize(azimuth), unreliable))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    unreliable = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
                }
            }
        }
        manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        manager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        // Al dejar de observar (pantalla en segundo plano) se apagan los sensores: ahorra batería.
        awaitClose { manager.unregisterListener(listener) }
    }

    private fun lowPass(input: FloatArray, output: FloatArray, initialized: Boolean) {
        if (!initialized) {
            input.copyInto(output, endIndex = 3)
            return
        }
        for (i in 0..2) output[i] = output[i] + ALPHA * (input[i] - output[i])
    }

    companion object {
        private const val ALPHA = 0.15f

        fun normalize(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

        /**
         * Diferencia entre el norte magnético y el geográfico en ese lugar (en Buenos Aires ronda
         * los -9°). El rumbo al auto se calcula respecto del norte geográfico, así que se corrige.
         */
        fun declination(latitude: Double, longitude: Double): Float =
            GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()).declination
    }
}
