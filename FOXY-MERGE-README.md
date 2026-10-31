# FoxyVPN UI + Panther VPN engine integration

This project uses the FoxyVPN-style UI as the launcher and keeps Panther's Android VpnService engine and native engine integration. It is an integration build, not a verified release.

## Modes
- Direct SSH: SSH authentication test only (does not route device traffic).
- HTTP Payload: sends a custom payload to a TCP endpoint and previews a response.
- SSL/TLS + SNI: performs a TLS handshake using the configured SNI host.
- Payload + SNI: sends the custom payload over a TLS connection.
- HTTP Proxy: sends HTTP CONNECT and checks for an HTTP 200 response.
- Payload + HTTP Proxy: sends a configured request/payload to the proxy endpoint.
- Panther VPN (Full Device): requests Android VPN permission and starts Panther's VpnService engine.

The first six modes are protocol/connection tests; they are not automatically full-device VPN modes. The Panther VPN mode uses the integrated engine and requires network connectivity plus the engine's supported upstream. No live server credentials are included.

## Build
The Android build needs JDK 17, Android SDK 36 and the native assets used by Panther. In GitHub Actions, run `npm ci` if a lockfile is present, then `npm run fetch:android`, then `cd android && ./gradlew assembleDebug lintDebug`. The original Panther workflow documents native asset verification.

## Test status
Static source integration only. This workspace has no configured Android SDK/native assets or user-provided server credentials, so an APK build and live connectivity test cannot be honestly marked passed here. Use the included GitHub Actions workflow to produce a reproducible build and test on a device with an authorized server.

## Licensing
This combines code from FoxyVPN and Panther. Panther is AGPL-3.0; retain all original notices and comply with AGPL-3.0 for modified/redistributed builds.
