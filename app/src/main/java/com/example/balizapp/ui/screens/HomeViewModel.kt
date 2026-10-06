package com.example.balizapp.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.UserRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.UserProfile
import com.example.balizapp.notifications.ReminderScheduler
import com.example.balizapp.ui.components.toMillis
import kotlinx.coroutines.launch
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

/**
 * Inicio (RF2): solo los estacionamientos del usuario con sesión (users/{uid}/parkings).
 * Además mantiene sincronizados los avisos de vencimiento (RF7) con los estacionamientos activos:
 * Inicio es la primera pantalla y queda abierta debajo de las demás mientras haya sesión.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val parkings = ParkingRepository()
    private val vehicles = VehicleRepository()
    private val users = UserRepository()
    private val reminders = ReminderScheduler(app)

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

    init {
        // Cualquier cambio (alta, edición, "Ya lo retiré", minutos de aviso, otro dispositivo)
        // vuelve a calcular las alarmas.
        viewModelScope.launch {
            combine(parkings.observeActive(), vehicles.observeVehicles(), users.observeProfile()) { active, vehicleList, profile ->
                val minutes = profile?.reminderMinutes ?: UserProfile.DEFAULT_REMINDER_MINUTES
                val names = vehicleList.associate { it.id to it.alias }
                active.mapNotNull { p ->
                    p.expiresAt?.let { expires ->
                        ReminderScheduler.Spec(
                            parkingId = p.id,
                            expiresAt = expires.toMillis(),
                            reminderMinutes = minutes,
                            vehicleName = names[p.vehicleId] ?: "Tu vehículo",
                            place = p.summaryLine(),
                        )
                    }
                }
            }.collect { specs -> reminders.sync(specs) }
        }
    }
}
