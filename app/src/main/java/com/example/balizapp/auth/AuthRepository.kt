package com.example.balizapp.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun signIn(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email, password).await().user!!

    suspend fun register(name: String, email: String, password: String): FirebaseUser {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user!!
        user.updateProfile(userProfileChangeRequest { displayName = name }).await()
        user.sendEmailVerification().await()
        return user
    }

    /** Inicia sesión (o registra, si es la primera vez) con una cuenta de Google. */
    suspend fun signInWithGoogle(context: Context): FirebaseUser {
        val webClientId = webClientId(context)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) error("Tipo de credencial no soportado")
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .await().user!!
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun resendVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** Refresca el usuario desde el servidor; devuelve true si el email ya fue verificado. */
    suspend fun reloadIsVerified(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        return user.isEmailVerified
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    // default_web_client_id lo genera el plugin google-services desde google-services.json.
    private fun webClientId(context: Context): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        require(id != 0) { "Falta app/google-services.json con el cliente web de Google" }
        return context.getString(id)
    }
}
