package com.example.balizapp.ui.screens

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.example.balizapp.R
import com.example.balizapp.data.CompassReading
import com.example.balizapp.data.CompassSensor
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.RouteCache
import com.example.balizapp.data.RouteRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.WalkingRoute
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.i18n.AppLocale
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReturnToCarViewModelTest {
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val locationRepo = mockk<LocationRepository>(relaxed = true)
    private val routeRepo = mockk<RouteRepository>(relaxed = true)
    private val routeCache = mockk<RouteCache>(relaxed = true)
    private val compass = mockk<CompassSensor>(relaxed = true)
    private lateinit var app: Application

    // Entre el auto y yo hay ~222 m sobre un mismo meridiano (0.001° de latitud ≈ 111 m).
    private val carPoint = GeoPoint(-34.602, -58.400)
    private val me = GeoPosition(-34.600, -58.400)
    private val car = Vehicle(id = "v1", alias = "Auto")
    private val parking = Parking(id = "p1", vehicleId = "v1", location = carPoint)
    private val route = WalkingRoute(
        points = listOf(me, GeoPosition(-34.601, -58.400), GeoPosition(carPoint.latitude, carPoint.longitude)),
        distanceMeters = 222.0,
        durationSeconds = 160.0,
    )

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        AppLocale.init(app)
        every { parkingRepo.observeParking("p1") } returns flowOf(parking)
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car))
        every { locationRepo.hasPermission() } returns true
        every { locationRepo.positionUpdates(any()) } returns flowOf(me)
        every { compass.isAvailable } returns false
        every { routeCache.load(any(), any()) } returns null
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReturnToCarViewModel(
        app, SavedStateHandle(mapOf("parkingId" to "p1")),
        parkingRepo, vehicleRepo, locationRepo, routeRepo, routeCache, compass,
    )

    private suspend fun ReceiveTurbine<ReturnToCarState>.awaitUntil(
        condition: (ReturnToCarState) -> Boolean,
    ): ReturnToCarState {
        var state = awaitItem()
        while (!condition(state)) state = awaitItem()
        return state
    }

    // --- Modo directo ---

    @Test fun `modo directo calcula distancia y rumbo hacia el auto`() = runTest {
        viewModel().state.test {
            val state = awaitUntil { it.directDistanceMeters != null }
            assertEquals(car, state.vehicle)
            assertEquals(222f, state.directDistanceMeters!!, 4f)
            assertEquals(180f, state.bearingDegrees!!, 1f)
            assertEquals(GuidanceMode.DIRECT, state.mode)
            assertFalse(state.arrived)
        }
    }

    @Test fun `llega cuando esta a menos de 15 metros`() = runTest {
        every { locationRepo.positionUpdates(any()) } returns flowOf(GeoPosition(-34.60195, -58.400))
        viewModel().state.test {
            assertTrue(awaitUntil { it.directDistanceMeters != null }.arrived)
        }
    }

    @Test fun `sin permiso no hay posicion ni distancia`() = runTest {
        every { locationRepo.hasPermission() } returns false
        viewModel().state.test {
            val state = awaitUntil { !it.loading }
            assertFalse(state.hasLocationPermission)
            assertNull(state.myPosition)
            assertNull(state.directDistanceMeters)
            assertNull(state.arrowRotation)
        }
    }

    @Test fun `al conceder el permiso empieza a seguir la posicion`() = runTest {
        every { locationRepo.hasPermission() } returns false
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.loading }
            vm.onLocationPermissionResult(true)
            val state = awaitUntil { it.myPosition != null }
            assertEquals(me, state.myPosition)
        }
    }

    // --- Brújula ---

    @Test fun `con brujula la flecha gira relativa a hacia donde apunta el telefono`() = runTest {
        every { compass.isAvailable } returns true
        every { compass.readings() } returns flowOf(CompassReading(azimuthDegrees = 90f, unreliable = true))
        viewModel().state.test {
            val state = awaitUntil { it.headingDegrees != null && it.directDistanceMeters != null }
            assertTrue(state.compassAvailable)
            assertTrue(state.compassUnreliable)
            // arrowRotation = rumbo - orientacion del telefono (normalizado a 0..360).
            assertEquals(CompassSensor.normalize(state.bearingDegrees!! - state.headingDegrees!!), state.arrowRotation!!, 0.01f)
        }
    }

    @Test fun `sin brujula no hay orientacion y la flecha es relativa al norte`() = runTest {
        viewModel().state.test {
            val state = awaitUntil { it.directDistanceMeters != null }
            assertFalse(state.compassAvailable)
            assertNull(state.headingDegrees)
            assertEquals(state.bearingDegrees!!, state.arrowRotation!!, 0.01f)
        }
    }

    // --- Modo por calles ---

    @Test fun `modo por calles pide la ruta y muestra lo que falta por recorrer`() = runTest {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } returns route
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.directDistanceMeters != null }
            vm.setMode(GuidanceMode.STREETS)
            val state = awaitUntil { it.routeUi.route != null && it.routeRemainingMeters != null }
            assertEquals(222f, state.routeRemainingMeters!!, 4f)
            assertEquals(route.points, state.routePoints)
            assertEquals(state.routeRemainingMeters, state.shownDistanceMeters)
        }
        coVerify(exactly = 1) { routeRepo.walkingRoute(me, carPoint.latitude, carPoint.longitude) }
        coVerify { routeCache.save("p1", GeoPosition(carPoint.latitude, carPoint.longitude), route) }
    }

    @Test fun `en modo directo no pide la ruta`() = runTest {
        viewModel().state.test {
            awaitUntil { it.directDistanceMeters != null }
        }
        coVerify(exactly = 0) { routeRepo.walkingRoute(any(), any(), any()) }
    }

    @Test fun `si falla la ruta y no hay copia guardada avisa el error`() = runTest {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } throws IOException("sin red")
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.directDistanceMeters != null }
            vm.setMode(GuidanceMode.STREETS)
            val state = awaitUntil { it.routeUi.error != null }
            assertEquals(AppLocale.str(R.string.nav_route_error_offline), state.routeUi.error)
            assertNull(state.routeUi.route)
            // Sin ruta, la distancia que se muestra es la directa.
            assertEquals(state.directDistanceMeters, state.shownDistanceMeters)
        }
    }

    @Test fun `si falla la ruta usa la copia guardada en disco`() = runTest {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } throws IOException("sin red")
        every { routeCache.load("p1", GeoPosition(carPoint.latitude, carPoint.longitude)) } returns route
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.directDistanceMeters != null }
            vm.setMode(GuidanceMode.STREETS)
            val state = awaitUntil { it.routeUi.error != null }
            assertEquals(AppLocale.str(R.string.nav_route_saved_offline), state.routeUi.error)
            assertEquals(route, state.routeUi.route)
        }
    }

    @Test fun `una copia guardada lejos del usuario no se usa`() = runTest {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } throws IOException("sin red")
        val far = route.copy(points = listOf(GeoPosition(-34.700, -58.400), GeoPosition(-34.702, -58.400)))
        every { routeCache.load(any(), any()) } returns far
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.directDistanceMeters != null }
            vm.setMode(GuidanceMode.STREETS)
            val state = awaitUntil { it.routeUi.error != null }
            assertEquals(AppLocale.str(R.string.nav_route_error_offline), state.routeUi.error)
        }
    }

    // --- Intervalos entre pedidos de ruta (con reloj controlado) ---

    private val gps = MutableSharedFlow<GeoPosition>(replay = 1)
    private var now = 1_000_000L

    // Unos 90 m al este de la ruta: mas lejos que el umbral de desvio (40 m).
    private val offRoute = GeoPosition(-34.601, -58.401)
    private val offRouteLater = GeoPosition(-34.6011, -58.401)

    private fun viewModelWithClock(): ReturnToCarViewModel {
        every { locationRepo.positionUpdates(any()) } returns gps
        gps.tryEmit(me)
        return ReturnToCarViewModel(
            app, SavedStateHandle(mapOf("parkingId" to "p1")),
            parkingRepo, vehicleRepo, locationRepo, routeRepo, routeCache, compass,
        ) { now }
    }

    @Test fun `si el usuario se desvia pide otra ruta solo cuando paso el intervalo minimo`() = runTest {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } returns route
        val vm = viewModelWithClock()
        vm.state.test {
            awaitUntil { it.directDistanceMeters != null }
            vm.setMode(GuidanceMode.STREETS)
            awaitUntil { it.routeUi.route != null }
            coVerify(exactly = 1) { routeRepo.walkingRoute(any(), any(), any()) }

            now += 1_000 // se desvia, pero recien pidio una ruta
            gps.emit(offRoute)
            awaitUntil { it.myPosition == offRoute }
            coVerify(exactly = 1) { routeRepo.walkingRoute(any(), any(), any()) }

            now += RouteRepository.MIN_INTERVAL_MS // ya paso el intervalo
            gps.emit(offRouteLater)
            awaitUntil { it.myPosition == offRouteLater }
            coVerify(exactly = 2) { routeRepo.walkingRoute(any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `una ruta que fallo no se reintenta hasta que pase el intervalo`() = runTest {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } throws IOException("sin red")
        val vm = viewModelWithClock()
        vm.state.test {
            awaitUntil { it.directDistanceMeters != null }
            vm.setMode(GuidanceMode.STREETS)
            awaitUntil { it.routeUi.error == AppLocale.str(R.string.nav_route_error_offline) }
            coVerify(exactly = 1) { routeRepo.walkingRoute(any(), any(), any()) }

            now += 1_000 // llega otra lectura de GPS enseguida: no insiste
            gps.emit(GeoPosition(-34.6001, -58.400))
            awaitUntil { it.routeUi.error == AppLocale.str(R.string.nav_route_error_retry) }
            coVerify(exactly = 1) { routeRepo.walkingRoute(any(), any(), any()) }

            now += RouteRepository.MIN_INTERVAL_MS // pasado el intervalo, reintenta
            val later = GeoPosition(-34.6002, -58.400)
            gps.emit(later)
            awaitUntil { it.myPosition == later && it.routeUi.error == AppLocale.str(R.string.nav_route_error_offline) }
            coVerify(exactly = 2) { routeRepo.walkingRoute(any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `recalcular ignora los toques repetidos en menos de 3 segundos`() = runTest {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } returns route
        val vm = viewModelWithClock()
        vm.state.test {
            awaitUntil { it.directDistanceMeters != null }
            vm.setMode(GuidanceMode.STREETS)
            awaitUntil { it.routeUi.route != null }
            coVerify(exactly = 1) { routeRepo.walkingRoute(any(), any(), any()) }

            now += 1_000
            vm.recalculateRoute()
            coVerify(exactly = 1) { routeRepo.walkingRoute(any(), any(), any()) }

            now += 3_000
            vm.recalculateRoute()
            coVerify(exactly = 2) { routeRepo.walkingRoute(any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Ya lo retiré ---

    @Test fun `finish marca como retirado y avisa que termino`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.parking != null }
            vm.finish()
            val state = awaitUntil { it.finished }
            assertFalse(state.working)
            assertNull(state.error)
        }
        coVerify { parkingRepo.finish("p1") }
    }

    @Test fun `finish no hace nada si ya estaba retirado`() = runTest {
        every { parkingRepo.observeParking("p1") } returns flowOf(parking.copy(status = ParkingStatus.FINISHED))
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.parking != null }
            vm.finish()
            expectNoEvents()
        }
        coVerify(exactly = 0) { parkingRepo.finish(any()) }
    }

    @Test fun `si finish falla informa el error y no termina`() = runTest {
        coEvery { parkingRepo.finish(any()) } throws RuntimeException("sin permisos")
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.parking != null }
            vm.finish()
            val state = awaitUntil { it.error != null }
            assertEquals(AppLocale.str(R.string.nav_finish_error, "sin permisos"), state.error)
            assertFalse(state.finished)
            assertFalse(state.working)
        }
    }

    // --- Cálculos del estado ---

    @Test fun `modo directo muestra la distancia en linea recta aunque haya ruta`() {
        val state = ReturnToCarState(
            mode = GuidanceMode.DIRECT, directDistanceMeters = 200f, routeRemainingMeters = 300f,
        )
        assertEquals(200f, state.shownDistanceMeters!!, 0f)
        assertTrue(state.routePoints.isEmpty())
    }

    @Test fun `el giro de la flecha se normaliza entre 0 y 360`() {
        val state = ReturnToCarState(bearingDegrees = 10f, headingDegrees = 30f)
        assertEquals(340f, state.arrowRotation!!, 0.01f)
        assertNotNull(state.arrowRotation)
    }
}
