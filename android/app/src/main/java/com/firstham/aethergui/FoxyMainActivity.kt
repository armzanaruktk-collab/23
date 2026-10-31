package com.firstham.aethergui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.widget.*
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Properties
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.net.Socket
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.io.BufferedInputStream
import com.firstham.aethergui.vpngate.VpnGateConnector

private class WebSocketTunnelSocketFactory(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val path: String,
    private val payload: String,
    private val targetHost: String,
    private val targetPort: Int
) : com.jcraft.jsch.SocketFactory {
    override fun createSocket(host: String, port: Int): Socket {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 15000)
            socket.soTimeout = 15000
            val keyBytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val key = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
            val normalized = payload.substringBefore("[split]", payload)
                .replace("[host_port]", "$targetHost:$targetPort", true)
                .replace("[host]", targetHost, true)
                .replace("[port]", targetPort.toString(), true)
                .replace("[protocol]", "HTTP/1.1", true)
                .replace("[crlf]", "\r\n", true)
                .replace("[lf]", "\n", true)
            val customHeaders = normalized.replace("\r\n", "\n").split("\n").map { it.trim() }
                .filter { it.isNotEmpty() && it.contains(":") &&
                    !it.startsWith("GET ", true) && !it.startsWith("CONNECT ", true) &&
                    !it.startsWith("Upgrade:", true) && !it.startsWith("Connection:", true) &&
                    !it.startsWith("Sec-WebSocket-", true) }
            val hostHeader = customHeaders.firstOrNull { it.startsWith("Host:", true) }?.substringAfter(":")?.trim()
                ?.takeIf { it.isNotBlank() } ?: "$proxyHost:$proxyPort"
            val extra = customHeaders.filterNot { it.startsWith("Host:", true) }
                .joinToString("") { "$it\r\n" }
            val request = "GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n$extra\r\n"
            socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().flush()
            val headerBytes = java.io.ByteArrayOutputStream()
            var matched = 0
            while (headerBytes.size() < 16384 && matched < 4) {
                val b = socket.getInputStream().read()
                if (b < 0) throw java.io.IOException("WebSocket endpoint closed during handshake")
                headerBytes.write(b)
                matched = when {
                    matched == 0 && b == 13 -> 1
                    matched == 1 && b == 10 -> 2
                    matched == 2 && b == 13 -> 3
                    matched == 3 && b == 10 -> 4
                    b == 13 -> 1
                    else -> 0
                }
            }
            if (matched != 4) throw java.io.IOException("WebSocket handshake headers are incomplete")
            val response = headerBytes.toString("ISO-8859-1")
            if (!response.startsWith("HTTP/1.1 101") && !response.startsWith("HTTP/1.0 101")) {
                throw java.io.IOException("WebSocket upgrade rejected: " + response.lineSequence().firstOrNull().orEmpty())
            }
            val expected = android.util.Base64.encodeToString(
                java.security.MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.ISO_8859_1)),
                android.util.Base64.NO_WRAP
            )
            val accept = response.lineSequence().firstOrNull { it.startsWith("Sec-WebSocket-Accept:", true) }
                ?.substringAfter(":")?.trim()
            if (accept != expected) throw java.io.IOException("WebSocket server returned an invalid Sec-WebSocket-Accept")
            socket.soTimeout = 0
            return socket
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
            throw e
        }
    }

    override fun getInputStream(socket: Socket): java.io.InputStream =
        WebSocketFrameInputStream(socket.getInputStream(), socket.getOutputStream())

    override fun getOutputStream(socket: Socket): java.io.OutputStream =
        WebSocketFrameOutputStream(socket.getOutputStream())
}

private class WebSocketFrameOutputStream(private val raw: java.io.OutputStream) : java.io.OutputStream() {
    override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) return
        synchronized(raw) {
            raw.write(0x82)
            when {
                len < 126 -> raw.write(0x80 or len)
                len <= 65535 -> { raw.write(0x80 or 126); raw.write((len ushr 8) and 255); raw.write(len and 255) }
                else -> {
                    raw.write(0x80 or 127)
                    for (shift in 56 downTo 0 step 8) raw.write((len.toLong() ushr shift).toInt() and 255)
                }
            }
            val mask = ByteArray(4).also { java.security.SecureRandom().nextBytes(it) }
            raw.write(mask)
            val chunk = ByteArray(len)
            for (i in 0 until len) chunk[i] = (b[off + i].toInt() xor mask[i and 3].toInt()).toByte()
            raw.write(chunk)
            raw.flush()
        }
    }
    override fun flush() = raw.flush()
}

private class WebSocketFrameInputStream(
    private val raw: java.io.InputStream,
    private val rawOut: java.io.OutputStream
) : java.io.InputStream() {
    private var frame = ByteArray(0)
    private var position = 0
    private var ended = false

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n < 0) -1 else one[0].toInt() and 255
    }

    override fun read(out: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (position >= frame.size) {
            if (ended) return -1
            loadDataFrame()
        }
        val n = minOf(len, frame.size - position)
        System.arraycopy(frame, position, out, off, n)
        position += n
        return n
    }

    private fun readByte(): Int {
        val value = raw.read()
        if (value < 0) throw java.io.IOException("WebSocket connection closed")
        return value
    }

    private fun readExact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val n = raw.read(bytes, offset, size - offset)
            if (n < 0) throw java.io.IOException("WebSocket frame ended unexpectedly")
            offset += n
        }
        return bytes
    }

    private fun loadDataFrame() {
        while (true) {
            val first = readByte()
            val second = readByte()
            val opcode = first and 0x0f
            val masked = (second and 0x80) != 0
            var size = (second and 0x7f).toLong()
            if (size == 126L) size = ((readByte() shl 8) or readByte()).toLong()
            else if (size == 127L) {
                size = 0
                repeat(8) { size = (size shl 8) or readByte().toLong() }
            }
            if (size > 16L * 1024L * 1024L) throw java.io.IOException("WebSocket frame exceeds 16 MiB")
            val mask = if (masked) readExact(4) else null
            val data = readExact(size.toInt())
            if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
            when (opcode) {
                0x8 -> { ended = true; return }
                0x9 -> {
                    synchronized(rawOut) {
                        rawOut.write(0x8a); rawOut.write(0x80 or data.size)
                        val clientMask = ByteArray(4).also { java.security.SecureRandom().nextBytes(it) }
                        rawOut.write(clientMask)
                        for (i in data.indices) rawOut.write((data[i].toInt() xor clientMask[i and 3].toInt()) and 255)
                        rawOut.flush()
                    }
                }
                0x1, 0x2, 0x0 -> if (data.isNotEmpty()) { frame = data; position = 0; return }
                else -> { /* Ignore unsupported control frames. */ }
            }
        }
    }
}

class FoxyMainActivity : Activity() {
    private val bg = Color.rgb(10, 16, 32)
    private val panel = Color.rgb(17, 29, 50)
    private val green = Color.rgb(101, 230, 181)
    private val pale = Color.rgb(235, 243, 255)
    private val muted = Color.rgb(155, 175, 201)
    private val worker = Executors.newSingleThreadExecutor()
    private var session: Session? = null
    private var pantherVpnRequested = false
    private val vpnConsentRequest = 8137
    private val openVpnConsentRequest = 8138
    private val openVpnFileRequest = 8139
    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AetherVpnService.ACTION_STATUS) {
                val state = intent.getStringExtra("state") ?: "unknown"
                val message = intent.getStringExtra("message") ?: state
                runOnUiThread {
                    status.text = "●  ${state.uppercase()}"
                    status.setTextColor(if (state == "connected") green else muted)
                    appendLog("Panther VPN: $message")
                    if (state == "disconnected" || state == "blocked") { pantherVpnRequested = false; connect.text = "CONNECT"; connect.isEnabled = true }
                }
            }
        }
    }
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var connect: Button
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var serverPicker: Spinner
    private lateinit var modePicker: Spinner
    private lateinit var payloadField: EditText
    private lateinit var sniField: EditText
    private lateinit var proxyHostField: EditText
    private lateinit var proxyPortField: EditText
    private lateinit var wsPathField: EditText
    private val savedServers = mutableListOf<JSONObject>()
    private var updatingServerPicker = false
    private var ignoreNextServerSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = bg
            window.navigationBarColor = bg
            buildUi()
            val filter = IntentFilter(AetherVpnService.ACTION_STATUS)
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(vpnStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(vpnStatusReceiver, filter)
            showPreviousCrashIfAny()
        } catch (e: Throwable) {
            android.util.Log.e("FoxyVPNStartup", "FoxyVPN failed during startup", e)
            val details = "${e.javaClass.simpleName}: ${e.message ?: "No error message"}"
            setContentView(ScrollView(this).apply {
                setBackgroundColor(bg)
                addView(LinearLayout(this@FoxyMainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(24), dp(32), dp(24), dp(24))
                    addView(TextView(this@FoxyMainActivity).apply {
                        text = "FoxyVPN could not start"
                        textSize = 22f
                        setTextColor(pale)
                    })
                    addView(TextView(this@FoxyMainActivity).apply {
                        text = details
                        textSize = 15f
                        setTextColor(green)
                        setPadding(0, dp(16), 0, dp(16))
                    })
                    addView(TextView(this@FoxyMainActivity).apply {
                        text = "Please take a screenshot of this error and send it to support."
                        textSize = 14f
                        setTextColor(muted)
                    })
                })
            })
        }
    }


    private fun showPreviousCrashIfAny() {
        val prefs = getSharedPreferences("foxy_crash_report", MODE_PRIVATE)
        val report = prefs.getString("last_crash", null) ?: return
        val time = prefs.getLong("crash_time", 0L)
        // Consume the report before showing it so reopening the app does not loop.
        prefs.edit().remove("last_crash").remove("crash_time").apply()
        val body = "The previous run closed unexpectedly. Send this report to support.\n\n" +
            (if (time > 0L) "Time: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time)) + "\n\n" else "") +
            report
        val textView = TextView(this).apply {
            text = body
            textSize = 13f
            setTextColor(Color.BLACK)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        val scroller = ScrollView(this).apply { addView(textView) }
        AlertDialog.Builder(this)
            .setTitle("FoxyVPN crash report")
            .setView(scroller)
            .setPositiveButton("OK", null)
            .setNeutralButton("COPY REPORT") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("FoxyVPN crash report", body))
                Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == vpnConsentRequest) {
            if (resultCode == RESULT_OK) startPantherVpn()
            else appendLog("VPN permission was denied; no tunnel was started.")
        } else if (requestCode == openVpnFileRequest && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri == null) { appendLog("No OpenVPN profile was selected."); return }
            try {
                val config = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (config.isNullOrBlank()) { appendLog("The selected OpenVPN profile is empty."); return }
                getSharedPreferences("foxy_openvpn", MODE_PRIVATE).edit()
                    .putString("config", config)
                    .putString("name", uri.lastPathSegment ?: "Imported profile")
                    .apply()
                appendLog("OpenVPN profile imported. Choose OpenVPN Profile (.ovpn) and press CONNECT to start it.")
            } catch (e: Exception) { appendLog("Could not read OpenVPN profile: ${e.message}") }
        } else if (requestCode == openVpnConsentRequest) {
            if (resultCode == RESULT_OK) launchImportedOpenVpn()
            else appendLog("OpenVPN permission was denied; no tunnel was started.")
        }
    }

    private fun requestPantherVpn() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) startActivityForResult(prepare, vpnConsentRequest) else startPantherVpn()
    }

    private fun pickOpenVpnProfile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, openVpnFileRequest)
    }

    private fun startImportedOpenVpn() {
        val prefs = getSharedPreferences("foxy_openvpn", MODE_PRIVATE)
        val config = prefs.getString("config", null)
        if (config.isNullOrBlank()) {
            appendLog("Import an .ovpn profile first. Opening the file picker.")
            pickOpenVpnProfile()
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) startActivityForResult(prepare, openVpnConsentRequest)
        else launchImportedOpenVpn()
    }

    private fun launchImportedOpenVpn() {
        val prefs = getSharedPreferences("foxy_openvpn", MODE_PRIVATE)
        val config = prefs.getString("config", null)
        if (config.isNullOrBlank()) { appendLog("No OpenVPN profile imported."); pickOpenVpnProfile(); return }
        connect.isEnabled = false
        status.text = "●  STARTING OPENVPN..."
        status.setTextColor(green)
        appendLog("Starting imported OpenVPN profile. A successful start request does not prove the tunnel is connected.")
        worker.execute {
            try {
                VpnConnectionController.disconnect(this)
                val accepted = VpnGateConnector.connectConfig(this, config, prefs.getString("name", "Imported profile"))
                runOnUiThread {
                    connect.isEnabled = true
                    if (accepted) {
                        connect.text = "DISCONNECT"
                        status.text = "●  OPENVPN START REQUESTED"
                        appendLog("OpenVPN accepted the profile. Check engine logs for CONNECTED; this is not yet confirmation of a live tunnel.")
                    } else {
                        status.text = "●  OPENVPN FAILED"
                        appendLog("OpenVPN could not parse or start this profile. Check the profile format and logs.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { connect.isEnabled = true; status.text = "●  OPENVPN FAILED"; appendLog("OpenVPN start failed: ${e.message}") }
            }
        }
    }

    private fun startPantherVpn() {
        try {
            val prefs = getSharedPreferences("aether", MODE_PRIVATE)
            prefs.edit().putString("mode", "vpn").putString("engine", "turbo").apply()
            val intent = VpnConnectionController.startIntent(this, prefs)
            ContextCompat.startForegroundService(this, intent)
            pantherVpnRequested = true
            connect.text = "DISCONNECT"
            status.text = "●  STARTING VPN..."; status.setTextColor(green)
            appendLog("Started Panther VpnService. Wait for the service status; actual success depends on the selected engine and network.")
        } catch (e: Exception) { appendLog("Could not start Panther VPN: ${e.message}") }
    }

    private fun disconnectPantherVpn() {
        try { VpnConnectionController.disconnect(this); appendLog("Sent disconnect request to Panther VpnService.") }
        catch (e: Exception) { appendLog("Could not stop Panther VPN: ${e.message}") }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "盾"; textSize = 26f; gravity = Gravity.CENTER; setTextColor(green)
            background = rounded(Color.rgb(24, 59, 69), 18); layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }
        header.addView(logo)
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(13), 0, 0, 0) }
        titles.addView(label("Foxy VPN", 25f, pale, true))
        titles.addView(label("SSH TUNNEL  •  ANDROID", 11f, muted, false))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        root.addView(space(24))

        val hero = card()
        hero.gravity = Gravity.CENTER
        hero.setPadding(dp(18), dp(25), dp(18), dp(25))
        status = label("●  DISCONNECTED", 13f, muted, true).apply { gravity = Gravity.CENTER }
        hero.addView(status)
        val power = TextView(this).apply {
            text = "⏻"; textSize = 58f; gravity = Gravity.CENTER; setTextColor(green)
            background = rounded(Color.rgb(24, 59, 69), 100)
            layoutParams = LinearLayout.LayoutParams(dp(142), dp(142)).apply { topMargin = dp(20); bottomMargin = dp(18) }
        }
        hero.addView(power)
        hero.addView(label("Secure SSH Connection", 19f, pale, true).apply { gravity = Gravity.CENTER })
        hero.addView(label("Connect to your SSH server", 13f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(7), 0, 0) })
        root.addView(hero)
        root.addView(space(16))

        root.addView(label("GET STARTED", 12f, green, true))
        root.addView(space(10))
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val serverChoice = Button(this).apply {
            text = "Choose Server"; isAllCaps = false; textSize = 13f; setTextColor(bg)
            background = rounded(green, 14)
            setOnClickListener { scroll.post { scroll.smoothScrollTo(0, fieldsTop()) } }
        }
        val firefoxChoice = Button(this).apply {
            text = "Login Firefox"; isAllCaps = false; textSize = 13f; setTextColor(pale)
            background = rounded(Color.rgb(39, 57, 82), 14)
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.firefox.com/signin")))
                    appendLog("Opened Firefox Account sign-in in your browser. This is browser sign-in only; app account linking is not implemented.")
                } catch (e: Exception) { appendLog("Could not open Firefox sign-in: ${e.message}") }
            }
        }
        choices.addView(serverChoice, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) })
        choices.addView(firefoxChoice, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) })
        root.addView(choices)
        root.addView(space(20))

        root.addView(label("SERVER CONFIGURATION", 12f, green, true))
        root.addView(space(10))
        val serverPanel = card()
        serverPanel.addView(label("Connection source", 13f, muted, true))
        serverPicker = Spinner(this)
        serverPicker.background = rounded(Color.rgb(9, 17, 31), 12)
        serverPanel.addView(serverPicker, matchTop())
        val serverActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val saveServer = Button(this).apply {
            text = "SAVE MY SERVER"; isAllCaps = false; textSize = 12f; setTextColor(bg); background = rounded(green, 12)
            setOnClickListener { saveCurrentServer() }
        }
        val removeServer = Button(this).apply {
            text = "REMOVE SAVED"; isAllCaps = false; textSize = 12f; setTextColor(pale); background = rounded(Color.rgb(39, 57, 82), 12)
            setOnClickListener { removeSelectedServer() }
        }
        serverActions.addView(saveServer, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(5) })
        serverActions.addView(removeServer, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(5) })
        serverPanel.addView(space(8)); serverPanel.addView(serverActions)
        root.addView(serverPanel)
        root.addView(space(10))
        val fields = card()
        fields.addView(label("SSH Host / IP", 13f, muted, true))
        host = edit("example.com", false); fields.addView(host, matchTop())
        fields.addView(label("Port", 13f, muted, true).apply { setPadding(0, dp(12), 0, 0) })
        port = edit("22", false); fields.addView(port, matchTop())
        fields.addView(label("Username", 13f, muted, true).apply { setPadding(0, dp(12), 0, 0) })
        username = edit("SSH username", false); fields.addView(username, matchTop())
        fields.addView(label("Password", 13f, muted, true).apply { setPadding(0, dp(12), 0, 0) })
        password = edit("SSH password", true); fields.addView(password, matchTop())
        root.addView(fields)
        root.addView(space(12))

        val modePanel = card()
        modePanel.addView(label("CONNECTION MODE", 12f, green, true))
        modePanel.addView(space(6))
        modePanel.addView(label("Select a mode and configure its values", 13f, muted, false))
        modePicker = Spinner(this).apply { background = rounded(Color.rgb(9, 17, 31), 12) }
        val modeNames = listOf("Direct SSH", "HTTP Payload", "SSL/TLS + SNI", "Payload + SNI", "HTTP Proxy", "Payload + HTTP Proxy", "Panther VPN (Full Device)", "OpenVPN Profile (.ovpn)", "SSH over WebSocket (WS + Payload)")
        modePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeNames)
        modePanel.addView(modePicker, matchTop())

        fun addModeField(caption: String, hintText: String, key: String, secret: Boolean = false): EditText {
            modePanel.addView(label(caption, 13f, muted, true).apply { setPadding(0, dp(10), 0, 0) })
            val field = edit(hintText, secret)
            field.setText(getSharedPreferences("foxy_mode", MODE_PRIVATE).getString(key, "") ?: "")
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    getSharedPreferences("foxy_mode", MODE_PRIVATE).edit().putString(key, s?.toString() ?: "").apply()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            modePanel.addView(field, matchTop())
            return field
        }
        payloadField = addModeField("HTTP Payload", "e.g. CONNECT [host_port] [protocol][crlf][crlf]", "payload")
        sniField = addModeField("SNI / TLS Host", "e.g. tls.example.com", "sni")
        proxyHostField = addModeField("HTTP Proxy Host (optional)", "proxy.example.com", "proxy_host")
        proxyPortField = addModeField("HTTP Proxy Port", "80", "proxy_port").apply { if (text.isNullOrBlank()) setText("80") }
        wsPathField = addModeField("WebSocket Path", "/", "ws_path")
        modePicker.setSelection(getSharedPreferences("foxy_mode", MODE_PRIVATE).getInt("mode", 0).coerceIn(0, modeNames.lastIndex))
        modePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getSharedPreferences("foxy_mode", MODE_PRIVATE).edit().putInt("mode", position).apply()
                if (::log.isInitialized) appendLog("Selected connection mode: ${modeNames[position]}")
            }
        }
        val saveMode = Button(this).apply {
            text = "SAVE CONNECTION SETTINGS"; isAllCaps = false; textSize = 12f; setTextColor(bg); background = rounded(green, 12)
            setOnClickListener {
                getSharedPreferences("foxy_mode", MODE_PRIVATE).edit().putInt("mode", modePicker.selectedItemPosition)
                    .putString("payload", payloadField.text.toString()).putString("sni", sniField.text.toString())
                    .putString("proxy_host", proxyHostField.text.toString()).putString("proxy_port", proxyPortField.text.toString()).putString("ws_path", wsPathField.text.toString()).apply()
                appendLog("Connection mode and custom values saved on this device.")
            }
        }
        modePanel.addView(space(10)); modePanel.addView(saveMode, matchTop())
        root.addView(modePanel)
        root.addView(space(16))
        loadSavedServers()
        refreshServerPicker()

        val warning = TextView(this).apply {
            text = "ⓘ  OpenVPN Profile (.ovpn) starts the integrated OpenVPN engine from an imported profile. Direct SSH and HTTP Payload modes are separate transport checks and do not create a full-device VPN tunnel. SSH-over-WebSocket requires a compatible WS-to-TCP server/bridge; payload alone cannot turn an SSH session into OpenVPN."
            textSize = 12f; setTextColor(muted); setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.rgb(23, 35, 56), 14)
        }
        root.addView(warning)
        root.addView(space(16))
        connect = Button(this).apply {
            text = "CONNECT"; textSize = 15f; isAllCaps = false; setTextColor(bg)
            background = rounded(green, 16); setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                if (modePicker.selectedItemPosition == 6) { if (pantherVpnRequested) disconnectPantherVpn() else requestPantherVpn() }
                else if (modePicker.selectedItemPosition == 7) {
                    if (connect.text.toString() == "DISCONNECT") {
                        VpnGateConnector.stop(this)
                        connect.text = "CONNECT"
                        status.text = "●  DISCONNECTED"
                        appendLog("OpenVPN disconnect requested.")
                    } else startImportedOpenVpn()
                }
                else if (session?.isConnected == true) disconnect()
                else if (modePicker.selectedItemPosition == 0 || modePicker.selectedItemPosition == 8) connectSsh()
                else connectTransportMode()
            }
        }
        root.addView(connect, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(space(22))
        root.addView(label("CONNECTION LOG", 12f, green, true))
        root.addView(space(8))
        log = TextView(this).apply {
            text = "Ready. Enter your SSH server details.\n"; textSize = 12f; setTextColor(muted); typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.rgb(7, 12, 24), 14)
        }
        root.addView(log)
    }

    private fun loadSavedServers() {
        savedServers.clear()
        try {
            val raw = getSharedPreferences("foxy_servers", MODE_PRIVATE).getString("items", "[]") ?: "[]"
            val array = JSONArray(raw)
            for (i in 0 until array.length()) savedServers.add(array.getJSONObject(i))
        } catch (_: Exception) { savedServers.clear() }
    }

    private fun persistSavedServers() {
        val array = JSONArray()
        savedServers.forEach { array.put(it) }
        getSharedPreferences("foxy_servers", MODE_PRIVATE).edit().putString("items", array.toString()).apply()
    }

    private fun refreshServerPicker(selectIndex: Int = 0) {
        updatingServerPicker = true
        ignoreNextServerSelection = true
        val options = mutableListOf("FoxyVPN servers (server list not configured)")
        savedServers.forEach { options.add(it.optString("name", it.optString("host", "My server"))) }
        serverPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        serverPicker.setSelection(selectIndex.coerceIn(0, options.lastIndex))
        serverPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingServerPicker) { updatingServerPicker = false; return }
                if (ignoreNextServerSelection) { ignoreNextServerSelection = false; return }
                if (position == 0) {
                    AlertDialog.Builder(this@FoxyMainActivity)
                        .setTitle("FoxyVPN server list")
                        .setMessage("This project does not include an official FoxyVPN server directory or API credentials, so no real FoxyVPN servers can be listed yet. Choose a saved server or add your own SSH server below.")
                        .setPositiveButton("OK", null).show()
                } else {
                    val item = savedServers[position - 1]
                    host.setText(item.optString("host")); port.setText(item.optString("port", "22"))
                    username.setText(item.optString("username")); password.setText("")
                    appendLog("Selected saved server: ${item.optString("name")}")
                }
            }
        }
        updatingServerPicker = false
    }

    private fun saveCurrentServer() {
        val h = host.text.toString().trim()
        val p = port.text.toString().toIntOrNull()
        val u = username.text.toString().trim()
        val pw = password.text.toString()
        if (h.isBlank() || p == null || p !in 1..65535 || u.isBlank()) {
            appendLog("Enter a valid host, port and username before saving the profile."); return
        }
        val nameInput = EditText(this).apply { hint = "Server name (e.g. My SSH)"; setText(h) }
        AlertDialog.Builder(this).setTitle("Save custom server")
            .setView(nameInput)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { h }
                savedServers.removeAll { it.optString("name") == name }
                savedServers.add(JSONObject().put("name", name).put("host", h).put("port", p.toString()).put("username", u))
                persistSavedServers(); refreshServerPicker(savedServers.size)
                password.setText("")
                appendLog("Saved custom server profile: $name. Password is not stored; enter it when connecting.")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun removeSelectedServer() {
        val index = serverPicker.selectedItemPosition
        if (index <= 0 || index - 1 !in savedServers.indices) {
            appendLog("Select one of your saved servers to remove."); return
        }
        val name = savedServers[index - 1].optString("name")
        savedServers.removeAt(index - 1); persistSavedServers(); refreshServerPicker()
        appendLog("Removed saved server: $name")
    }


    private fun connectTransportMode() {
        val mode = modePicker.selectedItemPosition
        val targetHost = host.text.toString().trim()
        val targetPort = port.text.toString().toIntOrNull()
        val proxyHost = proxyHostField.text.toString().trim().ifBlank { targetHost }
        val proxyPort = proxyPortField.text.toString().trim().ifBlank { "80" }.toIntOrNull()
        val sni = sniField.text.toString().trim()
        val payload = payloadField.text.toString()
        if (targetHost.isBlank() || targetPort == null || targetPort !in 1..65535) { appendLog("Enter a valid target host and port."); return }
        if ((mode == 4 || mode == 5) && (proxyHost.isBlank() || proxyPort == null || proxyPort !in 1..65535)) { appendLog("Enter a valid HTTP proxy host and port (1–65535). The proxy port is separate from the SSH server port."); return }
        if ((mode == 2 || mode == 3) && sni.isBlank()) { appendLog("Enter an SNI/TLS hostname."); return }
        hideKeyboard(); connect.isEnabled = false; status.text = "●  CONNECTING..."; status.setTextColor(green)
        appendLog("Starting ${modePicker.selectedItem} transport check. This check is not a full-device VPN connection.")
        worker.execute {
            var sock: Socket? = null
            try {
                when (mode) {
                    1 -> {
                        sock = Socket(); sock.connect(InetSocketAddress(targetHost, targetPort), 12000); sock.soTimeout = 8000
                        val wire = normalizePayload(payload.ifBlank { "GET / HTTP/1.1[crlf]Host: $targetHost[crlf]Connection: close[crlf][crlf]" }, targetHost, targetPort)
                        sock.getOutputStream().write(wire.toByteArray(Charsets.ISO_8859_1)); sock.getOutputStream().flush()
                        val response = readResponse(sock)
                        runOnUiThread { appendLog("Payload response preview:\n$response") }
                    }
                    2, 3 -> {
                        val tlsPort = if (targetPort == 22) 443 else targetPort
                        val raw = Socket(); raw.connect(InetSocketAddress(targetHost, tlsPort), 12000)
                        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, sni, tlsPort, true) as SSLSocket
                        tls.soTimeout = 8000
                        tls.sslParameters = tls.sslParameters.apply { serverNames = listOf(javax.net.ssl.SNIHostName(sni)) }
                        tls.startHandshake(); sock = tls
                        if (mode == 3 && payload.isNotBlank()) { tls.getOutputStream().write(normalizePayload(payload, targetHost, tlsPort).toByteArray(Charsets.ISO_8859_1)); tls.getOutputStream().flush() }
                        runOnUiThread { appendLog("TLS handshake succeeded for SNI $sni. ${if (mode == 3) "Custom payload sent if supplied." else "Handshake only."} Not a VPN tunnel.") }
                    }
                    4, 5 -> {
                        sock = Socket(); sock.connect(InetSocketAddress(proxyHost, proxyPort!!), 12000); sock.soTimeout = 8000
                        val req = if (mode == 5 && payload.isNotBlank()) normalizePayload(payload, targetHost, targetPort) else "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost:$targetPort\r\nProxy-Connection: Keep-Alive\r\n\r\n"
                        sock.getOutputStream().write(req.toByteArray(Charsets.ISO_8859_1)); sock.getOutputStream().flush()
                        val response = readResponse(sock); val ok = response.startsWith("HTTP/1.1 200") || response.startsWith("HTTP/1.0 200")
                        runOnUiThread { appendLog("Proxy response:\n$response"); appendLog(if (ok) "Proxy CONNECT accepted; device traffic is not routed yet." else "Proxy did not return HTTP 200.") }
                        if (!ok) throw IllegalStateException("HTTP proxy CONNECT was not accepted")
                    }
                }
                runOnUiThread { status.text = "●  CHECK COMPLETE (NOT VPN)"; status.setTextColor(green); connect.text = "TEST MODE"; connect.isEnabled = true }
            } catch (e: Exception) {
                try { sock?.close() } catch (_: Exception) {}
                runOnUiThread { status.text = "●  CONNECTION FAILED"; status.setTextColor(Color.rgb(255,130,130)); connect.isEnabled = true; appendLog("Connection test failed: ${e.message ?: e.javaClass.simpleName}") }
            } finally { try { sock?.close() } catch (_: Exception) {} }
        }
    }

    /**
     * Checks whether the selected endpoint speaks WebSocket. This is only an upgrade/bridge
     * check; SSH-over-WebSocket authentication requires a server-side bridge compatible with
     * the app's SSH protocol and is not reported as connected by this probe.
     */
    private fun connectWebSocketBridgeCheck() {
        val targetHost = host.text.toString().trim()
        val targetPort = port.text.toString().trim().toIntOrNull()
        val wsHost = proxyHostField.text.toString().trim().ifBlank { targetHost }
        val wsPort = proxyPortField.text.toString().trim().ifBlank { "80" }.toIntOrNull()
        val pathValue = wsPathField.text.toString().trim().ifBlank { "/" }
        val payload = payloadField.text.toString()
        if (targetHost.isBlank() || targetPort == null || targetPort !in 1..65535) {
            appendLog("Enter a valid SSH server host and port.")
            return
        }
        if (wsHost.isBlank() || wsPort == null || wsPort !in 1..65535) {
            appendLog("Enter a valid WS Proxy Host and Proxy Port (for example, port 80 or 8080 if your server uses it).")
            return
        }
        if (!pathValue.startsWith("/") || pathValue.contains("\r") || pathValue.contains("\n")) {
            appendLog("WebSocket path must start with / and must not contain line breaks.")
            return
        }
        hideKeyboard()
        connect.isEnabled = false
        status.text = "●  CHECKING WEBSOCKET..."
        status.setTextColor(green)
        appendLog("Checking WebSocket upgrade at $wsHost:$wsPort$pathValue. This is not yet an SSH login or a VPN tunnel.")
        worker.execute {
            var sock: Socket? = null
            try {
                sock = Socket()
                sock.connect(InetSocketAddress(wsHost, wsPort), 12000)
                sock.soTimeout = 8000
                val keyBytes = ByteArray(16)
                java.security.SecureRandom().nextBytes(keyBytes)
                val key = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
                val extraHeaders = if (payload.isBlank()) "" else {
                    val expanded = normalizePayload(payload, targetHost, targetPort)
                    val lines = expanded.replace("\r\n", "\n").split("\n")
                    lines.filter { line ->
                        val trimmed = line.trim()
                        trimmed.isNotEmpty() && !trimmed.startsWith("GET ", true) &&
                            !trimmed.startsWith("CONNECT ", true) && trimmed.contains(":") &&
                            !trimmed.startsWith("Host:", true) &&
                            !trimmed.startsWith("Upgrade:", true) &&
                            !trimmed.startsWith("Connection:", true) &&
                            !trimmed.startsWith("Sec-WebSocket-", true)
                    }.joinToString("\r\n", postfix = if (lines.any { it.contains(":") }) "\r\n" else "")
                }
                val request = "GET $pathValue HTTP/1.1\r\n" +
                    "Host: $wsHost:$wsPort\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" + extraHeaders + "\r\n"
                sock.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
                sock.getOutputStream().flush()
                val response = readResponse(sock)
                val upgraded = response.startsWith("HTTP/1.1 101") || response.startsWith("HTTP/1.0 101")
                runOnUiThread {
                    appendLog("WebSocket response:\n$response")
                    if (upgraded) {
                        status.text = "●  WEBSOCKET UPGRADE OK"
                        appendLog("WebSocket handshake succeeded. SSH-over-WS is NOT confirmed: a compatible WS-to-SSH bridge and framed data transport are still required.")
                    } else {
                        status.text = "●  WEBSOCKET FAILED"
                        appendLog("Endpoint did not accept a standard WebSocket upgrade. Verify WS Proxy Host/Port, path, TLS setting and server bridge.")
                    }
                    status.setTextColor(if (upgraded) green else Color.rgb(255, 130, 130))
                    connect.isEnabled = true
                }
            } catch (e: Exception) {
                try { sock?.close() } catch (_: Exception) {}
                runOnUiThread {
                    status.text = "●  WEBSOCKET FAILED"
                    status.setTextColor(Color.rgb(255, 130, 130))
                    connect.isEnabled = true
                    appendLog("WebSocket check failed: ${e.message ?: e.javaClass.simpleName}")
                }
            } finally { try { sock?.close() } catch (_: Exception) {} }
        }
    }

    private fun normalizePayload(input: String, targetHost: String, targetPort: Int): String = input
        .replace("[host_port]", "$targetHost:$targetPort", ignoreCase = true)
        .replace("[host]", targetHost, ignoreCase = true)
        .replace("[port]", targetPort.toString(), ignoreCase = true)
        .replace("[protocol]", "HTTP/1.1", ignoreCase = true)
        .replace("[crlf]", "\r\n", ignoreCase = true)
        .replace("[lf]", "\n", ignoreCase = true)

    private fun readResponse(socket: Socket, maxBytes: Int = 4096): String {
        val input = BufferedInputStream(socket.getInputStream()); val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(512)
        while (out.size() < maxBytes) {
            val n = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size())); if (n <= 0) break
            out.write(buffer, 0, n); val str = out.toString("ISO-8859-1")
            if (str.contains("\r\n\r\n") || str.contains("\n\n")) break
        }
        return out.toString("ISO-8859-1").ifBlank { "(No response received)" }
    }

    private fun connectSsh() {
        val h = host.text.toString().trim()
        val p = port.text.toString().toIntOrNull()
        val u = username.text.toString().trim()
        val pw = password.text.toString()
        if (h.isBlank() || p == null || p !in 1..65535 || u.isBlank() || pw.isEmpty()) {
            appendLog("Please fill in host, valid port, username and password."); return
        }
        hideKeyboard()
        connect.isEnabled = false
        status.text = "●  CONNECTING..."; status.setTextColor(green)
        appendLog(if (modePicker.selectedItemPosition == 8) "Connecting SSH through WebSocket + payload to $h:$p ..." else "Connecting to $h:$p ...")
        worker.execute {
            var candidate: Session? = null
            try {
                val jsch = JSch()
                candidate = jsch.getSession(u, h, p)
                if (modePicker.selectedItemPosition == 8) {
                    val wsHost = proxyHostField.text.toString().trim().ifBlank { h }
                    val wsPort = proxyPortField.text.toString().trim().ifBlank { "80" }.toIntOrNull()
                    val wsPath = wsPathField.text.toString().trim().ifBlank { "/" }
                    if (wsPort == null || wsPort !in 1..65535) throw IllegalArgumentException("Enter a valid WS Proxy Port (1–65535).")
                    if (!wsPath.startsWith("/") || wsPath.contains("\r") || wsPath.contains("\n")) throw IllegalArgumentException("WebSocket path must start with / and contain no line breaks.")
                    candidate.setSocketFactory(WebSocketTunnelSocketFactory(wsHost, wsPort, wsPath, payloadField.text.toString(), h, p))
                }
                candidate.setPassword(pw)
                candidate.userInfo = object : UserInfo {
                    override fun getPassphrase(): String? = null
                    override fun getPassword(): String = pw
                    override fun promptPassword(message: String?): Boolean = true
                    override fun promptPassphrase(message: String?): Boolean = false
                    override fun promptYesNo(message: String?): Boolean {
                        val answer = booleanArrayOf(false)
                        val latch = CountDownLatch(1)
                        runOnUiThread {
                            AlertDialog.Builder(this@FoxyMainActivity)
                                .setTitle("Trust SSH host key?")
                                .setMessage((message ?: "Unknown host key") + "\n\nOnly accept if you trust this server and have verified its fingerprint.")
                                .setPositiveButton("Trust for this session") { _, _ -> answer[0] = true; latch.countDown() }
                                .setNegativeButton("Cancel") { _, _ -> answer[0] = false; latch.countDown() }
                                .setOnCancelListener { answer[0] = false; latch.countDown() }
                                .show()
                        }
                        return latch.await(90, TimeUnit.SECONDS) && answer[0]
                    }
                    override fun showMessage(message: String?) { runOnUiThread { appendLog(message ?: "SSH server message") } }
                }
                val config = Properties()
                config["StrictHostKeyChecking"] = "ask"
                config["PreferredAuthentications"] = "password,keyboard-interactive"
                candidate.setConfig(config)
                candidate.connect(15000)
                session = candidate
                runOnUiThread {
                    status.text = "●  SSH CONNECTED"; status.setTextColor(green)
                    connect.text = "DISCONNECT"; connect.isEnabled = true
                    appendLog("SSH authentication succeeded.")
                    appendLog(if (modePicker.selectedItemPosition == 8) "SSH authentication succeeded over WebSocket. This SSH session still does not route all device traffic as a VPN." else "Note: internet traffic is not routed through this session yet.")
                }
            } catch (e: Exception) {
                try { candidate?.disconnect() } catch (_: Exception) {}
                runOnUiThread {
                    status.text = "●  CONNECTION FAILED"; status.setTextColor(Color.rgb(255, 130, 130))
                    connect.isEnabled = true
                    appendLog("Error: ${e.message ?: e.javaClass.simpleName}")
                    appendLog("Check server details, network, credentials and host-key trust.")
                }
            }
        }
    }

    private fun disconnect() {
        worker.execute {
            try { session?.disconnect() } catch (_: Exception) {}
            session = null
            runOnUiThread {
                status.text = "●  DISCONNECTED"; status.setTextColor(muted)
                connect.text = "CONNECT"; connect.isEnabled = true
                appendLog("SSH session disconnected.")
            }
        }
    }

    private fun fieldsTop(): Int {
        val fields = host.parent as? View ?: return 0
        var y = fields.top
        var parent = fields.parent
        while (parent is View && parent !== (host.rootView)) {
            y += parent.top
            parent = parent.parent
        }
        return y.coerceAtLeast(0)
    }

    private fun appendLog(message: String) {
        log.append("\n> $message")
    }
    private fun edit(hintText: String, secret: Boolean): EditText = EditText(this).apply {
        hint = hintText; setHintTextColor(Color.rgb(105, 125, 151)); setTextColor(pale); textSize = 15f
        setSingleLine(true); background = rounded(Color.rgb(9, 17, 31), 12)
        setPadding(dp(13), dp(10), dp(13), dp(10))
        if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        else if (hintText == "22") inputType = android.text.InputType.TYPE_CLASS_NUMBER
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(panel, 18)
    }
    private fun label(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchTop() = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun hideKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(currentFocus?.windowToken, 0) }
    override fun onDestroy() {
        try { unregisterReceiver(vpnStatusReceiver) } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        worker.shutdownNow()
        super.onDestroy()
    }
}
