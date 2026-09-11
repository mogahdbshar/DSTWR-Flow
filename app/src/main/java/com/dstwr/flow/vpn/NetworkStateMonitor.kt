package com.dstwr.flow.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NetworkStateMonitor(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun currentState(): NetworkState = readCurrentState()

    fun states(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(readCurrentState()) }
            override fun onLost(network: Network) { trySend(readCurrentState()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(readCurrentState())
            }
        }

        trySend(readCurrentState())
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onFailure { close(it) }
        awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }

    private fun readCurrentState(): NetworkState {
        val network = connectivity.activeNetwork ?: return NetworkState(false, NetworkState.Type.NONE)
        val caps = connectivity.getNetworkCapabilities(network)
            ?: return NetworkState(false, NetworkState.Type.NONE)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.Type.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.Type.MOBILE
            else -> NetworkState.Type.OTHER
        }
        return NetworkState(
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            networkType = type
        )
    }
}
