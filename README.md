# DSTWR Flow

DSTWR Flow is a local-first Android network control and data intelligence app designed to give the user a clear, modern control center for internet usage.

## Product identity

- Product: DSTWR Flow
- Brand: DSTWR
- Application ID: `com.dstwr.flow`
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Minimum Android: API 24
- Root: not required
- Cloud account: none required
- Design direction: premium, modern, glass-inspired control center

## Product vision

DSTWR Flow is not intended to be a basic data counter. The target is a polished, privacy-first network control suite with a fast dashboard, per-app controls, usage intelligence, schedules, quotas, notifications and a local VPN traffic engine.

## Planned product areas

- Device-wide traffic overview
- Per-app Wi-Fi and mobile-data usage
- Per-app allow/block controls
- Global emergency internet cut-off
- Local VPN traffic control without root
- Per-app speed policies where Android capabilities permit
- Daily and monthly data quotas
- Scheduled rules
- Usage history and charts
- Threshold and rule notifications
- Arabic-first RTL and English LTR interfaces
- Modern glass-inspired cards and controls
- Dark and light themes
- Battery-conscious monitoring
- Local-only data storage
- Clear permission and privacy explanations

## Important technical boundary

The application must never pretend that an Android API can do something it cannot. Exact per-app traffic shaping, full packet forwarding and complete IPv4/IPv6 interception require careful engineering around Android `VpnService` and device/version constraints. Features will therefore be implemented with explicit capability checks and safe fallbacks rather than fake switches.

## Build

GitHub Actions is intentionally **manual-only**. The workflow is not triggered by every push, so development changes do not consume Actions runs automatically.

To build a debug APK, open the Actions tab, select **Build DSTWR Flow**, choose **Run workflow**, and start it manually.

## Architecture direction

The project is being developed toward a local-first architecture with clear separation between:

- Presentation: Compose UI, state and navigation
- Domain: rules, policies and business logic
- Data: Room and DataStore
- Usage: Android NetworkStatsManager integration
- Traffic: VpnService and the local traffic engine
- Background: foreground service and scheduled work
- Notifications: threshold and rule events

## Development policy

Changes are implemented in deliberate phases. After a major phase, the repository is built manually through GitHub Actions and the result is inspected before moving to the next phase. This reduces wasted build runs and prevents repeatedly stacking unverified changes.
