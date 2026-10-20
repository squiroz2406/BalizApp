package com.example.balizapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.balizapp.auth.AuthViewModel
import com.example.balizapp.auth.ui.AuthGate
import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.navigation.AppNavHost
import com.example.balizapp.notifications.Notifications
import com.example.balizapp.ui.theme.BalizAppTheme
import com.example.balizapp.ui.theme.ThemeMode
import com.example.balizapp.ui.theme.ThemeViewModel

// FragmentActivity (subclase de ComponentActivity) porque BiometricPrompt la necesita.
class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    /** Estacionamiento a abrir en "Volver al auto" cuando se toca una notificación de vencimiento. */
    private val openReturnToCar = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannel(this)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val themeMode by themeViewModel.mode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Los íconos de las barras del sistema deben contrastar con el tema elegido, que puede
            // no coincidir con el del sistema.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose { }
            }
            BalizAppTheme(darkTheme = darkTheme) {
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
                            themeMode = themeMode,
                            onThemeModeChange = themeViewModel::setMode,
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
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        const val ACTION_OPEN_RETURN_TO_CAR = "com.example.balizapp.OPEN_RETURN_TO_CAR"
        const val EXTRA_PARKING_ID = "parkingId"
    }
}
