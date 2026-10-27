package com.example.balizapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric aporta android.location.Location, que en un test JVM común no calcula nada. */
@RunWith(RobolectricTestRunner::class)
class RouteMathTest {
    // Puntos sobre un mismo meridiano: 0.001° de latitud ≈ 111 m.
    private val a = GeoPosition(-34.600, -58.400)
    private val b = GeoPosition(-34.601, -58.400)
    private val c = GeoPosition(-34.602, -58.400)
    private val route = listOf(a, b, c)

    @Test fun `distancia entre puntos cercanos`() =
        assertEquals(111f, RouteRepository.distance(a, b), 2f)

    @Test fun `rumbo hacia el sur es 180`() =
        assertEquals(180f, RouteRepository.bearing(a, b), 1f)

    @Test fun `rumbo hacia el norte es 0 y nunca negativo`() {
        val bearing = RouteRepository.bearing(b, a)
        assertTrue(bearing in 0f..360f)
        assertTrue(bearing < 1f || bearing > 359f)
    }

    @Test fun `punto mas cercano de la ruta`() {
        val (index, distance) = RouteMath.closestPoint(route, GeoPosition(-34.6011, -58.400))
        assertEquals(1, index)
        assertTrue(distance < 20f)
    }

    @Test fun `metros restantes desde el inicio es toda la ruta`() =
        assertEquals(222f, RouteMath.remainingMeters(route, a), 4f)

    @Test fun `metros restantes de una ruta vacia es cero`() =
        assertEquals(0f, RouteMath.remainingMeters(emptyList(), a), 0f)

    @Test fun `siguiente objetivo respeta la distancia de anticipacion`() {
        val target = RouteMath.nextTarget(route, a, lookAheadMeters = 150f)
        assertEquals(c, target)
    }

    @Test fun `si nada supera la anticipacion apunta al final`() =
        assertEquals(c, RouteMath.nextTarget(route, a, lookAheadMeters = 10_000f))

    @Test fun `ruta vacia no tiene objetivo`() =
        assertNull(RouteMath.nextTarget(emptyList(), a))
}
