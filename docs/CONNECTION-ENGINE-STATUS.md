# FoxyVPN Connection Engine — implementation status

## Separation
- `foxy_servers` stores user-managed SSH server profiles (name, host, port, username).
- `foxy_mode` stores connection-mode settings (mode, payload, SNI, HTTP proxy host/port).
- Server selection and connection-mode selection are separate UI controls.

## Current behavior (do not confuse protocol checks with VPN)
- Direct SSH: establishes an authenticated JSch SSH session. It currently does not route device traffic.
- HTTP Payload: sends the configured HTTP request to the configured target and reads a response. This is a transport/protocol check, not an SSH tunnel.
- TLS/SNI and Payload + SNI: tests a TLS handshake and optionally writes a payload. This is not an SSH tunnel.
- HTTP Proxy and Payload + HTTP Proxy: tests HTTP proxy/CONNECT response. This is not a full-device VPN tunnel.
- Panther VPN (Full Device): starts the app's existing Android `VpnService` engine. This is separate from the manual SSH test flow.
- OpenVPN is provided by the existing VPN Gate/OpenVPN integration; it is not automatically connected to manual SSH profiles.

## Remaining work required for a real SSH-based full-device VPN
1. Implement a JSch `Proxy`/socket transport for HTTP proxy and payload negotiation, with correct CONNECT success parsing and bounded header reads.
2. Feed the established SSH transport into a supported tunnel engine (for example, a tested SOCKS/dynamic-forwarding plus TUN packet forwarder). An authenticated SSH session alone is insufficient.
3. Wire DNS, TCP/UDP forwarding, MTU, lifecycle/reconnect, and Android VPN permission handling into `VpnService`.
4. Test with an authorized SSH server and proxy, verify routes/DNS and public egress IP, and run Android instrumentation/device tests.

## Build
The repository has a GitHub Actions workflow at `.github/workflows/foxy-merged-android.yml` that builds a debug APK and runs lint. Local Gradle execution could not be completed in the current environment because the Gradle distribution download requires network access.
