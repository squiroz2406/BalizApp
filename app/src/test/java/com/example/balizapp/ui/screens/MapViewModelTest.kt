package com.example.balizapp.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapViewModelTest {
    private val locationRepo = mockk<LocationRepository>(relaxed = true)
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private lateinit var app: Application

    private val car = Vehicle(id = "v1", alias = "Auto")
    private val withLocation = Parking(id = "p1", vehicleId = "v1", location = GeoPoint(-34.6, -58.4))
    private val withoutLocation = Parking(id = "p2", vehicleId = "v1", location = null)
    private val here = GeoPosition(-34.61, -58.41)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        every { locationRepo.hasPermission() } returns false
        every { parkingRepo.observeActive() } returns flowOf(listOf(withLocation, withoutLocation))
        every { parkingRepo.observeHistory() } returns flowOf(listOf(withLocation, withoutLocation))
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MapViewModel(app, locationRepo, parkingRepo, vehicleRepo)

    @Test fun `solo muestra estacionamientos que tienen ubicacion`() = runTest {
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(listOf(withLocation), state.active)
            assertEquals(listOf(withLocation), state.history)
            assertEquals(mapOf("v1" to car), state.vehicles)
        }
    }

    @Test fun `sin permiso no pide la posicion`() = runTest {
        val vm = viewModel()
        coVerify(exactly = 0) { locationRepo.currentPosition(any()) }
        vm.state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertFalse(state.hasLocationPermission)
            assertNull(state.myPosition)
        }
    }

    @Test fun `con permiso al iniciar obtiene mi posicion`() = runTest {
        every { locationRepo.hasPermission() } returns true
        coEvery { locationRepo.currentPosition(any()) } returns here
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertTrue(state.hasLocationPermission)
            assertEquals(here, state.myPosition)
        }
    }

    @Test fun `al conceder el permiso actualiza el estado y busca la posicion`() = runTest {
        coEvery { locationRepo.currentPosition(any()) } returns here
        val vm = viewModel()
        vm.state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertFalse(state.hasLocationPermission)

            every { locationRepo.hasPermission() } returns true
            vm.onLocationPermissionResult(true)
            while (state.myPosition == null) state = awaitItem()
            assertTrue(state.hasLocationPermission)
            assertEquals(here, state.myPosition)
        }
    }

    @Test fun `si se deniega el permiso no busca la posicion`() = runTest {
        val vm = viewModel()
        vm.onLocationPermissionResult(false)
        coVerify(exactly = 0) { locationRepo.currentPosition(any()) }
    }

    @Test fun `si una lectura nueva no consigue posicion conserva la anterior`() = runTest {
        every { locationRepo.hasPermission() } returns true
        coEvery { locationRepo.currentPosition(any()) } returns here
        val vm = viewModel()
        vm.state.test {
            var state = awaitItem()
            while (state.myPosition == null) state = awaitItem()

            coEvery { locationRepo.currentPosition(any()) } returns null
            vm.refreshMyPosition()
            expectNoEvents()
            assertEquals(here, vm.state.value.myPosition)
        }
    }
}
