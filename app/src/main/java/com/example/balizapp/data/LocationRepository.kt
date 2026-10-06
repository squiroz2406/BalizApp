package com.example.balizapp.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Una posición con su precisión aproximada en metros (si se conoce). */
data class GeoPosition(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null)

/** Ubicación del teléfono (FusedLocationProviderClient) y dirección legible (Geocoder). */
class LocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean = appContext.hasLocationPermission()

    /**
     * Lectura única de la posición actual (RF5). Devuelve null si no hay permiso o si no se
     * consiguió una posición en [timeoutMs]; en ese caso se usa la última conocida.
     */
    @SuppressLint("MissingPermission") // se verifica con hasPermission()
    suspend fun currentPosition(timeoutMs: Long = 15_000): GeoPosition? {
        if (!hasPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            val location = withTimeoutOrNull(timeoutMs) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            } ?: client.lastLocation.await()
            location?.let {
                GeoPosition(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener la ubicación", e)
            null
        } finally {
            cancellation.cancel()
        }
    }

    /** Dirección legible ("Av. Calchaquí 6200, Florencio Varela") o null si no se pudo resolver. */
    suspend fun addressFor(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.forLanguageTag("es-AR"))
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(5_000) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo resolver la dirección", e)
            null
        }
        return address?.let(::formatAddress)
    }

    private fun formatAddress(a: Address): String? {
        val street = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ").ifBlank { null }
        val city = a.locality ?: a.subAdminArea
        return listOfNotNull(street, city).joinToString(", ").ifBlank { a.getAddressLine(0) }
    }

    private companion object {
        const val TAG = "LocationRepository"
    }
}

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
