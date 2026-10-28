package com.example.balizapp.ui.screens

import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.ui.components.EmptyState

import com.example.balizapp.ui.components.formatDateTime
import com.example.balizapp.ui.components.formatRemaining
import com.example.balizapp.ui.components.rememberNow
import com.example.balizapp.ui.components.toMillis

/** Filtro por estado del estacionamiento en Inicio. */
private enum class StatusFilter(@StringRes val labelRes: Int) {
    ALL(R.string.home_filter_status_all),
    CURRENT(R.string.home_filter_status_current),
    EXPIRED(R.string.home_filter_status_expired),
    FINISHED(R.string.home_filter_status_finished),
}

/** Inicio (RF2): estacionamientos activos arriba, destacados, y el historial debajo. */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onParkHere: () -> Unit,
    onOpenParking: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val now by rememberNow()
    var vehicleFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf(StatusFilter.ALL) }

    // Si el vehículo filtrado se borró, vuelve a "todos".
    val selectedVehicle = vehicleFilter?.takeIf { it in state.vehicles }
    val matchesVehicle = { p: Parking -> selectedVehicle == null || p.vehicleId == selectedVehicle }
    val isExpired = { p: Parking -> p.expiresAt != null && p.expiresAt.toMillis() <= now }
    val active = state.active.filter {
        matchesVehicle(it) && when (statusFilter) {
            StatusFilter.ALL -> true
            StatusFilter.CURRENT -> !isExpired(it)
            StatusFilter.EXPIRED -> isExpired(it)
            StatusFilter.FINISHED -> false
        }
    }
    val history = state.history.filter {
        matchesVehicle(it) && (statusFilter == StatusFilter.ALL || statusFilter == StatusFilter.FINISHED)
    }

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                onClick = onParkHere,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Text("  " + stringResource(R.string.home_park_here), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> {
                    header()
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.active.isEmpty() && state.history.isEmpty() -> {
                    header()
                    EmptyState(
                        icon = Icons.Filled.Place,
                        title = stringResource(R.string.home_empty_title),
                        text = stringResource(R.string.home_empty_text),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { header() }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.vehicles.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item {
                                            FilterChip(
                                                selected = selectedVehicle == null,
                                                onClick = { vehicleFilter = null },
                                                label = { Text(stringResource(R.string.home_filter_all_vehicles)) },
                                            )
                                        }
                                        items(state.vehicles.values.toList(), key = { it.id }) { v ->
                                            FilterChip(
                                                selected = selectedVehicle == v.id,
                                                onClick = { vehicleFilter = v.id },
                                                label = { Text(v.alias.ifBlank { stringResource(v.type.labelRes) }) },
                                            )
                                        }
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(StatusFilter.entries.toList()) { f ->
                                        FilterChip(
                                            selected = statusFilter == f,
                                            onClick = { statusFilter = f },
                                            label = { Text(stringResource(f.labelRes)) },
                                        )
                                    }
                                }
                            }
                        }
                        if (active.isEmpty() && history.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.home_no_results),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        if (active.isNotEmpty()) {
                            item { SectionLabel(stringResource(R.string.home_section_active)) }
                            items(active, key = { it.id }) { parking ->
                                ActiveParkingCard(parking, state.vehicles[parking.vehicleId], now) {
                                    onOpenParking(parking.id)
                                }
                            }
                        }
                        if (statusFilter == StatusFilter.ALL || statusFilter == StatusFilter.FINISHED) {
                            item { SectionLabel(stringResource(R.string.home_section_history)) }
                            if (history.isEmpty() && state.history.isEmpty()) {
                                item {
                                    Text(
                                        stringResource(R.string.home_history_empty),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(history, key = { it.id }) { parking ->
                                HistoryItem(parking, state.vehicles[parking.vehicleId]) { onOpenParking(parking.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ActiveParkingCard(parking: Parking, vehicle: Vehicle?, now: Long, onClick: () -> Unit) {
    val expired = parking.expiresAt != null && parking.expiresAt.toMillis() <= now
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (expired) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            vehicle?.let { VehicleIcon(it.type) }
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    vehicle?.alias ?: stringResource(R.string.core_vehicle_default),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                parking.summaryLine()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.home_since, formatDateTime(parking.startedAt)), style = MaterialTheme.typography.bodySmall)
            }
            parking.expiresAt?.let { expiresAt ->
                Text(
                    formatRemaining(expiresAt.toMillis(), now),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(parking: Parking, vehicle: Vehicle?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(parking.summaryLine() ?: (vehicle?.alias ?: stringResource(R.string.core_parking_default))) },
        supportingContent = {
            Text(listOfNotNull(vehicle?.alias, formatDateTime(parking.startedAt)).joinToString(" · "))
        },
        leadingContent = { vehicle?.let { VehicleIcon(it.type) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Texto corto para identificar el lugar: dirección, nota o piso/sector. */
fun Parking.summaryLine(): String? =
    listOfNotNull(addressText, note, levelSector?.let { AppLocale.str(R.string.core_sector, it) })
        .firstOrNull { it.isNotBlank() }
