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

Future traffic engine
  TUN reader/writer -> protocol handling -> policy evaluation -> forwarding
  IPv4 + IPv6 -> per-app rules -> quotas -> shaping where technically supported
```

## Current foundation

The repository now contains:

- A stable Material 3 Compose shell without the removed `SmallTopAppBar` API.
- A premium dashboard foundation with RTL-friendly Arabic UI.
- Explicit VPN consent handling.
- A foreground-capable local VPN service lifecycle.
- Room database entities and DAOs for app policies and usage snapshots.
- DataStore persistence for protection, emergency mode, language, refresh rate and global quota settings.
- Installed-app inventory through the Android launcher query.
- NetworkStatsManager integration for per-UID accounting of Wi-Fi and mobile traffic.
- Domain models for policies, network scope and control mode.
- Unit tests for core data formatting.

## What is deliberately not claimed yet

A TUN interface by itself is not a complete VPN. Without a forwarding engine, intercepted packets can be dropped. Therefore DSTWR Flow does **not** advertise the current VPN foundation as a finished internet-blocking engine.

Per-app speed shaping without root is also constrained by Android and depends on the eventual packet-forwarding architecture and OS/device capabilities. The production implementation will expose only capabilities that can actually be enforced.

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
