package com.example.balizapp.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val loading: Boolean = true,
    val active: List<Parking> = emptyList(),
    val history: List<Parking> = emptyList(),
    val vehicles: Map<String, Vehicle> = emptyMap(),
    val hasLocationPermission: Boolean = false,
    val myPosition: GeoPosition? = null,
)

/** Mapa general (RF5): ubicación actual y marcadores de los estacionamientos guardados. */
class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val locationRepo = LocationRepository(app)
    private val parkingRepo = ParkingRepository()
    private val vehicleRepo = VehicleRepository()

    private val location = MutableStateFlow(
        Pair<Boolean, GeoPosition?>(locationRepo.hasPermission(), null)
    )

    val state: StateFlow<MapUiState> = combine(
        parkingRepo.observeActive(),
        parkingRepo.observeHistory(),
        vehicleRepo.observeVehicles(),
        location,
    ) { active, history, vehicles, (permission, position) ->
        MapUiState(
            loading = false,
            active = active.filter { it.location != null },
            history = history.filter { it.location != null },
            vehicles = vehicles.associateBy { it.id },
            hasLocationPermission = permission,
            myPosition = position,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        refreshMyPosition()
    }

    fun onLocationPermissionResult(granted: Boolean) {
        location.update { it.copy(first = granted) }
        if (granted) refreshMyPosition()
    }

    fun refreshMyPosition() {
        if (!locationRepo.hasPermission()) return
        viewModelScope.launch {
            val position = locationRepo.currentPosition()
            location.update { it.copy(second = position ?: it.second) }
        }
    }
}
