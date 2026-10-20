package com.example.balizapp.data

import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.R
import android.graphics.BitmapFactory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** ¿Se puede estacionar ahora según el cartel? */
enum class ParkingVerdict { ALLOWED, FORBIDDEN, UNKNOWN }

/** Lo que Gemini entendió de la foto del cartel. Es una propuesta: el usuario la revisa. */
data class SignAnalysis(
    /** Regla resumida, por ejemplo "Medido, máx. 2 h, lun a vie de 8 a 18". */
    val ruleSummary: String?,
    val verdict: ParkingVerdict,
    /** Tiempo máximo que se puede quedar a partir de ahora, en minutos. */
    val maxMinutes: Int?,
    /** Hora (HH:mm) hasta la que se puede estar, si el cartel indica un horario. */
    val allowedUntil: String?,
    /** Explicación corta de por qué sí o por qué no. */
    val explanation: String?,
    /** "high", "medium" o "low". */
    val confidence: String,
)

/**
 * Usa Gemini (Firebase AI Logic) para leer fotos: el cartel de la calle (reglas y si se puede estacionar
 * ahora) y el lugar (una descripción corta para reconocerlo al volver).
 */
class SignAnalyzer {
    private val backend = Firebase.ai(backend = GenerativeBackend.googleAI())

    private val signModel by lazy {
        backend.generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = Schema.obj(
                    mapOf(
                        "readable" to Schema.boolean(),
                        "ruleSummary" to Schema.string(nullable = true),
                        "verdict" to Schema.enumeration(listOf("allowed", "forbidden", "unknown")),
                        "maxMinutes" to Schema.integer(nullable = true),
                        "allowedUntil" to Schema.string(nullable = true),
                        "explanation" to Schema.string(nullable = true),
                        "confidence" to Schema.enumeration(listOf("high", "medium", "low")),
                    )
                )
            },
        )
    }

    private val placeModel by lazy { backend.generativeModel(modelName = MODEL) }

    /** Lee el cartel de [jpeg]. Devuelve null si la foto no tiene un cartel legible. */
    suspend fun analyzeSign(jpeg: ByteArray, now: LocalDateTime = LocalDateTime.now()): SignAnalysis? {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: error(AppLocale.str(R.string.form_err_photo_read))
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR"))
        val language = AppLocale.current().nativeName
        val prompt = """
            Sos un asistente que lee carteles de estacionamiento de calles de Argentina.
            Ahora es $day ${now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))} (hora local).
            Mirá la foto del cartel y respondé en JSON:
            - readable: false si en la foto no hay un cartel de estacionamiento que se pueda leer.
            - ruleSummary: la regla en una frase corta (por ejemplo "Medido, máx. 2 h, lun a vie de 8 a 18").
            - verdict: "allowed" si ahora se puede estacionar, "forbidden" si ahora está prohibido o
              hay riesgo de grúa o multa, "unknown" si no alcanza la información.
            - maxMinutes: minutos que se puede quedar a partir de ahora (solo si el cartel fija un máximo), si no null.
            - allowedUntil: hora HH:mm (24 h) a partir de la cual deja de poder estar o empieza una prohibición HOY, si no null.
            - explanation: una frase corta de por qué sí o por qué no.
            - confidence: qué tan seguro estás de haber leído bien.
            No inventes: si no estás seguro de algo, usá null o "unknown" y confidence "low".
            Escribí ruleSummary y explanation en $language.
        """.trimIndent()
        val response = signModel.generateContent(content { image(bitmap); text(prompt) })
        val json = JSONObject(response.text ?: error(AppLocale.str(R.string.form_err_ai_no_response)))
        if (!json.optBoolean("readable", false)) return null
        return SignAnalysis(
            ruleSummary = json.optStringOrNull("ruleSummary"),
            verdict = when (json.optString("verdict")) {
                "allowed" -> ParkingVerdict.ALLOWED
                "forbidden" -> ParkingVerdict.FORBIDDEN
                else -> ParkingVerdict.UNKNOWN
            },
            maxMinutes = if (json.isNull("maxMinutes")) null else json.optInt("maxMinutes").takeIf { it > 0 },
            allowedUntil = json.optStringOrNull("allowedUntil")?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) },
            explanation = json.optStringOrNull("explanation"),
            confidence = json.optString("confidence", "low"),
        )
    }

    /** Descripción corta del lugar para recordarlo ("Frente a la farmacia, junto a un árbol"). */
    suspend fun describePlace(jpeg: ByteArray, address: String?): String? {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: error(AppLocale.str(R.string.form_err_photo_read))
        val language = AppLocale.current().nativeName
        val prompt = """
            Esta foto muestra dónde alguien dejó su vehículo${address?.let { " (dirección: $it)" }.orEmpty()}.
            Escribí en $language UNA frase corta (máximo 15 palabras) que ayude a reconocer el lugar al volver:
            referencias fijas como comercios, carteles, árboles, esquinas o columnas. No describas el vehículo ni
            inventes lo que no se ve. Respondé solo la frase, sin comillas.
        """.trimIndent()
        val response = placeModel.generateContent(content { image(bitmap); text(prompt) })
        return response.text?.trim()?.trim('"')?.ifBlank { null }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifBlank { null }

    private companion object {
        const val MODEL = "gemini-2.5-flash"
    }
}
