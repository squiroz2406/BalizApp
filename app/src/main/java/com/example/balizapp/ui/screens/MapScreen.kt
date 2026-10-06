package com.example.balizapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.ui.components.MapMarker
import com.example.balizapp.ui.components.MapPoint
import com.example.balizapp.ui.components.OsmMap
import com.example.balizapp.ui.components.ScreenScaffold
import com.example.balizapp.ui.components.formatDateTime
import com.example.balizapp.ui.components.rememberLocationPermissionRequest

private val ActiveColor = Color(0xFFE65100)   // naranja
private val HistoryColor = Color(0xFF1E88E5)  // azul

/**
 * Mapa general (RF5): tu ubicación y tus estacionamientos (OpenStreetMap).
 * Activos en naranja; historial en azul y semitransparente. Tocar un marcador muestra su tarjeta.
 */
@Composable
fun MapScreen(vm: MapViewModel, onOpenParking: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val requestPermission = rememberLocationPermissionRequest(vm::onLocationPermissionResult)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Hacia dónde mover la cámara; cambia al centrar en el auto o en tu posición.
    var cameraTarget by remember { mutableStateOf<MapPoint?>(null) }
    var centerRequest by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!state.hasLocationPermission) requestPermission()
    }

    // Primer encuadre: el estacionamiento activo o, si no hay, tu posición.
    LaunchedEffect(state.loading, state.active, state.myPosition) {
        if (cameraTarget != null || state.loading) return@LaunchedEffect
        cameraTarget = state.active.firstOrNull()?.point()
            ?: state.myPosition?.let { MapPoint(it.latitude, it.longitude) }
    }

    val markers = remember(state.active, state.history, state.vehicles) {
        state.history.mapNotNull { p ->
            p.point()?.let { MapMarker(p.id, it, state.vehicles[p.vehicleId]?.alias, HistoryColor, alpha = 0.6f) }
        } + state.active.mapNotNull { p ->
            p.point()?.let { MapMarker(p.id, it, state.vehicles[p.vehicleId]?.alias, ActiveColor) }
        }
    }
    val selected = (state.active + state.history).firstOrNull { it.id == selectedId }

    ScreenScaffold(title = "Mapa") {
        Box(Modifier.weight(1f)) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                center = cameraTarget,
                zoom = 16.0,
                centerRequest = centerRequest,
                markers = markers,
                showMyLocation = state.hasLocationPermission,
                onMapClick = { selectedId = null },
                onMarkerClick = { selectedId = it },
            )

            if (state.hasLocationPermission && state.myPosition != null) {
                SmallFloatingActionButton(
                    onClick = {
                        state.myPosition?.let { cameraTarget = MapPoint(it.latitude, it.longitude) }
                        centerRequest++
                        vm.refreshMyPosition()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) { Icon(Icons.Filled.LocationOn, contentDescription = "Centrar en mi ubicación") }
            }

            when {
                selected != null -> SelectedParkingCard(
                    parking = selected,
                    vehicleName = state.vehicles[selected.vehicleId]?.alias,
                    onOpen = { onOpenParking(selected.id) },
                    onClose = { selectedId = null },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                !state.hasLocationPermission -> ElevatedCard(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                ) {
                    Text(
                        "Permití la ubicación para ver dónde estás.",
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = requestPermission, modifier = Modifier.padding(12.dp)) {
                        Text("Permitir ubicación")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedParkingCard(
    parking: Parking,
    vehicleName: String?,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = parking.status == ParkingStatus.ACTIVE
    ElevatedCard(
        modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(vehicleName ?: "Estacionamiento", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        if (active) "Estacionado ahora" else formatDateTime(parking.startedAt),
                        parking.summaryLine(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
        }
        Button(onClick = onOpen, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)) {
            Text("Ver detalle")
        }
    }
}

private fun Parking.point(): MapPoint? = location?.let { MapPoint(it.latitude, it.longitude) }
