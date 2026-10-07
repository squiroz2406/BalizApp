package com.example.balizapp.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.UserRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.UserProfile
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.data.model.VehicleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val error: String? = null,
)

class ProfileViewModel(
    private val users: UserRepository = UserRepository(),
    private val vehicleRepo: VehicleRepository = VehicleRepository(),
    private val parkingRepo: ParkingRepository = ParkingRepository(),
) : ViewModel() {

    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ProfileUiState> =
        combine(users.observeProfile(), vehicleRepo.observeVehicles(), error) { profile, vehicles, err ->
            ProfileUiState(loading = false, profile = profile, vehicles = vehicles, error = err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun clearError() = error.update { null }

    fun setReminderMinutes(minutes: Int) = launchWrite { users.setReminderMinutes(minutes) }

    /** Valida y guarda. Devuelve un mensaje de error para mostrar en el diálogo, o null si está bien. */
    fun saveVehicle(id: String, type: VehicleType, alias: String, plate: String): String? {
        if (alias.isBlank()) return "Poné un nombre para el vehículo"
        val vehicle = Vehicle(
            id = id,
            type = type,
            alias = alias.trim(),
            plate = plate.trim().uppercase().ifEmpty { null },
        )
        launchWrite { vehicleRepo.save(vehicle) }
        return null
    }

    fun deleteVehicle(id: String) = launchWrite {
        if (parkingRepo.hasActiveParking(id)) {
            error.update { "Ese vehículo tiene un estacionamiento activo. Retiralo antes de borrarlo." }
        } else {
            vehicleRepo.delete(id)
        }
    }

    /*
     * Firestore aplica la escritura en el cache local al instante y la pantalla se actualiza
     * por el listener. Por eso no se bloquea la UI esperando al servidor (sin conexión,
     * el await recién termina cuando vuelve la red); solo se informa si falla.
     */
    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.w("ProfileViewModel", "Error guardando", e)
                error.update { "No se pudo guardar: ${e.message}" }
            }
        }
    }
}
