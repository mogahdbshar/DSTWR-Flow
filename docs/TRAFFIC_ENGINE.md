# DSTWR Flow Traffic Engine

## Purpose

DSTWR Flow now contains a local user-space forwarding layer inside the Android application. It does not use a remote VPN server and does not use Go, NDK, or a native tunnel binary.

## Data path

```text
Android application
        |
        v
VpnService / TUN
        |
        v
TrafficEngine
        |
        +--> PacketParser
        |
        +--> ConnectionOwnerUidResolver (Android 10+)
        |
        +--> TrafficPolicyRegistry
        |
        +--> SpeedLimitRegistry / TokenBucket
        |
        +--> UserSpaceForwardingTransport
                  |
                  +--> TCP bridge -> protected java.net.Socket
                  |
                  +--> UDP bridge -> protected DatagramSocket
                  |
                  +--> response packet queue -> TUN
```

## Important Android behavior

The Android VPN API supplies IP packets through the TUN file descriptor and expects the VPN application to process outgoing packets and inject incoming packets back into the interface. Upstream sockets must be protected from the VPN to avoid routing the application's own forwarding sockets back into its TUN interface.

Android 10 (API 29) and newer expose `ConnectivityManager.getConnectionOwnerUid()` to the active VPN service. DSTWR Flow uses this API when available to associate intercepted TCP/UDP flows with an Android UID and then map that UID to an installed package.

## TCP

TCP connections are terminated locally by DSTWR Flow and bridged to a protected Java socket. The local side receives a SYN, the application opens the upstream socket, returns a SYN/ACK, accepts the application's ACK, forwards payload bytes to the upstream socket, and converts upstream bytes back into TCP packets for the TUN interface.

This is a user-space TCP proxy, not kernel-level TCP forwarding.

## UDP

UDP flows are mapped to protected DatagramSockets. Outgoing datagrams are sent to the original destination. Incoming datagrams are converted back into IP/UDP packets and queued for the TUN writer.

This also covers ordinary DNS over UDP when the destination is reachable through the underlying network.

## Policy enforcement

The packet decision layer runs before forwarding:

1. Parse the IP packet.
2. Resolve the application identity when Android exposes it.
3. Apply emergency blocking.
4. Apply per-application block rules.
5. Apply upload/download token-bucket limits.
6. Forward, throttle, or drop the packet.
7. Meter the result.

Quota decisions are incorporated into the runtime policy snapshot before the VPN transport is established.

## Per-app VPN scope

DSTWR Flow uses `VpnService.Builder.addAllowedApplication()` for the managed package set. Unmanaged applications remain on normal system networking. When emergency mode is enabled, the VPN is established without an allow-list so the entire device is placed in the local forwarding path.

## Known technical boundaries

- `ConnectivityManager.getConnectionOwnerUid()` is available from API 29. Older Android versions cannot reliably recover the originating UID from a raw TUN packet using this API, so exact per-app packet enforcement is not guaranteed below API 29.
- TCP retransmission, congestion-control behavior, unusual extension options, and every possible protocol edge case still require real-device interoperability testing. The current implementation is deliberately a user-space proxy rather than a claim of being a replacement TCP/IP kernel stack.
- QUIC is carried over UDP and therefore uses the UDP forwarding path, but application behavior still depends on correct UDP flow handling.
- IPv4 and IPv6 packet construction/parsing are implemented in the codec. Real-device IPv6 interoperability must be validated separately because Android networking and IPv6 extension behavior vary by device/network.
- No build was automatically triggered while implementing this layer. The repository workflow remains manual-only.

## Safety model

All upstream TCP and UDP sockets are passed through `VpnService.protect()` so their own traffic bypasses the local VPN interface. Without this protection the forwarding loop could recursively capture its own traffic.

The application still requires explicit Android VPN consent. The VPN service remains local to the device and does not send traffic to a DSTWR-owned remote server.
