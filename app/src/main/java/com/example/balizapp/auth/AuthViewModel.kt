package com.example.balizapp.auth

import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.R
import android.app.Application
import android.util.Patterns
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.FragmentActivity
import com.example.balizapp.data.UserRepository
import com.example.balizapp.notifications.ReminderScheduler
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
        if (password.isEmpty()) return fail(AppLocale.str(R.string.auth_error_password_required))
        run { repo.signIn(email.trim(), password) }
    }

    fun register(name: String, email: String, password: String, confirm: String) {
        if (name.isBlank()) return fail(AppLocale.str(R.string.auth_error_name_required))
        validateEmail(email)?.let { return fail(it) }
        if (password.length < 6) return fail(AppLocale.str(R.string.auth_error_password_short))
        if (password != confirm) return fail(AppLocale.str(R.string.auth_error_password_mismatch))
        run { repo.register(name.trim(), email.trim(), password) }
    }

    /** Ingreso con Google: primero pide la huella y, si se verifica, abre el selector de cuentas. */
    fun signInWithGoogle(activity: FragmentActivity) {
        run {
            when (val result = BiometricGate.authenticate(
                activity,
                title = AppLocale.str(R.string.auth_biometric_title),
                subtitle = AppLocale.str(R.string.auth_biometric_subtitle),
            )) {
                BiometricResult.Success -> repo.signInWithGoogle(activity)
                BiometricResult.Cancelled -> throw BiometricCancelledException()
                is BiometricResult.Unavailable -> error(result.message)
                is BiometricResult.Failed -> error(AppLocale.str(R.string.auth_error_identity_failed, result.message))
            }
        }
    }

    fun sendPasswordReset(email: String) {
        validateEmail(email)?.let { return fail(it) }
        run(success = AppLocale.str(R.string.auth_msg_reset_sent)) {
            repo.sendPasswordReset(email.trim())
        }
    }

    fun resendVerification() =
        run(success = AppLocale.str(R.string.auth_msg_verification_sent)) { repo.resendVerification() }

    fun checkVerified() = run {
        if (!repo.reloadIsVerified()) error("verification-pending")
    }

    fun signOut(activityContext: android.content.Context) {
        viewModelScope.launch {
            repo.signOut(activityContext)
            ReminderScheduler(getApplication<Application>()).cancelAll() // no deben sonar avisos de esta cuenta
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
            } catch (e: BiometricCancelledException) {
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                Log.w(TAG, "Error de autenticación", e)
                _state.update { it.copy(busy = false, error = messageFor(e)) }
            }
        }
    }

    private fun fail(msg: String) = _state.update { it.copy(error = msg, message = null) }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> AppLocale.str(R.string.auth_error_email_required)
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> AppLocale.str(R.string.auth_error_email_invalid)
        else -> null
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }

    private fun messageFor(e: Exception): String = when {
        e.message == "verification-pending" -> AppLocale.str(R.string.auth_error_verification_pending)
        e is FirebaseAuthWeakPasswordException -> AppLocale.str(R.string.auth_error_weak_password)
        e is FirebaseAuthUserCollisionException -> AppLocale.str(R.string.auth_error_account_exists)
        e is FirebaseAuthInvalidUserException -> AppLocale.str(R.string.auth_error_no_account)
        e is FirebaseAuthInvalidCredentialsException -> AppLocale.str(R.string.auth_error_wrong_credentials)
        e is FirebaseNetworkException -> AppLocale.str(R.string.auth_error_network)
        e is NoCredentialException ->
            AppLocale.str(R.string.auth_error_no_google_account)
        e is GetCredentialException -> AppLocale.str(R.string.auth_error_google_failed, e.errorMessage ?: e.type)
        e is FirebaseAuthException -> AppLocale.str(R.string.auth_error_auth_code, e.errorCode)
        else -> e.message ?: AppLocale.str(R.string.auth_error_unexpected)
    }
}
