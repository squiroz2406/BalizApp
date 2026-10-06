package com.example.balizapp.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import com.example.balizapp.ui.components.EmptyState
import com.example.balizapp.ui.components.ScreenScaffold

/* Pantallas que todavía no están implementadas: muestran qué van a hacer. */

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
