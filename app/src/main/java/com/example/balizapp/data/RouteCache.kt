package com.example.balizapp.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda en disco el último recorrido por calles de cada estacionamiento (filesDir/routes), para
 * poder seguirlo sin conexión. Es un archivo JSON por estacionamiento; los de más de 30 días se
 * borran solos al guardar uno nuevo.
 */
class RouteCache(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "routes")

    fun save(parkingId: String, destination: GeoPosition, route: WalkingRoute) {
        try {
            dir.mkdirs()
            val json = JSONObject()
                .put("destLat", destination.latitude)
                .put("destLon", destination.longitude)
                .put("distance", route.distanceMeters)
                .put("duration", route.durationSeconds)
                .put("points", JSONArray().also { arr ->
                    route.points.forEach { arr.put(JSONArray().put(it.latitude).put(it.longitude)) }
                })
            File(dir, "$parkingId.json").writeText(json.toString())
            prune()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar el recorrido", e)
        }
    }

    /** El recorrido guardado, solo si termina donde está el vehículo ahora (a menos de 10 m). */
    fun load(parkingId: String, destination: GeoPosition): WalkingRoute? {
        val file = File(dir, "$parkingId.json")
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val saved = GeoPosition(json.getDouble("destLat"), json.getDouble("destLon"))
            if (RouteRepository.distance(saved, destination) > 10f) return null
            val arr = json.getJSONArray("points")
            WalkingRoute(
                points = List(arr.length()) { i ->
                    arr.getJSONArray(i).let { GeoPosition(it.getDouble(0), it.getDouble(1)) }
                },
                distanceMeters = json.getDouble("distance"),
                durationSeconds = json.getDouble("duration"),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Recorrido guardado ilegible", e)
            null
        }
    }

    private fun prune() {
        val limit = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    private companion object {
        const val TAG = "RouteCache"
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
