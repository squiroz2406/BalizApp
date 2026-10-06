package com.example.balizapp.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.balizapp.ui.screens.HomeScreen
import com.example.balizapp.ui.screens.MapScreen
import com.example.balizapp.ui.screens.ParkingDetailScreen
import com.example.balizapp.ui.screens.ParkingFormScreen
import com.example.balizapp.ui.screens.ProfileScreen
import com.example.balizapp.ui.screens.ProfileViewModel
import com.example.balizapp.ui.screens.ReturnToCarScreen
import com.example.balizapp.ui.screens.SignAssistantScreen
import kotlin.reflect.KClass

/** Un destino de la barra inferior. */
private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, HomeRoute::class, "Inicio", Icons.Filled.Home),
    TopLevelDestination(MapRoute, MapRoute::class, "Mapa", Icons.Filled.Place),
    TopLevelDestination(ProfileRoute, ProfileRoute::class, "Perfil", Icons.Filled.Person),
)

/**
 * Navegación de la app una vez que hay sesión. El login no está acá: AuthGate (en MainActivity)
 * solo muestra este NavHost cuando el usuario está autenticado y verificado, así que funciona
 * como guardia de todas las pantallas.
 */
@Composable
fun AppNavHost(
    userName: String?,
    userEmail: String?,
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { currentDestination.isOn(it.routeClass) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(dest.routeClass),
                            onClick = { navController.navigateToTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            // consumeWindowInsets evita que las barras superiores de cada pantalla
            // vuelvan a sumar el espacio de la barra de estado.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onParkHere = { navController.navigate(ParkingFormRoute()) },
                    onOpenParking = { id -> navController.navigate(ParkingDetailRoute(id)) },
                )
            }
            composable<MapRoute> { MapScreen() }
            composable<ProfileRoute> {
                ProfileScreen(
                    vm = viewModel<ProfileViewModel>(),
                    userName = userName,
                    userEmail = userEmail,
                    onSignOut = onSignOut,
                )
            }
            composable<ParkingFormRoute> { entry ->
                val route = entry.toRoute<ParkingFormRoute>()
                ParkingFormScreen(
                    parkingId = route.parkingId,
                    onBack = { navController.popBackStack() },
                    onOpenAssistant = { navController.navigate(SignAssistantRoute) },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable<ParkingDetailRoute> { entry ->
                val route = entry.toRoute<ParkingDetailRoute>()
                ParkingDetailScreen(
                    parkingId = route.parkingId,
                    onBack = { navController.popBackStack() },
                    onReturnToCar = { navController.navigate(ReturnToCarRoute(route.parkingId)) },
                    onEdit = { navController.navigate(ParkingFormRoute(route.parkingId)) },
                )
            }
            composable<ReturnToCarRoute> { entry ->
                val route = entry.toRoute<ReturnToCarRoute>()
                ReturnToCarScreen(parkingId = route.parkingId, onBack = { navController.popBackStack() })
            }
            composable<SignAssistantRoute> {
                SignAssistantScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavDestination?.isOn(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true

/** Cambia de pestaña sin apilar pantallas y conservando el estado de cada una. */
private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
