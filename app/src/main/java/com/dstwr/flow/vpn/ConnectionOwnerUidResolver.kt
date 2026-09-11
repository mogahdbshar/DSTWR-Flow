package com.dstwr.flow.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import java.net.InetSocketAddress

/** Resolves application identity from Android's connection-owner API and remembers flows. */
class ConnectionOwnerUidResolver(
    context: Context,
    private val policies: TrafficPolicyRegistry
) : TrafficIdentityResolver {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val packageManager = appContext.packageManager
    private val sessions = TrafficFlowTable()

    override fun resolve(packet: ParsedPacket, direction: TrafficDirection): String? {
        if (packet.protocol != 6 && packet.protocol != 17) return null
        sessions.find(packet.toFlowKey())?.packageName?.let { return it }
        if (direction == TrafficDirection.DOWNLOAD) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (packet.sourcePort <= 0 || packet.destinationPort <= 0) return null

        val key = packet.toFlowKey()
        val local = InetSocketAddress(packet.sourceAddress, packet.sourcePort)
        val remote = InetSocketAddress(packet.destinationAddress, packet.destinationPort)
        val uid = runCatching {
            connectivity.getConnectionOwnerUid(packet.protocol, local, remote)
        }.getOrDefault(Process.INVALID_UID)
        if (uid == Process.INVALID_UID) return null

        val packageName = packageManager.getPackagesForUid(uid)
            ?.asSequence()
            ?.firstOrNull { policies.get(it) != null }
            ?: packageManager.getPackagesForUid(uid)?.firstOrNull()

        if (!packageName.isNullOrBlank()) sessions.bind(key, packageName)
        return packageName
    }

    fun clear() = sessions.clear()
}
