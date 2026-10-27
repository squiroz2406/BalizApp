package com.example.balizapp.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.balizapp.R
import com.example.balizapp.auth.AuthRepository
import com.example.balizapp.auth.AuthStatus
import com.example.balizapp.auth.AuthViewModel
import com.example.balizapp.data.UserRepository
import com.example.balizapp.i18n.AppLocale
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthViewModelTest {
    private val repo = mockk<AuthRepository>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private lateinit var app: Application

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        AppLocale.init(app)
        every { repo.currentUser } returns null
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AuthViewModel(app, repo, users)
    private fun str(id: Int) = AppLocale.str(id)

    @Test fun `sin usuario arranca como sesion cerrada`() =
        assertEquals(AuthStatus.SignedOut, viewModel().state.value.status)

    @Test fun `usuario con correo verificado arranca con sesion iniciada y crea el perfil`() {
        val user = mockk<FirebaseUser>()
        every { user.isEmailVerified } returns true
        every { user.displayName } returns "Sofi"
        every { user.email } returns "s@x.com"
        every { repo.currentUser } returns user
        val vm = viewModel()
        assertEquals(AuthStatus.SignedIn, vm.state.value.status)
        assertEquals("Sofi", vm.state.value.userName)
        coVerify { users.ensureProfile() }
    }

    @Test fun `usuario sin verificar y sin Google debe verificar el correo`() {
        val user = mockk<FirebaseUser>()
        every { user.isEmailVerified } returns false
        every { user.providerData } returns emptyList()
        every { user.displayName } returns null
        every { user.email } returns "s@x.com"
        every { repo.currentUser } returns user
        assertEquals(AuthStatus.NeedsVerification, viewModel().state.value.status)
        coVerify(exactly = 0) { users.ensureProfile() }
    }

    @Test fun `ingresar con correo en blanco da error y no llama al repositorio`() {
        val vm = viewModel()
        vm.signIn("  ", "123456")
        assertEquals(str(R.string.auth_error_email_required), vm.state.value.error)
        coVerify(exactly = 0) { repo.signIn(any(), any()) }
    }

    @Test fun `ingresar con correo invalido da error`() {
        val vm = viewModel()
        vm.signIn("no-es-un-correo", "123456")
        assertEquals(str(R.string.auth_error_email_invalid), vm.state.value.error)
    }

    @Test fun `ingresar sin contrasenia da error`() {
        val vm = viewModel()
        vm.signIn("s@x.com", "")
        assertEquals(str(R.string.auth_error_password_required), vm.state.value.error)
    }

    @Test fun `ingreso correcto recorta el correo y deja de estar ocupado`() {
        val vm = viewModel()
        vm.signIn(" s@x.com ", "123456")
        coVerify { repo.signIn("s@x.com", "123456") }
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error)
    }

    @Test fun `credenciales incorrectas muestran el mensaje correspondiente`() {
        coEvery { repo.signIn(any(), any()) } throws FirebaseAuthInvalidCredentialsException("x", "y")
        val vm = viewModel()
        vm.signIn("s@x.com", "mala")
        assertEquals(str(R.string.auth_error_wrong_credentials), vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `registro valida nombre, contrasenia corta y confirmacion en ese orden`() {
        val vm = viewModel()
        vm.register(" ", "s@x.com", "123456", "123456")
        assertEquals(str(R.string.auth_error_name_required), vm.state.value.error)
        vm.register("Sofi", "s@x.com", "123", "123")
        assertEquals(str(R.string.auth_error_password_short), vm.state.value.error)
        vm.register("Sofi", "s@x.com", "123456", "654321")
        assertEquals(str(R.string.auth_error_password_mismatch), vm.state.value.error)
        coVerify(exactly = 0) { repo.register(any(), any(), any()) }
    }

    @Test fun `registro valido llama al repositorio con datos recortados`() {
        viewModel().register(" Sofi ", " s@x.com ", "123456", "123456")
        coVerify { repo.register("Sofi", "s@x.com", "123456") }
    }

    @Test fun `recuperar contrasenia informa que se envio el correo`() {
        val vm = viewModel()
        vm.sendPasswordReset("s@x.com")
        coVerify { repo.sendPasswordReset("s@x.com") }
        assertEquals(str(R.string.auth_msg_reset_sent), vm.state.value.message)
    }

    @Test fun `verificar sin haber confirmado el correo muestra pendiente`() {
        coEvery { repo.reloadIsVerified() } returns false
        val vm = viewModel()
        vm.checkVerified()
        assertEquals(str(R.string.auth_error_verification_pending), vm.state.value.error)
    }

    @Test fun `clearFeedback limpia error y mensaje`() {
        val vm = viewModel()
        vm.signIn("", "")
        vm.clearFeedback()
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.message)
    }
}
