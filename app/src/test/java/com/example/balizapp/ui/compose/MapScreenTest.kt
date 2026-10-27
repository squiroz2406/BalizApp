package com.example.balizapp.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.balizapp.R
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.ui.screens.MapScreen
import com.example.balizapp.ui.screens.MapViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test

class MapScreenTest : ComposeScreenTest() {
    private val locationRepo = mockk<LocationRepository>(relaxed = true)
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)

    @Before fun setUp() {
        every { locationRepo.hasPermission() } returns false
        every { parkingRepo.observeActive() } returns flowOf(emptyList())
        every { parkingRepo.observeHistory() } returns flowOf(emptyList())
        every { vehicleRepo.observeVehicles() } returns flowOf(emptyList())
    }

    private fun show() {
        val vm = MapViewModel(app, locationRepo, parkingRepo, vehicleRepo)
        rule.setContent { MapScreen(vm, onOpenParking = {}) }
    }

    @Test fun `muestra el titulo del mapa`() {
        show()
        rule.onNodeWithText(str(R.string.map_title)).assertIsDisplayed()
    }

    @Test fun `sin permiso invita a permitir la ubicacion y no ofrece centrar`() {
        show()
        rule.onNodeWithText(str(R.string.map_allow_location_prompt)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.map_allow_location)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.map_center_on_me)).assertDoesNotExist()
    }

    @Test fun `con permiso y posicion ofrece centrar y vuelve a leer la ubicacion al tocarlo`() {
        every { locationRepo.hasPermission() } returns true
        coEvery { locationRepo.currentPosition(any()) } returns GeoPosition(-34.6, -58.4)
        show()
        rule.onNodeWithText(str(R.string.map_allow_location_prompt)).assertDoesNotExist()
        rule.onNodeWithContentDescription(str(R.string.map_center_on_me)).assertIsDisplayed().performClick()
        coVerify(atLeast = 2) { locationRepo.currentPosition(any()) }
    }

    @Test fun `con permiso pero sin posicion todavia no ofrece centrar`() {
        every { locationRepo.hasPermission() } returns true
        coEvery { locationRepo.currentPosition(any()) } returns null
        show()
        rule.onNodeWithContentDescription(str(R.string.map_center_on_me)).assertDoesNotExist()
    }
}
