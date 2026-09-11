# DSTWR Flow - Final Engineering Status

## Product contract

DSTWR Flow is a local-first Android network control application using Kotlin, Jetpack Compose, Room, DataStore, NetworkStatsManager and Android VpnService.

Application ID: `com.dstwr.flow`
Minimum API: 24
Target API: 35

## Implemented foundation

- Android application shell and Material 3 UI.
- Arabic/English-ready presentation structure and RTL/LTR-aware layout.
- Installed launchable application inventory.
- Persistent application policies.
- Block rules, network scope, schedules and quotas.
- Daily/monthly usage evaluation.
- Wi-Fi/mobile usage accounting through NetworkStatsManager.
- Usage history snapshots and statistics models.
- Quota warning/reached notifications.
- Explicit VPN consent handling.
- Foreground VPN service lifecycle.
- Reboot restoration safeguards.
- Physical-network detection that ignores the app's own VPN transport.
- IPv4 and IPv6 packet parsing foundation.
- Five-tuple flow identity.
- Bounded flow table with reverse-flow lookup and expiry.
- Packet decision and traffic-metering foundation.
- Upload/download token-bucket primitives.
- Runtime traffic policy registry.
- Defensive validation and unit-test coverage for core policy/traffic primitives.
- Manual-only GitHub Actions build workflow.

## Deliberate capability boundary

Android VpnService exposes a TUN interface, but it does not provide a complete transparent IP forwarding stack. A production transparent forwarding engine must terminate/reconstruct TCP and UDP flows, maintain NAT/state, handle checksums, route replies, manage DNS behavior and handle IPv4/IPv6 edge cases.

The repository therefore does not pretend that the current packet pump is a complete Internet forwarding implementation. The current VPN enforcement path is suitable for selective blocking by routing selected applications into a local non-forwarding tunnel. Unblocked applications are intentionally kept outside that tunnel.

Per-application upload/download speed shaping is represented as a policy and token-bucket capability, but it must not be advertised as enforced until a real forwarding transport is integrated and validated on supported Android versions/devices.

## Security and privacy rules

- No remote VPN server is required.
- No cloud account is required.
- No root access is required.
- Android permission/consent boundaries are explicit.
- The app must never guess an application's identity from a packet.
- Unsupported traffic-control capabilities must remain visibly unsupported instead of silently failing.

## Build policy

GitHub Actions is manual-only. No automatic workflow trigger is configured.

A final build must be run manually after the implementation batch is complete. Build success is a validation step, not a substitute for real-device testing.

## Required real-device acceptance tests

1. VPN consent and cancellation.
2. Enable/disable protection.
3. Block one launchable application on Wi-Fi.
4. Block one launchable application on mobile data.
5. Switch Wi-Fi/mobile while protection is active.
6. Emergency block.
7. Reboot restoration with and without VPN consent.
8. Usage access granted/revoked.
9. Notification permission granted/revoked on Android 13+.
10. Daily/monthly quota warning and enforcement.
11. Schedule activation/deactivation, including overnight schedules.
12. IPv4 traffic.
13. IPv6 traffic on devices/networks where IPv6 is available.
14. Battery and memory behavior during long-running protection.
15. Verify that unblocked applications retain Internet access while blocked applications are isolated.

## Final truth statement

A repository build can validate compilation and tests, but it cannot prove transparent packet forwarding, per-app identity attribution, speed shaping or every Android OEM behavior. Those capabilities require an actual forwarding implementation plus device-level acceptance tests.
