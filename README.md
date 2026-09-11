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
- Dark and light themes
- Manual-only GitHub Actions build

## Implemented product areas

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
- Network transition detection
- IPv4/IPv6 packet parsing foundation
- Flow/session bookkeeping
- Traffic metering primitives
- Upload/download rate-limit primitives
- Diagnostics and privacy settings
- Arabic/English-ready UI structure

## Important technical boundary

`VpnService` provides the TUN interception point, not a complete transparent Internet forwarding stack. DSTWR Flow's selective blocking path intentionally routes selected applications into a local non-forwarding tunnel, where their traffic is discarded. Applications that are not selected remain outside that tunnel.

A true transparent forwarding engine requires substantially more networking machinery: TCP connection termination/reconstruction, UDP flow handling, NAT/state management, checksum handling, reply routing, DNS behavior and robust IPv4/IPv6 edge-case handling. The repository does not falsely label its current packet pump as that finished engine.

Likewise, per-app upload/download speed limits are modeled and validated by the policy layer, but are not advertised as physically enforced until a real forwarding transport exists and is validated on supported Android versions and devices.

## Permissions

Usage access is an Android special access opened through system settings. VPN control uses the Android system VPN consent dialog. Notification permission is requested explicitly where required by Android. The application does not require a remote VPN server or cloud account.

## Build

GitHub Actions is manual-only to conserve usage. Nothing in the repository should automatically start an Actions build on push.

Run manually:

`Actions` -> `Build DSTWR Flow` -> `Run workflow`

## Acceptance testing

A successful APK build validates compilation and automated tests. It does not replace real-device testing.

See [`docs/FINAL_PROJECT_STATUS.md`](docs/FINAL_PROJECT_STATUS.md) for the complete acceptance checklist and the exact technical capability boundary.
