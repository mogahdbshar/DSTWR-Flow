package com.dstwr.flow.vpn

import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Process
import dev.netvalve.bridge.Bridge
import dev.netvalve.bridge.Handler
import dev.netvalve.bridge.TCPConn
import dev.netvalve.bridge.Tunnel
import dev.netvalve.bridge.UDPConn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** Production VPN bridge: gVisor handles TCP/IP, Kotlin handles policy and shaping. */
class ProductionNetstackBridge(
    private val vpnService: VpnService,
    private val scope: CoroutineScope,
    private val policies: TrafficPolicyRegistry,
    private val speedLimits: SpeedLimitRegistry
) {
    private val connectivity = vpnService.getSystemService(ConnectivityManager::class.java)
    private val packageManager = vpnService.packageManager
    private var tunnel: Tunnel? = null
    private val stopped = AtomicBoolean(false)

    fun start(tunFd: Int) {
        stopped.set(false)
        val handler = object : Handler {
            override fun handleTCP(srcIp: String, srcPort: Long, dstIp: String, dstPort: Long, conn: TCPConn) {
                scope.launch(Dispatchers.IO) {
                    relayTcp(srcIp, srcPort.toInt(), dstIp, dstPort.toInt(), conn)
                }
            }

            override fun handleUDP(srcIp: String, srcPort: Long, dstIp: String, dstPort: Long, conn: UDPConn) {
                scope.launch(Dispatchers.IO) {
                    relayUdp(srcIp, srcPort.toInt(), dstIp, dstPort.toInt(), conn)
                }
            }

            override fun log(level: Long, msg: String) {
                android.util.Log.println(
                    when (level.toInt()) {
                        3 -> android.util.Log.ERROR
                        2 -> android.util.Log.WARN
                        1 -> android.util.Log.INFO
                        else -> android.util.Log.DEBUG
                    },
                    "DSTWR-Netstack",
                    msg
                )
            }
        }
        tunnel = Bridge.newTunnel(tunFd.toLong(), 1500L, "RELAY", handler)
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { tunnel?.stop() }
        tunnel = null
    }

    private fun resolvePackage(protocol: Int, srcIp: String, srcPort: Int, dstIp: String, dstPort: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || srcPort <= 0 || dstPort <= 0) return null
        return runCatching {
            val uid = connectivity.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(InetAddress.getByName(srcIp), srcPort),
                InetSocketAddress(InetAddress.getByName(dstIp), dstPort)
            )
            if (uid == Process.INVALID_UID) return@runCatching null
            val packages = packageManager.getPackagesForUid(uid) ?: return@runCatching null
            packages.firstOrNull { policies.get(it) != null } ?: packages.firstOrNull()
        }.getOrNull()
    }

    private suspend fun relayTcp(srcIp: String, srcPort: Int, dstIp: String, dstPort: Int, appConn: TCPConn) {
        val packageName = resolvePackage(6, srcIp, srcPort, dstIp, dstPort)
        if (policies.isGlobalBlocked() || packageName?.let { policies.get(it)?.blocked == true } == true) {
            runCatching { appConn.close() }
            return
        }
        val upstream = Socket()
        try {
            if (!vpnService.protect(upstream)) {
                runCatching { appConn.close() }
                return
            }
            upstream.tcpNoDelay = true
            upstream.connect(InetSocketAddress(InetAddress.getByName(dstIp), dstPort), CONNECT_TIMEOUT_MS)
            coroutineScope {
                launch(Dispatchers.IO) {
                    val buffer = ByteArray(RELAY_BUFFER)
                    try {
                        while (!stopped.get()) {
                            val n = appConn.read(buffer)
                            if (n <= 0) break
                            paceUpload(packageName, n)
                            upstream.getOutputStream().write(buffer, 0, n)
                            upstream.getOutputStream().flush()
                        }
                    } finally {
                        runCatching { upstream.shutdownOutput() }
                    }
                }
                launch(Dispatchers.IO) {
                    val buffer = ByteArray(RELAY_BUFFER)
                    try {
                        val input = upstream.getInputStream()
                        while (!stopped.get()) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            paceDownload(packageName, n)
                            appConn.write(buffer.copyOf(n))
                        }
                    } finally {
                        runCatching { appConn.close() }
                    }
                }
            }
        } catch (_: Throwable) {
            runCatching { appConn.close() }
        } finally {
            runCatching { upstream.close() }
            runCatching { appConn.close() }
        }
    }

    private suspend fun relayUdp(srcIp: String, srcPort: Int, dstIp: String, dstPort: Int, appConn: UDPConn) {
        val packageName = resolvePackage(17, srcIp, srcPort, dstIp, dstPort)
        if (policies.isGlobalBlocked() || packageName?.let { policies.get(it)?.blocked == true } == true) {
            runCatching { appConn.close() }
            return
        }
        val upstream = DatagramSocket()
        try {
            if (!vpnService.protect(upstream)) {
                runCatching { appConn.close() }
                return
            }
            val address = InetAddress.getByName(dstIp)
            upstream.connect(InetSocketAddress(address, dstPort))
            upstream.soTimeout = UDP_TIMEOUT_MS
            coroutineScope {
                launch(Dispatchers.IO) {
                    try {
                        while (!stopped.get()) {
                            val data = appConn.receive() ?: break
                            paceUpload(packageName, data.size)
                            upstream.send(DatagramPacket(data, data.size, address, dstPort))
                        }
                    } catch (_: Throwable) {}
                }
                launch(Dispatchers.IO) {
                    try {
                        val buffer = ByteArray(MAX_UDP_PACKET)
                        while (!stopped.get()) {
                            val packet = DatagramPacket(buffer, buffer.size)
                            upstream.receive(packet)
                            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                            paceDownload(packageName, data.size)
                            appConn.send(data)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } finally {
            runCatching { upstream.close() }
            runCatching { appConn.close() }
        }
    }

    private suspend fun paceUpload(packageName: String?, bytes: Int) {
        if (packageName == null) return
        val result = speedLimits.consumeUpload(packageName, bytes.toLong())
        if (!result.allowed && result.retryAfterMillis > 0L) delay(result.retryAfterMillis)
    }

    private suspend fun paceDownload(packageName: String?, bytes: Int) {
        if (packageName == null) return
        val result = speedLimits.consumeDownload(packageName, bytes.toLong())
        if (!result.allowed && result.retryAfterMillis > 0L) delay(result.retryAfterMillis)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val UDP_TIMEOUT_MS = 30_000
        private const val RELAY_BUFFER = 16 * 1024
        private const val MAX_UDP_PACKET = 65_535
    }
}
