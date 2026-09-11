# DSTWR Flow

DSTWR Flow is a local-first Android network control and data intelligence suite. The goal is a production-quality control center for understanding and managing device network usage without root or a remote VPN server.

## Identity

- Product: DSTWR Flow
- Brand: DSTWR
- Application ID: `com.dstwr.flow`
- Kotlin only
- Jetpack Compose + Material 3
- Minimum Android API 24
- Local-first and privacy-focused
- Arabic RTL + English LTR
- Dark and light themes
- Premium glass-inspired visual language

## Engineering architecture

```text
Presentation
  Compose UI -> ViewModels -> StateFlow

Domain
  Policies -> quotas -> schedules -> capability rules

Data
  Room -> app policies + usage history
  DataStore -> persistent user settings

Android adapters
  PackageManager -> installed launchable apps
  NetworkStatsManager -> Wi-Fi/mobile usage accounting
  VpnService -> local traffic interception foundation
  Foreground Service -> persistent control lifecycle

Traffic foundation
  TUN reader/writer -> packet parsing -> connection/session tracking
  policy snapshot -> identity resolution -> block/rate decision -> metering
  IPv4 + IPv6 parsing -> bounded flow tables -> lifecycle-safe shutdown

Future traffic transport
  packet decision -> real forwarding transport -> upstream/downstream routing
  shaping where technically supported by the final transport architecture
```

## Phase 1 status: foundation completed

The first engineering phase establishes the production-safe foundation without introducing native code, a remote server, or an external VPN provider.

Completed in this phase:

- Stable Material 3 Compose shell and dashboard foundation.
- Explicit VPN consent handling and local foreground-service lifecycle.
- Room database entities/DAOs for app policies and usage snapshots.
- DataStore persistence for protection, emergency mode, language, refresh and global settings.
- Installed-app inventory through Android PackageManager launcher queries.
- NetworkStatsManager integration for per-UID Wi-Fi/mobile accounting.
- Policy models for block state, quotas, schedules, network scope and speed limits.
- Runtime policy evaluation with deterministic priority and quota alerts.
- Network transport detection that ignores the app's own VPN and reacts to transport changes through ConnectivityManager callbacks.
- Bounded bidirectional flow/session bookkeeping with idle expiry and maximum entry limits.
- Defensive IPv4/IPv6 packet parsing with strict declared-length validation and bounded extension-header traversal.
- Explicit upload/download traffic direction models.
- Runtime traffic policy snapshots and a thread-safe policy registry.
- Packet decision layer that can return Forward, Blocked, Throttled or Malformed decisions without guessing an app identity.
- Direction-aware traffic pump with safe sibling cancellation and transport shutdown.
- Unit coverage for flow keys, flow/session expiry, policy registry, packet boundaries and traffic lifecycle basics.
- Manual-only GitHub Actions build workflow to conserve Actions usage.

## What is deliberately not claimed yet

A TUN interface by itself is not a complete VPN. The current blocking tunnel intentionally routes selected blocked applications into a local TUN interface where their packets are discarded. It is therefore a local blocking foundation, not yet a transparent internet-forwarding VPN.

The traffic engine contains the contracts needed for forwarding, policy decisions and shaping, but no upstream/downstream forwarding transport is claimed as complete yet. Per-app speed shaping without root also depends on that final transport and Android/device capabilities.

App identity is never guessed from a raw TUN packet. The current session resolver works only when a trusted flow-to-package binding exists. A future identity adapter must establish that binding before per-app packet enforcement can be considered complete.

## Product modules

1. Onboarding and permissions
2. Live dashboard
3. Installed applications
4. Per-app policy editor
5. Wi-Fi/mobile usage analytics
6. Quotas and alerts
7. Schedules and rule engine
8. Local traffic engine
9. Speed-control capability layer
10. Notifications
11. Settings and privacy center
12. Arabic/English localization
13. Diagnostics and safe fallbacks
14. Tests and build validation

## Permissions and privacy

Usage access is an Android special access and is opened through system settings. VPN control requires the Android system consent dialog. The app is designed around local storage and does not require a cloud account or remote VPN server.

## Build policy

GitHub Actions is **manual-only** to conserve Actions usage. Do not expect a push to start a build.

After each major engineering phase:

`Actions` -> `Build DSTWR Flow` -> `Run workflow`

The build result should be inspected before the next major phase.
