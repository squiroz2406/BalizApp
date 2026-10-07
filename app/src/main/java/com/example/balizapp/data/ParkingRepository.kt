package com.example.balizapp.data

import android.util.Log
import com.example.balizapp.data.model.Parking
import com.example.balizapp.data.model.ParkingStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** El vehículo elegido ya tiene un estacionamiento activo (regla: uno activo por vehículo). */
class ActiveParkingExistsException : IllegalStateException("Ese vehículo ya tiene un estacionamiento activo")

/** Estacionamientos del usuario: users/{uid}/parkings. */
class ParkingRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val photos: PhotoRepository = PhotoRepository(auth, db),
) {
    private fun parkings(uid: String) = db.collection("users").document(uid).collection("parkings")

    private fun requireUid(): String = auth.currentUser?.uid ?: error("No hay sesión iniciada")

    /** Estacionamientos activos (uno por vehículo como máximo), más recientes primero. */
    fun observeActive(): Flow<List<Parking>> = observeByStatus(ParkingStatus.ACTIVE)

    /** Historial: estacionamientos ya retirados, más recientes primero. */
    fun observeHistory(): Flow<List<Parking>> = observeByStatus(ParkingStatus.FINISHED)

    /*
     * Se filtra por status en Firestore y se ordena en el teléfono: igualdad en un campo + orden por
     * otro exigiría crear un índice compuesto, y la cantidad de registros por usuario es chica.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeByStatus(status: ParkingStatus): Flow<List<Parking>> =
        auth.uidFlow().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else parkings(uid).whereEqualTo("status", status.firestoreValue).observe()
                .map { snapshot ->
                    snapshot.documents.map(Parking::from).sortedByDescending { it.startedAt }
                }
                .catch { e ->
                    Log.w(TAG, "Error leyendo estacionamientos ($status)", e)
                    emit(emptyList())
                }
        }

    /** Un estacionamiento en tiempo real; null si no existe (por ejemplo, si se borró). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeParking(id: String): Flow<Parking?> = auth.uidFlow().flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else parkings(uid).document(id).observe()
            .map { if (it.exists()) Parking.from(it) else null }
            .catch { e ->
                Log.w(TAG, "Error leyendo el estacionamiento $id", e)
                emit(null)
            }
    }

    suspend fun getParking(id: String): Parking? {
        val doc = parkings(requireUid()).document(id).get().await()
        return if (doc.exists()) Parking.from(doc) else null
    }

    suspend fun hasActiveParking(vehicleId: String): Boolean =
        parkings(requireUid())
            .whereEqualTo("vehicleId", vehicleId)
            .whereEqualTo("status", ParkingStatus.ACTIVE.firestoreValue)
            .limit(1)
            .get().await()
            .isEmpty.not()

    /** Crea un estacionamiento activo y devuelve su id. */
    suspend fun create(parking: Parking): String {
        if (hasActiveParking(parking.vehicleId)) throw ActiveParkingExistsException()
        val ref = parkings(requireUid()).document()
        ref.set(parking.copy(id = ref.id, status = ParkingStatus.ACTIVE).toFirestore()).awaitOrPending()
        return ref.id
    }

    suspend fun update(parking: Parking) {
        require(parking.id.isNotEmpty()) { "Falta el id del estacionamiento" }
        parkings(requireUid()).document(parking.id).set(parking.toFirestore()).awaitOrPending()
    }

    /** "Ya lo retiré": pasa al historial. */
    suspend fun finish(id: String) {
        parkings(requireUid()).document(id).update(
            mapOf(
                "status" to ParkingStatus.FINISHED.firestoreValue,
                "finishedAt" to Timestamp.now(),
            )
        ).awaitOrPending()
    }

    /** Guarda las rutas de las fotos (null = sin foto). */
    suspend fun setPhotoPaths(id: String, placePhotoPath: String?, signPhotoPath: String?) {
        parkings(requireUid()).document(id).update(
            mapOf("placePhotoUrl" to placePhotoPath, "signPhotoUrl" to signPhotoPath)
        ).awaitOrPending()
    }

    /** Borra el estacionamiento y sus fotos (Firestore no borra subcolecciones solo). */
    suspend fun delete(id: String) {
        photos.deleteAll(id)
        parkings(requireUid()).document(id).delete().awaitOrPending()
    }

    /*
     * Firestore guarda primero en el cache local y después sincroniza. Sin conexión, await()
     * recién termina cuando vuelve la red; para no congelar la pantalla se espera un rato y,
     * si no hubo respuesta, se da por buena la escritura (queda pendiente de sincronizar).
     * Un error real (por ejemplo, las reglas de seguridad) sí se propaga.
     */
    private suspend fun Task<Void>.awaitOrPending() {
        withTimeoutOrNull(WRITE_TIMEOUT_MS) { await() }
    }

    private companion object {
        const val TAG = "ParkingRepository"
        const val WRITE_TIMEOUT_MS = 3_000L
    }
}
