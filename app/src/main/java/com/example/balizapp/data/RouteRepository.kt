package com.example.balizapp.data

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Recorrido a pie por calles: puntos de la línea, distancia total y tiempo estimado. */
data class WalkingRoute(
    val points: List<GeoPosition>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

/**
 * Calcula la ruta a pie por calles (respetando cuadras, veredas y sendas peatonales) con el
 * servidor público OSRM de FOSSGIS, que usa los datos de OpenStreetMap. No necesita clave
 * ni facturación.
 *
 * Política de uso del servidor (https://routing.openstreetmap.de/about.html): máximo un pedido
 * por segundo, sin uso intensivo, User-Agent propio y atribución visible. La app pide la ruta
 * solo al activarla y al desviarse, con al menos [MIN_INTERVAL_MS] entre pedidos.
 */
class RouteRepository {

    suspend fun walkingRoute(from: GeoPosition, toLatitude: Double, toLongitude: Double): WalkingRoute =
        withContext(Dispatchers.IO) {
            // OSRM recibe las coordenadas como longitud,latitud.
            val coords = String.format(
                Locale.US, "%.6f,%.6f;%.6f,%.6f",
                from.longitude, from.latitude, toLongitude, toLatitude,
            )
            val url = URL("$BASE_URL$coords?overview=full&geometries=geojson&alternatives=false&steps=false")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw IOException("El servidor de rutas respondió $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                connection.disconnect()
            }
        }

    private fun parse(body: String): WalkingRoute {
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") {
            throw IOException("No se encontró una ruta a pie (${json.optString("code")})")
        }
        val route = json.getJSONArray("routes").getJSONObject(0)
        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = List(coordinates.length()) { i ->
            val pair = coordinates.getJSONArray(i) // [longitud, latitud]
            GeoPosition(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
        }
        return WalkingRoute(
            points = points,
            distanceMeters = route.getDouble("distance"),
            durationSeconds = route.getDouble("duration"),
        )
    }

    companion object {
        private const val BASE_URL = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/"
        private const val USER_AGENT =
            "BalizApp/1.0 (Android; trabajo practico UNAJ; +https://github.com/squiroz2406/BalizApp)"

        /** Tiempo mínimo entre pedidos al servidor (su límite es 1 por segundo; somos más prudentes). */
        const val MIN_INTERVAL_MS = 15_000L

        /** Distancia en metros entre dos posiciones. */
        fun distance(a: GeoPosition, b: GeoPosition): Float {
            val r = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, r)
            return r[0]
        }

        /** Rumbo (0–360°, respecto del norte geográfico) de [a] hacia [b]. */
        fun bearing(a: GeoPosition, b: GeoPosition): Float {
            val r = FloatArray(2)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, r)
            return ((r[1] % 360f) + 360f) % 360f
        }
    }
}

/**
 * Cálculos sobre una ruta ya obtenida, con la posición actual:
 * dónde está el usuario sobre la línea, cuánto le falta y hacia qué punto apuntar.
 */
object RouteMath {
    /** Índice del punto de la ruta más cercano a [me] y su distancia. */
    fun closestPoint(route: List<GeoPosition>, me: GeoPosition): Pair<Int, Float> {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        route.forEachIndexed { i, p ->
            val d = RouteRepository.distance(me, p)
            if (d < bestDistance) { best = i; bestDistance = d }
        }
        return best to bestDistance
    }

    /** Metros que faltan recorrer por la ruta desde [me] (pasando por el punto más cercano). */
    fun remainingMeters(route: List<GeoPosition>, me: GeoPosition): Float {
        if (route.isEmpty()) return 0f
        val (index, toClosest) = closestPoint(route, me)
        var total = toClosest
        for (i in index until route.lastIndex) total += RouteRepository.distance(route[i], route[i + 1])
        return total
    }

    /**
     * Punto hacia el que tiene que apuntar la flecha: el primero de la ruta, más adelante del
     * usuario, que esté a por lo menos [lookAheadMeters]. Así la flecha sigue las esquinas.
     */
    fun nextTarget(route: List<GeoPosition>, me: GeoPosition, lookAheadMeters: Float = 25f): GeoPosition? {
        if (route.isEmpty()) return null
        val (index, _) = closestPoint(route, me)
        for (i in index until route.size) {
            if (RouteRepository.distance(me, route[i]) >= lookAheadMeters) return route[i]
        }
        return route.last()
    }
}
