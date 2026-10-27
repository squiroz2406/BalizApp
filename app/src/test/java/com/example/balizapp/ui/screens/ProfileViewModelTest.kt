package com.example.balizapp.ui.screens

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.balizapp.R
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.UserRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.UserProfile
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.data.model.VehicleType
import com.example.balizapp.i18n.AppLocale
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {
    private val users = mockk<UserRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)

    private val profile = UserProfile(uid = "u1", displayName = "Sofi", email = "s@x.com")
    private val car = Vehicle(id = "v1", alias = "Auto", plate = "AB123CD")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppLocale.init(ApplicationProvider.getApplicationContext())
        every { users.observeProfile() } returns flowOf(profile)
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ProfileViewModel(users, vehicleRepo, parkingRepo)

    @Test fun `el estado combina perfil y vehiculos`() = kotlinx.coroutines.test.runTest {
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(false, state.loading)
            assertEquals(profile, state.profile)
            assertEquals(listOf(car), state.vehicles)
            assertNull(state.error)
        }
    }

    @Test fun `nombre en blanco devuelve error y no guarda`() {
        val vm = viewModel()
        val error = vm.saveVehicle("", VehicleType.CAR, "   ", "AB123CD")
        assertEquals(AppLocale.str(R.string.profile_error_vehicle_name_required), error)
        coVerify(exactly = 0) { vehicleRepo.save(any()) }
    }

    @Test fun `guarda con alias recortado y patente en mayusculas`() {
        val vm = viewModel()
        val error = vm.saveVehicle("v9", VehicleType.MOTORCYCLE, "  Moto  ", " ab12cd ")
        assertNull(error)
        coVerify {
            vehicleRepo.save(Vehicle(id = "v9", type = VehicleType.MOTORCYCLE, alias = "Moto", plate = "AB12CD"))
        }
    }

    @Test fun `patente vacia se guarda como null`() {
        viewModel().saveVehicle("", VehicleType.CAR, "Auto", "  ")
        coVerify { vehicleRepo.save(match { it.plate == null }) }
    }

    @Test fun `no borra un vehiculo con estacionamiento activo y avisa`() = kotlinx.coroutines.test.runTest {
        coEvery { parkingRepo.hasActiveParking("v1") } returns true
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.deleteVehicle("v1")
            var withError = awaitItem()
            while (withError.error == null) withError = awaitItem()
            assertEquals(AppLocale.str(R.string.profile_error_vehicle_active_parking), withError.error)
            coVerify(exactly = 0) { vehicleRepo.delete(any()) }

            vm.clearError()
            assertNull(awaitItem().error)
        }
    }

    @Test fun `borra un vehiculo sin estacionamiento activo`() {
        coEvery { parkingRepo.hasActiveParking("v1") } returns false
        viewModel().deleteVehicle("v1")
        coVerify { vehicleRepo.delete("v1") }
    }

    @Test fun `guardar minutos de aviso delega en el repositorio`() {
        viewModel().setReminderMinutes(30)
        coVerify { users.setReminderMinutes(30) }
    }

    @Test fun `si falla la escritura se informa el error`() = kotlinx.coroutines.test.runTest {
        coEvery { users.setReminderMinutes(any()) } throws RuntimeException("sin permisos")
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.setReminderMinutes(10)
            var withError = awaitItem()
            while (withError.error == null) withError = awaitItem()
            assertTrue(withError.error!!.contains("sin permisos"))
        }
    }
}
