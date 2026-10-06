package com.example.balizapp.ui.screens

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.navigation.ParkingDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParkingDetailState(
    val loading: Boolean = true,
    val parking: Parking? = null,
    val vehicle: Vehicle? = null,
    val working: Boolean = false,
    val error: String? = null,
    /** true cuando se borró el registro: la pantalla vuelve atrás. */
    val closed: Boolean = false,
)

/** Detalle de un estacionamiento (RF3), en tiempo real. */
class ParkingDetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val parkingRepo = ParkingRepository()
    private val vehicleRepo = VehicleRepository()
    private val parkingId = savedStateHandle.toRoute<ParkingDetailRoute>().parkingId

    private val ui = MutableStateFlow(ParkingDetailState())

    val state: StateFlow<ParkingDetailState> = combine(
        parkingRepo.observeParking(parkingId),
        vehicleRepo.observeVehicles(),
        ui,
    ) { parking, vehicles, local ->
        local.copy(
            loading = false,
            parking = parking,
            vehicle = parking?.let { p -> vehicles.firstOrNull { it.id == p.vehicleId } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParkingDetailState())

    /** "Ya lo retiré": pasa al historial. El listener actualiza la pantalla. */
    fun finish() = perform("No se pudo marcar como retirado") { parkingRepo.finish(parkingId) }

    fun delete() = perform("No se pudo borrar") {
        parkingRepo.delete(parkingId)
        ui.update { it.copy(closed = true) }
    }

    private fun perform(errorMessage: String, block: suspend () -> Unit) {
        if (ui.value.working) return
        ui.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.w("ParkingDetailViewModel", errorMessage, e)
                ui.update { it.copy(error = "$errorMessage: ${e.message}") }
            } finally {
                ui.update { it.copy(working = false) }
            }
        }
    }
}
