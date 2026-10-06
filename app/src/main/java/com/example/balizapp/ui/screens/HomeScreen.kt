package com.example.balizapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.balizapp.ui.components.EmptyState
import com.example.balizapp.ui.components.ScreenScaffold

/**
 * Inicio (RF2). En la etapa 3 muestra el estacionamiento activo arriba y el historial debajo.
 * Por ahora tiene un registro de ejemplo para poder recorrer la navegación.
 */
@Composable
fun HomeScreen(
    onParkHere: () -> Unit,
    onOpenParking: (String) -> Unit,
) {
    ScreenScaffold(title = "BalizApp") {
        Button(
            onClick = onParkHere,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null)
            Text("  Estacioné aquí", style = MaterialTheme.typography.titleMedium)
        }

        // TODO(etapa 3): reemplazar por el estacionamiento activo leído de Firestore.
        Text("Ejemplo temporal", style = MaterialTheme.typography.labelLarge)
        ElevatedCard(onClick = { onOpenParking("demo") }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mi auto · frente a la farmacia", style = MaterialTheme.typography.titleMedium)
                Text("Vence a las 16:00", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        EmptyState(
            icon = Icons.Filled.Place,
            title = "Todavía no hay historial",
            text = "Cuando retires el auto, el estacionamiento aparece acá.",
        )
    }
}
