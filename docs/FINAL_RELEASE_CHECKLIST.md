# DSTWR Flow Final Release Checklist

## Product identity
- Product: DSTWR Flow
- Brand: DSTWR
- Application ID: `com.dstwr.flow`
- Kotlin + Jetpack Compose + Material 3
- Android API 24+
- Local-first architecture

## User experience
- Arabic-first interface with RTL support
- Material 3 theme with system dark/light detection
- Premium blue/cyan DSTWR visual identity
- Branded application icon
- Android 12+ branded system splash screen
- Branded foreground-service notification icon
- Dashboard, applications, statistics and settings surfaces
- Per-app policy editor
- Search and app filters
- Usage summaries and history
- Permission and diagnostics surfaces

## Android integration
- Explicit VPN consent
- Usage Access through Android system settings
- Notification permission on Android 13+
- Foreground service for persistent VPN control
- Boot restoration with safe fallback when consent is unavailable
- Connectivity callbacks instead of continuous network polling
- Wi-Fi/mobile policy scopes

## Traffic engine
- VpnService TUN interface
- IPv4/IPv6 packet parsing
- TCP/UDP packet codec
- User-space TCP bridge
- User-space UDP bridge
- DNS over UDP path
- Protected upstream sockets via `VpnService.protect()`
- Bidirectional response queue
- Flow/session bookkeeping with bounded memory and expiry
- Android connection-owner UID resolution on API 29+
- Flow-based identity retention for subsequent packets
- Global emergency blocking
- Per-app blocking
- Per-app upload/download token-bucket shaping
- Daily/monthly quota enforcement
- Traffic metering

## Reliability and performance
- No remote VPN server
- No Go/NDK/native tunnel binary
- Bounded flow table
- Defensive packet length validation
- Bounded IPv6 extension-header traversal
- Lifecycle-safe transport shutdown
- Network-change driven policy reapplication
- Manual-only GitHub Actions workflow
- No automatic build execution during implementation

## Final validation required on real hardware
The implementation is now ready for the validation phase, but production behavior must be verified on the target Android device. The validation pass should cover:

1. Application launch and splash branding.
2. VPN consent and protection enable/disable.
3. Usage Access and application list.
4. Wi-Fi browsing with protection enabled.
5. Mobile-data browsing with protection enabled.
6. DNS resolution.
7. TCP HTTPS connections.
8. UDP traffic and QUIC-capable applications.
9. IPv6 where the carrier/network provides it.
10. Blocking one configured application.
11. Emergency blocking.
12. Per-app quota behavior and notifications.
13. Upload/download speed limits.
14. Network transition Wi-Fi <-> mobile.
15. Reboot restoration.
16. Battery/CPU behavior while idle.
17. Repeated enable/disable cycles.
18. Large downloads/uploads and long-lived connections.

The checklist distinguishes implemented architecture from behavior that can only be proven by APK build and real-device interoperability testing.
