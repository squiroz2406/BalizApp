package com.example.balizapp.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.balizapp.R
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.UserRepository
import com.example.balizapp.data.VehicleRepository
import com.example.balizapp.data.model.UserProfile
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.data.model.VehicleType
import com.example.balizapp.ui.screens.ProfileScreen
import com.example.balizapp.ui.screens.ProfileViewModel
import com.example.balizapp.ui.theme.ThemeMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProfileScreenTest : ComposeScreenTest() {
    private val users = mockk<UserRepository>(relaxed = true)
    private val vehicleRepo = mockk<VehicleRepository>(relaxed = true)
    private val parkingRepo = mockk<ParkingRepository>(relaxed = true)

    private val car = Vehicle(id = "v1", type = VehicleType.CAR, alias = "Mi Gol", plate = "AB123CD")
    private var signedOut = 0
    private val themes = mutableListOf<ThemeMode>()

    @Before fun setUp() {
        every { users.observeProfile() } returns flowOf(UserProfile("u1", "Sofia", "s@x.com", reminderMinutes = 15))
        every { vehicleRepo.observeVehicles() } returns flowOf(listOf(car))
    }

    private fun show() {
        val vm = ProfileViewModel(users, vehicleRepo, parkingRepo)
        rule.setContent {
            ProfileScreen(
                vm, userName = "Sofia", userEmail = "s@x.com", onSignOut = { signedOut++ },
                themeMode = ThemeMode.entries.first(), onThemeModeChange = { themes += it },
            )
        }
    }

    private fun text(id: Int) = rule.onNodeWithText(str(id))
    private fun text(s: String) = rule.onNodeWithText(s)

    /** Estos botones llevan un icono y espacios antes del texto: se buscan por fragmento. */
    private fun button(id: Int) = rule.onNodeWithText(str(id), substring = true)

    @Test fun `muestra el nombre, el correo y los vehiculos con su patente`() {
        show()
        text("Sofia").assertIsDisplayed()
        text("s@x.com").assertIsDisplayed()
        text("Mi Gol").assertIsDisplayed()
        text("${str(R.string.core_vehicle_car)} · AB123CD").assertIsDisplayed()
    }

    @Test fun `sin vehiculos explica que todavia no cargo ninguno`() {
        every { vehicleRepo.observeVehicles() } returns flowOf(emptyList())
        show()
        text(R.string.profile_no_vehicles).assertIsDisplayed()
    }

    @Test fun `elegir los minutos de aviso los guarda`() {
        show()
        text(str(R.string.profile_minutes, 30)).performClick()
        coVerify { users.setReminderMinutes(30) }
    }

    @Test fun `cerrar sesion avisa a la navegacion`() {
        show()
        button(R.string.profile_sign_out).performClick()
        assertEquals(1, signedOut)
    }

    @Test fun `cambiar el tema avisa cual eligio`() {
        show()
        val other = ThemeMode.entries.last()
        text(other.labelRes).performScrollTo().performClick()
        assertEquals(listOf(other), themes)
    }

    // El dialogo de alta/edicion de vehiculo (con campos de texto) no se prueba aca: en Robolectric un campo
    // de texto dentro de un Dialog deja a Compose sin quedar nunca inactivo y el test se cuelga. Su logica
    // (validacion, patente en mayusculas, borrado) esta cubierta en ProfileViewModelTest.
}
