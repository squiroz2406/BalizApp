package com.example.balizapp.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.balizapp.auth.AuthStatus
import com.example.balizapp.auth.AuthUiState
import com.example.balizapp.auth.AuthViewModel

private enum class AuthRoute { Login, Register, Forgot }

/** Contenedor de todo el flujo de autenticación; muestra [content] cuando hay sesión válida. */
@Composable
fun AuthGate(vm: AuthViewModel, state: AuthUiState, content: @Composable () -> Unit) {
    var route by rememberSaveable { mutableStateOf(AuthRoute.Login) }
    when (state.status) {
        AuthStatus.Loading -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        AuthStatus.SignedOut -> when (route) {
            AuthRoute.Login -> LoginScreen(
                state, vm,
                onRegister = { vm.clearFeedback(); route = AuthRoute.Register },
                onForgot = { vm.clearFeedback(); route = AuthRoute.Forgot },
            )
            AuthRoute.Register -> RegisterScreen(state, vm) { vm.clearFeedback(); route = AuthRoute.Login }
            AuthRoute.Forgot -> ForgotScreen(state, vm) { vm.clearFeedback(); route = AuthRoute.Login }
        }

        AuthStatus.NeedsVerification -> VerifyScreen(state, vm)
        AuthStatus.SignedIn -> content()
    }
}

@Composable
private fun AuthLayout(title: String, state: AuthUiState, body: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        body()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun LoginScreen(state: AuthUiState, vm: AuthViewModel, onRegister: () -> Unit, onForgot: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    AuthLayout("Iniciar sesión", state) {
        EmailField(email) { email = it }
        PasswordField("Contraseña", password) { password = it }
        Button({ vm.signIn(email, password) }, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Text("Ingresar")
        }
        TextButton(onForgot, Modifier.align(Alignment.End)) { Text("¿Olvidaste tu contraseña?") }
        OrDivider()
        OutlinedButton({ vm.signInWithGoogle(context) }, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Text("Continuar con Google")
        }
        TextButton(onRegister, Modifier.align(Alignment.CenterHorizontally)) {
            Text("¿No tenés cuenta? Registrate")
        }
    }
}

@Composable
private fun RegisterScreen(state: AuthUiState, vm: AuthViewModel, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    AuthLayout("Crear cuenta", state) {
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("Nombre") }, singleLine = true,
        )
        EmailField(email) { email = it }
        PasswordField("Contraseña", password) { password = it }
        PasswordField("Repetir contraseña", confirm) { confirm = it }
        Button({ vm.register(name, email, password, confirm) }, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Text("Registrarme")
        }
        OrDivider()
        OutlinedButton({ vm.signInWithGoogle(context) }, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Text("Registrarme con Google")
        }
        TextButton(onBack, Modifier.align(Alignment.CenterHorizontally)) {
            Text("¿Ya tenés cuenta? Iniciá sesión")
        }
    }
}

@Composable
private fun ForgotScreen(state: AuthUiState, vm: AuthViewModel, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    AuthLayout("Restablecer contraseña", state) {
        Text("Te enviaremos un enlace para crear una nueva contraseña.")
        EmailField(email) { email = it }
        Button({ vm.sendPasswordReset(email) }, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Text("Enviar enlace")
        }
        TextButton(onBack, Modifier.align(Alignment.CenterHorizontally)) { Text("Volver") }
    }
}

@Composable
private fun VerifyScreen(state: AuthUiState, vm: AuthViewModel) {
    val context = LocalContext.current
    AuthLayout("Verificá tu correo", state) {
        Text("Enviamos un enlace de verificación a ${state.userEmail}. Abrilo y luego tocá el botón.")
        Button({ vm.checkVerified() }, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Text("Ya verifiqué mi correo")
        }
        OutlinedButton({ vm.resendVerification() }, Modifier.fillMaxWidth(), enabled = !state.busy) {
            Text("Reenviar correo")
        }
        TextButton({ vm.signOut(context) }, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value, onChange, Modifier.fillMaxWidth(),
    label = { Text("Correo electrónico") }, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
)

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value, onChange, Modifier.fillMaxWidth(),
    label = { Text(label) }, singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
)

@Composable
private fun ColumnScope.OrDivider() {
    Spacer(Modifier.height(4.dp))
    HorizontalDivider()
    Text("o", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall)
}
