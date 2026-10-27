package com.example.balizapp.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.example.balizapp.R
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.PhotoRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.ui.screens.ParkingDetailScreen
import com.example.balizapp.ui.screens.ParkingDetailViewModel
import com.google.firebase.Timestamp
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class ParkingDetailScreenTest : ComposeScreenTest() {
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val photoRepo = mockk<PhotoRepository>(relaxed = true)

    private val car = Vehicle(id = "v1", alias = "Mi Gol", plate = "AB123CD")
    private val active = Parking(
        id = "p1", vehicleId = "v1", addressText = "Calle 123", note = "Cerca del kiosco",
        levelSector = "2B", ruleSummary = "Medido 2 h",
        expiresAt = Timestamp(Date(System.currentTimeMillis() + 3_600_000L)),
    )

    private var backs = 0
    private var returns = 0
    private var edits = 0

    @Before fun setUp() {
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car))
        every { parkingRepo.observeParking("p1") } returns flowOf(active)
    }

    private fun show() {
        val vm = ParkingDetailViewModel(SavedStateHandle(mapOf("parkingId" to "p1")), parkingRepo, vehicleRepo, photoRepo)
        rule.setContent {
            ParkingDetailScreen(vm, onBack = { backs++ }, onReturnToCar = { returns++ }, onEdit = { edits++ })
        }
    }

    private fun text(id: Int) = rule.onNodeWithText(str(id))
    private fun text(s: String) = rule.onNodeWithText(s)

    @Test fun `muestra el vehiculo y todos los datos del registro`() {
        show()
        text("Mi Gol").assertIsDisplayed()
        text("Calle 123").assertIsDisplayed()
        text("Cerca del kiosco").assertIsDisplayed()
        text("2B").assertIsDisplayed()
        text("Medido 2 h").assertIsDisplayed()
        text("AB123CD").assertIsDisplayed()
    }

    @Test fun `un estacionamiento activo ofrece volver, editar y retirar`() {
        show()
        text(R.string.detail_return_to_car).assertIsDisplayed()
        text(R.string.detail_edit).assertIsDisplayed()
        text(R.string.detail_finish).assertIsDisplayed()
        text(R.string.detail_delete_history).assertDoesNotExist()
    }

    @Test fun `volver al auto y editar avisan a la navegacion`() {
        show()
        text(R.string.detail_return_to_car).performClick()
        text(R.string.detail_edit).performClick()
        assertEquals(1, returns)
        assertEquals(1, edits)
    }

    @Test fun `ya lo retire pide confirmacion y recien despues lo marca`() {
        show()
        text(R.string.detail_finish).performClick()
        text(R.string.detail_confirm_finish_title).assertIsDisplayed()
        coVerify(exactly = 0) { parkingRepo.finish(any()) }
        text(R.string.detail_confirm_finish_ok).performClick()
        coVerify { parkingRepo.finish("p1") }
        text(R.string.detail_confirm_finish_title).assertDoesNotExist()
    }

    @Test fun `cancelar la confirmacion no retira el estacionamiento`() {
        show()
        text(R.string.detail_finish).performClick()
        text(R.string.detail_cancel).performClick()
        coVerify(exactly = 0) { parkingRepo.finish(any()) }
        text(R.string.detail_confirm_finish_title).assertDoesNotExist()
    }

    @Test fun `un registro del historial solo ofrece borrarlo`() {
        every { parkingRepo.observeParking("p1") } returns
            flowOf(active.copy(status = ParkingStatus.FINISHED, expiresAt = null, finishedAt = Timestamp.now()))
        show()
        text(R.string.detail_delete_history).assertIsDisplayed()
        text(R.string.detail_return_to_car).assertDoesNotExist()
        text(R.string.detail_finish).assertDoesNotExist()
    }

    @Test fun `borrar del historial pide confirmacion, borra y vuelve atras`() {
        every { parkingRepo.observeParking("p1") } returns
            flowOf(active.copy(status = ParkingStatus.FINISHED, expiresAt = null, finishedAt = Timestamp.now()))
        show()
        text(R.string.detail_delete_history).performClick()
        text(R.string.detail_confirm_delete_title).assertIsDisplayed()
        text(R.string.detail_confirm_delete_ok).performClick()
        coVerify { parkingRepo.delete("p1") }
        rule.waitForIdle()
        assertEquals(1, backs)
    }

    @Test fun `si el estacionamiento no existe lo explica`() {
        every { parkingRepo.observeParking("p1") } returns flowOf(null)
        show()
        text(R.string.detail_not_found_title).assertIsDisplayed()
        text(R.string.detail_return_to_car).assertDoesNotExist()
    }
}
