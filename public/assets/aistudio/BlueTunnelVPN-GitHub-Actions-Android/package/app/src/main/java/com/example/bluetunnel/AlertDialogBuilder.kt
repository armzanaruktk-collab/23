package com.example.bluetunnel

import android.app.AlertDialog
import android.content.Context

object AlertDialogBuilder {
    fun show(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
