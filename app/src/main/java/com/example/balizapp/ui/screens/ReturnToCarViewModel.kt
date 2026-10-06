package com.example.balizapp.ui.screens

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.balizapp.data.CompassReading
import com.example.balizapp.data.CompassSensor
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.navigation.ReturnToCarRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReturnToCarState(
    val loading: Boolean = true,
    val parking: Parking? = null,
    val vehicle: Vehicle? = null,
    val hasLocationPermission: Boolean = false,
    val myPosition: GeoPosition? = null,
    /** Distancia en línea recta hasta el vehículo, en metros. */
    val distanceMeters: Float? = null,
    /** Rumbo hacia el vehículo respecto del norte geográfico (0–360°). */
    val bearingDegrees: Float? = null,
    /** Hacia dónde apunta el teléfono respecto del norte geográfico; null sin brújula. */
    val headingDegrees: Float? = null,
    val compassAvailable: Boolean = false,
    val compassUnreliable: Boolean = false,
    val working: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
) {
    /** Giro de la flecha: con brújula, relativo al teléfono; sin brújula, relativo al norte. */
    val arrowRotation: Float?
        get() = bearingDegrees?.let { bearing ->
            CompassSensor.normalize(bearing - (headingDegrees ?: 0f))
        }

    val arrived: Boolean get() = (distanceMeters ?: Float.MAX_VALUE) <= ARRIVED_METERS

    companion object {
        const val ARRIVED_METERS = 15f
    }
}

/**
 * Volver al auto (RF5 + RF6): combina el estacionamiento, el GPS en tiempo real y la brújula.
 * Sensores y GPS solo están activos mientras la pantalla está visible (WhileSubscribed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnToCarViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val parkingId = savedStateHandle.toRoute<ReturnToCarRoute>().parkingId
    private val parkingRepo = ParkingRepository()
    private val vehicleRepo = VehicleRepository()
    private val locationRepo = LocationRepository(app)
    private val compass = CompassSensor(app)

    private val permission = MutableStateFlow(locationRepo.hasPermission())
    private val ui = MutableStateFlow(ReturnToCarState())

    private val positions: Flow<GeoPosition?> = permission.flatMapLatest { granted ->
        if (!granted) flowOf(null)
        else locationRepo.positionUpdates()
            .catch { Log.w(TAG, "Error de ubicación", it) }
    }.onStart { emit(null) }

    private val compassReadings: Flow<CompassReading?> =
        if (compass.isAvailable) compass.readings().onStart<CompassReading?> { emit(null) } else flowOf(null)

    val state: StateFlow<ReturnToCarState> = combine(
        parkingRepo.observeParking(parkingId),
        vehicleRepo.observeVehicles(),
        positions,
        compassReadings,
        ui,
    ) { parking, vehicles, me, reading, local ->
        val car = parking?.location
        var distance: Float? = null
        var bearing: Float? = null
        if (car != null && me != null) {
            val results = FloatArray(2)
            Location.distanceBetween(me.latitude, me.longitude, car.latitude, car.longitude, results)
            distance = results[0]
            bearing = CompassSensor.normalize(results[1])
        }
        // La brújula mide contra el norte magnético; se suma la declinación del lugar.
        val heading = reading?.let { r ->
            val ref = me ?: car?.let { GeoPosition(it.latitude, it.longitude) }
            val declination = ref?.let { CompassSensor.declination(it.latitude, it.longitude) } ?: 0f
            CompassSensor.normalize(r.azimuthDegrees + declination)
        }
        local.copy(
            loading = false,
            parking = parking,
            vehicle = parking?.let { p -> vehicles.firstOrNull { it.id == p.vehicleId } },
            hasLocationPermission = permission.value,
            myPosition = me,
            distanceMeters = distance,
            bearingDegrees = bearing,
            headingDegrees = heading,
            compassAvailable = compass.isAvailable,
            compassUnreliable = reading?.unreliable == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReturnToCarState())

    fun onLocationPermissionResult(granted: Boolean) {
        permission.value = granted
    }

    /** "Ya lo retiré" desde esta pantalla. */
    fun finish() {
        val parking = state.value.parking ?: return
        if (ui.value.working || parking.status != ParkingStatus.ACTIVE) return
        ui.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try {
                parkingRepo.finish(parkingId)
                ui.update { it.copy(working = false, finished = true) }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo marcar como retirado", e)
                ui.update { it.copy(working = false, error = "No se pudo marcar como retirado: ${e.message}") }
            }
        }
    }

    private companion object {
        const val TAG = "ReturnToCarViewModel"
    }
}
