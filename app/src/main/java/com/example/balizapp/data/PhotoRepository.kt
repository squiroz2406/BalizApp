package com.example.balizapp.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Qué foto de un estacionamiento: la del lugar o la del cartel. */
enum class PhotoKind(val id: String) { PLACE("place"), SIGN("sign") }

/**
 * Fotos de los estacionamientos, guardadas como JPEG comprimido dentro de Firestore:
 * users/{uid}/parkings/{parkingId}/photos/{place|sign} → { data: bytes, createdAt }.
 *
 * Por qué Firestore y no Storage: Storage exige el plan Blaze (con tarjeta). Así las fotos
 * quedan bajo las mismas reglas de seguridad por usuario y no existen enlaces públicos.
 * Van en una subcolección aparte para que la lista de estacionamientos no las descargue.
 */
class PhotoRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun requireUid(): String = auth.currentUser?.uid ?: error("No hay sesión iniciada")

    private fun photos(uid: String, parkingId: String) =
        db.collection("users").document(uid)
            .collection("parkings").document(parkingId)
            .collection("photos")

    /** Ruta que se guarda en el estacionamiento (placePhotoUrl / signPhotoUrl). */
    fun pathFor(parkingId: String, kind: PhotoKind): String =
        "users/${requireUid()}/parkings/$parkingId/photos/${kind.id}"

    /** Guarda (o reemplaza) la foto y devuelve su ruta. */
    suspend fun save(parkingId: String, kind: PhotoKind, jpeg: ByteArray): String {
        photos(requireUid(), parkingId).document(kind.id)
            .set(mapOf("data" to Blob.fromBytes(jpeg), "createdAt" to Timestamp.now()))
            .let { task -> withTimeoutOrNull(WRITE_TIMEOUT_MS) { task.await() } }
        return pathFor(parkingId, kind)
    }

    /** Bytes JPEG de la foto, o null si no existe. */
    suspend fun load(parkingId: String, kind: PhotoKind): ByteArray? {
        val doc = photos(requireUid(), parkingId).document(kind.id).get().await()
        return doc.getBlob("data")?.toBytes()
    }

    suspend fun delete(parkingId: String, kind: PhotoKind) {
        photos(requireUid(), parkingId).document(kind.id).delete()
            .let { task -> withTimeoutOrNull(WRITE_TIMEOUT_MS) { task.await() } }
    }

    /** Firestore no borra subcolecciones solo: se llama antes de borrar el estacionamiento. */
    suspend fun deleteAll(parkingId: String) {
        PhotoKind.entries.forEach { delete(parkingId, it) }
    }

    private companion object {
        // Igual que en ParkingRepository: sin conexión no se congela la pantalla.
        const val WRITE_TIMEOUT_MS = 5_000L
    }
}
