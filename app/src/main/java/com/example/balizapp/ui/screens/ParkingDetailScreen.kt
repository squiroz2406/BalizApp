package com.example.balizapp.ui.screens

import com.example.balizapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.balizapp.data.PhotoKind
import com.example.balizapp.ui.components.PhotoViewer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.balizapp.ui.components.MapPoint
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.ui.components.EmptyState
import com.example.balizapp.ui.components.ParkingMiniMap
import com.example.balizapp.ui.components.ScreenScaffold
import com.example.balizapp.ui.components.formatDateTime
import com.example.balizapp.ui.components.formatDuration
import com.example.balizapp.ui.components.formatRemaining
import com.example.balizapp.ui.components.rememberNow
import com.example.balizapp.ui.components.toMillis

/** Detalle (RF3): toda la información de un registro, su mapa, sus fotos y sus acciones. */
@Composable
fun ParkingDetailScreen(
    vm: ParkingDetailViewModel,
    onBack: () -> Unit,
    onReturnToCar: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.closed) { if (state.closed) onBack() }

    ScreenScaffold(title = stringResource(R.string.detail_title), onBack = onBack) {
        val parking = state.parking
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            parking == null -> EmptyState(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.detail_not_found_title),
                text = stringResource(R.string.detail_not_found_text),
            )
            else -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.vehicle?.let { VehicleIcon(it.type) }
                        Text(
                            state.vehicle?.alias ?: stringResource(R.string.detail_vehicle_default),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    TimeCard(parking)
                    parking.location?.let { point ->
                        ParkingMiniMap(
                            position = MapPoint(point.latitude, point.longitude),
                            liteMode = true,
                            height = 180.dp,
                            markerTitle = state.vehicle?.alias,
                            vehicleType = state.vehicle?.type,
                        )
                    }
                    if (state.photos.isNotEmpty()) PhotosRow(state.photos)
                    HorizontalDivider()
                    parking.addressText?.let { InfoRow(stringResource(R.string.detail_label_address), it) }
                    InfoRow(stringResource(R.string.detail_label_since), formatDateTime(parking.startedAt))
                    parking.finishedAt?.let { InfoRow(stringResource(R.string.detail_label_finished), formatDateTime(it)) }
                    parking.levelSector?.let { InfoRow(stringResource(R.string.detail_label_level_sector), it) }
                    parking.note?.let { InfoRow(stringResource(R.string.detail_label_note), it) }
                    parking.ruleSummary?.let { InfoRow(stringResource(R.string.detail_label_rule), it) }
                    state.vehicle?.plate?.let { InfoRow(stringResource(R.string.detail_label_plate), it) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                if (parking.status == ParkingStatus.ACTIVE) {
                    Button(onClick = onReturnToCar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detail_return_to_car)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.detail_edit)) }
                        OutlinedButton(
                            onClick = { confirmFinish = true },
                            enabled = !state.working,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.detail_finish)) }
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.detail_delete_history), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmFinish) {
        ConfirmDialog(
            title = stringResource(R.string.detail_confirm_finish_title),
            text = stringResource(R.string.detail_confirm_finish_text),
            confirmLabel = stringResource(R.string.detail_confirm_finish_ok),
            onConfirm = { confirmFinish = false; vm.finish() },
            onDismiss = { confirmFinish = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.detail_confirm_delete_title),
            text = stringResource(R.string.detail_confirm_delete_text),
            confirmLabel = stringResource(R.string.detail_confirm_delete_ok),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Tiempo restante con cuenta regresiva, o cuánto duró si ya se retiró. */
@Composable
private fun TimeCard(parking: Parking) {
    val now by rememberNow()
    val expiresAt = parking.expiresAt?.toMillis()
    val active = parking.status == ParkingStatus.ACTIVE
    val expired = active && expiresAt != null && expiresAt <= now

    val (title, subtitle) = when {
        !active -> {
            val end = parking.finishedAt?.toMillis() ?: now
            stringResource(R.string.detail_time_lasted, formatDuration(end - parking.startedAt.toMillis())) to null
        }
        expiresAt == null -> stringResource(R.string.detail_time_no_limit) to
            stringResource(R.string.detail_time_parked_ago, formatDuration(now - parking.startedAt.toMillis()))
        else -> formatRemaining(expiresAt, now) to stringResource(R.string.detail_time_expires, formatDateTime(expiresAt))
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                expired -> MaterialTheme.colorScheme.errorContainer
                active -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Miniaturas de las fotos (RF6); al tocarlas se ven en pantalla completa. */
@Composable
private fun PhotosRow(photos: Map<PhotoKind, ImageBitmap>) {
    var viewing by remember { mutableStateOf<PhotoKind?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoKind.entries.forEach { kind ->
            photos[kind]?.let { bitmap ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = kind.label(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewing = kind },
                    )
                    Text(kind.label(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    viewing?.let { kind ->
        photos[kind]?.let { PhotoViewer(it, kind.label()) { viewing = null } }
    }
}

@Composable
private fun PhotoKind.label() = when (this) {
    PhotoKind.PLACE -> stringResource(R.string.detail_photo_place)
    PhotoKind.SIGN -> stringResource(R.string.detail_photo_sign)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) } },
    )
}
