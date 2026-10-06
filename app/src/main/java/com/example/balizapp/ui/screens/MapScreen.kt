package com.example.balizapp.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import com.example.balizapp.ui.components.EmptyState
import com.example.balizapp.ui.components.ScreenScaffold

/** Mapa general (RF5): ubicación actual y marcadores de los estacionamientos. Etapa 4. */
@Composable
fun MapScreen() {
    ScreenScaffold(title = "Mapa") {
        EmptyState(
            icon = Icons.Filled.Place,
            title = "Mapa",
            text = "En la etapa 4 se muestra tu ubicación y tus estacionamientos.",
        )
    }
}
