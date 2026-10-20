package com.example.balizapp.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import com.example.balizapp.ui.components.precacheTilesAround
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.balizapp.data.ActiveParkingExistsException
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.ImageCompressor
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.PhotoKind
import com.example.balizapp.data.ParkingVerdict
import com.example.balizapp.data.PhotoRepository
import com.example.balizapp.data.SignAnalysis
import com.example.balizapp.data.SignAnalyzer
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingSource
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.navigation.ParkingFormRoute
import com.example.balizapp.ui.components.toImageBitmap
import com.example.balizapp.ui.components.toMillis
import androidx.compose.ui.graphics.ImageBitmap
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

/**
 * Una foto del formulario: los bytes JPEG ya comprimidos y su imagen para mostrar.
 * Clase común (no data class) para no comparar arreglos de bytes en cada actualización.
 */
class FormPhoto(val jpeg: ByteArray, val bitmap: ImageBitmap)

/** De dónde salió la ubicación que muestra el formulario. */
enum class PositionSource { NONE, GPS, MANUAL, SAVED }

data class ParkingFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicleId: String? = null,
    val note: String = "",
    val levelSector: String = "",
    val ruleSummary: String = "",
    /** Ubicación del vehículo (RF5). */
    val position: GeoPosition? = null,
    val positionSource: PositionSource = PositionSource.NONE,
    val addressText: String? = null,
    val locating: Boolean = false,
    val hasLocationPermission: Boolean = false,
    /** Fotos (RF6). changed = hay que guardar (o borrar) la foto al confirmar. */
    val placePhoto: FormPhoto? = null,
    val signPhoto: FormPhoto? = null,
    val placePhotoChanged: Boolean = false,
    val signPhotoChanged: Boolean = false,
    val processingPhoto: PhotoKind? = null,
    /** Vencimiento en milisegundos; null = sin límite. */
    val expiresAt: Long? = null,
    /** Chip de límite marcado: minutos del preset, NO_LIMIT o null si se eligió una hora. */
    val selectedPreset: Int? = NO_LIMIT,
    /** IA: se está leyendo el cartel / describiendo el lugar. */
    val analyzingSign: Boolean = false,
    val describingPlace: Boolean = false,
    /** Lo que la IA entendió del cartel; se muestra para que el usuario lo revise. */
    val signAnalysis: SignAnalysis? = null,
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
class ParkingFormViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val parkingRepo = ParkingRepository()
    private val vehicleRepo = VehicleRepository()
    private val locationRepo = LocationRepository(app)
    private val photoRepo = PhotoRepository()
    private val analyzer = SignAnalyzer()

    private val parkingId: String? = savedStateHandle.toRoute<ParkingFormRoute>().parkingId
    /** Registro original al editar; se copia para no perder ubicación, fotos, origen, etc. */
    private var original: Parking? = null

    private val _state = MutableStateFlow(
        ParkingFormState(isEdit = parkingId != null, hasLocationPermission = locationRepo.hasPermission())
    )
    val state: StateFlow<ParkingFormState> = _state.asStateFlow()

    init {
        if (parkingId != null) loadExisting(parkingId)
        else if (locationRepo.hasPermission()) locate() // la ubicación se toma al abrir el formulario
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
                        position = parking.location?.let { GeoPosition(it.latitude, it.longitude) },
                        positionSource = if (parking.location != null) PositionSource.SAVED else PositionSource.NONE,
                        addressText = parking.addressText,
                        expiresAt = parking.expiresAt?.toMillis(),
                        selectedPreset = if (parking.expiresAt == null) ParkingFormState.NO_LIMIT else null,
                    )
                }
                if (parking.placePhotoPath != null) loadPhoto(id, PhotoKind.PLACE)
                if (parking.signPhotoPath != null) loadPhoto(id, PhotoKind.SIGN)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar $id", e)
                _state.update { it.copy(loading = false, error = e.message ?: "No se pudo cargar") }
            }
        }
    }

    /** Resultado del pedido de permiso: si se concedió, se toma la ubicación. */
    fun onLocationPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasLocationPermission = granted) }
        if (granted && !_state.value.isEdit) locate()
    }

    /** Lectura única del GPS. */
    fun locate() {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true, error = null) }
        viewModelScope.launch {
            val position = locationRepo.currentPosition()
            if (position == null) {
                _state.update {
                    it.copy(locating = false, error = "No se pudo obtener la ubicación. Tocá el mapa para marcarla.")
                }
                return@launch
            }
            _state.update {
                it.copy(locating = false, position = position, positionSource = PositionSource.GPS, addressText = null)
            }
            resolveAddress(position)
        }
    }

    /** Plan B sin GPS o para corregir: el usuario toca el mapa donde dejó el vehículo. */
    fun setManualPosition(latitude: Double, longitude: Double) {
        val position = GeoPosition(latitude, longitude)
        _state.update {
            it.copy(position = position, positionSource = PositionSource.MANUAL, addressText = null, error = null)
        }
        resolveAddress(position)
    }

    private fun resolveAddress(position: GeoPosition) {
        viewModelScope.launch {
            val address = locationRepo.addressFor(position.latitude, position.longitude)
            // Solo se aplica si la ubicación no cambió mientras se resolvía.
            _state.update { if (it.position == position) it.copy(addressText = address) else it }
        }
    }

    private fun loadPhoto(parkingId: String, kind: PhotoKind) {
        viewModelScope.launch {
            _state.update { it.copy(processingPhoto = kind) }
            val photo = runCatching { photoRepo.load(parkingId, kind) }
                .onFailure { Log.w(TAG, "No se pudo cargar la foto $kind", it) }
                .getOrNull()
                ?.let { bytes -> bytes.toImageBitmap()?.let { FormPhoto(bytes, it) } }
            _state.update { it.withPhoto(kind, photo, changed = false).copy(processingPhoto = null) }
        }
    }

    /** Foto sacada con la cámara o elegida de la galería: se comprime antes de mostrarla. */
    fun onPhotoPicked(kind: PhotoKind, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(processingPhoto = kind, error = null) }
            try {
                val jpeg = ImageCompressor.compress(getApplication<Application>(), uri)
                val bitmap = jpeg.toImageBitmap() ?: error("No se pudo mostrar la foto")
                _state.update { it.withPhoto(kind, FormPhoto(jpeg, bitmap), changed = true).copy(processingPhoto = null) }
            } catch (e: Exception) {
                Log.w(TAG, "Error procesando la foto", e)
                _state.update { it.copy(processingPhoto = null, error = "No se pudo usar la foto: ${e.message}") }
            }
        }
    }

    fun removePhoto(kind: PhotoKind) = _state.update { it.withPhoto(kind, null, changed = true) }

    private fun ParkingFormState.withPhoto(kind: PhotoKind, photo: FormPhoto?, changed: Boolean) = when (kind) {
        PhotoKind.PLACE -> copy(placePhoto = photo, placePhotoChanged = changed || placePhotoChanged)
        PhotoKind.SIGN -> copy(
            signPhoto = photo,
            signPhotoChanged = changed || signPhotoChanged,
            signAnalysis = if (changed) null else signAnalysis,
        )
    }

    /** Guarda o borra cada foto que cambió y devuelve las rutas finales. */
    private suspend fun savePhotos(parkingId: String, s: ParkingFormState, base: Parking?) {
        suspend fun resolve(kind: PhotoKind, photo: FormPhoto?, changed: Boolean, existing: String?): String? =
            when {
                !changed -> existing
                photo != null -> photoRepo.save(parkingId, kind, photo.jpeg)
                else -> {
                    if (existing != null) photoRepo.delete(parkingId, kind)
                    null
                }
            }
        if (!s.placePhotoChanged && !s.signPhotoChanged) return
        val place = resolve(PhotoKind.PLACE, s.placePhoto, s.placePhotoChanged, base?.placePhotoPath)
        val sign = resolve(PhotoKind.SIGN, s.signPhoto, s.signPhotoChanged, base?.signPhotoPath)
        parkingRepo.setPhotoPaths(parkingId, place, sign)
    }

    /** Lee el cartel con IA: completa la regla y el vencimiento (el usuario puede corregirlos antes de guardar). */
    fun analyzeSign() {
        val photo = _state.value.signPhoto ?: return fail("Primero sacá o elegí la foto del cartel")
        if (_state.value.analyzingSign) return
        _state.update { it.copy(analyzingSign = true, error = null) }
        viewModelScope.launch {
            try {
                val analysis = analyzer.analyzeSign(photo.jpeg)
                if (analysis == null) {
                    _state.update { it.copy(analyzingSign = false, error = "No pude leer un cartel en esa foto. Probá con otra más cerca y derecha.") }
                    return@launch
                }
                _state.update { s ->
                    val expiry = if (analysis.verdict == ParkingVerdict.FORBIDDEN) null else suggestedExpiry(analysis)
                    s.copy(
                        analyzingSign = false,
                        signAnalysis = analysis,
                        ruleSummary = analysis.ruleSummary ?: s.ruleSummary,
                        expiresAt = expiry ?: s.expiresAt,
                        selectedPreset = if (expiry != null) null else s.selectedPreset,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error leyendo el cartel", e)
                _state.update { it.copy(analyzingSign = false, error = "No se pudo leer el cartel con IA: ${e.message?.take(300)}") }
            }
        }
    }

    /** Escribe en la nota una descripción del lugar sacada de la foto. */
    fun describePlace() {
        val s = _state.value
        val photo = s.placePhoto ?: return fail("Primero sacá o elegí la foto del lugar")
        if (s.describingPlace) return
        _state.update { it.copy(describingPlace = true, error = null) }
        viewModelScope.launch {
            try {
                val text = analyzer.describePlace(photo.jpeg, s.addressText)
                _state.update {
                    if (text == null) it.copy(describingPlace = false, error = "La IA no pudo describir el lugar.")
                    else it.copy(describingPlace = false, note = text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error describiendo el lugar", e)
                _state.update { it.copy(describingPlace = false, error = "No se pudo describir el lugar con IA: ${e.message?.take(300)}") }
            }
        }
    }

    /** El vencimiento más cercano entre "máximo de minutos" y "hasta la hora del cartel"; null si no hay ninguno. */
    private fun suggestedExpiry(analysis: SignAnalysis): Long? {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val byDuration = analysis.maxMinutes?.let { now.plusMinutes(it.toLong()).toInstant().toEpochMilli() }
        val byClock = analysis.allowedUntil?.let { text ->
            runCatching {
                val (h, m) = text.split(":").map(String::toInt)
                now.with(LocalTime.of(h, m)).withSecond(0).withNano(0)
            }.getOrNull()?.takeIf { it.isAfter(now) }?.toInstant()?.toEpochMilli()
        }
        return listOfNotNull(byDuration, byClock).minOrNull()
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
        if (s.processingPhoto != null) return fail("Esperá a que termine de procesarse la foto")
        val vehicleId = s.vehicleId ?: return fail("Elegí un vehículo")
        val position = s.position ?: return fail("Marcá en el mapa dónde dejaste el vehículo")
        val geoPoint = GeoPoint(position.latitude, position.longitude)
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
                            location = geoPoint,
                            addressText = s.addressText,
                            note = s.note.trim().ifEmpty { null },
                            levelSector = s.levelSector.trim().ifEmpty { null },
                            ruleSummary = s.ruleSummary.trim().ifEmpty { null },
                            startedAt = Timestamp.now(),
                            expiresAt = expires,
                            source = if (s.signAnalysis != null) ParkingSource.AGENT_SIGN else ParkingSource.MANUAL,
                            agentConfidence = s.signAnalysis?.confidence,
                        )
                    )
                } else {
                    if (vehicleId != base.vehicleId && parkingRepo.hasActiveParking(vehicleId)) {
                        throw ActiveParkingExistsException()
                    }
                    parkingRepo.update(
                        base.copy(
                            vehicleId = vehicleId,
                            location = geoPoint,
                            addressText = s.addressText,
                            note = s.note.trim().ifEmpty { null },
                            levelSector = s.levelSector.trim().ifEmpty { null },
                            ruleSummary = s.ruleSummary.trim().ifEmpty { null },
                            expiresAt = expires,
                            source = if (s.signAnalysis != null) ParkingSource.AGENT_SIGN else base.source,
                            agentConfidence = s.signAnalysis?.confidence ?: base.agentConfidence,
                        )
                    )
                    base.id
                }
                savePhotos(id, s, base)
                precacheTilesAround(getApplication(), position.latitude, position.longitude)
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
