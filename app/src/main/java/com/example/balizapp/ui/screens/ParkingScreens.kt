package com.example.balizapp.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.balizapp.ui.components.EmptyState
import com.example.balizapp.ui.components.ScreenScaffold

/*
 * Pantallas secundarias del estacionamiento. En esta etapa solo muestran qué van a hacer
 * y los botones que llevan a la siguiente pantalla, para probar la navegación completa.
 */

/** Nuevo / editar estacionamiento (RF4, RF5, RF6). Etapas 3 a 5. */
@Composable
fun ParkingFormScreen(
    parkingId: String?,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit,
    onSaved: () -> Unit,
) {
    val editing = parkingId != null
    ScreenScaffold(title = if (editing) "Editar estacionamiento" else "Nuevo estacionamiento", onBack = onBack) {
        OutlinedButton(onClick = onOpenAssistant, modifier = Modifier.fillMaxWidth()) {
            Text("Leer el cartel con el asistente")
        }
        EmptyState(
            icon = Icons.Filled.Create,
            title = "Formulario",
            text = "Vehículo, nota, piso o sector, límite de tiempo y fotos. Se arma en la etapa 3.",
        )
        Button(onClick = onSaved, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
    }
}

/** Detalle de un estacionamiento (RF3). Etapa 3. */
@Composable
fun ParkingDetailScreen(
    parkingId: String,
    onBack: () -> Unit,
    onReturnToCar: () -> Unit,
    onEdit: () -> Unit,
) {
    ScreenScaffold(title = "Detalle", onBack = onBack) {
        EmptyState(
            icon = Icons.Filled.Info,
            title = "Estacionamiento $parkingId",
            text = "Fotos, mapa chico y cuenta regresiva. Se arma en la etapa 3.",
        )
        Button(onClick = onReturnToCar, modifier = Modifier.fillMaxWidth()) { Text("Volver al auto") }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar") }
        // TODO(etapa 3): "Ya lo retiré" marca status = finished.
    }
}

/** Volver al auto (RF5, RF6): mapa, distancia y flecha de brújula. Etapa 7. */
@Composable
fun ReturnToCarScreen(parkingId: String, onBack: () -> Unit) {
    ScreenScaffold(title = "Volver al auto", onBack = onBack) {
        EmptyState(
            icon = Icons.Filled.LocationOn,
            title = "Brújula hacia el auto",
            text = "Distancia y flecha que apunta al estacionamiento $parkingId. Se arma en la etapa 7.",
        )
    }
}

/** Asistente del cartel (agente de IA). Etapa 8. */
@Composable
fun SignAssistantScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Asistente del cartel", onBack = onBack) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "Asistente",
            text = "Foto del cartel, texto o voz. Muestra lo que entendió y pide confirmar. Etapa 8.",
        )
    }
}
