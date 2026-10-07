package com.example.balizapp.ui.screens

import android.app.Application
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
import com.example.balizapp.data.RouteMath
import com.example.balizapp.data.RouteRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.WalkingRoute
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cómo guía la pantalla: flecha directa al auto o siguiendo la ruta por calles. */
enum class GuidanceMode { DIRECT, STREETS }

/** Estado de la ruta por calles. */
data class RouteUi(
    val loading: Boolean = false,
    val route: WalkingRoute? = null,
    val error: String? = null,
)

data class ReturnToCarState(
    val loading: Boolean = true,
    val parking: Parking? = null,
    val vehicle: Vehicle? = null,
    val hasLocationPermission: Boolean = false,
    val myPosition: GeoPosition? = null,
    val mode: GuidanceMode = GuidanceMode.DIRECT,
    /** Distancia en línea recta hasta el vehículo, en metros. */
    val directDistanceMeters: Float? = null,
    /** Lo que falta por calles (solo en modo STREETS con ruta calculada). */
    val routeRemainingMeters: Float? = null,
    val routeUi: RouteUi = RouteUi(),
    /** Rumbo hacia donde apunta la flecha (auto o próximo tramo de la ruta), respecto del norte geográfico. */
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
        get() = bearingDegrees?.let { CompassSensor.normalize(it - (headingDegrees ?: 0f)) }

    /** Distancia que se muestra en grande: por calles si hay ruta, si no en línea recta. */
    val shownDistanceMeters: Float?
        get() = if (mode == GuidanceMode.STREETS) routeRemainingMeters ?: directDistanceMeters else directDistanceMeters

    val arrived: Boolean get() = (directDistanceMeters ?: Float.MAX_VALUE) <= ARRIVED_METERS

    /** Puntos de la ruta a dibujar (vacío si no corresponde). */
    val routePoints: List<GeoPosition>
        get() = if (mode == GuidanceMode.STREETS) routeUi.route?.points.orEmpty() else emptyList()

    companion object {
        const val ARRIVED_METERS = 15f
    }
}

/**
 * Volver al auto (RF5 + RF6): combina el estacionamiento, el GPS en tiempo real, la brújula y,
 * en modo "Por calles", la ruta a pie calculada con OSRM (OpenStreetMap).
 * Sensores, GPS y ruta solo están activos mientras la pantalla está visible (WhileSubscribed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnToCarViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {
    private val parkingId = savedStateHandle.toRoute<ReturnToCarRoute>().parkingId
    private val parkingRepo = ParkingRepository()
    private val vehicleRepo = VehicleRepository()
    private val locationRepo = LocationRepository(app)
    private val routeRepo = RouteRepository()
    private val compass = CompassSensor(app)

    private val permission = MutableStateFlow(locationRepo.hasPermission())
    private val mode = MutableStateFlow(GuidanceMode.DIRECT)
    private val ui = MutableStateFlow(ReturnToCarState())

    private val parkingFlow = parkingRepo.observeParking(parkingId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** GPS compartido: lo usan el estado y el cálculo de ruta sin abrir dos suscripciones. */
    private val positions: Flow<GeoPosition?> = permission.flatMapLatest { granted ->
        if (!granted) flowOf(null)
        else locationRepo.positionUpdates().catch { Log.w(TAG, "Error de ubicación", it) }
    }.onStart { emit(null) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val compassReadings: Flow<CompassReading?> =
        if (compass.isAvailable) compass.readings().onStart<CompassReading?> { emit(null) } else flowOf(null)

    // --- Ruta por calles ---
    private var lastRoute: WalkingRoute? = null
    private var lastRouteDestination: GeoPosition? = null
    private var lastRequestAt = 0L
    private var routeRequest = 0          // se incrementa con "Recalcular"
    private val manualRecalc = MutableStateFlow(0)

    /**
     * Pide la ruta al activar el modo y la vuelve a pedir si el usuario se desvía más de
     * [OFF_ROUTE_METERS] de la línea o si toca "Recalcular", respetando el intervalo mínimo.
     * conflate + transform: los pedidos van de a uno; si mientras tanto llegan lecturas de GPS,
     * se usa solo la última.
     */
    private val routeFlow: Flow<RouteUi> = combine(
        mode,
        positions,
        parkingFlow.map { p -> p?.location?.let { GeoPosition(it.latitude, it.longitude) } }.distinctUntilChanged(),
        manualRecalc,
    ) { m, me, car, recalc -> RouteInput(m, me, car, recalc) }
        .conflate()
        .transform { input ->
            if (input.mode != GuidanceMode.STREETS || input.car == null) {
                emit(RouteUi())
                return@transform
            }
            val me = input.me
            if (me == null) {
                emit(RouteUi(loading = true))
                return@transform
            }
            val current = lastRoute
            val now = System.currentTimeMillis()
            val forced = input.recalc != routeRequest
            val offRoute = current != null && RouteMath.closestPoint(current.points, me).second > OFF_ROUTE_METERS
            val needsNew = current == null || forced || lastRouteDestination != input.car ||
                (offRoute && now - lastRequestAt >= RouteRepository.MIN_INTERVAL_MS)
            if (!needsNew) {
                emit(RouteUi(route = current))
                return@transform
            }
            if (!forced && current == null && now - lastRequestAt < RouteRepository.MIN_INTERVAL_MS && lastRequestAt != 0L) {
                // Falló hace poco: no insistir hasta que pase el intervalo.
                emit(RouteUi(error = "No se pudo calcular la ruta. Se reintenta en unos segundos."))
                return@transform
            }
            routeRequest = input.recalc
            lastRequestAt = now
            emit(RouteUi(loading = true, route = current))
            try {
                val route = routeRepo.walkingRoute(me, input.car.latitude, input.car.longitude)
                lastRoute = route
                lastRouteDestination = input.car
                emit(RouteUi(route = route))
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo calcular la ruta", e)
                emit(RouteUi(route = current, error = "No se pudo calcular la ruta por calles (¿sin conexión?). Se muestra la dirección directa."))
            }
        }
        .onStart { emit(RouteUi()) }

    private data class RouteInput(val mode: GuidanceMode, val me: GeoPosition?, val car: GeoPosition?, val recalc: Int)

    val state: StateFlow<ReturnToCarState> = combine(
        combine(parkingFlow, vehicleRepo.observeVehicles()) { p, v -> p to v },
        positions,
        compassReadings,
        routeFlow,
        combine(ui, mode) { u, m -> u to m },
    ) { (parking, vehicles), me, reading, routeUi, (local, currentMode) ->
        val carPoint = parking?.location?.let { GeoPosition(it.latitude, it.longitude) }
        var direct: Float? = null
        var bearing: Float? = null
        var remaining: Float? = null
        if (carPoint != null && me != null) {
            direct = RouteRepository.distance(me, carPoint)
            bearing = RouteRepository.bearing(me, carPoint)
            val route = routeUi.route
            if (currentMode == GuidanceMode.STREETS && route != null && route.points.size >= 2) {
                remaining = RouteMath.remainingMeters(route.points, me)
                // La flecha apunta al próximo tramo de la ruta, no al auto en línea recta.
                RouteMath.nextTarget(route.points, me)?.let { bearing = RouteRepository.bearing(me, it) }
            }
        }
        // La brújula mide contra el norte magnético; se suma la declinación del lugar.
        val heading = reading?.let { r ->
            val ref = me ?: carPoint
            val declination = ref?.let { CompassSensor.declination(it.latitude, it.longitude) } ?: 0f
            CompassSensor.normalize(r.azimuthDegrees + declination)
        }
        local.copy(
            loading = false,
            parking = parking,
            vehicle = parking?.let { p -> vehicles.firstOrNull { it.id == p.vehicleId } },
            hasLocationPermission = permission.value,
            myPosition = me,
            mode = currentMode,
            directDistanceMeters = direct,
            routeRemainingMeters = remaining,
            routeUi = routeUi,
            bearingDegrees = bearing,
            headingDegrees = heading,
            compassAvailable = compass.isAvailable,
            compassUnreliable = reading?.unreliable == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReturnToCarState())

    fun onLocationPermissionResult(granted: Boolean) {
        permission.value = granted
    }

    fun setMode(newMode: GuidanceMode) {
        mode.value = newMode
    }

    /** Fuerza un nuevo cálculo de la ruta (por ejemplo, si la calle está cortada). */
    fun recalculateRoute() {
        if (System.currentTimeMillis() - lastRequestAt < 3_000) return // evita toques repetidos
        manualRecalc.update { it + 1 }
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
        /** Si el usuario se aleja más que esto de la línea, se recalcula la ruta. */
        const val OFF_ROUTE_METERS = 40f
    }
}
