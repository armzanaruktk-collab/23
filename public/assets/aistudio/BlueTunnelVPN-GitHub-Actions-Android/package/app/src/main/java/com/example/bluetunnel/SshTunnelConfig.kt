package com.example.bluetunnel

data class SshTunnelConfig(
    val host: String,
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    val payload: String = "",
    val openVpnMode: String = "normal"
)
