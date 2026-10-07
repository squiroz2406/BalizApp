package com.example.balizapp.navigation

import kotlinx.serialization.Serializable

/*
 * Rutas tipadas de Navigation Compose. Cada pantalla es un objeto o data class serializable;
 * los argumentos viajan como propiedades, sin armar strings a mano.
 */

// Destinos de la barra inferior
@Serializable object HomeRoute
@Serializable object MapRoute
@Serializable object ProfileRoute

// Pantallas secundarias
/** Alta (parkingId = null) o edición de un estacionamiento. */
@Serializable data class ParkingFormRoute(val parkingId: String? = null)
@Serializable data class ParkingDetailRoute(val parkingId: String)
@Serializable data class ReturnToCarRoute(val parkingId: String)
@Serializable object SignAssistantRoute
