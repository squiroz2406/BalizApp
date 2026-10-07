package com.example.balizapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.ui.components.EmptyState
import com.example.balizapp.ui.components.ScreenScaffold
import com.example.balizapp.ui.components.formatDateTime
import com.example.balizapp.ui.components.formatRemaining
import com.example.balizapp.ui.components.rememberNow
import com.example.balizapp.ui.components.toMillis

/** Inicio (RF2): estacionamientos activos arriba, destacados, y el historial debajo. */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onParkHere: () -> Unit,
    onOpenParking: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val now by rememberNow()

    ScreenScaffold(title = "BalizApp") {
        Button(
            onClick = onParkHere,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null)
            Text("  Estacioné aquí", style = MaterialTheme.typography.titleMedium)
        }

        when {
            state.loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }

            state.active.isEmpty() && state.history.isEmpty() -> EmptyState(
                icon = Icons.Filled.Place,
                title = "Todavía no guardaste ningún estacionamiento",
                text = "Cuando dejes el auto, tocá \"Estacioné aquí\".",
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.active.isNotEmpty()) {
                    item { SectionLabel("Estacionado ahora") }
                    items(state.active, key = { it.id }) { parking ->
                        ActiveParkingCard(parking, state.vehicles[parking.vehicleId], now) {
                            onOpenParking(parking.id)
                        }
                    }
                }
                item { SectionLabel("Historial") }
                if (state.history.isEmpty()) {
                    item {
                        Text(
                            "Cuando retires el auto, el estacionamiento aparece acá.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.history, key = { it.id }) { parking ->
                    HistoryItem(parking, state.vehicles[parking.vehicleId]) { onOpenParking(parking.id) }
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
                    vehicle?.alias ?: "Vehículo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                parking.summaryLine()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("Desde ${formatDateTime(parking.startedAt)}", style = MaterialTheme.typography.bodySmall)
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
        headlineContent = { Text(parking.summaryLine() ?: (vehicle?.alias ?: "Estacionamiento")) },
        supportingContent = {
            Text(listOfNotNull(vehicle?.alias, formatDateTime(parking.startedAt)).joinToString(" · "))
        },
        leadingContent = { vehicle?.let { VehicleIcon(it.type) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Texto corto para identificar el lugar: dirección, nota o piso/sector. */
fun Parking.summaryLine(): String? =
    listOfNotNull(addressText, note, levelSector?.let { "Sector $it" })
        .firstOrNull { it.isNotBlank() }
