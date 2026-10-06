package com.example.balizapp.auth

import android.app.Application
import android.util.Patterns
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.balizapp.data.UserRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStatus { Loading, SignedOut, NeedsVerification, SignedIn }

data class AuthUiState(
    val status: AuthStatus = AuthStatus.Loading,
    val busy: Boolean = false,
    val userName: String? = null,
    val userEmail: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository()
    private val users = UserRepository()
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        refreshSession()
    }

    private fun refreshSession() {
        val user = repo.currentUser
        _state.update {
            it.copy(
                status = when {
                    user == null -> AuthStatus.SignedOut
                    !user.isEmailVerified && user.providerData.none { p -> p.providerId == "google.com" } ->
                        AuthStatus.NeedsVerification
                    else -> AuthStatus.SignedIn
                },
                userName = user?.displayName,
                userEmail = user?.email,
            )
        }
        // Crea users/{uid} la primera vez que entra (o actualiza nombre y correo).
        if (_state.value.status == AuthStatus.SignedIn) {
            viewModelScope.launch {
                runCatching { users.ensureProfile() }
                    .onFailure { Log.w(TAG, "No se pudo crear el perfil en Firestore", it) }
            }
        }
    }

    fun clearFeedback() = _state.update { it.copy(error = null, message = null) }

    fun signIn(email: String, password: String) {
        validateEmail(email)?.let { return fail(it) }
        if (password.isEmpty()) return fail("Ingresá tu contraseña")
        run { repo.signIn(email.trim(), password) }
    }

    fun register(name: String, email: String, password: String, confirm: String) {
        if (name.isBlank()) return fail("Ingresá tu nombre")
        validateEmail(email)?.let { return fail(it) }
        if (password.length < 6) return fail("La contraseña debe tener al menos 6 caracteres")
        if (password != confirm) return fail("Las contraseñas no coinciden")
        run { repo.register(name.trim(), email.trim(), password) }
    }

    fun signInWithGoogle(activityContext: android.content.Context) {
        run { repo.signInWithGoogle(activityContext) }
    }

    fun sendPasswordReset(email: String) {
        validateEmail(email)?.let { return fail(it) }
        run(success = "Te enviamos un correo para restablecer tu contraseña") {
            repo.sendPasswordReset(email.trim())
        }
    }

    fun resendVerification() =
        run(success = "Correo de verificación enviado") { repo.resendVerification() }

    fun checkVerified() = run {
        if (!repo.reloadIsVerified()) error("verification-pending")
    }

    fun signOut(activityContext: android.content.Context) {
        viewModelScope.launch {
            repo.signOut(activityContext)
            _state.update { AuthUiState(status = AuthStatus.SignedOut) }
        }
    }

    private fun run(success: String? = null, block: suspend () -> Any?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                block()
                refreshSession()
                _state.update { it.copy(busy = false, message = success) }
            } catch (e: GetCredentialCancellationException) {
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                Log.w(TAG, "Error de autenticación", e)
                _state.update { it.copy(busy = false, error = messageFor(e)) }
            }
        }
    }

    private fun fail(msg: String) = _state.update { it.copy(error = msg, message = null) }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Ingresá tu correo electrónico"
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "El correo no es válido"
        else -> null
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }

    private fun messageFor(e: Exception): String = when {
        e.message == "verification-pending" -> "Todavía no verificaste tu correo"
        e is FirebaseAuthWeakPasswordException -> "La contraseña es demasiado débil"
        e is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo"
        e is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo"
        e is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
        e is FirebaseNetworkException -> "Sin conexión. Revisá tu internet"
        e is NoCredentialException ->
            "No hay una cuenta de Google disponible. Agregá una en Ajustes del teléfono; " +
                "si ya hay una, falta registrar la huella SHA-1 de la app en Firebase"
        e is GetCredentialException -> "No se pudo iniciar sesión con Google: ${e.errorMessage ?: e.type}"
        e is FirebaseAuthException -> "Error de autenticación (${e.errorCode})"
        else -> e.message ?: "Ocurrió un error inesperado"
    }
}
