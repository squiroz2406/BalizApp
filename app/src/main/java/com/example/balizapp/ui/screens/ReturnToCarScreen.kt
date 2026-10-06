package com.example.balizapp.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.balizapp.ui.components.EmptyState
import com.example.balizapp.ui.components.MapMarker
import com.example.balizapp.ui.components.MapPoint
import com.example.balizapp.ui.components.OsmMap
import com.example.balizapp.ui.components.ScreenScaffold
import com.example.balizapp.ui.components.rememberLocationPermissionRequest
import java.util.Locale
import kotlin.math.roundToInt

private val CarColor = Color(0xFFE65100)
private val ArrivedColor = Color(0xFF2E7D32)

/**
 * Volver al auto (RF5 + RF6): flecha de brújula que apunta al vehículo, distancia en vivo y mapa
 * con tu posición, la del auto y una línea entre ambos. Útil en interiores y playas grandes,
 * donde el mapa solo no alcanza para orientarse.
 */
@Composable
fun ReturnToCarScreen(vm: ReturnToCarViewModel, onBack: () -> Unit, onFinished: () -> Unit) {
    // collectAsStateWithLifecycle: en segundo plano deja de observar y se apagan GPS y sensores.
    val state by vm.state.collectAsStateWithLifecycle()
    val requestPermission = rememberLocationPermissionRequest(vm::onLocationPermissionResult)

    LaunchedEffect(Unit) { if (!state.hasLocationPermission) requestPermission() }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    // La pantalla no se apaga mientras se camina hacia el auto.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    ScreenScaffold(title = "Volver al auto", onBack = onBack) {
        val parking = state.parking
        val car = parking?.location?.let { MapPoint(it.latitude, it.longitude) }
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            parking == null || car == null -> EmptyState(
                icon = Icons.Filled.Info,
                title = "No se encontró el estacionamiento",
                text = "Puede que se haya borrado o que no tenga ubicación guardada.",
            )
            else -> {
                GuidanceCard(state, onRequestPermission = requestPermission)
                ReturnMap(state, car, Modifier.weight(1f))
                PlaceInfo(state)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalkingRouteButton(car, Modifier.weight(1f))
                    Button(
                        onClick = vm::finish,
                        enabled = !state.working,
                        modifier = Modifier.weight(1f),
                    ) { Text("Ya lo retiré") }
                }
            }
        }
    }
}

/** Flecha + distancia + indicaciones. */
@Composable
private fun GuidanceCard(state: ReturnToCarState, onRequestPermission: () -> Unit) {
    val arrived = state.arrived
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (arrived) ArrivedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CompassArrow(rotation = state.arrowRotation, color = if (arrived) ArrivedColor else CarColor)
            Column(
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val distance = state.distanceMeters
                Text(
                    when {
                        !state.hasLocationPermission -> "Sin ubicación"
                        distance == null -> "Buscando tu posición…"
                        arrived -> "¡Llegaste!"
                        else -> formatDistance(distance)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(guidanceText(state), style = MaterialTheme.typography.bodyMedium)
                state.myPosition?.accuracyMeters?.takeIf { it > 30f }?.let {
                    Text(
                        "GPS con poca precisión (±${it.roundToInt()} m)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!state.hasLocationPermission) {
                    OutlinedButton(onClick = onRequestPermission) { Text("Permitir ubicación") }
                }
            }
        }
    }
}

private fun guidanceText(state: ReturnToCarState): String {
    val bearing = state.bearingDegrees
    return when {
        !state.hasLocationPermission -> "Permití la ubicación para calcular hacia dónde caminar."
        state.arrived -> "Estás a menos de ${ReturnToCarState.ARRIVED_METERS.roundToInt()} m del vehículo."
        bearing == null -> "Esperando el GPS…"
        !state.compassAvailable ->
            "Sin brújula en este teléfono: caminá hacia el ${cardinal(bearing)} (la flecha indica el rumbo con el norte arriba)."
        state.compassUnreliable -> "Calibrá la brújula moviendo el teléfono en forma de 8."
        else -> "Sostené el teléfono horizontal y caminá hacia donde apunta la flecha (${cardinal(bearing)})."
    }
}

/**
 * Flecha dibujada con Canvas. La rotación se anima siempre por el camino más corto
 * (de 350° a 10° gira 20°, no 340°).
 */
@Composable
private fun CompassArrow(rotation: Float?, color: Color) {
    var accumulated by remember { mutableFloatStateOf(rotation ?: 0f) }
    LaunchedEffect(rotation) {
        if (rotation != null) {
            val current = ((accumulated % 360f) + 360f) % 360f
            val delta = ((rotation - current + 540f) % 360f) - 180f
            accumulated += delta
        }
    }
    val animated by animateFloatAsState(accumulated, animationSpec = tween(250), label = "flecha")
    val background = MaterialTheme.colorScheme.surface
    val disabled = MaterialTheme.colorScheme.outline

    Box(
        Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(60.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .rotate(if (rotation != null) animated else 0f)
        ) {
            drawCircle(background)
            val w = size.width
            val h = size.height
            val arrow = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)  // punta
                lineTo(w * 0.78f, h * 0.70f)
                lineTo(w * 0.5f, h * 0.58f)
                lineTo(w * 0.22f, h * 0.70f)
                close()
            }
            drawPath(arrow, if (rotation != null) color else disabled)
            drawCircle(if (rotation != null) color else disabled, radius = w * 0.04f, center = Offset(w * 0.5f, h * 0.86f))
        }
    }
}

/** Mapa con el auto (naranja), tu posición y una línea punteada entre ambos. */
@Composable
private fun ReturnMap(state: ReturnToCarState, car: MapPoint, modifier: Modifier) {
    val me = state.myPosition?.let { MapPoint(it.latitude, it.longitude) }
    // Se encuadran ambos puntos una vez, cuando llega la primera posición.
    var fitRequest by remember { mutableIntStateOf(0) }
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(me != null) {
        if (me != null && !fitted) {
            fitted = true
            fitRequest++
        }
    }
    val markers = remember(car, state.vehicle) {
        listOf(MapMarker("car", car, state.vehicle?.alias, CarColor))
    }
    OsmMap(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        center = car,
        markers = markers,
        showMyLocation = state.hasLocationPermission,
        line = if (me != null && !state.arrived) listOf(me, car) else emptyList(),
        fitPoints = if (me != null) listOf(me, car) else emptyList(),
        fitRequest = fitRequest,
    )
}

/** Datos para encontrar el auto: piso/sector bien visible (playas de estacionamiento), dirección y nota. */
@Composable
private fun PlaceInfo(state: ReturnToCarState) {
    val parking = state.parking ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            listOfNotNull(state.vehicle?.alias, state.vehicle?.plate).joinToString(" · ").ifEmpty { "Tu vehículo" },
            style = MaterialTheme.typography.titleMedium,
        )
        parking.levelSector?.let {
            Text("Piso / sector: $it", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        listOfNotNull(parking.addressText, parking.note).takeIf { it.isNotEmpty() }?.let {
            Text(
                it.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/**
 * Ruta a pie por calles: se delega en la app de mapas instalada (Google Maps u otra) con un
 * enlace universal, que no necesita clave de API. Dentro de BalizApp se muestra la línea recta.
 */
@Composable
private fun WalkingRouteButton(car: MapPoint, modifier: Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val url = "https://www.google.com/maps/dir/?api=1&destination=${car.latitude},${car.longitude}&travelmode=walking"
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: ActivityNotFoundException) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:${car.latitude},${car.longitude}?q=${car.latitude},${car.longitude}"))
                )
            }
        },
        modifier = modifier,
    ) { Text("Ruta a pie") }
}

private fun formatDistance(meters: Float): String =
    if (meters < 1000) "${meters.roundToInt()} m"
    else String.format(Locale.forLanguageTag("es-AR"), "%.1f km", meters / 1000)

private fun cardinal(bearing: Float): String {
    val names = listOf("norte", "noreste", "este", "sudeste", "sur", "sudoeste", "oeste", "noroeste")
    return names[(((bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
}
