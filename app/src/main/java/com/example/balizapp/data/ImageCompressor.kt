package com.example.balizapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Prepara una foto para guardarla en Firestore: la achica (lado mayor [MAX_SIDE_PX]),
 * la endereza según la orientación EXIF de la cámara y la comprime en JPEG hasta que
 * pese menos de [MAX_BYTES] (un documento de Firestore admite hasta 1 MiB).
 */
object ImageCompressor {
    const val MAX_SIDE_PX = 1280
    const val MAX_BYTES = 700 * 1024

    suspend fun compress(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1) Solo leer el tamaño, para decodificar ya reducida (ahorra memoria).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo leer la imagen" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("No se pudo leer la imagen")

        // 2) Escala fina + rotación EXIF en una sola transformación.
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        val scale = minOf(1f, MAX_SIDE_PX.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (bitmap !== decoded) decoded.recycle()

        // 3) JPEG bajando la calidad hasta entrar en el límite.
        var quality = 80
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 30)
        bitmap.recycle()
        require(bytes.size <= MAX_BYTES) { "La foto es demasiado grande" }
        bytes
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
