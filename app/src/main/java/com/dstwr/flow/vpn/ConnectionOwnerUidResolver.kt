package com.dstwr.flow.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import java.net.InetSocketAddress

/** Resolves the UID owning an intercepted TCP/UDP connection on API 29+. */
class ConnectionOwnerUidResolver(
    context: Context,
    private val policies: TrafficPolicyRegistry
) : TrafficIdentityResolver {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val packageManager = appContext.packageManager

    override fun resolve(packet: ParsedPacket, direction: TrafficDirection): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (packet.protocol != 6 && packet.protocol != 17) return null
        if (packet.sourcePort <= 0 || packet.destinationPort <= 0) return null

        val local = InetSocketAddress(packet.sourceAddress, packet.sourcePort)
        val remote = InetSocketAddress(packet.destinationAddress, packet.destinationPort)
        val uid = runCatching {
            connectivity.getConnectionOwnerUid(packet.protocol, local, remote)
        }.getOrDefault(Process.INVALID_UID)
        if (uid == Process.INVALID_UID) return null

        val packages = packageManager.getPackagesForUid(uid)?.asSequence().orEmpty()
        return packages.firstOrNull { policies.get(it) != null } ?: packages.firstOrNull()
    }
}
