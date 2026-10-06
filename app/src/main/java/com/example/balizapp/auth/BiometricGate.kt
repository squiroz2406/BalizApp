package com.example.balizapp.auth

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object Cancelled : BiometricResult
    data class Unavailable(val message: String) : BiometricResult
    data class Failed(val message: String) : BiometricResult
}

/** El usuario cerró el diálogo de huella: no es un error, no se muestra mensaje. */
class BiometricCancelledException : Exception("Verificación cancelada")

/**
 * Pide la huella (o el rostro, si el teléfono lo usa como biometría fuerte) antes de continuar.
 * Si el usuario no puede usarla, el sistema ofrece el PIN o patrón del teléfono como respaldo.
 */
object BiometricGate {

    // BIOMETRIC_STRONG + DEVICE_CREDENTIAL no está soportado antes de Android 11 (API 30).
    private val authenticators: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String): BiometricResult {
        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> return BiometricResult.Unavailable(
                "Registrá una huella o un bloqueo de pantalla en los Ajustes del teléfono para ingresar con Google"
            )
            else -> return BiometricResult.Unavailable(
                "Este teléfono no permite verificar tu identidad con huella o bloqueo de pantalla"
            )
        }

        return suspendCancellableCoroutine { cont ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val result = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> BiometricResult.Cancelled
                        else -> BiometricResult.Failed(errString.toString())
                    }
                    if (cont.isActive) cont.resume(result)
                }
                // onAuthenticationFailed (huella no reconocida): el diálogo sigue abierto y deja reintentar.
            }

            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}

/** Busca la FragmentActivity que contiene este contexto (LocalContext puede ser un envoltorio). */
fun Context.findFragmentActivity(): FragmentActivity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    error("La pantalla no está dentro de una FragmentActivity")
}
