package com.example.balizapp.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.balizapp.R
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.UserRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.data.model.UserProfile
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.notifications.ReminderScheduler
import com.google.firebase.Timestamp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val parkings = mockk<ParkingRepository>(relaxed = true)
    private val vehicles = mockk<VehicleRepository>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private val reminders = mockk<ReminderScheduler>(relaxed = true)
    private lateinit var app: Application

    private val expiresMillis = 1_800_000_000_000L
    private val car = Vehicle(id = "v1", alias = "Auto")
    private val active = Parking(
        id = "p1", vehicleId = "v1", addressText = "Calle 123",
        expiresAt = Timestamp(Date(expiresMillis)),
    )
    private val finished = Parking(id = "p0", vehicleId = "v1", status = ParkingStatus.FINISHED)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        AppLocale.init(app)
        every { parkings.observeActive() } returns flowOf(listOf(active))
        every { parkings.observeHistory() } returns flowOf(listOf(finished))
        every { vehicles.observeVehicles() } returns flowOf(listOf(car))
        every { users.observeProfile() } returns flowOf(UserProfile("u1", "Sofi", "s@x.com", reminderMinutes = 30))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(app, parkings, vehicles, users, reminders)

    @Test fun `el estado separa activos e historial e indexa los vehiculos`() = runTest {
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(false, state.loading)
            assertEquals(listOf(active), state.active)
            assertEquals(listOf(finished), state.history)
            assertEquals(mapOf("v1" to car), state.vehicles)
        }
    }

    @Test fun `programa un aviso por cada estacionamiento activo con vencimiento`() {
        val specs = slot<List<ReminderScheduler.Spec>>()
        every { reminders.sync(capture(specs)) } returns Unit
        viewModel()
        assertEquals(
            listOf(
                ReminderScheduler.Spec(
                    parkingId = "p1",
                    expiresAt = expiresMillis,
                    reminderMinutes = 30,
                    vehicleName = "Auto",
                    place = "Calle 123",
                )
            ),
            specs.captured,
        )
    }

    @Test fun `usa los minutos por defecto si no hay perfil`() {
        every { users.observeProfile() } returns flowOf(null)
        val specs = slot<List<ReminderScheduler.Spec>>()
        every { reminders.sync(capture(specs)) } returns Unit
        viewModel()
        assertEquals(UserProfile.DEFAULT_REMINDER_MINUTES, specs.captured.single().reminderMinutes)
    }

    @Test fun `si el vehiculo no existe usa el nombre generico`() {
        every { vehicles.observeVehicles() } returns flowOf(emptyList())
        val specs = slot<List<ReminderScheduler.Spec>>()
        every { reminders.sync(capture(specs)) } returns Unit
        viewModel()
        assertEquals(AppLocale.str(R.string.core_your_vehicle), specs.captured.single().vehicleName)
    }

    @Test fun `un estacionamiento sin vencimiento no genera aviso`() {
        every { parkings.observeActive() } returns flowOf(listOf(active.copy(expiresAt = null)))
        val specs = slot<List<ReminderScheduler.Spec>>()
        every { reminders.sync(capture(specs)) } returns Unit
        viewModel()
        assertTrue(specs.captured.isEmpty())
    }

    @Test fun `vuelve a sincronizar los avisos cuando cambia el perfil`() {
        val profile = MutableStateFlow<UserProfile?>(UserProfile("u1", null, null, reminderMinutes = 5))
        every { users.observeProfile() } returns profile
        val calls = mutableListOf<List<ReminderScheduler.Spec>>()
        every { reminders.sync(capture(calls)) } returns Unit
        viewModel()
        profile.value = UserProfile("u1", null, null, reminderMinutes = 15)
        assertEquals(listOf(5, 15), calls.map { it.single().reminderMinutes })
    }

    @Test fun `sin estacionamientos activos sincroniza una lista vacia para cancelar avisos`() {
        every { parkings.observeActive() } returns flowOf(emptyList())
        viewModel()
        verify { reminders.sync(emptyList()) }
    }
}
