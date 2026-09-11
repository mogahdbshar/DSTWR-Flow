# DSTWR Flow

DSTWR Flow is a local-first Android network control and data intelligence application. It is designed to understand device network usage and apply explicit per-app control without root or a remote VPN server.

## Identity

- Product: DSTWR Flow
- Brand: DSTWR
- Application ID: `com.dstwr.flow`
- Kotlin
- Jetpack Compose + Material 3
- Minimum Android API 24
- Target Android API 35
- Local-first and privacy-focused
- Arabic RTL + English LTR ready
- System-aware dark and light themes
- Branded launcher icon and Android SplashScreen
- Manual-only GitHub Actions build

## Implemented product areas

- Branded startup experience and application identity
- Dashboard
- Installed applications
- Per-app block policy
- Wi-Fi/mobile policy scope
- Daily/monthly quotas
- Time schedules, including overnight schedules
- Usage access integration
- Wi-Fi/mobile usage statistics
- Usage history and top-app views
- Quota warning/reached notifications
- Emergency protection mode
- Explicit VPN consent
- Foreground protection lifecycle
- Reboot safety handling
- Network transition detection without polling the physical network state continuously
- IPv4/IPv6 packet parsing and packet construction
- Bounded flow/session bookkeeping
- Runtime traffic policy registry
- Android 10+ connection-owner UID resolution
- Reverse-flow application identity caching
- Local user-space TCP forwarding through protected sockets
- Local user-space UDP forwarding through protected datagram sockets
- TUN response queue and reverse packet injection
- Upload/download token-bucket enforcement path
- Packet metering
- Diagnostics and privacy settings
- System-aware Material 3 design system
- Arabic/English-ready UI structure

## Real traffic engine

DSTWR Flow contains an actual local forwarding transport inside the application rather than only a TUN reader/blackhole.

The live path is:

`Android app -> TUN -> packet parser -> connection identity -> policy -> rate limiter -> local TCP/UDP forwarder -> protected system socket -> Internet -> response packet -> TUN -> Android app`

TCP is terminated and bridged through protected Java sockets. UDP is forwarded through protected datagram sockets. `VpnService.protect()` prevents the forwarding sockets from being captured by the VPN itself and avoids a routing loop.

Android 10+ `ConnectivityManager.getConnectionOwnerUid()` is used to recover the owner UID for intercepted TCP/UDP flows when the framework exposes that mapping to the active VPN service. The identity is cached in a bounded flow table so reverse packets can reuse the trusted association without guessing from packet contents.

## Performance and battery model

The design avoids a permanent high-frequency polling loop for physical network changes. Connectivity callbacks trigger policy re-evaluation only when the underlying network state changes, while quota notifications use a low-frequency maintenance interval. Traffic processing remains event-driven around the TUN descriptor and blocking network sockets.

The application keeps all policy and usage state on-device and does not upload telemetry to a DSTWR server.

## Technical boundaries

This is a real user-space forwarding implementation, but it is not presented as a replacement for Android's kernel TCP/IP stack. Real-device validation is still required for retransmission behavior, congestion control, unusual TCP options, IPv6 edge cases, captive portals, OEM networking differences and long-lived connections.

On Android versions below API 29, the framework does not provide the same connection-owner UID API, so exact per-app packet attribution cannot be guaranteed for every intercepted flow on those versions.

The application does not use Go, NDK, a native tunnel binary, an external VPN server or cloud forwarding infrastructure.

See [`docs/TRAFFIC_ENGINE.md`](docs/TRAFFIC_ENGINE.md) for the complete engineering description and acceptance boundaries.

## Permissions

Usage access is an Android special access opened through system settings. VPN control uses the Android system VPN consent dialog. Notification permission is requested explicitly where required by Android. Every sensitive system capability is surfaced in the application settings instead of being silently enabled.

## Build

GitHub Actions is manual-only to conserve usage. Nothing in the repository should automatically start an Actions build on push.

Run manually:

`Actions` -> `Build DSTWR Flow` -> `Run workflow`

## Acceptance testing

The repository contains unit tests for packet parsing, packet codec round trips, policy evaluation, flow tables, speed primitives and traffic lifecycle. A successful APK build is required before calling this transport production-ready, followed by real-device tests for TCP, UDP, DNS, IPv4, IPv6, Wi-Fi/mobile transitions, blocking, quotas, schedules, speed limits, reboot recovery and battery behavior.

See [`docs/FINAL_PROJECT_STATUS.md`](docs/FINAL_PROJECT_STATUS.md) for the project status and acceptance checklist.
