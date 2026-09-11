package com.dstwr.flow.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TUN packet pump. It provides bounded packet parsing, connection tracking,
 * live counters and directional rate decisions. Actual upstream forwarding is
 * deliberately isolated behind PacketTransport so the service never pretends
 * a packet was delivered when no transport exists.
 */
class TrafficEngine(
    private val scope: CoroutineScope,
    private val input: InputStream,
    private val output: OutputStream,
    private val transport: PacketTransport,
    private val decisionEngine: PacketDecisionEngine
) {
    private val running = AtomicBoolean(false)
    private var job: Job? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(MAX_PACKET_SIZE)
            try {
                while (isActive && running.get()) {
                    val count = input.read(buffer)
                    if (count <= 0) continue
                    when (val decision = decisionEngine.inspect(buffer, count, transport.packageFor(buffer, count), upload = true)) {
                        is PacketDecisionEngine.Decision.Forward -> transport.forward(buffer, count, decision.packet)
                        is PacketDecisionEngine.Decision.Throttled -> delay(decision.retryAfterMillis)
                        PacketDecisionEngine.Decision.Malformed -> Unit
                    }
                }
            } catch (_: IOException) {
                // Interface shutdown is an expected lifecycle event.
            } finally {
                running.set(false)
                transport.close()
            }
        }
    }

    fun stop() {
        running.set(false)
        job?.cancel()
        job = null
        transport.close()
    }

    interface PacketTransport {
        fun packageFor(buffer: ByteArray, length: Int): String? = null
        fun forward(buffer: ByteArray, length: Int, packet: ParsedPacket)
        fun close()
    }

    companion object {
        const val MAX_PACKET_SIZE = 65_535
    }
}
