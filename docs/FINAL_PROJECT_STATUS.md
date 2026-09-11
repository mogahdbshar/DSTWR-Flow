# DSTWR Flow - Final Engineering Status

## Product contract

DSTWR Flow is a local-first Android network control application using Kotlin, Jetpack Compose, Room, DataStore, NetworkStatsManager and Android VpnService.

Application ID: `com.dstwr.flow`
Minimum API: 24
Target API: 35

## Product and UI completion

- Branded DSTWR Flow identity.
- Dedicated vector application logo used by the launcher and service notification.
- AndroidX SplashScreen integration for a consistent startup experience across supported Android versions.
- Material 3 design system with refined light/dark palettes, typography and system-theme detection.
- Dashboard, application management, statistics and settings surfaces.
- App search, filtering and per-app policy editor.
- Wi-Fi/mobile policy scope.
- Daily/monthly quotas and schedules, including overnight schedules.
- Usage access, VPN consent and notification permission flows surfaced explicitly in settings.
- Diagnostics and privacy information.

## Network engine

- Android VpnService/TUN packet path.
- Defensive IPv4 and IPv6 parsing.
- IPv4/IPv6 TCP and UDP packet construction with checksums.
- Bounded five-tuple flow table with reverse lookup and expiry.
- Android 10+ connection-owner UID resolution when the framework exposes the mapping.
- Trusted flow identity caching for reverse traffic instead of guessing application identity from packet contents.
- Runtime traffic policy registry.
- Upload/download token-bucket control path.
- Local user-space TCP bridge using protected Java sockets.
- Local user-space UDP bridge using protected DatagramSockets.
- Response packet queue with an idle-safe blocking read so the forwarding engine does not shut down simply because traffic is temporarily quiet.
- Protected upstream sockets using `VpnService.protect()` to prevent recursive VPN capture.
- Integration of the transport with `FlowVpnService` and `TrafficEngine`.

## Performance model

- Physical-network changes use ConnectivityManager callbacks instead of continuous high-frequency polling.
- Quota maintenance uses a low-frequency interval.
- Packet handling is event-driven around the TUN descriptor and blocking sockets.
- No cloud telemetry or remote traffic forwarding.
- No Go, NDK, native tunnel binary or external VPN server.

## Capability boundary

The forwarding engine is a genuine user-space TCP/UDP forwarding implementation, but it is not a replacement for Android's kernel TCP/IP stack. TCP retransmission/congestion behavior, unusual options, captive portals, IPv6 extension cases, QUIC-heavy applications, OEM networking differences and long-lived connections still require real-device interoperability testing.

Android versions below API 29 do not expose the same connection-owner UID API, so exact per-app packet attribution cannot be guaranteed for every intercepted flow on those versions.

The speed limiter is wired into the forwarding decision loop and retries throttled packets until the token bucket permits forwarding. Its real-world accuracy still requires device testing under sustained traffic.

## Security and privacy rules

- No remote VPN server is required.
- No cloud account is required.
- No root access is required.
- Android permission/consent boundaries are explicit.
- The app must never guess an application's identity from a packet.
- Unsupported traffic-control capabilities must remain visibly unsupported instead of silently failing.
- Upstream forwarding sockets must remain protected from the VPN interface.

## Build policy

GitHub Actions is manual-only. No automatic workflow trigger is configured.

The implementation batch is complete before the final build. The build is a validation step and is intentionally not triggered automatically.

## Required real-device acceptance tests

1. APK compilation and unit tests.
2. VPN consent and cancellation.
3. Enable/disable protection.
4. One-app blocking on Wi-Fi.
5. One-app blocking on mobile data.
6. Wi-Fi/mobile transition while protection is active.
7. Emergency block.
8. Reboot restoration with and without VPN consent.
9. Usage access granted/revoked.
10. Notification permission on Android 13+.
11. Daily/monthly quota warning and enforcement.
12. Schedule activation/deactivation, including overnight schedules.
13. IPv4 TCP browsing and HTTPS.
14. IPv4 UDP and DNS.
15. IPv6 TCP/UDP where the network provides IPv6.
16. Sustained upload and download speed-limit behavior.
17. Verify unblocked applications retain Internet access while blocked applications are isolated.
18. Long-running battery, memory and thermal behavior.
19. Recovery after network disconnect/reconnect.
20. Recovery after stopping and restarting protection.

## Final truth statement

The repository now contains the real forwarding transport and the complete product wiring needed for device validation. A repository build can prove compilation/tests, but only real-device acceptance can prove transparent forwarding behavior, per-app attribution, speed accuracy and compatibility across Android/OEM/network combinations.
