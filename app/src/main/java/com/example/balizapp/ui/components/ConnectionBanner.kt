package com.example.balizapp.ui.components

import com.example.balizapp.R
import androidx.compose.ui.res.stringResource
import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.balizapp.data.ParkingRepository
import com.example.balizapp.data.observeOnline
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SyncStatus { ONLINE, OFFLINE, SYNCING }

/** Combina el estado de la red con las escrituras de Firestore que todavía no se subieron. */
class ConnectionViewModel(app: Application) : AndroidViewModel(app) {
    val status: StateFlow<SyncStatus> = combine(
        app.observeOnline(),
        ParkingRepository().observePendingWrites(),
    ) { online, pending ->
        when {
            !online -> SyncStatus.OFFLINE
            pending -> SyncStatus.SYNCING
            else -> SyncStatus.ONLINE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.ONLINE)
}

/** Franja arriba del contenido: avisa si no hay conexión o si hay cambios por sincronizar. */
@Composable
fun ConnectionBanner(vm: ConnectionViewModel, modifier: Modifier = Modifier) {
    val status by vm.status.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = status != SyncStatus.ONLINE, modifier = modifier) {
        val offline = status == SyncStatus.OFFLINE
        Text(
            if (offline) stringResource(R.string.map_banner_offline)
            else stringResource(R.string.map_banner_syncing),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (offline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (offline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
