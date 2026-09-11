package com.dstwr.flow.vpn

import android.net.VpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Local user-space forwarding transport.
 *
 * TCP is terminated locally and bridged to a protected java.net.Socket.
 * UDP is bridged with protected DatagramSockets. Responses are converted back
 * into IP packets and delivered to the TUN writer. No remote VPN server is used.
 */
class UserSpaceForwardingTransport(
    private val vpnService: VpnService,
    private val scope: CoroutineScope,
    private val tunWriter: (ByteArray) -> Unit
) : TrafficEngine.PacketTransport {
    private val tcp = ConcurrentHashMap<TrafficFlowKey, TcpBridge>()
    private val udp = ConcurrentHashMap<TrafficFlowKey, UdpBridge>()
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun forwardUpload(buffer: ByteArray, length: Int, packet: ParsedPacket) {
        if (closed.get()) return
        val decoded = IpPacketCodec.decode(buffer, length) ?: return
        when (decoded.protocol) {
            6 -> forwardTcp(decoded)
            17 -> forwardUdp(decoded)
        }
    }

    override fun readDownload(): ByteArray? = null

    private fun forwardTcp(packet: IpPacketCodec.TransportPacket) {
        val key = packet.toFlowKey()
        if ((packet.flags and IpPacketCodec.TCP_RST) != 0) {
            tcp.remove(key)?.close()
            return
        }
        val bridge = tcp[key] ?: tcp[key.reversed()]
        if (bridge == null) {
            if ((packet.flags and IpPacketCodec.TCP_SYN) == 0) return
            val created = TcpBridge(packet)
            val existing = tcp.putIfAbsent(key, created)
            if (existing != null) existing.accept(packet) else {
                created.start()
                created.accept(packet)
            }
        } else {
            bridge.accept(packet)
        }
    }

    private fun forwardUdp(packet: IpPacketCodec.TransportPacket) {
        val key = packet.toFlowKey()
        val bridge = udp.computeIfAbsent(key) {
            UdpBridge(packet).also { it.start() }
        }
        bridge.send(packet.payload)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        tcp.values.forEach { it.close() }
        udp.values.forEach { it.close() }
        tcp.clear()
        udp.clear()
    }

    private inner class TcpBridge(private val first: IpPacketCodec.TransportPacket) {
        private val socket = Socket()
        private val key = first.toFlowKey()
        private val clientAddress = first.sourceAddress
        private val clientPort = first.sourcePort
        private val remoteAddress = first.destinationAddress
        private val remotePort = first.destinationPort
        private var clientNextSequence = first.sequence + 1L
        private var serverSequence = IpPacketCodec.randomSequence()
        private var established = false
        private var closedLocal = false

        fun start() {
            scope.launch(Dispatchers.IO) {
                try {
                    if (!vpnService.protect(socket)) throw IOException("Unable to protect TCP socket from VPN")
                    socket.connect(InetSocketAddress(InetAddress.getByName(remoteAddress), remotePort), CONNECT_TIMEOUT_MS)
                    sendToTun(IpPacketCodec.tcp(
                        first.ipVersion, remoteAddress, clientAddress, remotePort, clientPort,
                        serverSequence, clientNextSequence, IpPacketCodec.TCP_SYN or IpPacketCodec.TCP_ACK
                    ))
                    serverSequence++
                    readRemote()
                } catch (_: Exception) {
                    if (!closedLocal) {
                        sendToTun(IpPacketCodec.tcp(
                            first.ipVersion, remoteAddress, clientAddress, remotePort, clientPort,
                            serverSequence, clientNextSequence, IpPacketCodec.TCP_RST or IpPacketCodec.TCP_ACK
                        ))
                    }
                    close()
                }
            }
        }

        @Synchronized
        fun accept(packet: IpPacketCodec.TransportPacket) {
            if (closedLocal) return
            if ((packet.flags and IpPacketCodec.TCP_ACK) != 0) {
                established = true
                clientNextSequence = maxOf(clientNextSequence, packet.acknowledgement)
            }
            if (packet.payload.isNotEmpty() && established) {
                try {
                    socket.getOutputStream().write(packet.payload)
                    socket.getOutputStream().flush()
                    clientNextSequence = packet.sequence + packet.payload.size
                    sendToTun(IpPacketCodec.tcp(
                        first.ipVersion, remoteAddress, clientAddress, remotePort, clientPort,
                        serverSequence, clientNextSequence, IpPacketCodec.TCP_ACK
                    ))
                } catch (_: IOException) {
                    close()
                }
            }
            if ((packet.flags and IpPacketCodec.TCP_FIN) != 0) {
                clientNextSequence = maxOf(clientNextSequence, packet.sequence + 1L)
                sendToTun(IpPacketCodec.tcp(
                    first.ipVersion, remoteAddress, clientAddress, remotePort, clientPort,
                    serverSequence, clientNextSequence, IpPacketCodec.TCP_ACK
                ))
                close()
            }
        }

        private fun readRemote() {
            val input = socket.getInputStream()
            val buffer = ByteArray(MAX_TCP_PAYLOAD)
            while (scope.isActive && !closedLocal) {
                val count = input.read(buffer)
                if (count < 0) {
                    sendToTun(IpPacketCodec.tcp(
                        first.ipVersion, remoteAddress, clientAddress, remotePort, clientPort,
                        serverSequence, clientNextSequence, IpPacketCodec.TCP_FIN or IpPacketCodec.TCP_ACK
                    ))
                    serverSequence++
                    break
                }
                if (count == 0) continue
                val payload = buffer.copyOf(count)
                sendToTun(IpPacketCodec.tcp(
                    first.ipVersion, remoteAddress, clientAddress, remotePort, clientPort,
                    serverSequence, clientNextSequence, IpPacketCodec.TCP_PSH or IpPacketCodec.TCP_ACK,
                    payload = payload
                ))
                serverSequence += count
            }
            close()
        }

        @Synchronized
        fun close() {
            if (closedLocal) return
            closedLocal = true
            runCatching { socket.close() }
            tcp.remove(key, this)
        }
    }

    private inner class UdpBridge(private val first: IpPacketCodec.TransportPacket) {
        private val socket = DatagramSocket()
        private val key = first.toFlowKey()
        private var closedLocal = false

        fun start() {
            scope.launch(Dispatchers.IO) {
                try {
                    if (!vpnService.protect(socket)) throw IOException("Unable to protect UDP socket from VPN")
                    socket.connect(InetSocketAddress(InetAddress.getByName(first.destinationAddress), first.destinationPort))
                    val buffer = ByteArray(MAX_UDP_PACKET)
                    while (scope.isActive && !closedLocal) {
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)
                        val payload = response.data.copyOfRange(response.offset, response.offset + response.length)
                        sendToTun(IpPacketCodec.udp(
                            first.ipVersion,
                            first.destinationAddress,
                            first.sourceAddress,
                            first.destinationPort,
                            first.sourcePort,
                            payload
                        ))
                    }
                } catch (_: Exception) {
                    close()
                }
            }
        }

        @Synchronized
        fun send(payload: ByteArray) {
            if (closedLocal || payload.isEmpty()) return
            runCatching {
                socket.send(DatagramPacket(payload, payload.size))
            }.onFailure { close() }
        }

        @Synchronized
        fun close() {
            if (closedLocal) return
            closedLocal = true
            socket.close()
            udp.remove(key, this)
        }
    }

    private fun sendToTun(packet: ByteArray) {
        if (!closed.get()) tunWriter(packet)
    }

    private fun IpPacketCodec.TransportPacket.toFlowKey(): TrafficFlowKey = TrafficFlowKey(
        ipVersion = ipVersion,
        protocol = protocol,
        sourceAddress = sourceAddress,
        sourcePort = sourcePort,
        destinationAddress = destinationAddress,
        destinationPort = destinationPort
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val MAX_TCP_PAYLOAD = 16 * 1024
        private const val MAX_UDP_PACKET = 65_535
    }
}
