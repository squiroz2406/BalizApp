package com.example.balizapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.balizapp.ui.components.ParkingMiniMap
import com.example.balizapp.ui.components.ScreenScaffold
import com.example.balizapp.ui.components.rememberLocationPermissionRequest
import com.example.balizapp.ui.components.MapPoint
import com.example.balizapp.ui.components.formatDateTime
import com.example.balizapp.ui.components.formatDuration
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Nuevo / editar estacionamiento (RF4). Ubicación (etapa 4) y fotos (etapa 5) se suman después.
 * [onSaved] recibe el id guardado y si era una edición.
 */
@Composable
fun ParkingFormScreen(
    vm: ParkingFormViewModel,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit,
    onGoToProfile: () -> Unit,
    onSaved: (id: String, wasEdit: Boolean) -> Unit,
) {
    val state by vm.state.collectAsState()
    var showTimeDialog by remember { mutableStateOf(false) }
    val requestLocationPermission = rememberLocationPermissionRequest(vm::onLocationPermissionResult)

    // Al abrir un alta sin permiso, se pide una vez (la explicación queda visible en el formulario).
    LaunchedEffect(Unit) {
        if (!state.isEdit && !state.hasLocationPermission) requestLocationPermission()
    }

    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.isEdit) }
    }

    ScreenScaffold(
        title = if (state.isEdit) "Editar estacionamiento" else "Nuevo estacionamiento",
        onBack = onBack,
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            state.vehicles.isEmpty() -> NoVehiclesCard(onGoToProfile)
            else -> FormContent(
                state = state,
                vm = vm,
                onOpenAssistant = onOpenAssistant,
                onShowTimeDialog = { showTimeDialog = true },
                onRequestLocationPermission = requestLocationPermission,
            )
        }
    }

    if (showTimeDialog) {
        ExpiryTimeDialog(
            onDismiss = { showTimeDialog = false },
            onConfirm = { hour, minute ->
                vm.setExpiryTime(hour, minute)
                showTimeDialog = false
            },
        )
    }
}

@Composable
private fun NoVehiclesCard(onGoToProfile: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Primero cargá un vehículo", style = MaterialTheme.typography.titleMedium)
            Text("Lo hacés una sola vez, desde Perfil → Mis vehículos.")
            Button(onClick = onGoToProfile) { Text("Ir a Perfil") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FormContent(
    state: ParkingFormState,
    vm: ParkingFormViewModel,
    onOpenAssistant: () -> Unit,
    onShowTimeDialog: () -> Unit,
    onRequestLocationPermission: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.isEdit) {
            OutlinedButton(onClick = onOpenAssistant, modifier = Modifier.fillMaxWidth()) {
                Text("Leer el cartel con el asistente")
            }
        }

        LocationSection(state, vm, onRequestLocationPermission)

        FieldLabel("Vehículo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.vehicles.forEach { vehicle ->
                FilterChip(
                    selected = vehicle.id == state.vehicleId,
                    onClick = { vm.selectVehicle(vehicle.id) },
                    label = { Text(vehicle.alias) },
                    leadingIcon = { VehicleIcon(vehicle.type) },
                )
            }
        }

        FieldLabel("Límite de tiempo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedPreset == ParkingFormState.NO_LIMIT,
                onClick = vm::setNoLimit,
                label = { Text("Sin límite") },
            )
            ParkingFormState.PRESETS_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = state.selectedPreset == minutes,
                    onClick = { vm.setPreset(minutes) },
                    label = { Text(formatDuration(minutes * 60_000L)) },
                )
            }
            FilterChip(
                selected = state.expiresAt != null && state.selectedPreset == null,
                onClick = onShowTimeDialog,
                label = { Text("Elegir hora") },
            )
        }
        Text(
            state.expiresAt?.let { "Vence: ${formatDateTime(it)}" } ?: "Sin vencimiento: no se programa aviso",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.ruleSummary,
            onValueChange = vm::setRuleSummary,
            label = { Text("Regla del cartel (opcional)") },
            placeholder = { Text("Medido, máx. 2 h, lun a vie de 8 a 18") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.levelSector,
            onValueChange = vm::setLevelSector,
            label = { Text("Piso, sector o cochera (opcional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = vm::setNote,
            label = { Text("Nota (opcional)") },
            placeholder = { Text("Frente a la farmacia") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    Button(
        onClick = vm::save,
        enabled = !state.saving && state.vehicleId != null,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(if (state.isEdit) "Guardar cambios" else "Guardar estacionamiento")
    }
}

/** Ubicación (RF5): mapa con el marcador, estado del GPS y alternativa de tocar el mapa. */
@Composable
private fun LocationSection(
    state: ParkingFormState,
    vm: ParkingFormViewModel,
    onRequestLocationPermission: () -> Unit,
) {
    FieldLabel("Ubicación")
    val point = state.position?.let { MapPoint(it.latitude, it.longitude) }
    ParkingMiniMap(
        position = point,
        onMapClick = { vm.setManualPosition(it.latitude, it.longitude) },
    )
    val status = when {
        state.locating -> "Buscando tu ubicación…"
        state.position == null && !state.hasLocationPermission ->
            "Sin permiso de ubicación. Permitilo o tocá el mapa donde dejaste el vehículo."
        state.position == null -> "Tocá el mapa donde dejaste el vehículo."
        else -> {
            val origin = when (state.positionSource) {
                PositionSource.GPS -> state.position.accuracyMeters
                    ?.let { "Tomada por GPS (±${it.roundToInt()} m)" } ?: "Tomada por GPS"
                PositionSource.MANUAL -> "Marcada a mano en el mapa"
                else -> "Ubicación guardada"
            }
            listOfNotNull(state.addressText, origin).joinToString(" · ")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.locating) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state.hasLocationPermission) {
            TextButton(onClick = vm::locate, enabled = !state.locating) { Text("Usar GPS") }
        } else {
            TextButton(onClick = onRequestLocationPermission) { Text("Permitir") }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryTimeDialog(onDismiss: () -> Unit, onConfirm: (hour: Int, minute: Int) -> Unit) {
    val initial = LocalTime.now().plusHours(2)
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Hasta qué hora podés quedarte?") },
        text = { TimeInput(state = timeState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
