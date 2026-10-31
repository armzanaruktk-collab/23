package com.firstham.aethergui

enum class ConnectionMode(val title: String) {
    DIRECT_SSH("Direct SSH"),
    HTTP_PAYLOAD("HTTP Payload"),
    SSL_TLS_SNI("SSL/TLS + SNI"),
    PAYLOAD_SNI("Payload + SNI"),
    HTTP_PROXY("HTTP Proxy"),
    PAYLOAD_HTTP_PROXY("Payload + HTTP Proxy"),
    PANTHER_VPN("Panther VPN (Full Device)"),
    OPENVPN_PROFILE("OpenVPN Profile (.ovpn)"),
    SSH_WEBSOCKET("SSH over WebSocket (WS + Payload)")
}

enum class VpnEngine(val title: String, val detail: String) {
    TURBO("Turbo", "Fastest. Stays local."),
    GLOBAL("Global", "Comes out abroad."),
    PROWL("Prowl", "Gets through anyway."),
    BEACON("Beacon", "Stands on its own.")
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val isPreset: Boolean = false
)

data class TelemetryState(
    val pingMs: Int? = null,
    val exitIp: String? = null,
    val locationName: String? = null,
    val countryFlag: String? = null,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val downloadHistory: List<Float> = emptyList()
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false
)
