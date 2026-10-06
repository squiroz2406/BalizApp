package com.example.balizapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.balizapp.auth.AuthViewModel
import com.example.balizapp.auth.ui.AuthGate
import com.example.balizapp.navigation.AppNavHost
import com.example.balizapp.ui.theme.BalizAppTheme

// FragmentActivity (subclase de ComponentActivity) porque BiometricPrompt la necesita.
class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BalizAppTheme {
                val state by authViewModel.state.collectAsState()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // AuthGate es la guardia: sin sesión válida muestra login/registro/verificación.
                    AuthGate(authViewModel, state) {
                        AppNavHost(
                            userName = state.userName,
                            userEmail = state.userEmail,
                            onSignOut = { authViewModel.signOut(this@MainActivity) },
                        )
                    }
                }
            }
        }
    }
}
