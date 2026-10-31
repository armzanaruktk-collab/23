package com.example.bluetunnel

import android.net.VpnService
import android.os.ParcelFileDescriptor

class OpenVpn3Engine {
    var isRunning: Boolean = false
        private set

    fun start(vpnService: VpnService, config: String): Boolean {
        // Prototype / stub integration
        isRunning = true
        return true
    }

    fun stop() {
        isRunning = false
    }
}
