package com.dstwr.flow.vpn

/** JNI boundary for the bundled gVisor/tun2socks forwarding engine. */
object NativeTun2Socks {
    private var loaded = false

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        return runCatching {
            System.loadLibrary("dstwr_tun2socks")
            loaded = true
        }.isSuccess
    }

    fun start(tunFd: Int): Boolean {
        if (!ensureLoaded() || tunFd < 0) return false
        return runCatching { nativeStart(tunFd) }.getOrDefault(false)
    }

    fun stop() {
        if (!loaded) return
        runCatching { nativeStop() }
    }

    private external fun nativeStart(tunFd: Int): Boolean
    private external fun nativeStop()
}
