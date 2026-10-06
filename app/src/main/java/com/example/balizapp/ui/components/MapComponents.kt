package com.example.balizapp.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.balizapp.R
import com.example.balizapp.data.hasLocationPermission
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

/*
 * Mapa con OpenStreetMap (osmdroid): no necesita clave de API ni cuenta de facturación.
 * MapView es una View clásica, así que se integra en Compose con AndroidView.
 */

/** Un punto del mapa (lat/lng). Propio de la UI, para no depender de la clase de ninguna biblioteca. */
data class MapPoint(val latitude: Double, val longitude: Double)

/** Centro por defecto cuando todavía no hay ubicación: Obelisco, CABA. */
val DefaultMapPoint = MapPoint(-34.6037, -58.3816)

/** Un marcador a dibujar. [id] se devuelve al tocarlo. */
data class MapMarker(
    val id: String,
    val point: MapPoint,
    val title: String? = null,
    val color: Color,
    val alpha: Float = 1f,
)

/**
 * Devuelve una función que pide el permiso de ubicación (precisa o aproximada).
 * [onResult] recibe true si el usuario concedió alguna de las dos.
 */
@Composable
fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> currentOnResult.value(result.values.any { it }) }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }
}

/**
 * Mapa de OpenStreetMap.
 * - [center]: cuando cambia, la cámara se mueve ahí (con [zoom]). Para volver a centrar en el
 *   mismo punto (por ejemplo, después de que el usuario movió el mapa), se incrementa [centerRequest].
 * - [interactive] = false: mapa estático (sin gestos), para el detalle.
 * - [showMyLocation]: punto azul con tu posición (requiere permiso).
 * - [line]: línea recta entre puntos (por ejemplo, de tu posición al auto).
 * - [fitPoints]: encuadra todos esos puntos; se aplica cuando cambia [fitRequest].
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    center: MapPoint?,
    zoom: Double = 17.0,
    centerRequest: Int = 0,
    markers: List<MapMarker> = emptyList(),
    interactive: Boolean = true,
    showMyLocation: Boolean = false,
    onMapClick: ((MapPoint) -> Unit)? = null,
    onMarkerClick: ((String) -> Unit)? = null,
    line: List<MapPoint> = emptyList(),
    lineColor: Color = Color(0xFF1E88E5),
    fitPoints: List<MapPoint> = emptyList(),
    fitRequest: Int = 0,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMapClick = rememberUpdatedState(onMapClick)
    val currentOnMarkerClick = rememberUpdatedState(onMarkerClick)
    val holder = remember { MapHolder() }

    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(
                if (interactive) CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
                else CustomZoomButtonsController.Visibility.NEVER
            )
            isTilesScaledToDpi = true
            controller.setZoom(if (center != null) zoom else 12.0)
            val start = center ?: DefaultMapPoint
            controller.setCenter(GeoPoint(start.latitude, start.longitude))
            // Atribución obligatoria de OpenStreetMap
            overlays.add(CopyrightOverlay(context))
            // Toques sobre el mapa (no sobre un marcador)
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    val handler = currentOnMapClick.value ?: return false
                    handler(MapPoint(p.latitude, p.longitude))
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
            if (!interactive) setOnTouchListener { _, _ -> true } // mapa estático
            holder.lastCenter = center
        }
    }

    // El MapView sigue el ciclo de vida de la pantalla (pausa la descarga de mosaicos en segundo plano).
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.myLocation?.disableMyLocation()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            // Cámara: solo se mueve cuando cambia el centro pedido.
            if (center != null && (center != holder.lastCenter || centerRequest != holder.lastRequest)) {
                val target = GeoPoint(center.latitude, center.longitude)
                if (holder.lastCenter == null || !interactive) {
                    map.controller.setZoom(zoom)
                    map.controller.setCenter(target)
                } else {
                    map.controller.animateTo(target, zoom, 600L)
                }
                holder.lastCenter = center
                holder.lastRequest = centerRequest
            }

            // Marcadores: se rehacen solo si cambió la lista.
            if (markers != holder.lastMarkers) {
                map.overlays.removeAll { it is Marker }
                markers.forEach { m ->
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(m.point.latitude, m.point.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = pinIcon(map.context, m.color)
                        alpha = m.alpha
                        title = m.title
                        setInfoWindow(null) // la info se muestra en Compose, no en el globo de osmdroid
                        setOnMarkerClickListener { _, _ ->
                            currentOnMarkerClick.value?.invoke(m.id)
                            true
                        }
                    })
                }
                holder.lastMarkers = markers
            }

            // Línea (se reemplaza si cambian los puntos).
            if (line != holder.lastLine) {
                holder.polyline?.let { map.overlays.remove(it) }
                holder.polyline = if (line.size >= 2) {
                    Polyline(map).apply {
                        setPoints(line.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = lineColor.toArgb()
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(30f, 20f), 0f)
                        setOnClickListener { _, _, _ -> false }
                    }.also { map.overlays.add(1, it) } // debajo de los marcadores
                } else null
                holder.lastLine = line
            }

            // Encuadre de varios puntos (después del primer dibujado, cuando el mapa ya tiene tamaño).
            if (fitPoints.size >= 2 && fitRequest != holder.lastFitRequest) {
                holder.lastFitRequest = fitRequest
                val box = BoundingBox.fromGeoPointsSafe(fitPoints.map { GeoPoint(it.latitude, it.longitude) })
                map.post { map.zoomToBoundingBox(box.increaseByScale(1.4f), true, 80) }
            }

            updateMyLocation(map, holder, showMyLocation)
            map.invalidate()
        },
    )
}

/** Mapa chico con un marcador. Si se pasa [onMapClick], tocar el mapa mueve el marcador. */
@Composable
fun ParkingMiniMap(
    position: MapPoint?,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    liteMode: Boolean = false,
    markerTitle: String? = null,
    markerColor: Color = Color(0xFFE65100),
    onMapClick: ((MapPoint) -> Unit)? = null,
) {
    OsmMap(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp)),
        center = position,
        markers = position?.let { listOf(MapMarker("pos", it, markerTitle, markerColor)) }.orEmpty(),
        interactive = !liteMode,
        onMapClick = onMapClick,
    )
}

private class MapHolder {
    var lastCenter: MapPoint? = null
    var lastRequest: Int = 0
    var lastMarkers: List<MapMarker>? = null
    var myLocation: MyLocationNewOverlay? = null
    var lastLine: List<MapPoint>? = null
    var polyline: Polyline? = null
    var lastFitRequest: Int = 0
}

@SuppressLint("MissingPermission") // se verifica hasLocationPermission() antes de activar
private fun updateMyLocation(map: MapView, holder: MapHolder, show: Boolean) {
    val wanted = show && map.context.hasLocationPermission()
    val current = holder.myLocation
    if (wanted && current == null) {
        val overlay = MyLocationNewOverlay(GpsMyLocationProvider(map.context), map)
        overlay.enableMyLocation()
        map.overlays.add(overlay)
        holder.myLocation = overlay
    } else if (!wanted && current != null) {
        current.disableMyLocation()
        map.overlays.remove(current)
        holder.myLocation = null
    }
}

private fun pinIcon(context: Context, color: Color): Drawable? =
    ContextCompat.getDrawable(context, R.drawable.ic_map_pin)?.mutate()?.apply { setTint(color.toArgb()) }

/**
 * Configuración de osmdroid según la política de uso de los servidores de OpenStreetMap
 * (https://operations.osmfoundation.org/policies/tiles/):
 * - User-Agent propio y estable que nombra la app. Un ID de paquete genérico como
 *   "com.example..." está bloqueado y devuelve mosaicos de "Access blocked".
 * - Los mosaicos se guardan en cache (carpeta privada de la app) para no volver a pedirlos.
 */
private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    if (config.userAgentValue == OSM_USER_AGENT) return // ya configurado
    config.userAgentValue = OSM_USER_AGENT
    val base = File(context.cacheDir, "osmdroid")
    // El cache viejo puede tener guardados los mosaicos de "Access blocked": se descarta.
    File(base, "tiles").takeIf { it.exists() }?.deleteRecursively()
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tile-cache")
}

private const val OSM_USER_AGENT = "BalizApp/1.0 (Android; trabajo practico UNAJ; +https://github.com/squiroz2406/BalizApp)"
