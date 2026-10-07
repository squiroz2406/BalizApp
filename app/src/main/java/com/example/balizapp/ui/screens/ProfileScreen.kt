package com.example.balizapp.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.balizapp.notifications.Notifications
import com.example.balizapp.notifications.ReminderScheduler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.balizapp.R
import com.example.balizapp.data.model.UserProfile
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.data.model.VehicleType
import com.example.balizapp.ui.components.ScreenScaffold

/** Perfil y ajustes (RF1): datos del usuario, vehículos, minutos de aviso y cerrar sesión. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    userName: String?,
    userEmail: String?,
    onSignOut: () -> Unit,
) {
    val state by vm.state.collectAsState()
    // null = diálogo cerrado; Vehicle() = alta; Vehicle con id = edición
    var editing by remember { mutableStateOf<Vehicle?>(null) }

    ScreenScaffold(title = "Perfil") {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(name = state.profile?.displayName ?: userName, email = userEmail)
            HorizontalDivider()

            SectionTitle("Mis vehículos")
            if (state.vehicles.isEmpty() && !state.loading) {
                Text(
                    "Todavía no cargaste ningún vehículo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.vehicles.forEach { vehicle ->
                ListItem(
                    headlineContent = { Text(vehicle.alias) },
                    supportingContent = {
                        Text(listOfNotNull(vehicle.type.label, vehicle.plate).joinToString(" · "))
                    },
                    leadingContent = { VehicleIcon(vehicle.type) },
                    trailingContent = {
                        TextButton(onClick = { editing = vehicle }) { Text("Editar") }
                    },
                )
            }
            OutlinedButton(onClick = { editing = Vehicle() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Agregar vehículo")
            }

            HorizontalDivider()
            SectionTitle("Aviso antes del vencimiento")
            val current = state.profile?.reminderMinutes ?: UserProfile.DEFAULT_REMINDER_MINUTES
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserProfile.REMINDER_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = minutes == current,
                        onClick = { vm.setReminderMinutes(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
            }

            AlertsStatus()

            state.error?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearError) { Text("Cerrar") }
                }
            }
        }

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            Text("  Cerrar sesión")
        }
    }

    editing?.let { vehicle ->
        VehicleDialog(
            initial = vehicle,
            onDismiss = { editing = null },
            onSave = { type, alias, plate -> vm.saveVehicle(vehicle.id, type, alias, plate) },
            onDelete = { vm.deleteVehicle(vehicle.id) },
            onDone = { editing = null },
        )
    }
}

/**
 * Estado de los permisos que necesitan los avisos (RF7) y un aviso de prueba.
 * Se vuelve a leer cada vez que la pantalla queda visible (por ejemplo, al volver de Ajustes).
 */
@Composable
private fun AlertsStatus() {
    val context = LocalContext.current
    val scheduler = remember { ReminderScheduler(context) }
    var canNotify by remember { mutableStateOf(Notifications.canPost(context)) }
    var canExact by remember { mutableStateOf(scheduler.canScheduleExact()) }
    var testSent by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        canNotify = Notifications.canPost(context)
        canExact = scheduler.canScheduleExact()
        onPauseOrDispose { }
    }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> canNotify = granted && Notifications.canPost(context) }

    if (!canNotify) {
        Text(
            "Las notificaciones están desactivadas: no vas a recibir los avisos.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }) { Text("Activar notificaciones") }
    }
    if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Text(
            "Sin \"Alarmas y recordatorios\" el aviso puede llegar unos minutos tarde.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }) { Text("Permitir alarmas exactas") }
    }
    TextButton(
        onClick = { scheduler.scheduleTest(seconds = 10); testSent = true },
        enabled = canNotify,
    ) { Text(if (testSent) "Aviso de prueba programado (10 s)" else "Enviar un aviso de prueba") }
}

@Composable
private fun Header(name: String?, email: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(name ?: "Sin nombre", style = MaterialTheme.typography.titleLarge)
        email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun VehicleIcon(type: VehicleType) {
    val icon = when (type) {
        VehicleType.CAR -> R.drawable.ic_car
        VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
    }
    Icon(painterResource(icon), contentDescription = type.label)
}

/**
 * Alta o edición de un vehículo. [onSave] devuelve un mensaje de error de validación,
 * o null si se guardó; en ese caso se llama a [onDone] para cerrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleDialog(
    initial: Vehicle,
    onDismiss: () -> Unit,
    onSave: (VehicleType, String, String) -> String?,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val isNew = initial.id.isEmpty()
    var type by remember { mutableStateOf(initial.type) }
    var alias by remember { mutableStateOf(initial.alias) }
    var plate by remember { mutableStateOf(initial.plate.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuevo vehículo" else "Editar vehículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    VehicleType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = type == option,
                            onClick = { type = option },
                            shape = SegmentedButtonDefaults.itemShape(index, VehicleType.entries.size),
                            icon = { VehicleIcon(option) },
                        ) { Text(option.label) }
                    }
                }
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it; error = null },
                    label = { Text("Nombre (por ejemplo, Mi Gol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Patente (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!isNew) {
                    TextButton(
                        onClick = {
                            if (confirmDelete) { onDelete(); onDone() } else confirmDelete = true
                        },
                    ) {
                        Text(
                            if (confirmDelete) "Tocá de nuevo para confirmar" else "Eliminar vehículo",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onSave(type, alias, plate)
                if (result == null) onDone() else error = result
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
