package com.example.bluetunnel

import android.content.Intent
import android.net.VpnService

class TunnelVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
