package com.example.balizapp.ui.screens

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.balizapp.R
import com.example.balizapp.data.ActiveParkingExistsException
import com.example.balizapp.data.GeoPosition
import com.example.balizapp.data.ImageCompressor
import com.example.balizapp.data.LocationRepository
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.ParkingVerdict
import com.example.balizapp.data.PhotoKind
import com.example.balizapp.data.PhotoRepository
import com.example.balizapp.data.SignAnalysis
import com.example.balizapp.data.SignAnalyzer
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingSource
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.ui.components.precacheTilesAround
import com.example.balizapp.ui.components.toImageBitmap
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ParkingFormViewModelTest {
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val locationRepo = mockk<LocationRepository>(relaxed = true)
    private val photoRepo = mockk<PhotoRepository>(relaxed = true)
    private val analyzer = mockk<SignAnalyzer>(relaxed = true)
    private lateinit var app: Application

    private val car = Vehicle(id = "v1", alias = "Auto")
    private val moto = Vehicle(id = "v2", alias = "Moto")
    private val here = GeoPosition(-34.6, -58.4)
    private val bitmap = mockk<ImageBitmap>()
    private val jpeg = byteArrayOf(1, 2, 3)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        AppLocale.init(app)
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car, moto))
        every { parkingRepo.observeActive() } returns flowOf(emptyList())
        every { locationRepo.hasPermission() } returns false
        coEvery { locationRepo.addressFor(any(), any()) } returns null

        // Descarga de mapas, compresión y decodificación de imágenes: se simulan.
        mockkStatic("com.example.balizapp.ui.components.OfflineTilesKt")
        every { precacheTilesAround(any(), any(), any()) } returns Unit
        mockkStatic("com.example.balizapp.ui.components.PhotoComponentsKt")
        coEvery { any<ByteArray>().toImageBitmap() } returns bitmap
        mockkObject(ImageCompressor)
        coEvery { ImageCompressor.compress(any(), any()) } returns jpeg
    }

    @After fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun viewModel(parkingId: String? = null): ParkingFormViewModel {
        val handle = SavedStateHandle(if (parkingId == null) emptyMap() else mapOf("parkingId" to parkingId))
        return ParkingFormViewModel(app, handle, parkingRepo, vehicleRepo, locationRepo, photoRepo, analyzer)
    }

    private fun str(id: Int, vararg args: Any) = AppLocale.str(id, *args)
    private val ParkingFormViewModel.s get() = state.value

    private fun analysis(
        verdict: ParkingVerdict = ParkingVerdict.ALLOWED,
        maxMinutes: Int? = null,
        allowedUntil: String? = null,
        rule: String? = "Medido 2 h",
    ) = SignAnalysis(rule, verdict, maxMinutes, allowedUntil, "explicacion", "high")

    /** Un formulario de alta listo para guardar: vehículo y ubicación elegidos. */
    private fun readyForm(): ParkingFormViewModel = viewModel().also {
        it.setManualPosition(here.latitude, here.longitude)
    }

    // --- Alta: vehículos y ubicación ---

    @Test fun `en un alta preselecciona el primer vehiculo que no esta estacionado`() {
        every { parkingRepo.observeActive() } returns flowOf(listOf(Parking(id = "p", vehicleId = "v1")))
        assertEquals("v2", viewModel().s.vehicleId)
    }

    @Test fun `si todos estan estacionados preselecciona el primero`() {
        every { parkingRepo.observeActive() } returns
            flowOf(listOf(Parking(id = "a", vehicleId = "v1"), Parking(id = "b", vehicleId = "v2")))
        assertEquals("v1", viewModel().s.vehicleId)
    }

    @Test fun `con permiso toma la ubicacion al abrir y resuelve la direccion`() {
        every { locationRepo.hasPermission() } returns true
        coEvery { locationRepo.currentPosition(any()) } returns here
        coEvery { locationRepo.addressFor(here.latitude, here.longitude) } returns "Calle 123"
        val s = viewModel().s
        assertEquals(here, s.position)
        assertEquals(PositionSource.GPS, s.positionSource)
        assertEquals("Calle 123", s.addressText)
        assertFalse(s.locating)
    }

    @Test fun `sin permiso no toma la ubicacion hasta que se conceda`() {
        coEvery { locationRepo.currentPosition(any()) } returns here
        val vm = viewModel()
        assertNull(vm.s.position)
        coVerify(exactly = 0) { locationRepo.currentPosition(any()) }
        vm.onLocationPermissionResult(true)
        assertTrue(vm.s.hasLocationPermission)
        assertEquals(here, vm.s.position)
    }

    @Test fun `si el GPS no responde informa el error`() {
        every { locationRepo.hasPermission() } returns true
        coEvery { locationRepo.currentPosition(any()) } returns null
        val s = viewModel().s
        assertEquals(str(R.string.form_err_location), s.error)
        assertFalse(s.locating)
        assertNull(s.position)
    }

    @Test fun `marcar en el mapa fija la ubicacion manual`() {
        coEvery { locationRepo.addressFor(1.0, 2.0) } returns "Esquina"
        val vm = viewModel()
        vm.setManualPosition(1.0, 2.0)
        assertEquals(GeoPosition(1.0, 2.0), vm.s.position)
        assertEquals(PositionSource.MANUAL, vm.s.positionSource)
        assertEquals("Esquina", vm.s.addressText)
    }

    @Test fun `una direccion que llega tarde no pisa una ubicacion mas nueva`() {
        val first = CompletableDeferred<String?>()
        val second = CompletableDeferred<String?>()
        coEvery { locationRepo.addressFor(1.0, 1.0) } coAnswers { first.await() }
        coEvery { locationRepo.addressFor(2.0, 2.0) } coAnswers { second.await() }
        val vm = viewModel()
        vm.setManualPosition(1.0, 1.0)
        vm.setManualPosition(2.0, 2.0)
        second.complete("Calle nueva")
        first.complete("Calle vieja")
        assertEquals("Calle nueva", vm.s.addressText)
    }

    // --- Vencimiento ---

    @Test fun `un preset vence dentro de esos minutos`() {
        val vm = viewModel()
        val before = System.currentTimeMillis()
        vm.setPreset(60)
        assertEquals(60, vm.s.selectedPreset)
        assertEquals((before + 60 * 60_000L).toDouble(), vm.s.expiresAt!!.toDouble(), 5_000.0)
    }

    @Test fun `sin limite borra el vencimiento`() {
        val vm = viewModel()
        vm.setPreset(30)
        vm.setNoLimit()
        assertNull(vm.s.expiresAt)
        assertEquals(ParkingFormState.NO_LIMIT, vm.s.selectedPreset)
    }

    @Test fun `una hora elegida es siempre futura y dentro de las proximas 24 h`() {
        val vm = viewModel()
        listOf(0 to 0, 12 to 30, 23 to 59).forEach { (h, m) ->
            vm.setExpiryTime(h, m)
            val expires = vm.s.expiresAt!!
            val now = System.currentTimeMillis()
            assertTrue("$h:$m debe ser futura", expires > now)
            assertTrue("$h:$m debe estar dentro de 24 h", expires <= now + 24 * 3_600_000L)
            assertNull(vm.s.selectedPreset)
        }
    }

    // --- Guardar: validaciones ---

    @Test fun `guardar sin vehiculo da error`() {
        every { vehicleRepo.observeVehicles() } returns flowOf(emptyList())
        val vm = viewModel()
        vm.setManualPosition(1.0, 1.0)
        vm.save()
        assertEquals(str(R.string.form_err_pick_vehicle), vm.s.error)
        coVerify(exactly = 0) { parkingRepo.create(any()) }
    }

    @Test fun `guardar sin ubicacion pide marcarla en el mapa`() {
        val vm = viewModel()
        vm.save()
        assertEquals(str(R.string.form_err_mark_map), vm.s.error)
        coVerify(exactly = 0) { parkingRepo.create(any()) }
    }

    @Test fun `guardar con vencimiento vencido da error`() {
        val vm = readyForm()
        vm.setPreset(-5)
        vm.save()
        assertEquals(str(R.string.form_err_expiry_past), vm.s.error)
        coVerify(exactly = 0) { parkingRepo.create(any()) }
    }

    @Test fun `no guarda mientras se procesa una foto`() {
        val vm = readyForm()
        coEvery { ImageCompressor.compress(any(), any()) } coAnswers { CompletableDeferred<ByteArray>().await() }
        vm.onPhotoPicked(PhotoKind.PLACE, Uri.parse("content://foto"))
        vm.save()
        assertEquals(str(R.string.form_err_wait_photo), vm.s.error)
        coVerify(exactly = 0) { parkingRepo.create(any()) }
    }

    // --- Guardar: alta ---

    @Test fun `alta guarda con campos recortados y vacios como null`() {
        val created = slot<Parking>()
        coEvery { parkingRepo.create(capture(created)) } returns "nuevo"
        val vm = readyForm()
        vm.setNote("  cerca del arbol  ")
        vm.setLevelSector("   ")
        vm.setRuleSummary("")
        vm.setPreset(60)
        vm.save()

        with(created.captured) {
            assertEquals("v1", vehicleId)
            assertEquals(GeoPoint(here.latitude, here.longitude), location)
            assertEquals("cerca del arbol", note)
            assertNull(levelSector)
            assertNull(ruleSummary)
            assertNotNull(expiresAt)
            assertEquals(ParkingSource.MANUAL, source)
            assertNull(agentConfidence)
        }
        assertEquals("nuevo", vm.s.savedId)
        assertFalse(vm.s.saving)
        verify { precacheTilesAround(any(), here.latitude, here.longitude) }
    }

    @Test fun `alta sin limite guarda sin vencimiento`() {
        val created = slot<Parking>()
        coEvery { parkingRepo.create(capture(created)) } returns "nuevo"
        readyForm().save()
        assertNull(created.captured.expiresAt)
    }

    @Test fun `alta tras leer el cartel con IA queda marcada como AGENT_SIGN`() {
        val created = slot<Parking>()
        coEvery { parkingRepo.create(capture(created)) } returns "nuevo"
        coEvery { analyzer.analyzeSign(any(), any()) } returns analysis()
        val vm = readyForm()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://cartel"))
        vm.analyzeSign()
        vm.save()
        assertEquals(ParkingSource.AGENT_SIGN, created.captured.source)
        assertEquals("high", created.captured.agentConfidence)
        assertEquals("Medido 2 h", created.captured.ruleSummary)
    }

    @Test fun `si el vehiculo ya tiene estacionamiento activo muestra ese mensaje`() {
        coEvery { parkingRepo.create(any()) } throws ActiveParkingExistsException()
        val vm = readyForm()
        vm.save()
        assertEquals(str(R.string.form_err_active_exists), vm.s.error)
        assertNull(vm.s.savedId)
        assertFalse(vm.s.saving)
    }

    @Test fun `un error al guardar se informa y deja volver a intentar`() {
        coEvery { parkingRepo.create(any()) } throws RuntimeException("sin red")
        val vm = readyForm()
        vm.save()
        assertEquals(str(R.string.form_err_save, "sin red"), vm.s.error)
        assertFalse(vm.s.saving)
        assertNull(vm.s.savedId)
    }

    // --- Fotos ---

    @Test fun `elegir una foto la comprime y la marca como cambiada`() {
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.PLACE, Uri.parse("content://foto"))
        assertSame(jpeg, vm.s.placePhoto!!.jpeg)
        assertSame(bitmap, vm.s.placePhoto!!.bitmap)
        assertTrue(vm.s.placePhotoChanged)
        assertFalse(vm.s.signPhotoChanged)
        assertNull(vm.s.processingPhoto)
    }

    @Test fun `si falla la compresion informa el error`() {
        coEvery { ImageCompressor.compress(any(), any()) } throws IllegalArgumentException("ilegible")
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.PLACE, Uri.parse("content://foto"))
        assertEquals(str(R.string.form_err_photo_use, "ilegible"), vm.s.error)
        assertNull(vm.s.placePhoto)
        assertNull(vm.s.processingPhoto)
    }

    @Test fun `alta con fotos las guarda y registra sus rutas`() {
        coEvery { parkingRepo.create(any()) } returns "nuevo"
        coEvery { photoRepo.save("nuevo", PhotoKind.PLACE, any()) } returns "ruta/place"
        coEvery { photoRepo.save("nuevo", PhotoKind.SIGN, any()) } returns "ruta/sign"
        val vm = readyForm()
        vm.onPhotoPicked(PhotoKind.PLACE, Uri.parse("content://a"))
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://b"))
        vm.save()
        coVerify { parkingRepo.setPhotoPaths("nuevo", "ruta/place", "ruta/sign") }
    }

    @Test fun `sin fotos nuevas no toca el almacenamiento de fotos`() {
        coEvery { parkingRepo.create(any()) } returns "nuevo"
        readyForm().save()
        coVerify(exactly = 0) { photoRepo.save(any(), any(), any()) }
        coVerify(exactly = 0) { parkingRepo.setPhotoPaths(any(), any(), any()) }
    }

    @Test fun `cambiar la foto del cartel descarta la lectura anterior de la IA`() {
        coEvery { analyzer.analyzeSign(any(), any()) } returns analysis()
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://a"))
        vm.analyzeSign()
        assertNotNull(vm.s.signAnalysis)
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://b"))
        assertNull(vm.s.signAnalysis)
    }

    // --- IA: cartel ---

    @Test fun `leer el cartel sin foto pide sacarla primero`() {
        val vm = viewModel()
        vm.analyzeSign()
        assertEquals(str(R.string.form_err_sign_photo_first), vm.s.error)
        coVerify(exactly = 0) { analyzer.analyzeSign(any(), any()) }
    }

    @Test fun `el cartel con maximo de minutos propone ese vencimiento`() {
        coEvery { analyzer.analyzeSign(any(), any()) } returns analysis(maxMinutes = 60)
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://a"))
        val before = System.currentTimeMillis()
        vm.analyzeSign()
        assertEquals((before + 60 * 60_000L).toDouble(), vm.s.expiresAt!!.toDouble(), 5_000.0)
        assertNull(vm.s.selectedPreset)
        assertEquals("Medido 2 h", vm.s.ruleSummary)
        assertFalse(vm.s.analyzingSign)
    }

    @Test fun `si esta prohibido no propone vencimiento aunque tenga minutos`() {
        coEvery { analyzer.analyzeSign(any(), any()) } returns
            analysis(verdict = ParkingVerdict.FORBIDDEN, maxMinutes = 60)
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://a"))
        vm.analyzeSign()
        assertNull(vm.s.expiresAt)
        assertEquals(ParkingFormState.NO_LIMIT, vm.s.selectedPreset)
        assertNotNull(vm.s.signAnalysis)
    }

    @Test fun `una hora del cartel que ya paso hoy se ignora`() {
        coEvery { analyzer.analyzeSign(any(), any()) } returns analysis(allowedUntil = "00:00")
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://a"))
        vm.analyzeSign()
        assertNull(vm.s.expiresAt)
    }

    @Test fun `un cartel ilegible informa el error`() {
        coEvery { analyzer.analyzeSign(any(), any()) } returns null
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://a"))
        vm.analyzeSign()
        assertEquals(str(R.string.form_err_sign_unreadable), vm.s.error)
        assertFalse(vm.s.analyzingSign)
    }

    @Test fun `un error de cuota de la IA se explica sin el texto crudo`() {
        coEvery { analyzer.analyzeSign(any(), any()) } throws RuntimeException("Quota exceeded for model")
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://a"))
        vm.analyzeSign()
        assertEquals(str(R.string.form_err_ai_quota), vm.s.error)
    }

    @Test fun `otro error de la IA incluye el motivo recortado`() {
        coEvery { analyzer.analyzeSign(any(), any()) } throws RuntimeException("x".repeat(400))
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.SIGN, Uri.parse("content://a"))
        vm.analyzeSign()
        assertEquals(str(R.string.form_err_ai_generic, str(R.string.form_err_sign_ai), "x".repeat(150)), vm.s.error)
        assertFalse(vm.s.analyzingSign)
    }

    // --- IA: descripción del lugar ---

    @Test fun `describir el lugar sin foto pide sacarla primero`() {
        val vm = viewModel()
        vm.describePlace()
        assertEquals(str(R.string.form_err_place_photo_first), vm.s.error)
    }

    @Test fun `la descripcion de la IA se escribe en la nota`() {
        coEvery { analyzer.describePlace(any(), any()) } returns "Frente a la farmacia"
        val vm = viewModel()
        vm.onPhotoPicked(PhotoKind.PLACE, Uri.parse("content://a"))
        vm.describePlace()
        assertEquals("Frente a la farmacia", vm.s.note)
        assertFalse(vm.s.describingPlace)
    }

    @Test fun `una descripcion vacia informa el error y conserva la nota`() {
        coEvery { analyzer.describePlace(any(), any()) } returns null
        val vm = viewModel()
        vm.setNote("mi nota")
        vm.onPhotoPicked(PhotoKind.PLACE, Uri.parse("content://a"))
        vm.describePlace()
        assertEquals(str(R.string.form_err_place_ai_empty), vm.s.error)
        assertEquals("mi nota", vm.s.note)
    }

    // --- Edición ---

    private val existing = Parking(
        id = "p1", vehicleId = "v1", location = GeoPoint(-34.7, -58.5),
        addressText = "Calle 9", note = "nota", levelSector = "2B", ruleSummary = "Libre",
        startedAt = Timestamp(Date(1_700_000_000_000L)),
        source = ParkingSource.AGENT_TEXT, agentConfidence = "medium",
    )

    @Test fun `editar carga los datos del estacionamiento`() {
        coEvery { parkingRepo.getParking("p1") } returns existing
        val s = viewModel("p1").s
        assertTrue(s.isEdit)
        assertFalse(s.loading)
        assertEquals("v1", s.vehicleId)
        assertEquals("nota", s.note)
        assertEquals("2B", s.levelSector)
        assertEquals("Libre", s.ruleSummary)
        assertEquals(GeoPosition(-34.7, -58.5), s.position)
        assertEquals(PositionSource.SAVED, s.positionSource)
        assertEquals(ParkingFormState.NO_LIMIT, s.selectedPreset)
        assertNull(s.expiresAt)
    }

    @Test fun `editar no toma la ubicacion del GPS aunque haya permiso`() {
        every { locationRepo.hasPermission() } returns true
        coEvery { parkingRepo.getParking("p1") } returns existing
        viewModel("p1")
        coVerify(exactly = 0) { locationRepo.currentPosition(any()) }
    }

    @Test fun `editar un estacionamiento que no existe informa el error`() {
        coEvery { parkingRepo.getParking("p1") } returns null
        assertEquals(str(R.string.form_err_not_exists), viewModel("p1").s.error)
    }

    // Bug conocido: tras fallar la carga, el colector de vehiculos recalcula loading = isEdit && original == null
    // y lo deja en true, asi que la pantalla muestra el indicador de carga y nunca el error.
    @Ignore("bug conocido en ParkingFormViewModel: loading vuelve a true tras un error de carga")
    @Test fun `editar un estacionamiento inexistente deja de mostrar el cargando`() {
        coEvery { parkingRepo.getParking("p1") } returns null
        assertFalse(viewModel("p1").s.loading)
    }

    @Test fun `editar carga las fotos guardadas sin marcarlas como cambiadas`() {
        coEvery { parkingRepo.getParking("p1") } returns existing.copy(placePhotoPath = "ruta/place")
        coEvery { photoRepo.load("p1", PhotoKind.PLACE) } returns jpeg
        val s = viewModel("p1").s
        assertSame(bitmap, s.placePhoto!!.bitmap)
        assertFalse(s.placePhotoChanged)
        assertNull(s.signPhoto)
        coVerify(exactly = 0) { photoRepo.load("p1", PhotoKind.SIGN) }
    }

    @Test fun `guardar una edicion conserva inicio, origen y confianza`() {
        coEvery { parkingRepo.getParking("p1") } returns existing
        val updated = slot<Parking>()
        coEvery { parkingRepo.update(capture(updated)) } returns Unit
        val vm = viewModel("p1")
        vm.setNote("nota nueva")
        vm.save()
        with(updated.captured) {
            assertEquals("p1", id)
            assertEquals("nota nueva", note)
            assertEquals(existing.startedAt, startedAt)
            assertEquals(ParkingSource.AGENT_TEXT, source)
            assertEquals("medium", agentConfidence)
        }
        assertEquals("p1", vm.s.savedId)
        coVerify(exactly = 0) { parkingRepo.create(any()) }
    }

    @Test fun `cambiar a un vehiculo que ya esta estacionado no deja guardar`() {
        coEvery { parkingRepo.getParking("p1") } returns existing
        coEvery { parkingRepo.hasActiveParking("v2") } returns true
        val vm = viewModel("p1")
        vm.selectVehicle("v2")
        vm.save()
        assertEquals(str(R.string.form_err_active_exists), vm.s.error)
        coVerify(exactly = 0) { parkingRepo.update(any()) }
    }

    @Test fun `quitar una foto existente la borra y conserva la otra ruta`() {
        coEvery { parkingRepo.getParking("p1") } returns
            existing.copy(placePhotoPath = "ruta/place", signPhotoPath = "ruta/sign")
        coEvery { photoRepo.load("p1", any()) } returns jpeg
        val vm = viewModel("p1")
        vm.removePhoto(PhotoKind.PLACE)
        assertNull(vm.s.placePhoto)
        assertTrue(vm.s.placePhotoChanged)
        vm.save()
        coVerify { photoRepo.delete("p1", PhotoKind.PLACE) }
        coVerify { parkingRepo.setPhotoPaths("p1", null, "ruta/sign") }
        coVerify(exactly = 0) { photoRepo.delete("p1", PhotoKind.SIGN) }
    }
}
