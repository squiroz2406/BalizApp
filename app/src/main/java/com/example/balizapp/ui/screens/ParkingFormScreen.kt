package com.example.balizapp.ui.screens

import androidx.compose.ui.res.stringResource
import com.example.balizapp.R
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.balizapp.notifications.Notifications
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
import com.example.balizapp.ui.components.PhotoSlot
import com.example.balizapp.data.ParkingVerdict
import com.example.balizapp.data.PhotoKind
import com.example.balizapp.data.SignAnalysis
import com.example.balizapp.ui.components.ScreenScaffold
import com.example.balizapp.ui.components.rememberLocationPermissionRequest
import com.example.balizapp.ui.components.MapPoint
import com.example.balizapp.ui.components.formatDateTime
import com.example.balizapp.ui.components.formatDuration
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Nuevo / editar estacionamiento (RF4): ubicación (RF5) y fotos del lugar y del cartel (RF6).
 * [onSaved] recibe el id guardado y si era una edición.
 */
@Composable
fun ParkingFormScreen(
    vm: ParkingFormViewModel,
    onBack: () -> Unit,
    onGoToProfile: () -> Unit,
    onSaved: (id: String, wasEdit: Boolean) -> Unit,
) {
    val state by vm.state.collectAsState()
    var showTimeDialog by remember { mutableStateOf(false) }
    val requestLocationPermission = rememberLocationPermissionRequest(vm::onLocationPermissionResult)

    // Al elegir un límite de tiempo se pide (una vez) el permiso de notificaciones de Android 13+,
    // necesario para el aviso de vencimiento (RF7).
    val context = LocalContext.current
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.expiresAt != null) {
        if (state.expiresAt != null && !askedNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)
        ) {
            askedNotifications = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Al abrir un alta sin permiso, se pide una vez (la explicación queda visible en el formulario).
    LaunchedEffect(Unit) {
        if (!state.isEdit && !state.hasLocationPermission) requestLocationPermission()
    }

    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.isEdit) }
    }

    ScreenScaffold(
        title = stringResource(if (state.isEdit) R.string.form_title_edit else R.string.form_title_new),
        onBack = onBack,
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            state.vehicles.isEmpty() -> NoVehiclesCard(onGoToProfile)
            else -> FormContent(
                state = state,
                vm = vm,
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
            Text(stringResource(R.string.form_no_vehicles_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.form_no_vehicles_body))
            Button(onClick = onGoToProfile) { Text(stringResource(R.string.form_go_to_profile)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FormContent(
    state: ParkingFormState,
    vm: ParkingFormViewModel,
    onShowTimeDialog: () -> Unit,
    onRequestLocationPermission: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LocationSection(state, vm, onRequestLocationPermission)

        FieldLabel(stringResource(R.string.form_section_photos))
        PhotoSlot(
            label = stringResource(R.string.form_place_photo_label),
            helperText = stringResource(R.string.form_place_photo_helper),
            bitmap = state.placePhoto?.bitmap,
            processing = state.processingPhoto == PhotoKind.PLACE,
            onPicked = { vm.onPhotoPicked(PhotoKind.PLACE, it) },
            onRemove = { vm.removePhoto(PhotoKind.PLACE) },
        )
        if (state.placePhoto != null) {
            AiButton(
                label = stringResource(R.string.form_describe_place_ai),
                working = state.describingPlace,
                onClick = vm::describePlace,
            )
        }
        PhotoSlot(
            label = stringResource(R.string.form_sign_photo_label),
            helperText = stringResource(R.string.form_sign_photo_helper),
            bitmap = state.signPhoto?.bitmap,
            processing = state.processingPhoto == PhotoKind.SIGN,
            onPicked = { vm.onPhotoPicked(PhotoKind.SIGN, it) },
            onRemove = { vm.removePhoto(PhotoKind.SIGN) },
        )
        if (state.signPhoto != null) {
            AiButton(
                label = stringResource(R.string.form_read_sign_ai),
                working = state.analyzingSign,
                onClick = vm::analyzeSign,
            )
        }
        state.signAnalysis?.let { SignVerdictCard(it) }

        FieldLabel(stringResource(R.string.form_section_vehicle))
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

        FieldLabel(stringResource(R.string.form_section_time_limit))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedPreset == ParkingFormState.NO_LIMIT,
                onClick = vm::setNoLimit,
                label = { Text(stringResource(R.string.form_no_limit)) },
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
                label = { Text(stringResource(R.string.form_pick_time)) },
            )
        }
        Text(
            state.expiresAt?.let { stringResource(R.string.form_expires_info, formatDateTime(it)) }
                ?: stringResource(R.string.form_no_expiry_info),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.ruleSummary,
            onValueChange = vm::setRuleSummary,
            label = { Text(stringResource(R.string.form_rule_label)) },
            placeholder = { Text(stringResource(R.string.form_rule_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.levelSector,
            onValueChange = vm::setLevelSector,
            label = { Text(stringResource(R.string.form_level_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = vm::setNote,
            label = { Text(stringResource(R.string.form_note_label)) },
            placeholder = { Text(stringResource(R.string.form_note_placeholder)) },
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
        else Text(stringResource(if (state.isEdit) R.string.form_save_changes else R.string.form_save_parking))
    }
}

/** Ubicación (RF5): mapa con el marcador, estado del GPS y alternativa de tocar el mapa. */
@Composable
private fun LocationSection(
    state: ParkingFormState,
    vm: ParkingFormViewModel,
    onRequestLocationPermission: () -> Unit,
) {
    FieldLabel(stringResource(R.string.form_section_location))
    val point = state.position?.let { MapPoint(it.latitude, it.longitude) }
    ParkingMiniMap(
        position = point,
        vehicleType = state.vehicles.firstOrNull { it.id == state.vehicleId }?.type,
        onMapClick = { vm.setManualPosition(it.latitude, it.longitude) },
    )
    val status = when {
        state.locating -> stringResource(R.string.form_locating)
        state.position == null && !state.hasLocationPermission ->
            stringResource(R.string.form_no_permission_hint)
        state.position == null -> stringResource(R.string.form_tap_map_hint)
        else -> {
            val origin = when (state.positionSource) {
                PositionSource.GPS -> state.position.accuracyMeters
                    ?.let { stringResource(R.string.form_source_gps_accuracy, it.roundToInt()) }
                        ?: stringResource(R.string.form_source_gps)
                PositionSource.MANUAL -> stringResource(R.string.form_source_manual)
                else -> stringResource(R.string.form_source_saved)
            }
            listOfNotNull(state.addressText ?: stringResource(R.string.form_address_unavailable).takeIf { !state.locating && state.position != null }, origin).joinToString(" · ")
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
            TextButton(onClick = vm::locate, enabled = !state.locating) { Text(stringResource(R.string.form_use_gps)) }
        } else {
            TextButton(onClick = onRequestLocationPermission) { Text(stringResource(R.string.form_allow)) }
        }
    }
}

@Composable
private fun AiButton(label: String, working: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !working, modifier = Modifier.fillMaxWidth()) {
        if (working) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.form_analyzing))
        } else {
            Text(label)
        }
    }
}

/** Lo que la IA entendió del cartel y si se puede estacionar ahora; el usuario lo revisa antes de guardar. */
@Composable
private fun SignVerdictCard(analysis: SignAnalysis) {
    val (title, color) = when (analysis.verdict) {
        ParkingVerdict.ALLOWED -> stringResource(R.string.form_verdict_allowed) to MaterialTheme.colorScheme.primary
        ParkingVerdict.FORBIDDEN -> stringResource(R.string.form_verdict_forbidden) to MaterialTheme.colorScheme.error
        ParkingVerdict.UNKNOWN -> stringResource(R.string.form_verdict_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            analysis.explanation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                stringResource(
                    if (analysis.confidence == "high") R.string.form_ai_review_note else R.string.form_ai_review_note_low,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        title = { Text(stringResource(R.string.form_time_dialog_title)) },
        text = { TimeInput(state = timeState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text(stringResource(R.string.form_accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    )
}
