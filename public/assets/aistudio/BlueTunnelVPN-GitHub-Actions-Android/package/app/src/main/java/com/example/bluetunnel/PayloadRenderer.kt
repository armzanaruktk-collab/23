package com.example.bluetunnel

/**
 * Small template renderer only. It does not open a socket or establish a tunnel.
 * Tokens supported here: [host], [port], [ua], [crlf].
 */
object PayloadRenderer {
    fun render(template: String, host: String, port: String, userAgent: String): String {
        return template
            .replace("[host]", host)
            .replace("[port]", port)
            .replace("[ua]", userAgent)
            .replace("[crlf]", "\r\n")
    }
}
