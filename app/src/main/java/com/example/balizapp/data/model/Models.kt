package com.example.balizapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint

/*
 * Modelos del dominio y su conversión desde/hacia Firestore.
 * La conversión es manual (y no con toObject) para que los nombres de campo y los valores
 * guardados ("car", "active", ...) coincidan exactamente con la estructura documentada.
 */

/** users/{uid} */
data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTES = 15
        val REMINDER_OPTIONS = listOf(5, 10, 15, 30)

        fun from(doc: DocumentSnapshot) = UserProfile(
            uid = doc.id,
            displayName = doc.getString("displayName"),
            email = doc.getString("email"),
            reminderMinutes = doc.getLong("reminderMinutes")?.toInt() ?: DEFAULT_REMINDER_MINUTES,
        )
    }
}

enum class VehicleType(val firestoreValue: String, val label: String) {
    CAR("car", "Auto"),
    MOTORCYCLE("motorcycle", "Moto");

    companion object {
        fun fromFirestore(value: String?) = entries.firstOrNull { it.firestoreValue == value } ?: CAR
    }
}

/** users/{uid}/vehicles/{vehicleId} */
data class Vehicle(
    val id: String = "",
    val type: VehicleType = VehicleType.CAR,
    val alias: String = "",
    val plate: String? = null,
) {
    fun toFirestore(): Map<String, Any?> = mapOf(
        "type" to type.firestoreValue,
        "alias" to alias,
        "plate" to plate,
    )

    companion object {
        fun from(doc: DocumentSnapshot) = Vehicle(
            id = doc.id,
            type = VehicleType.fromFirestore(doc.getString("type")),
            alias = doc.getString("alias").orEmpty(),
            plate = doc.getString("plate"),
        )
    }
}

enum class ParkingStatus(val firestoreValue: String) {
    ACTIVE("active"), FINISHED("finished");

    companion object {
        fun fromFirestore(value: String?) = entries.firstOrNull { it.firestoreValue == value } ?: ACTIVE
    }
}

enum class ParkingSource(val firestoreValue: String) {
    MANUAL("manual"), AGENT_SIGN("agent_sign"), AGENT_TEXT("agent_text");

    companion object {
        fun fromFirestore(value: String?) = entries.firstOrNull { it.firestoreValue == value } ?: MANUAL
    }
}

/** users/{uid}/parkings/{parkingId}. Se usa desde la etapa 3. */
data class Parking(
    val id: String = "",
    val vehicleId: String = "",
    val status: ParkingStatus = ParkingStatus.ACTIVE,
    val location: GeoPoint? = null,
    val addressText: String? = null,
    val note: String? = null,
    val levelSector: String? = null,
    /** Ruta en Storage (no URL pública), por ejemplo users/UID/parkings/ID/place.jpg */
    val placePhotoPath: String? = null,
    val signPhotoPath: String? = null,
    val startedAt: Timestamp = Timestamp.now(),
    val expiresAt: Timestamp? = null,
    val ruleSummary: String? = null,
    val source: ParkingSource = ParkingSource.MANUAL,
    val agentConfidence: String? = null,
    val finishedAt: Timestamp? = null,
) {
    fun toFirestore(): Map<String, Any?> = mapOf(
        "vehicleId" to vehicleId,
        "status" to status.firestoreValue,
        "location" to location,
        "addressText" to addressText,
        "note" to note,
        "levelSector" to levelSector,
        "placePhotoUrl" to placePhotoPath,
        "signPhotoUrl" to signPhotoPath,
        "startedAt" to startedAt,
        "expiresAt" to expiresAt,
        "ruleSummary" to ruleSummary,
        "source" to source.firestoreValue,
        "agentConfidence" to agentConfidence,
        "finishedAt" to finishedAt,
    )

    companion object {
        fun from(doc: DocumentSnapshot) = Parking(
            id = doc.id,
            vehicleId = doc.getString("vehicleId").orEmpty(),
            status = ParkingStatus.fromFirestore(doc.getString("status")),
            location = doc.getGeoPoint("location"),
            addressText = doc.getString("addressText"),
            note = doc.getString("note"),
            levelSector = doc.getString("levelSector"),
            placePhotoPath = doc.getString("placePhotoUrl"),
            signPhotoPath = doc.getString("signPhotoUrl"),
            startedAt = doc.getTimestamp("startedAt") ?: Timestamp.now(),
            expiresAt = doc.getTimestamp("expiresAt"),
            ruleSummary = doc.getString("ruleSummary"),
            source = ParkingSource.fromFirestore(doc.getString("source")),
            agentConfidence = doc.getString("agentConfidence"),
            finishedAt = doc.getTimestamp("finishedAt"),
        )
    }
}
