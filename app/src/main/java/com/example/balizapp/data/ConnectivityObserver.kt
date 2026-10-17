package com.example.balizapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** true mientras el teléfono tiene una red con acceso a internet; emite cada vez que cambia. */
fun Context.observeOnline(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    if (cm == null) {
        trySend(true)
        close()
        return@callbackFlow
    }
    fun online() = cm.getNetworkCapabilities(cm.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(online()) }
        override fun onLost(network: Network) { trySend(online()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(online()) }
    }
    trySend(online())
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
