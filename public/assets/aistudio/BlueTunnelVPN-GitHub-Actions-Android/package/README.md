# BlueTunnelVPN — OpenVPN 2 + Payload integration workspace

This package includes the upstream OpenVPN 2 source tree under `third_party/openvpn2/` and a UI selector for direct `.ovpn` vs Payload mode.

## Current status (important)
This is **not yet a working VPN APK**. The Kotlin app still does not launch a compiled OpenVPN 2 Android daemon. The OpenVPN source is included, but Android cross-compilation, management IPC, TUN file-descriptor passing, socket protection, and the Payload bridge adapter remain to be implemented and tested. Do not treat a successful Gradle UI build as a working VPN connection.

## Why OpenVPN 2 Android integration is more than copying source
OpenVPN 2's `doc/android.txt` documents `TARGET_ANDROID`, management callbacks, `OPENTUN`, `PROTECTFD`, and passing the Android `VpnService.Builder.establish()` file descriptor to the daemon. The application must implement this management protocol (including UNIX-domain socket/ancillary FD passing) and protect the transport socket with `VpnService.protect()` to prevent routing loops.

## Payload mode design
- Normal mode: use the imported `.ovpn` profile and connect directly to its configured remote.
- Payload mode: establish a supported proxy/bridge transport first, then configure OpenVPN to use that transport. The bridge must be compatible with the proxy protocol and server. An arbitrary `GET / ...` string cannot be inserted into an `.ovpn` file and expected to work universally.
- Keep SSH tunnel handling separate.
