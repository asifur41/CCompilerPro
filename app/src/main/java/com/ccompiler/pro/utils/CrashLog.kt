package com.ccompiler.pro.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * If the app crashes, the error text (class names and line numbers only - never your code or files) is
 * saved in a small file on THIS phone. It is never sent anywhere; it is attached to a bug-report email
 * only when you choose to send one.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    @Volatile private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText(
                    "Time: ${Date()}\nThread: ${thread.name}\nApp: ${LinkOpener.versionName(app)}\n" +
                        "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}\n\n$sw"
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(ctx: Context): String? = try {
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText()?.take(4000)
    } catch (_: Exception) {
        null
    }
}
