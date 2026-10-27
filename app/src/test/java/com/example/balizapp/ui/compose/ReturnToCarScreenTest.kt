package com.example.balizapp.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
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
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.ui.screens.ReturnToCarScreen
import com.example.balizapp.ui.screens.ReturnToCarViewModel
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.math.roundToInt

class ReturnToCarScreenTest : ComposeScreenTest() {
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val locationRepo = mockk<LocationRepository>(relaxed = true)
    private val routeRepo = mockk<RouteRepository>(relaxed = true)
    private val routeCache = mockk<RouteCache>(relaxed = true)
    private val compass = mockk<CompassSensor>(relaxed = true)

    // El auto esta ~222 m al sur de mi posicion.
    private val carPoint = GeoPoint(-34.602, -58.400)
    private val me = GeoPosition(-34.600, -58.400)
    private val car = Vehicle(id = "v1", alias = "Mi Gol", plate = "AB123CD")
    private val parking = Parking(
        id = "p1", vehicleId = "v1", location = carPoint,
        addressText = "Calle 123", note = "junto al kiosco", levelSector = "2B",
    )
    private val route = WalkingRoute(
        points = listOf(me, GeoPosition(-34.601, -58.400), GeoPosition(carPoint.latitude, carPoint.longitude)),
        distanceMeters = 230.0,
        durationSeconds = 160.0, // 2,7 min: se muestra "unos 3 min"
    )

    private var backs = 0
    private var finishedCalls = 0

    @Before fun setUp() {
        every { parkingRepo.observeParking("p1") } returns flowOf(parking)
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car))
        every { locationRepo.hasPermission() } returns true
        every { locationRepo.positionUpdates(any()) } returns flowOf(me)
        every { compass.isAvailable } returns false
        every { routeCache.load(any(), any()) } returns null
    }

    private fun show() {
        val vm = ReturnToCarViewModel(
            app, SavedStateHandle(mapOf("parkingId" to "p1")),
            parkingRepo, vehicleRepo, locationRepo, routeRepo, routeCache, compass,
        )
        rule.setContent { ReturnToCarScreen(vm, onBack = { backs++ }, onFinished = { finishedCalls++ }) }
    }

    private fun text(id: Int) = rule.onNodeWithText(str(id))
    private fun text(s: String) = rule.onNodeWithText(s)

    private fun distanceText() = "${RouteRepository.distance(me, GeoPosition(carPoint.latitude, carPoint.longitude)).roundToInt()} m"

    // --- Estado general ---

    @Test fun `muestra el titulo y vuelve atras con la flecha`() {
        show()
        text(R.string.nav_title).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.core_back)).performClick()
        assertEquals(1, backs)
    }

    @Test fun `un estacionamiento sin ubicacion guardada lo explica`() {
        every { parkingRepo.observeParking("p1") } returns flowOf(parking.copy(location = null))
        show()
        text(R.string.nav_not_found_title).assertIsDisplayed()
        text(R.string.nav_finish).assertDoesNotExist()
    }

    @Test fun `muestra los datos para reconocer el auto`() {
        show()
        text("Mi Gol · AB123CD").assertIsDisplayed()
        text(str(R.string.nav_level_sector, "2B")).assertIsDisplayed()
        text("Calle 123 · junto al kiosco").assertIsDisplayed()
    }

    // --- Modo directo ---

    @Test fun `con GPS muestra la distancia hasta el auto`() {
        show()
        text(distanceText()).assertIsDisplayed()
    }

    @Test fun `sin brujula indica el rumbo en palabras`() {
        show()
        text(str(R.string.nav_guide_no_compass, str(R.string.nav_cardinal_s))).assertIsDisplayed()
    }

    @Test fun `con brujula confiable explica como seguir la flecha`() {
        every { compass.isAvailable } returns true
        every { compass.readings() } returns flowOf(CompassReading(azimuthDegrees = 0f, unreliable = false))
        show()
        text(str(R.string.nav_guide_compass, str(R.string.nav_cardinal_s))).assertIsDisplayed()
    }

    @Test fun `con brujula sin calibrar pide calibrarla`() {
        every { compass.isAvailable } returns true
        every { compass.readings() } returns flowOf(CompassReading(azimuthDegrees = 0f, unreliable = true))
        show()
        text(R.string.nav_guide_calibrate).assertIsDisplayed()
    }

    @Test fun `mientras espera el GPS lo avisa`() {
        every { locationRepo.positionUpdates(any()) } returns emptyFlow()
        show()
        text(R.string.nav_searching_position).assertIsDisplayed()
        text(R.string.nav_guide_waiting_gps).assertIsDisplayed()
    }

    @Test fun `cerca del auto avisa que llego`() {
        every { locationRepo.positionUpdates(any()) } returns flowOf(GeoPosition(-34.60195, -58.400))
        show()
        text(R.string.nav_arrived).assertIsDisplayed()
        text(str(R.string.nav_guide_arrived, 15)).assertIsDisplayed()
    }

    @Test fun `con poca precision de GPS lo advierte`() {
        every { locationRepo.positionUpdates(any()) } returns flowOf(me.copy(accuracyMeters = 50f))
        show()
        text(str(R.string.nav_gps_low_accuracy, 50)).assertIsDisplayed()
    }

    @Test fun `sin permiso de ubicacion ofrece permitirla`() {
        every { locationRepo.hasPermission() } returns false
        show()
        text(R.string.nav_no_location).assertIsDisplayed()
        text(R.string.nav_guide_no_permission).assertIsDisplayed()
        text(R.string.nav_allow_location).assertIsDisplayed()
    }

    // --- Modo por calles ---

    @Test fun `el modo por calles muestra el resumen del recorrido y la atribucion`() {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } returns route
        show()
        text(R.string.nav_mode_streets).performClick()
        text(str(R.string.nav_route_summary, 3)).assertIsDisplayed()
        text(R.string.nav_guide_follow_blue_line).assertIsDisplayed()
        rule.onNode(hasText("OSRM", substring = true)).assertIsDisplayed()
        text(R.string.nav_recalculate).assertIsDisplayed()
    }

    @Test fun `en modo por calles la flecha se reemplaza por la linea y vuelve en modo directo`() {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } returns route
        show()
        text(R.string.nav_mode_streets).performClick()
        text(R.string.nav_mode_direct).performClick()
        text(R.string.nav_guide_follow_blue_line).assertDoesNotExist()
        text(str(R.string.nav_guide_no_compass, str(R.string.nav_cardinal_s))).assertIsDisplayed()
    }

    @Test fun `si falla la ruta por calles explica que se muestra la linea recta`() {
        coEvery { routeRepo.walkingRoute(any(), any(), any()) } throws IOException("sin red")
        show()
        text(R.string.nav_mode_streets).performClick()
        text(R.string.nav_route_error_offline).assertIsDisplayed()
    }

    // --- Ya lo retiré ---

    @Test fun `ya lo retire marca el estacionamiento y avisa a la navegacion`() {
        show()
        text(R.string.nav_finish).performClick()
        coVerify { parkingRepo.finish("p1") }
        rule.waitForIdle()
        assertEquals(1, finishedCalls)
    }

    @Test fun `si marcar como retirado falla muestra el error y no navega`() {
        coEvery { parkingRepo.finish(any()) } throws RuntimeException("sin permisos")
        show()
        text(R.string.nav_finish).performClick()
        text(str(R.string.nav_finish_error, "sin permisos")).assertIsDisplayed()
        assertEquals(0, finishedCalls)
    }
}
