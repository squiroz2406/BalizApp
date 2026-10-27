package com.example.balizapp.ui.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.balizapp.R
import com.example.balizapp.auth.AuthRepository
import com.example.balizapp.auth.AuthViewModel
import com.example.balizapp.data.UserRepository
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class AuthGateTest : ComposeScreenTest() {
    private val repo = mockk<AuthRepository>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)

    @Before fun setUp() {
        every { repo.currentUser } returns null
    }

    private fun show() {
        val vm = AuthViewModel(app, repo, users)
        rule.setContent {
            val state by vm.state.collectAsState()
            com.example.balizapp.auth.ui.AuthGate(vm, state) { Text(CONTENT) }
        }
    }

    private fun field(label: Int) = rule.onNodeWithText(str(label))
    private fun type(label: Int, text: String) = field(label).performTextInput(text)
    private fun click(label: Int) = rule.onNodeWithText(str(label)).performClick()

    // --- Inicio de sesión ---

    @Test fun `sin sesion muestra el inicio de sesion`() {
        show()
        rule.onNodeWithText(str(R.string.auth_title_login)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.auth_login_button)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.auth_continue_google)).assertIsDisplayed()
        rule.onNodeWithText(CONTENT).assertDoesNotExist()
    }

    @Test fun `ingresar con todo vacio pide el correo`() {
        show()
        click(R.string.auth_login_button)
        rule.onNodeWithText(str(R.string.auth_error_email_required)).assertIsDisplayed()
        coVerify(exactly = 0) { repo.signIn(any(), any()) }
    }

    @Test fun `un correo invalido muestra el error`() {
        show()
        type(R.string.auth_email, "no-es-correo")
        type(R.string.auth_password, "123456")
        click(R.string.auth_login_button)
        rule.onNodeWithText(str(R.string.auth_error_email_invalid)).assertIsDisplayed()
    }

    @Test fun `ingresar con datos validos llama al repositorio`() {
        show()
        type(R.string.auth_email, "s@x.com")
        type(R.string.auth_password, "123456")
        click(R.string.auth_login_button)
        coVerify { repo.signIn("s@x.com", "123456") }
    }

    @Test fun `credenciales incorrectas se muestran en pantalla`() {
        coEvery { repo.signIn(any(), any()) } throws FirebaseAuthInvalidCredentialsException("x", "y")
        show()
        type(R.string.auth_email, "s@x.com")
        type(R.string.auth_password, "mala")
        click(R.string.auth_login_button)
        rule.onNodeWithText(str(R.string.auth_error_wrong_credentials)).assertIsDisplayed()
    }

    // --- Navegación entre pantallas ---

    @Test fun `se puede ir al registro y volver al inicio de sesion`() {
        show()
        click(R.string.auth_register_link)
        rule.onNodeWithText(str(R.string.auth_title_register)).assertIsDisplayed()
        click(R.string.auth_have_account_link)
        rule.onNodeWithText(str(R.string.auth_title_login)).assertIsDisplayed()
    }

    @Test fun `se puede ir a recuperar la contrasenia y volver`() {
        show()
        click(R.string.auth_forgot_link)
        rule.onNodeWithText(str(R.string.auth_title_reset)).assertIsDisplayed()
        click(R.string.auth_back)
        rule.onNodeWithText(str(R.string.auth_title_login)).assertIsDisplayed()
    }

    @Test fun `al cambiar de pantalla se limpia el error anterior`() {
        show()
        click(R.string.auth_login_button)
        rule.onNodeWithText(str(R.string.auth_error_email_required)).assertIsDisplayed()
        click(R.string.auth_register_link)
        rule.onNodeWithText(str(R.string.auth_error_email_required)).assertDoesNotExist()
    }

    // --- Registro ---

    @Test fun `registro valida que las contrasenias coincidan`() {
        show()
        click(R.string.auth_register_link)
        type(R.string.auth_name, "Sofi")
        type(R.string.auth_email, "s@x.com")
        type(R.string.auth_password, "123456")
        type(R.string.auth_repeat_password, "654321")
        click(R.string.auth_register_button)
        rule.onNodeWithText(str(R.string.auth_error_password_mismatch)).assertIsDisplayed()
        coVerify(exactly = 0) { repo.register(any(), any(), any()) }
    }

    @Test fun `registro valido llama al repositorio`() {
        show()
        click(R.string.auth_register_link)
        type(R.string.auth_name, "Sofi")
        type(R.string.auth_email, "s@x.com")
        type(R.string.auth_password, "123456")
        type(R.string.auth_repeat_password, "123456")
        click(R.string.auth_register_button)
        coVerify { repo.register("Sofi", "s@x.com", "123456") }
    }

    // --- Recuperar contraseña ---

    @Test fun `recuperar contrasenia informa que se envio el correo`() {
        show()
        click(R.string.auth_forgot_link)
        type(R.string.auth_email, "s@x.com")
        click(R.string.auth_send_link)
        coVerify { repo.sendPasswordReset("s@x.com") }
        rule.onNodeWithText(str(R.string.auth_msg_reset_sent)).assertIsDisplayed()
    }

    // --- Con sesión ---

    @Test fun `con sesion verificada muestra el contenido de la app`() {
        val user = mockk<FirebaseUser>()
        every { user.isEmailVerified } returns true
        every { user.displayName } returns "Sofi"
        every { user.email } returns "s@x.com"
        every { repo.currentUser } returns user
        show()
        rule.onNodeWithText(CONTENT).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.auth_title_login)).assertDoesNotExist()
    }

    @Test fun `con correo sin verificar pide verificarlo`() {
        val user = mockk<FirebaseUser>()
        every { user.isEmailVerified } returns false
        every { user.providerData } returns emptyList()
        every { user.displayName } returns null
        every { user.email } returns "s@x.com"
        every { repo.currentUser } returns user
        show()
        rule.onNodeWithText(str(R.string.auth_title_verify)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.auth_verify_info, "s@x.com")).assertIsDisplayed()
        rule.onNodeWithText(CONTENT).assertDoesNotExist()
    }

    @Test fun `verificar sin haber confirmado el correo avisa que esta pendiente`() {
        val user = mockk<FirebaseUser>()
        every { user.isEmailVerified } returns false
        every { user.providerData } returns emptyList()
        every { user.displayName } returns null
        every { user.email } returns "s@x.com"
        every { repo.currentUser } returns user
        coEvery { repo.reloadIsVerified() } returns false
        show()
        click(R.string.auth_verified_button)
        rule.onNodeWithText(str(R.string.auth_error_verification_pending)).assertIsDisplayed()
    }

    private companion object {
        const val CONTENT = "CONTENIDO DE LA APP"
    }
}
