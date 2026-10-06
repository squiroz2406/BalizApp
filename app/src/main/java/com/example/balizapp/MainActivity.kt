package com.example.balizapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.balizapp.auth.AuthViewModel
import com.example.balizapp.auth.ui.AuthGate
import com.example.balizapp.navigation.AppNavHost
import com.example.balizapp.notifications.Notifications
import com.example.balizapp.ui.theme.BalizAppTheme

// FragmentActivity (subclase de ComponentActivity) porque BiometricPrompt la necesita.
class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    /** Estacionamiento a abrir en "Volver al auto" cuando se toca una notificación de vencimiento. */
    private val openReturnToCar = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannel(this)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            BalizAppTheme {
                val state by authViewModel.state.collectAsState()
                val pendingParkingId by openReturnToCar
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // AuthGate es la guardia: sin sesión válida muestra login/registro/verificación.
                    // Si se tocó una notificación sin sesión, se abre después de ingresar.
                    AuthGate(authViewModel, state) {
                        AppNavHost(
                            userName = state.userName,
                            userEmail = state.userEmail,
                            onSignOut = { authViewModel.signOut(this@MainActivity) },
                            openReturnToCar = pendingParkingId,
                            onOpenReturnToCarHandled = { openReturnToCar.value = null },
                        )
                    }
                }
            }
        }
    }

    // Con launchMode="singleTop", si la app ya está abierta la notificación llega por acá.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_RETURN_TO_CAR) {
            openReturnToCar.value = intent.getStringExtra(EXTRA_PARKING_ID)
        }
    }

    companion object {
        const val ACTION_OPEN_RETURN_TO_CAR = "com.example.balizapp.OPEN_RETURN_TO_CAR"
        const val EXTRA_PARKING_ID = "parkingId"
    }
}
