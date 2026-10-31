package com.example.bluetunnel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 13, 27)
    private val panel = Color.rgb(17, 27, 49)
    private val blue = Color.rgb(42, 132, 255)
    private val white = Color.rgb(240, 245, 255)
    private val muted = Color.rgb(155, 174, 204)
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var payload: EditText
    private lateinit var status: TextView
    private lateinit var profileInfo: TextView
    private lateinit var openVpnMode: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSettings()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 30)
        }
        scroll.addView(layout)
        setContentView(scroll)

        layout.addView(TextView(this).apply {
            text = "BlueTunnel"; textSize = 29f; setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = "OPENVPN 2  •  SSH  •  PAYLOAD"
            textSize = 11f; setTextColor(blue); setPadding(0, 4, 0, 18)
        })

        status = TextView(this).apply {
            text = "●  DISCONNECTED"; textSize = 15f; setTextColor(muted)
            gravity = Gravity.CENTER; setPadding(10, 18, 10, 18); setBackgroundColor(panel)
        }
        layout.addView(status, matchWrap())

        section(layout, "SSH SERVER")
        host = field(layout, "SSH Host / IP")
        port = field(layout, "SSH Port", "22")
        username = field(layout, "SSH Username")
        password = field(layout, "SSH Password").apply { inputType = 129 }

        section(layout, "OPENVPN CONNECTION MODE")
        openVpnMode = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        openVpnMode.addView(RadioButton(this).apply {
            id = 2001; text = "OpenVPN normal (.ovpn direct)"; setTextColor(white)
        })
        openVpnMode.addView(RadioButton(this).apply {
            id = 2002; text = "OpenVPN + Payload Bridge"; setTextColor(white)
        })
        layout.addView(openVpnMode, matchWrap())

        section(layout, "CUSTOM PAYLOAD")
        payload = EditText(this).apply {
            setTextColor(white); setHintTextColor(muted)
            hint = "GET / HTTP/1.1[crlf]\nHost: [host][crlf]"
            textSize = 13f; gravity = Gravity.TOP; minLines = 4; maxLines = 10
            setPadding(14, 12, 14, 12); setBackgroundColor(panel)
        }
        layout.addView(payload, matchWrap().apply { topMargin = 4 })

        layout.addView(button("PREVIEW PAYLOAD", Color.rgb(35, 53, 83)) {
            val rendered = PayloadRenderer.render(
                payload.text.toString(),
                host.text.toString(),
                port.text.toString(),
                "BlueTunnel-Android"
            )
            AlertDialogBuilder.show(this, "Rendered payload", rendered)
        }, matchWrap().apply { topMargin = 8 })

        layout.addView(button("SAVE SETTINGS", blue) {
            saveSettings(); status.text = "●  SETTINGS SAVED"; status.setTextColor(blue)
        }, matchWrap().apply { topMargin = 8 })

        layout.addView(button("IMPORT .OVPN FILE", Color.rgb(35, 53, 83)) {
            openOvpnPicker()
        }, matchWrap().apply { topMargin = 8 })

        profileInfo = TextView(this).apply {
            text = "No .ovpn profile imported"; textSize = 12f
            setTextColor(muted); setPadding(2, 8, 2, 8)
        }
        layout.addView(profileInfo)

        layout.addView(button("CONNECT (OpenVPN 2 integration pending)", blue) {
            saveSettings()
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, 101)
            else showNotImplemented()
        }, matchWrap().apply { topMargin = 8 })

        layout.addView(TextView(this).apply {
            text = "OpenVPN 2 source is bundled under third_party/openvpn2. Native Android build, management IPC/TUN-FD adapter, and Payload bridge integration are not yet complete; traffic is not tunneled in this package."
            textSize = 12f; setTextColor(muted); setPadding(2, 16, 2, 4)
        })
    }

    private fun section(parent: LinearLayout, name: String) {
        parent.addView(TextView(this).apply {
            text = name; textSize = 12f; setTextColor(blue)
            typeface = Typeface.DEFAULT_BOLD; setPadding(2, 22, 2, 8)
        })
    }

    private fun field(parent: LinearLayout, hintText: String, default: String = ""): EditText {
        val e = EditText(this).apply {
            hint = hintText; setText(default); setTextColor(white); setHintTextColor(muted)
            textSize = 14f; singleLine = true; setPadding(14, 4, 14, 4); setBackgroundColor(panel)
        }
        parent.addView(e, matchWrap().apply { topMargin = 5 })
        return e
    }

    private fun button(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label; setTextColor(Color.WHITE); setBackgroundColor(color)
        textSize = 12f; setOnClickListener { action() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun saveSettings() {
        prefs.edit()
            .putString("host", host.text.toString())
            .putString("port", port.text.toString())
            .putString("username", username.text.toString())
            .putString("password", password.text.toString())
            .putString("payload", payload.text.toString())
            .putString("openvpn_mode", if (openVpnMode.checkedRadioButtonId == 2002) "payload" else "normal")
            .apply()
    }

    private fun loadSettings() {
        host.setText(prefs.getString("host", ""))
        port.setText(prefs.getString("port", "22"))
        username.setText(prefs.getString("username", ""))
        password.setText(prefs.getString("password", ""))
        payload.setText(prefs.getString("payload",
            "GET / HTTP/1.1[crlf]\nHost: [host][crlf]\nConnection: Keep-Alive[crlf][crlf]"))
        openVpnMode.check(if (prefs.getString("openvpn_mode", "normal") == "payload") 2002 else 2001)
        prefs.getString("ovpn_uri", null)?.let { profileInfo.text = "Imported profile URI: $it" }
    }

    private fun openOvpnPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }
        startActivityForResult(intent, 100)
    }

    @Deprecated("Legacy callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            prefs.edit().putString("ovpn_uri", uri.toString()).apply()
            profileInfo.text = "Imported profile URI: $uri\nStored only; not connected."
        } else if (requestCode == 101 && resultCode == RESULT_OK) {
            showNotImplemented()
        }
    }

    private fun showNotImplemented() {
        status.text = "●  ENGINE NOT LINKED"
        status.setTextColor(Color.rgb(255, 190, 80))
        Toast.makeText(this, "OpenVPN 2 native engine + Android management/TUN adapter ยังต้อง build และเชื่อมต่อก่อนใช้งานจริง", Toast.LENGTH_LONG).show()
    }
}
