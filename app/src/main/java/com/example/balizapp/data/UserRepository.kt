package com.example.balizapp.data

import android.util.Log
import com.example.balizapp.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Perfil y ajustes del usuario: users/{uid}. Solo este repositorio conoce esa ruta. */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun requireUid(): String = auth.currentUser?.uid ?: error("No hay sesión iniciada")

    /** Perfil del usuario con sesión, en tiempo real. null si no hay sesión o todavía no existe. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeProfile(): Flow<UserProfile?> = auth.uidFlow().flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else userDoc(uid).observe()
            .map { if (it.exists()) UserProfile.from(it) else null }
            .catch { e ->
                Log.w(TAG, "Error leyendo el perfil", e)
                emit(null)
            }
    }

    /** Crea users/{uid} si no existe; si existe, actualiza nombre y correo desde Auth. */
    suspend fun ensureProfile() {
        val user = auth.currentUser ?: return
        val ref = userDoc(user.uid)
        val snapshot = ref.get().await()
        if (!snapshot.exists()) {
            ref.set(
                mapOf(
                    "displayName" to user.displayName,
                    "email" to user.email,
                    "reminderMinutes" to UserProfile.DEFAULT_REMINDER_MINUTES,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } else {
            val changes = buildMap<String, Any> {
                user.displayName?.let { if (it != snapshot.getString("displayName")) put("displayName", it) }
                user.email?.let { if (it != snapshot.getString("email")) put("email", it) }
            }
            if (changes.isNotEmpty()) ref.update(changes).await()
        }
    }

    suspend fun setReminderMinutes(minutes: Int) {
        userDoc(requireUid()).set(mapOf("reminderMinutes" to minutes), SetOptions.merge()).await()
    }

    private companion object {
        const val TAG = "UserRepository"
    }
}
