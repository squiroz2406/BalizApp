package com.example.balizapp.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox

private const val TAG = "OfflineTiles"
private const val RADIUS_METERS = 500.0
private const val MIN_ZOOM = 15
private const val MAX_ZOOM = 18

/**
 * Descarga a disco los mosaicos del mapa alrededor de un punto (unos 150 en total), para poder
 * volver al vehículo sin conexión. Es un área chica a propósito: la política de OpenStreetMap
 * no permite descargas masivas. No hace nada si no hay internet. Llamar desde el hilo principal.
 */
fun precacheTilesAround(context: Context, latitude: Double, longitude: Double) {
    val appContext = context.applicationContext
    if (!appContext.isOnline()) return
    try {
        configureOsmdroid(appContext)
        val dLat = RADIUS_METERS / 111_320.0
        val dLon = RADIUS_METERS / (111_320.0 * Math.cos(Math.toRadians(latitude)))
        val box = BoundingBox(latitude + dLat, longitude + dLon, latitude - dLat, longitude - dLon)
        CacheManager(TileSourceFactory.MAPNIK, SqlTileWriter(), MIN_ZOOM, MAX_ZOOM)
            .downloadAreaAsyncNoUI(appContext, box, MIN_ZOOM, MAX_ZOOM, object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() = Unit
                override fun onTaskFailed(errors: Int) { Log.w(TAG, "Mosaicos con error: $errors") }
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = Unit
                override fun downloadStarted() = Unit
                override fun setPossibleTilesInArea(total: Int) = Unit
            })
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo precargar el mapa", e)
    }
}

private fun Context.isOnline(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
