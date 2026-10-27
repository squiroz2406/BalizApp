package com.example.balizapp.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RouteCacheTest {
    private lateinit var context: Context
    private lateinit var cache: RouteCache
    private val destination = GeoPosition(-34.600, -58.400)
    private val route = WalkingRoute(
        points = listOf(GeoPosition(-34.598, -58.400), destination),
        distanceMeters = 222.0,
        durationSeconds = 160.0,
    )

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "routes").deleteRecursively()
        cache = RouteCache(context)
    }

    @Test fun `guardar y cargar devuelve la misma ruta`() {
        cache.save("p1", destination, route)
        assertEquals(route, cache.load("p1", destination))
    }

    @Test fun `sin archivo guardado devuelve null`() =
        assertNull(cache.load("nunca-guardado", destination))

    @Test fun `si el vehiculo se movio mas de 10 m descarta la ruta`() {
        cache.save("p1", destination, route)
        assertNull(cache.load("p1", GeoPosition(-34.601, -58.400)))
    }

    @Test fun `si el vehiculo se movio menos de 10 m la conserva`() {
        cache.save("p1", destination, route)
        assertNotNull(cache.load("p1", GeoPosition(-34.60003, -58.400)))
    }

    @Test fun `archivo corrupto devuelve null en vez de fallar`() {
        File(context.filesDir, "routes").mkdirs()
        File(context.filesDir, "routes/p1.json").writeText("{no es json")
        assertNull(cache.load("p1", destination))
    }

    @Test fun `borra los recorridos de mas de 30 dias al guardar uno nuevo`() {
        cache.save("viejo", destination, route)
        val old = File(context.filesDir, "routes/viejo.json")
        old.setLastModified(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000)
        cache.save("nuevo", destination, route)
        assertEquals(false, old.exists())
        assertNotNull(cache.load("nuevo", destination))
    }
}
