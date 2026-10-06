package com.example.balizapp.ui.screens

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

/** Detalle (RF3): toda la información de un registro, su mapa y sus acciones. Fotos: etapa 5. */
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

    ScreenScaffold(title = "Detalle", onBack = onBack) {
        val parking = state.parking
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            parking == null -> EmptyState(
                icon = Icons.Filled.Info,
                title = "No se encontró el estacionamiento",
                text = "Puede que se haya borrado.",
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
                            state.vehicle?.alias ?: "Vehículo",
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
                        )
                    }
                    HorizontalDivider()
                    parking.addressText?.let { InfoRow("Dirección", it) }
                    InfoRow("Desde", formatDateTime(parking.startedAt))
                    parking.finishedAt?.let { InfoRow("Retirado", formatDateTime(it)) }
                    parking.levelSector?.let { InfoRow("Piso o sector", it) }
                    parking.note?.let { InfoRow("Nota", it) }
                    parking.ruleSummary?.let { InfoRow("Regla del cartel", it) }
                    state.vehicle?.plate?.let { InfoRow("Patente", it) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                if (parking.status == ParkingStatus.ACTIVE) {
                    Button(onClick = onReturnToCar, modifier = Modifier.fillMaxWidth()) { Text("Volver al auto") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Editar") }
                        OutlinedButton(
                            onClick = { confirmFinish = true },
                            enabled = !state.working,
                            modifier = Modifier.weight(1f),
                        ) { Text("Ya lo retiré") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Borrar del historial", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmFinish) {
        ConfirmDialog(
            title = "¿Ya retiraste el vehículo?",
            text = "El estacionamiento pasa al historial.",
            confirmLabel = "Sí, lo retiré",
            onConfirm = { confirmFinish = false; vm.finish() },
            onDismiss = { confirmFinish = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Borrar este registro?",
            text = "Se elimina del historial y no se puede deshacer.",
            confirmLabel = "Borrar",
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
            "Estuvo ${formatDuration(end - parking.startedAt.toMillis())}" to null
        }
        expiresAt == null -> "Sin límite de tiempo" to "Estacionado hace ${formatDuration(now - parking.startedAt.toMillis())}"
        else -> formatRemaining(expiresAt, now) to "Vence ${formatDateTime(expiresAt)}"
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
