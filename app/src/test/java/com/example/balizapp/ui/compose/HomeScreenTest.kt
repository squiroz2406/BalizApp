package com.example.balizapp.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.balizapp.R
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.UserRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.data.model.VehicleType
import com.example.balizapp.notifications.ReminderScheduler
import com.example.balizapp.ui.screens.HomeScreen
import com.example.balizapp.ui.screens.HomeViewModel
import com.google.firebase.Timestamp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class HomeScreenTest : ComposeScreenTest() {
    private val parkings = mockk<ParkingRepository>(relaxed = true)
    private val vehicles = mockk<VehicleRepository>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private val reminders = mockk<ReminderScheduler>(relaxed = true)

    private val car = Vehicle(id = "v1", type = VehicleType.CAR, alias = "Mi Gol")
    private val moto = Vehicle(id = "v2", type = VehicleType.MOTORCYCLE, alias = "Mi Moto")
    private val inOneHour get() = Timestamp(Date(System.currentTimeMillis() + 3_600_000L))
    private val anHourAgo get() = Timestamp(Date(System.currentTimeMillis() - 3_600_000L))

    private val current = Parking(id = "p1", vehicleId = "v1", addressText = "Calle Vigente 100", expiresAt = inOneHour)
    private val expired = Parking(id = "p2", vehicleId = "v2", addressText = "Calle Vencida 200", expiresAt = anHourAgo)
    private val finished = Parking(
        id = "p3", vehicleId = "v1", status = ParkingStatus.FINISHED, addressText = "Calle Historica 300",
    )

    private var parkHere = 0
    private val opened = mutableListOf<String>()

    @Before fun setUp() {
        every { vehicles.observeVehicles() } returns flowOf(listOf(car, moto))
        every { users.observeProfile() } returns flowOf(null)
        every { parkings.observeActive() } returns flowOf(listOf(current, expired))
        every { parkings.observeHistory() } returns flowOf(listOf(finished))
    }

    private fun show() {
        val vm = HomeViewModel(app, parkings, vehicles, users, reminders)
        rule.setContent { HomeScreen(vm, onParkHere = { parkHere++ }, onOpenParking = { opened += it }) }
    }

    private fun text(id: Int) = rule.onNodeWithText(str(id))
    private fun text(s: String) = rule.onNodeWithText(s)

    /**
     * El boton lleva espacios antes del texto (se busca por fragmento) y el mismo texto aparece en el
     * mensaje del estado vacio: se distingue porque el boton es el que se puede tocar.
     */
    private fun parkHereButton() =
        rule.onNode(hasText(str(R.string.home_park_here), substring = true) and hasClickAction())

    /** El nombre del vehiculo aparece en el filtro (primero) y en la tarjeta: se toca el filtro. */
    private fun vehicleChip(alias: String) = rule.onAllNodesWithText(alias).onFirst()

    @Test fun `sin estacionamientos muestra el estado vacio y el boton para estacionar`() {
        every { parkings.observeActive() } returns flowOf(emptyList())
        every { parkings.observeHistory() } returns flowOf(emptyList())
        show()
        text(R.string.home_empty_title).assertIsDisplayed()
        parkHereButton().assertIsDisplayed()
    }

    @Test fun `el boton estacione aqui avisa a la navegacion`() {
        show()
        parkHereButton().performClick()
        assertEquals(1, parkHere)
    }

    @Test fun `muestra activos e historial con sus direcciones`() {
        show()
        text(R.string.home_section_active).assertIsDisplayed()
        text("Calle Vigente 100").assertIsDisplayed()
        text("Calle Vencida 200").assertIsDisplayed()
        text(R.string.home_section_history).assertIsDisplayed()
        text("Calle Historica 300").assertIsDisplayed()
    }

    @Test fun `tocar un estacionamiento activo lo abre`() {
        show()
        text("Calle Vigente 100").performClick()
        assertEquals(listOf("p1"), opened)
    }

    @Test fun `tocar un item del historial lo abre`() {
        show()
        text("Calle Historica 300").performClick()
        assertEquals(listOf("p3"), opened)
    }

    @Test fun `el filtro Vencidos deja solo los vencidos`() {
        show()
        text(R.string.home_filter_status_expired).performClick()
        text("Calle Vencida 200").assertIsDisplayed()
        text("Calle Vigente 100").assertDoesNotExist()
        text("Calle Historica 300").assertDoesNotExist()
    }

    @Test fun `el filtro Actuales oculta los vencidos y el historial`() {
        show()
        text(R.string.home_filter_status_current).performClick()
        text("Calle Vigente 100").assertIsDisplayed()
        text("Calle Vencida 200").assertDoesNotExist()
        text("Calle Historica 300").assertDoesNotExist()
    }

    @Test fun `el filtro Finalizados muestra solo el historial`() {
        show()
        text(R.string.home_filter_status_finished).performClick()
        text("Calle Historica 300").assertIsDisplayed()
        text("Calle Vigente 100").assertDoesNotExist()
        text(R.string.home_section_active).assertDoesNotExist()
    }

    @Test fun `filtrar por vehiculo muestra solo sus estacionamientos`() {
        show()
        vehicleChip("Mi Moto").performClick()
        text("Calle Vencida 200").assertIsDisplayed()
        text("Calle Vigente 100").assertDoesNotExist()
        text("Calle Historica 300").assertDoesNotExist()
    }

    @Test fun `volver a Todos los vehiculos quita el filtro`() {
        show()
        vehicleChip("Mi Moto").performClick()
        text(R.string.home_filter_all_vehicles).performClick()
        text("Calle Vigente 100").assertIsDisplayed()
        text("Calle Vencida 200").assertIsDisplayed()
    }

    @Test fun `combinar filtros sin coincidencias muestra el mensaje de sin resultados`() {
        show()
        vehicleChip("Mi Moto").performClick()
        text(R.string.home_filter_status_current).performClick()
        text(R.string.home_no_results).assertIsDisplayed()
    }

    @Test fun `un historial vacio explica cuando aparecen los retirados`() {
        every { parkings.observeHistory() } returns flowOf(emptyList())
        show()
        text(R.string.home_history_empty).assertIsDisplayed()
    }
}
