# DSTWR Flow

DSTWR Flow is an Android application for monitoring and controlling device network usage locally.

## Identity

- App: DSTWR Flow
- Brand: DSTWR
- Package: `com.dstwr.flow`
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Minimum Android: API 24
- Root: not required
- Cloud/server: none planned

## Planned capabilities

- Per-app mobile and Wi-Fi usage statistics
- Local VPN-based traffic control
- Per-app allow/block rules
- Global internet control
- Data quotas and threshold notifications
- Scheduled rules
- Usage history and charts
- Arabic and English interfaces
- Battery-conscious background monitoring

## Build

GitHub Actions builds a debug APK automatically after pushes to `main` and can also be started manually from the Actions tab.

## Architecture

The project is being built around a local-first architecture with separate presentation, domain, data, usage monitoring, and VPN traffic-control components.
