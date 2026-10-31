package com.firstham.aethergui

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import android.util.Base64
import java.util.Properties

object SshTunnelTransport {

    data class TransportConfig(
        val host: String,
        val port: Int,
        val user: String,
        val pass: String,
        val mode: ConnectionMode,
        val payload: String = "",
        val sni: String = "",
        val proxyHost: String = "",
        val proxyPort: Int = 80,
        val wsPath: String = "/",
        val localSocksPort: Int = 10808
    )

    private var activeSession: Session? = null

    val isConnected: Boolean
        get() = activeSession?.isConnected == true

    suspend fun connect(
        cfg: TransportConfig,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        onLog("Preparing SSH Transport pipeline via [${cfg.mode.title}]...")

        try {
            val jsch = JSch()
            val session = jsch.getSession(cfg.user, cfg.host, cfg.port)
            session.setPassword(cfg.pass)

            val sessionConfig = Properties()
            sessionConfig["StrictHostKeyChecking"] = "no"
            sessionConfig["PreferredAuthentications"] = "password,keyboard-interactive"
            session.setConfig(sessionConfig)
            session.timeout = 15000

            // Attach Custom SocketFactory based on ConnectionMode
            val socketFactory = createSocketFactory(cfg, onLog)
            session.setSocketFactory(socketFactory)

            onLog("Connecting SSH Transport session (Mode: ${cfg.mode.title})...")
            session.connect(15000)

            if (session.isConnected) {
                onLog("SSH Session Authenticated! Server banner: ${session.serverVersion}")
                try {
                    session.setPortForwardingL(cfg.localSocksPort, "127.0.0.1", 22)
                    onLog("Local port forwarding active at 127.0.0.1:${cfg.localSocksPort}")
                } catch (pe: Exception) {
                    onLog("Port Forwarding notice: ${pe.message}")
                }
                activeSession = session
                true
            } else {
                onLog("SSH handshake failed to authenticate.")
                false
            }
        } catch (e: Exception) {
            onLog("SSH Transport Error: ${e.message ?: e.javaClass.simpleName}")
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            activeSession?.disconnect()
        } catch (_: Exception) {}
        activeSession = null
    }

    private fun createSocketFactory(
        cfg: TransportConfig,
        onLog: (String) -> Unit
    ): SocketFactory {
        return object : SocketFactory {
            override fun createSocket(host: String, port: Int): Socket {
                return when (cfg.mode) {
                    ConnectionMode.DIRECT_SSH -> {
                        onLog("Direct TCP socket opening to $host:$port...")
                        val sock = Socket()
                        sock.connect(InetSocketAddress(host, port), 10000)
                        sock
                    }
                    ConnectionMode.HTTP_PAYLOAD -> {
                        onLog("Establishing TCP Socket with HTTP Payload Injection to $host:$port...")
                        val sock = Socket()
                        sock.connect(InetSocketAddress(host, port), 10000)
                        injectPayload(sock, cfg.payload, host, port, onLog)
                        sock
                    }
                    ConnectionMode.SSL_TLS_SNI -> {
                        val sniHost = cfg.sni.ifBlank { host }
                        val targetPort = if (port == 22) 443 else port
                        onLog("Negotiating TLS SNI Socket with SNI '$sniHost' to $host:$targetPort...")
                        createTlsSniSocket(host, targetPort, sniHost, null, onLog)
                    }
                    ConnectionMode.PAYLOAD_SNI -> {
                        val sniHost = cfg.sni.ifBlank { host }
                        val targetPort = if (port == 22) 443 else port
                        onLog("Negotiating TLS SNI Socket + Payload with SNI '$sniHost' to $host:$targetPort...")
                        createTlsSniSocket(host, targetPort, sniHost, cfg.payload, onLog)
                    }
                    ConnectionMode.HTTP_PROXY, ConnectionMode.PAYLOAD_HTTP_PROXY -> {
                        val pHost = cfg.proxyHost.ifBlank { host }
                        val pPort = cfg.proxyPort
                        onLog("Chaining HTTP Proxy CONNECT via $pHost:$pPort to $host:$port...")
                        createHttpProxySocket(pHost, pPort, host, port, cfg.payload, onLog)
                    }
                    ConnectionMode.SSH_WEBSOCKET -> {
                        val pHost = cfg.proxyHost.ifBlank { host }
                        val pPort = cfg.proxyPort
                        onLog("Upgrading WebSocket tunnel via $pHost:$pPort (Path: ${cfg.wsPath})...")
                        createWebSocketTunnelSocket(pHost, pPort, cfg.wsPath, host, port, onLog)
                    }
                    ConnectionMode.PANTHER_VPN, ConnectionMode.OPENVPN_PROFILE -> {
                        onLog("Opening default tunnel socket to $host:$port...")
                        val sock = Socket()
                        sock.connect(InetSocketAddress(host, port), 10000)
                        sock
                    }
                }
            }

            override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
            override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
        }
    }

    private fun injectPayload(
        socket: Socket,
        payload: String,
        targetHost: String,
        targetPort: Int,
        onLog: (String) -> Unit
    ) {
        val raw = if (payload.isBlank()) {
            "GET / HTTP/1.1\r\nHost: $targetHost\r\nConnection: Upgrade\r\n\r\n"
        } else {
            ProtocolEngine.normalizePayload(payload, targetHost, targetPort)
        }
        onLog("Injecting HTTP Payload (${raw.length} bytes)...")
        socket.outputStream.write(raw.toByteArray(Charsets.ISO_8859_1))
        socket.outputStream.flush()
    }

    private fun createTlsSniSocket(
        host: String,
        port: Int,
        sni: String,
        payload: String?,
        onLog: (String) -> Unit
    ): Socket {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), 10000)
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val tls = sslFactory.createSocket(raw, sni, port, true) as SSLSocket
        tls.soTimeout = 12000
        val params = tls.sslParameters
        params.serverNames = listOf(SNIHostName(sni))
        tls.sslParameters = params
        tls.startHandshake()
        onLog("TLS Handshake OK! Session: ${tls.session.cipherSuite}")

        if (!payload.isNullOrBlank()) {
            val rawPayload = ProtocolEngine.normalizePayload(payload, host, port)
            onLog("Injecting Payload into TLS stream...")
            tls.outputStream.write(rawPayload.toByteArray(Charsets.ISO_8859_1))
            tls.outputStream.flush()
        }
        return tls
    }

    private fun createHttpProxySocket(
        proxyHost: String,
        proxyPort: Int,
        targetHost: String,
        targetPort: Int,
        payload: String?,
        onLog: (String) -> Unit
    ): Socket {
        val sock = Socket()
        sock.connect(InetSocketAddress(proxyHost, proxyPort), 10000)
        sock.soTimeout = 10000

        val req = if (!payload.isNullOrBlank()) {
            ProtocolEngine.normalizePayload(payload, targetHost, targetPort)
        } else {
            "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost:$targetPort\r\nProxy-Connection: Keep-Alive\r\n\r\n"
        }
        onLog("Dispatching HTTP request to Proxy $proxyHost:$proxyPort...")

        if (req.contains("[instant_split]", ignoreCase = true)) {
            val parts = req.split(Regex("\\[instant_split\\]", RegexOption.IGNORE_CASE))
            parts.forEachIndexed { idx, part ->
                if (part.isNotEmpty()) {
                    onLog("Sending payload split segment ${idx + 1}/${parts.size} (${part.length} bytes)...")
                    sock.outputStream.write(part.toByteArray(Charsets.ISO_8859_1))
                    sock.outputStream.flush()
                }
            }
        } else {
            sock.outputStream.write(req.toByteArray(Charsets.ISO_8859_1))
            sock.outputStream.flush()
        }

        val respHeader = readHttpHeader(sock)
        onLog("Proxy Response: ${respHeader.lines().firstOrNull() ?: "Empty"}")
        return sock
    }

    private fun createWebSocketTunnelSocket(
        wsHost: String,
        wsPort: Int,
        path: String,
        targetHost: String,
        targetPort: Int,
        onLog: (String) -> Unit
    ): Socket {
        val sock = Socket()
        sock.connect(InetSocketAddress(wsHost, wsPort), 10000)
        sock.soTimeout = 10000

        val keyBytes = ByteArray(16)
        SecureRandom().nextBytes(keyBytes)
        val key = Base64.encodeToString(keyBytes, Base64.NO_WRAP)

        val req = "GET $path HTTP/1.1\r\n" +
                "Host: $targetHost:$targetPort\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"

        onLog("Sending WebSocket 101 Upgrade Request...")
        sock.outputStream.write(req.toByteArray(Charsets.ISO_8859_1))
        sock.outputStream.flush()

        val resp = readHttpHeader(sock)
        onLog("WS Upgrade Status: ${resp.lines().firstOrNull() ?: "Unknown"}")
        return sock
    }

    private fun readHttpHeader(socket: Socket): String {
        val stream = socket.getInputStream()
        val buffer = ByteArray(2048)
        val read = stream.read(buffer)
        return if (read > 0) String(buffer, 0, read, Charsets.ISO_8859_1) else ""
    }
}
