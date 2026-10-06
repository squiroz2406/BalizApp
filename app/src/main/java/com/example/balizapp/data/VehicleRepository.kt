package com.example.balizapp.data

import android.util.Log
import com.example.balizapp.data.model.Vehicle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Vehículos del usuario: users/{uid}/vehicles. */
class VehicleRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun vehicles(uid: String) = db.collection("users").document(uid).collection("vehicles")

    private fun requireUid(): String = auth.currentUser?.uid ?: error("No hay sesión iniciada")

    /** Vehículos del usuario con sesión, ordenados por alias, en tiempo real. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVehicles(): Flow<List<Vehicle>> = auth.uidFlow().flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else vehicles(uid).orderBy("alias", Query.Direction.ASCENDING).observe()
            .map { snapshot -> snapshot.documents.map(Vehicle::from) }
            .catch { e ->
                Log.w(TAG, "Error leyendo vehículos", e)
                emit(emptyList())
            }
    }

    /** Crea el vehículo si no tiene id; si tiene, lo reemplaza. */
    suspend fun save(vehicle: Vehicle) {
        val collection = vehicles(requireUid())
        if (vehicle.id.isEmpty()) collection.add(vehicle.toFirestore()).await()
        else collection.document(vehicle.id).set(vehicle.toFirestore()).await()
    }

    suspend fun delete(vehicleId: String) {
        vehicles(requireUid()).document(vehicleId).delete().await()
    }

    private companion object {
        const val TAG = "VehicleRepository"
    }
}
