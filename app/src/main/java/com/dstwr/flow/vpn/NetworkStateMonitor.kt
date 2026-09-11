package com.dstwr.flow.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observes the physical internet transport without treating the app's VPN as upstream. */
class NetworkStateMonitor(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun currentState(): NetworkState {
        val networks = connectivity.allNetworks
        var hasOtherInternet = false
        for (network in networks) {
            val caps = connectivity.getNetworkCapabilities(network) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            hasOtherInternet = true
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    return NetworkState(true, NetworkState.Type.WIFI)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    return NetworkState(true, NetworkState.Type.MOBILE)
            }
        }
        return NetworkState(hasOtherInternet, NetworkState.Type.OTHER.takeIf { hasOtherInternet } ?: NetworkState.Type.NONE)
    }

    fun states(): Flow<NetworkState> = callbackFlow {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentState()) }
            override fun onLost(network: Network) { trySend(currentState()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(currentState())
            }
        }

        trySend(currentState())
        runCatching { connectivity.registerNetworkCallback(request, callback) }
            .onFailure { close(it) }
        awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
