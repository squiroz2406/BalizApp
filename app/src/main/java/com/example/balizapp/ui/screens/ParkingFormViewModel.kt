package com.example.balizapp.ui.screens

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.balizapp.data.ActiveParkingExistsException
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingSource
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.navigation.ParkingFormRoute
import com.example.balizapp.ui.components.toMillis
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

data class ParkingFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicleId: String? = null,
    val note: String = "",
    val levelSector: String = "",
    val ruleSummary: String = "",
    /** Vencimiento en milisegundos; null = sin límite. */
    val expiresAt: Long? = null,
    /** Chip de límite marcado: minutos del preset, NO_LIMIT o null si se eligió una hora. */
    val selectedPreset: Int? = NO_LIMIT,
    val saving: Boolean = false,
    val error: String? = null,
    /** Se completa al guardar: la pantalla navega cuando deja de ser null. */
    val savedId: String? = null,
) {
    companion object {
        const val NO_LIMIT = 0
        val PRESETS_MINUTES = listOf(30, 60, 120, 180, 240)
    }
}

/** Alta y edición de un estacionamiento (RF4). El id llega en la ruta tipada. */
class ParkingFormViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val parkingRepo = ParkingRepository()
    private val vehicleRepo = VehicleRepository()

    private val parkingId: String? = savedStateHandle.toRoute<ParkingFormRoute>().parkingId
    /** Registro original al editar; se copia para no perder ubicación, fotos, origen, etc. */
    private var original: Parking? = null

    private val _state = MutableStateFlow(ParkingFormState(isEdit = parkingId != null))
    val state: StateFlow<ParkingFormState> = _state.asStateFlow()

    init {
        parkingId?.let(::loadExisting)
        // Vehículos en tiempo real; en un alta se preselecciona el primero que no esté estacionado.
        viewModelScope.launch {
            combine(vehicleRepo.observeVehicles(), parkingRepo.observeActive()) { vehicles, active ->
                vehicles to active.map { it.vehicleId }.toSet()
            }.collect { (vehicles, busy) ->
                _state.update { s ->
                    val keepSelection = s.vehicleId != null && vehicles.any { it.id == s.vehicleId }
                    s.copy(
                        loading = s.isEdit && original == null,
                        vehicles = vehicles,
                        vehicleId = when {
                            keepSelection || s.isEdit -> s.vehicleId
                            else -> (vehicles.firstOrNull { it.id !in busy } ?: vehicles.firstOrNull())?.id
                        },
                    )
                }
            }
        }
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            try {
                val parking = parkingRepo.getParking(id) ?: error("El estacionamiento ya no existe")
                original = parking
                _state.update {
                    it.copy(
                        loading = false,
                        vehicleId = parking.vehicleId,
                        note = parking.note.orEmpty(),
                        levelSector = parking.levelSector.orEmpty(),
                        ruleSummary = parking.ruleSummary.orEmpty(),
                        expiresAt = parking.expiresAt?.toMillis(),
                        selectedPreset = if (parking.expiresAt == null) ParkingFormState.NO_LIMIT else null,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar $id", e)
                _state.update { it.copy(loading = false, error = e.message ?: "No se pudo cargar") }
            }
        }
    }

    fun selectVehicle(id: String) = _state.update { it.copy(vehicleId = id, error = null) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setLevelSector(value: String) = _state.update { it.copy(levelSector = value) }
    fun setRuleSummary(value: String) = _state.update { it.copy(ruleSummary = value) }

    fun setNoLimit() = _state.update {
        it.copy(expiresAt = null, selectedPreset = ParkingFormState.NO_LIMIT, error = null)
    }

    /** Vence dentro de [minutes] minutos contados desde ahora. */
    fun setPreset(minutes: Int) = _state.update {
        it.copy(
            expiresAt = System.currentTimeMillis() + minutes * 60_000L,
            selectedPreset = minutes,
            error = null,
        )
    }

    /** Vence a la hora elegida: hoy si todavía no pasó, si no mañana. */
    fun setExpiryTime(hour: Int, minute: Int) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var target = now.with(LocalTime.of(hour, minute)).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        _state.update {
            it.copy(expiresAt = target.toInstant().toEpochMilli(), selectedPreset = null, error = null)
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val vehicleId = s.vehicleId ?: return fail("Elegí un vehículo")
        if (s.expiresAt != null && s.expiresAt <= System.currentTimeMillis()) {
            return fail("El vencimiento tiene que ser posterior a ahora")
        }
        val expires = s.expiresAt?.let { Timestamp(Date(it)) }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val base = original
                val id = if (base == null) {
                    parkingRepo.create(
                        Parking(
                            vehicleId = vehicleId,
                            note = s.note.trim().ifEmpty { null },
                            levelSector = s.levelSector.trim().ifEmpty { null },
                            ruleSummary = s.ruleSummary.trim().ifEmpty { null },
                            startedAt = Timestamp.now(),
                            expiresAt = expires,
                            source = ParkingSource.MANUAL,
                        )
                    )
                } else {
                    if (vehicleId != base.vehicleId && parkingRepo.hasActiveParking(vehicleId)) {
                        throw ActiveParkingExistsException()
                    }
                    parkingRepo.update(
                        base.copy(
                            vehicleId = vehicleId,
                            note = s.note.trim().ifEmpty { null },
                            levelSector = s.levelSector.trim().ifEmpty { null },
                            ruleSummary = s.ruleSummary.trim().ifEmpty { null },
                            expiresAt = expires,
                        )
                    )
                    base.id
                }
                _state.update { it.copy(saving = false, savedId = id) }
            } catch (e: ActiveParkingExistsException) {
                _state.update { it.copy(saving = false, error = e.message) }
            } catch (e: Exception) {
                Log.w(TAG, "Error guardando", e)
                _state.update { it.copy(saving = false, error = "No se pudo guardar: ${e.message}") }
            }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(error = message) }

    private companion object {
        const val TAG = "ParkingFormViewModel"
    }
}
