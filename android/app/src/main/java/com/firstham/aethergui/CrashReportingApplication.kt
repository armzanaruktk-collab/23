package com.firstham.aethergui

import android.app.Application
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last uncaught Java/Kotlin crash on-device so it can be shown
 * on the next launch, even when Android closes the app without a dialog.
 */
class CrashReportingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                getSharedPreferences("foxy_crash_report", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", "Thread: ${thread.name}\n${writer}")
                    .putLong("crash_time", System.currentTimeMillis())
                    .commit()
                Log.e("FoxyVPNCrash", "Uncaught exception saved for next launch", error)
            } catch (reportError: Throwable) {
                Log.e("FoxyVPNCrash", "Could not save crash report", reportError)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}