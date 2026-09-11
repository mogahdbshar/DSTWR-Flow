package com.dstwr.flow.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direction-aware TUN packet pump.
 *
 * The engine only forwards packets through the supplied transport. It never
 * claims delivery when the transport cannot provide it. Download traffic is
 * injected back into the TUN interface by the transport implementation.
 */
class TrafficEngine(
    private val scope: CoroutineScope,
    private val input: InputStream,
    private val output: OutputStream,
    private val transport: PacketTransport,
    private val decisionEngine: PacketDecisionEngine
) {
    private val running = AtomicBoolean(false)
    private var uploadJob: Job? = null
    private var downloadJob: Job? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        uploadJob = scope.launch(Dispatchers.IO) { pumpUpload() }
        downloadJob = scope.launch(Dispatchers.IO) { pumpDownload() }
    }

    private suspend fun pumpUpload() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        try {
            while (isActive && running.get()) {
                val count = input.read(buffer)
                if (count <= 0) continue
                when (val decision = decisionEngine.inspect(buffer, count, TrafficDirection.UPLOAD)) {
                    is PacketDecisionEngine.Decision.Forward -> transport.forwardUpload(buffer, count, decision.packet)
                    is PacketDecisionEngine.Decision.Throttled -> {
                        delay(decision.retryAfterMillis)
                        if (isActive && running.get()) {
                            transport.forwardUpload(buffer, count, decision.packet)
                        }
                    }
                    PacketDecisionEngine.Decision.Malformed -> Unit
                }
            }
        } catch (_: IOException) {
            // TUN shutdown is an expected lifecycle event.
        } finally {
            stopFromWorker(downloadJob)
        }
    }

    private suspend fun pumpDownload() {
        try {
            while (isActive && running.get()) {
                val packet = transport.readDownload() ?: break
                when (val decision = decisionEngine.inspect(packet, packet.size, TrafficDirection.DOWNLOAD)) {
                    is PacketDecisionEngine.Decision.Forward -> writeToTun(packet)
                    is PacketDecisionEngine.Decision.Throttled -> {
                        delay(decision.retryAfterMillis)
                        if (isActive && running.get()) writeToTun(packet)
                    }
                    PacketDecisionEngine.Decision.Malformed -> Unit
                }
            }
        } catch (_: IOException) {
            // Transport shutdown is an expected lifecycle event.
        } finally {
            stopFromWorker(uploadJob)
        }
    }

    private fun writeToTun(packet: ByteArray) {
        output.write(packet)
        output.flush()
    }

    private fun stopFromWorker(sibling: Job?) {
        if (!running.compareAndSet(true, false)) return
        sibling?.cancel()
        transport.close()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        uploadJob?.cancel()
        downloadJob?.cancel()
        uploadJob = null
        downloadJob = null
        transport.close()
    }

    interface PacketTransport {
        fun forwardUpload(buffer: ByteArray, length: Int, packet: ParsedPacket)
        fun readDownload(): ByteArray?
        fun close()
    }

    companion object {
        const val MAX_PACKET_SIZE = 65_535
    }
}
