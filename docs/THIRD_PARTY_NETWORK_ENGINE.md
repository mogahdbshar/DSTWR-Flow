# Third-party network engine

DSTWR Flow now uses a production traffic engine derived from the open-source NetValve gVisor-netstack integration.

Source project:
- IEAmir/NetValve
- License: Apache License 2.0
- Production engine: gVisor userspace TCP/IP stack through a gomobile AAR
- The copied bridge files remain under `netstack/`.

The integration keeps DSTWR Flow's own UI, policies, settings, app database and product logic. The imported networking layer is used for protocol handling so the app does not rely on a hand-written TCP/IP implementation.

The engine is built during the manual GitHub Actions workflow. The workflow first builds `netstack.aar`, then builds the Android APK against that real engine.

Third-party licenses remain applicable to their respective components.
