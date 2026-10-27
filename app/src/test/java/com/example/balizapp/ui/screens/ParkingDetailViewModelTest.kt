package com.example.balizapp.ui.screens

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.balizapp.R
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.PhotoKind
import com.example.balizapp.data.PhotoRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.ui.components.toImageBitmap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
class ParkingDetailViewModelTest {
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val photoRepo = mockk<PhotoRepository>(relaxed = true)

    private val car = Vehicle(id = "v1", alias = "Auto")
    private val parking = Parking(id = "p1", vehicleId = "v1")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppLocale.init(ApplicationProvider.getApplicationContext())
        every { parkingRepo.observeParking("p1") } returns flowOf(parking)
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car))
    }

    @After fun tearDown() {
        unmockkStatic("com.example.balizapp.ui.components.PhotoComponentsKt")
        Dispatchers.resetMain()
    }

    private fun viewModel() = ParkingDetailViewModel(
        SavedStateHandle(mapOf("parkingId" to "p1")), parkingRepo, vehicleRepo, photoRepo,
    )

    @Test fun `el estado trae el estacionamiento y su vehiculo`() = runTest {
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(parking, state.parking)
            assertEquals(car, state.vehicle)
            assertFalse(state.working)
            assertNull(state.error)
        }
    }

    @Test fun `si el vehiculo no esta en la lista queda sin vehiculo`() = runTest {
        every { vehicleRepo.observeVehicles() } returns flowOf(emptyList())
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(parking, state.parking)
            assertNull(state.vehicle)
        }
    }

    @Test fun `si el estacionamiento no existe el estado queda sin datos`() = runTest {
        every { parkingRepo.observeParking("p1") } returns flowOf(null)
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertNull(state.parking)
            assertNull(state.vehicle)
        }
    }

    @Test fun `finish marca el estacionamiento como retirado`() {
        viewModel().finish()
        coVerify { parkingRepo.finish("p1") }
    }

    @Test fun `delete borra y cierra la pantalla`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.delete()
            var state = awaitItem()
            while (!state.closed) state = awaitItem()
            coVerify { parkingRepo.delete("p1") }
            assertFalse(state.working)
        }
    }

    @Test fun `si falla el borrado informa el error y no cierra`() = runTest {
        coEvery { parkingRepo.delete(any()) } throws RuntimeException("sin red")
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.delete()
            var state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertEquals("${AppLocale.str(R.string.detail_error_delete)}: sin red", state.error)
            assertFalse(state.closed)
        }
    }

    @Test fun `si falla finish informa el error y deja de estar ocupado`() = runTest {
        coEvery { parkingRepo.finish(any()) } throws RuntimeException("sin red")
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.finish()
            var state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertTrue(state.error!!.startsWith(AppLocale.str(R.string.detail_error_finish)))
            assertFalse(state.working)
        }
    }

    @Test fun `carga solo las fotos cuyas rutas existen`() = runTest {
        mockkStatic("com.example.balizapp.ui.components.PhotoComponentsKt")
        val bitmap = mockk<ImageBitmap>()
        val bytes = byteArrayOf(1, 2, 3)
        every { parkingRepo.observeParking("p1") } returns
            flowOf(parking.copy(placePhotoPath = "users/u/parkings/p1/photos/place"))
        coEvery { photoRepo.load("p1", PhotoKind.PLACE) } returns bytes
        coEvery { any<ByteArray>().toImageBitmap() } returns bitmap

        viewModel().state.test {
            var state = awaitItem()
            while (state.photos.isEmpty()) state = awaitItem()
            assertEquals(mapOf(PhotoKind.PLACE to bitmap), state.photos)
        }
        coVerify(exactly = 0) { photoRepo.load("p1", PhotoKind.SIGN) }
    }

    @Test fun `si la foto falla al cargar no rompe el detalle`() = runTest {
        every { parkingRepo.observeParking("p1") } returns
            flowOf(parking.copy(signPhotoPath = "users/u/parkings/p1/photos/sign"))
        coEvery { photoRepo.load("p1", PhotoKind.SIGN) } throws RuntimeException("sin red")
        viewModel().state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(parking.copy(signPhotoPath = "users/u/parkings/p1/photos/sign"), state.parking)
            assertTrue(state.photos.isEmpty())
        }
    }
}
