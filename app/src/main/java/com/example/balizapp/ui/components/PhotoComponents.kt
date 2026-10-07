package com.example.balizapp.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodifica un JPEG para mostrarlo en Compose (fuera del hilo principal). */
suspend fun ByteArray.toImageBitmap(): ImageBitmap? = withContext(Dispatchers.Default) {
    BitmapFactory.decodeByteArray(this@toImageBitmap, 0, size)?.asImageBitmap()
}

/**
 * Espacio para una foto (RF6): muestra la miniatura y botones para sacarla con la cámara,
 * elegirla de la galería o quitarla. [onPicked] recibe la imagen original sin comprimir.
 */
@Composable
fun PhotoSlot(
    label: String,
    bitmap: ImageBitmap?,
    processing: Boolean,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Uri del archivo que completa la app de cámara; se guarda por si Android recrea la pantalla.
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var viewing by rememberSaveable { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) onPicked(uri)
        pendingCameraUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = bitmap != null) { viewing = true },
            contentAlignment = Alignment.Center,
        ) {
            when {
                processing -> CircularProgressIndicator()
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Text("Sin foto", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            TextButton(
                onClick = {
                    val uri = newCameraUri(context)
                    pendingCameraUri = uri
                    takePicture.launch(uri)
                },
                enabled = !processing,
            ) { Text("Cámara") }
            TextButton(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !processing,
            ) { Text("Galería") }
            if (bitmap != null) {
                TextButton(onClick = onRemove, enabled = !processing) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (viewing && bitmap != null) PhotoViewer(bitmap, label) { viewing = false }
}

/** Foto en pantalla completa; se cierra tocándola. */
@Composable
fun PhotoViewer(bitmap: ImageBitmap, description: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = onDismiss)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = bitmap, contentDescription = description, contentScale = ContentScale.Fit)
        }
    }
}

/** Archivo temporal (cache/camera) compartido con la app de cámara vía FileProvider. */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
