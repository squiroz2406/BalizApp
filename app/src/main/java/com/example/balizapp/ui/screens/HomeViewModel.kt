package com.example.balizapp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val active: List<Parking> = emptyList(),
    val history: List<Parking> = emptyList(),
    /** Vehículos por id, para mostrar el nombre en cada tarjeta. */
    val vehicles: Map<String, Vehicle> = emptyMap(),
)

/** Inicio (RF2): solo los estacionamientos del usuario con sesión (users/{uid}/parkings). */
class HomeViewModel(
    parkings: ParkingRepository = ParkingRepository(),
    vehicles: VehicleRepository = VehicleRepository(),
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        parkings.observeActive(),
        parkings.observeHistory(),
        vehicles.observeVehicles(),
    ) { active, history, vehicleList ->
        HomeUiState(
            loading = false,
            active = active,
            history = history,
            vehicles = vehicleList.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
