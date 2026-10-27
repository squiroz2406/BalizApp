package com.example.balizapp.ui.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.example.balizapp.R
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.ParkingVerdict
import com.example.balizapp.data.PhotoKind
import com.example.balizapp.data.PhotoRepository
import com.example.balizapp.data.SignAnalysis
import com.example.balizapp.data.SignAnalyzer
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.ui.components.precacheTilesAround
import com.example.balizapp.ui.components.toImageBitmap
import com.example.balizapp.ui.screens.ParkingFormScreen
import com.example.balizapp.ui.screens.ParkingFormViewModel
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

// Modo grafico nativo: hace falta para crear un ImageBitmap real (las fotos del formulario).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParkingFormScreenTest : ComposeScreenTest() {
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val locationRepo = mockk<LocationRepository>(relaxed = true)
    private val photoRepo = mockk<PhotoRepository>(relaxed = true)
    private val analyzer = mockk<SignAnalyzer>(relaxed = true)

    private val car = Vehicle(id = "v1", alias = "Mi Gol")
    private val moto = Vehicle(id = "v2", alias = "Mi Moto")
    private val here = GeoPosition(-34.6, -58.4)

    private var backs = 0
    private var toProfile = 0
    private val saved = mutableListOf<Pair<String, Boolean>>()

    @Before fun setUp() {
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car, moto))
        every { parkingRepo.observeActive() } returns flowOf(emptyList())
        every { locationRepo.hasPermission() } returns false
        coEvery { locationRepo.addressFor(any(), any()) } returns null
        mockkStatic("com.example.balizapp.ui.components.OfflineTilesKt")
        every { precacheTilesAround(any(), any(), any()) } returns Unit
        mockkStatic("com.example.balizapp.ui.components.PhotoComponentsKt")
        coEvery { any<ByteArray>().toImageBitmap() } returns ImageBitmap(1, 1)
    }

    private fun show(parkingId: String? = null) {
        val handle = SavedStateHandle(if (parkingId == null) emptyMap() else mapOf("parkingId" to parkingId))
        val vm = ParkingFormViewModel(app, handle, parkingRepo, vehicleRepo, locationRepo, photoRepo, analyzer)
        rule.setContent {
            ParkingFormScreen(
                vm, onBack = { backs++ }, onGoToProfile = { toProfile++ },
                onSaved = { id, wasEdit -> saved += id to wasEdit },
            )
        }
    }

    private fun withGps() {
        every { locationRepo.hasPermission() } returns true
        coEvery { locationRepo.currentPosition(any()) } returns here
    }

    private fun text(id: Int) = rule.onNodeWithText(str(id))
    private fun text(s: String) = rule.onNodeWithText(s)

    // --- Estructura ---

    @Test fun `un alta muestra el titulo de nuevo estacionamiento y el boton de guardar`() {
        show()
        text(R.string.form_title_new).assertIsDisplayed()
        text(R.string.form_save_parking).assertIsDisplayed()
    }

    @Test fun `sin vehiculos pide cargar uno y lleva a Perfil`() {
        every { vehicleRepo.observeVehicles() } returns flowOf(emptyList())
        show()
        text(R.string.form_no_vehicles_title).assertIsDisplayed()
        text(R.string.form_save_parking).assertDoesNotExist()
        text(R.string.form_go_to_profile).performClick()
        assertEquals(1, toProfile)
    }

    // --- Ubicación ---

    @Test fun `sin permiso explica como marcar la ubicacion`() {
        show()
        text(R.string.form_no_permission_hint).performScrollTo().assertIsDisplayed()
    }

    @Test fun `con GPS muestra la direccion y el origen de la ubicacion`() {
        withGps()
        coEvery { locationRepo.addressFor(here.latitude, here.longitude) } returns "Calle 123"
        show()
        text("Calle 123 · ${str(R.string.form_source_gps)}").performScrollTo().assertIsDisplayed()
    }

    // --- Vehículo y límite de tiempo ---

    @Test fun `se puede elegir otro vehiculo y se guarda ese`() {
        withGps()
        val created = slot<Parking>()
        coEvery { parkingRepo.create(capture(created)) } returns "nuevo"
        show()
        text("Mi Moto").performScrollTo().performClick()
        text(R.string.form_save_parking).performClick()
        assertEquals("v2", created.captured.vehicleId)
    }

    @Test fun `por defecto no hay vencimiento y un preset lo agrega`() {
        show()
        text(R.string.form_no_expiry_info).performScrollTo().assertIsDisplayed()
        text("1 h").performScrollTo().performClick()
        rule.onNode(hasText("Vence:", substring = true)).assertIsDisplayed()
        text(R.string.form_no_expiry_info).assertDoesNotExist()
    }

    @Test fun `sin limite quita el vencimiento elegido`() {
        show()
        text("1 h").performScrollTo().performClick()
        text(R.string.form_no_limit).performScrollTo().performClick()
        text(R.string.form_no_expiry_info).performScrollTo().assertIsDisplayed()
    }

    // --- Guardar ---

    @Test fun `guardar sin ubicacion muestra el error y no guarda`() {
        show()
        text(R.string.form_save_parking).performClick()
        text(R.string.form_err_mark_map).performScrollTo().assertIsDisplayed()
        coVerify(exactly = 0) { parkingRepo.create(any()) }
        assertEquals(emptyList<Pair<String, Boolean>>(), saved)
    }

    @Test fun `los textos escritos se guardan y la pantalla avisa que termino`() {
        withGps()
        val created = slot<Parking>()
        coEvery { parkingRepo.create(capture(created)) } returns "nuevo"
        show()
        text(R.string.form_note_label).performScrollTo().performTextInput("Cerca del kiosco")
        text(R.string.form_level_label).performScrollTo().performTextInput("2b")
        text(R.string.form_save_parking).performClick()
        assertEquals("Cerca del kiosco", created.captured.note)
        // El campo de piso pide mayusculas al teclado; lo escrito llega tal cual lo ingresa el usuario.
        assertEquals("2b", created.captured.levelSector)
        rule.waitForIdle() // onSaved se llama desde un efecto de Compose, despues de guardar
        assertEquals(listOf("nuevo" to false), saved)
    }

    @Test fun `si guardar falla muestra el error en pantalla`() {
        withGps()
        coEvery { parkingRepo.create(any()) } throws RuntimeException("sin red")
        show()
        text(R.string.form_save_parking).performClick()
        text(str(R.string.form_err_save, "sin red")).performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<Pair<String, Boolean>>(), saved)
    }

    // --- Edición ---

    private val existing = Parking(
        id = "p1", vehicleId = "v1", location = GeoPoint(-34.7, -58.5),
        note = "nota existente", levelSector = "3C", ruleSummary = "Libre",
    )

    @Test fun `editar carga los datos y ofrece guardar cambios`() {
        coEvery { parkingRepo.getParking("p1") } returns existing
        show("p1")
        text(R.string.form_title_edit).assertIsDisplayed()
        text(R.string.form_save_changes).assertIsDisplayed()
        text("nota existente").performScrollTo().assertIsDisplayed()
        text("3C").performScrollTo().assertIsDisplayed()
    }

    @Test fun `guardar una edicion avisa que fue una edicion`() {
        coEvery { parkingRepo.getParking("p1") } returns existing
        show("p1")
        text(R.string.form_save_changes).performClick()
        coVerify { parkingRepo.update(any()) }
        rule.waitForIdle()
        assertEquals(listOf("p1" to true), saved)
    }

    // --- IA ---

    @Test fun `sin fotos no se ofrecen los botones de IA`() {
        show()
        text(R.string.form_describe_place_ai).assertDoesNotExist()
        text(R.string.form_read_sign_ai).assertDoesNotExist()
    }

    @Test fun `con foto del lugar se puede describir con IA y la nota se completa`() {
        coEvery { parkingRepo.getParking("p1") } returns existing.copy(placePhotoPath = "ruta/place", note = null)
        coEvery { photoRepo.load("p1", PhotoKind.PLACE) } returns byteArrayOf(1)
        coEvery { analyzer.describePlace(any(), any()) } returns "Frente a la farmacia"
        show("p1")
        text(R.string.form_describe_place_ai).performScrollTo().performClick()
        text("Frente a la farmacia").performScrollTo().assertIsDisplayed()
    }

    @Test fun `con foto del cartel se lee con IA y se muestra el veredicto`() {
        coEvery { parkingRepo.getParking("p1") } returns existing.copy(signPhotoPath = "ruta/sign")
        coEvery { photoRepo.load("p1", PhotoKind.SIGN) } returns byteArrayOf(1)
        coEvery { analyzer.analyzeSign(any(), any()) } returns SignAnalysis(
            ruleSummary = "Medido 2 h", verdict = ParkingVerdict.FORBIDDEN, maxMinutes = null,
            allowedUntil = null, explanation = "Es zona de carga", confidence = "high",
        )
        show("p1")
        text(R.string.form_read_sign_ai).performScrollTo().performClick()
        text(R.string.form_verdict_forbidden).performScrollTo().assertIsDisplayed()
        text("Es zona de carga").performScrollTo().assertIsDisplayed()
    }

    @Test fun `si la IA falla el error se ve en pantalla`() {
        coEvery { parkingRepo.getParking("p1") } returns existing.copy(signPhotoPath = "ruta/sign")
        coEvery { photoRepo.load("p1", PhotoKind.SIGN) } returns byteArrayOf(1)
        coEvery { analyzer.analyzeSign(any(), any()) } throws RuntimeException("Quota exceeded")
        show("p1")
        text(R.string.form_read_sign_ai).performScrollTo().performClick()
        text(R.string.form_err_ai_quota).performScrollTo().assertIsDisplayed()
    }
}
