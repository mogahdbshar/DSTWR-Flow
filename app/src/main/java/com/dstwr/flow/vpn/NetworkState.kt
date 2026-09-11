package com.dstwr.flow.vpn

data class NetworkState(
    val connected: Boolean,
    val networkType: Type
) {
    enum class Type { NONE, WIFI, MOBILE, OTHER }
}
