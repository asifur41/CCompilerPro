package com.ccompiler.pro.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object LinkOpener {
    const val WEBSITE = "https://asifur-dev.blogspot.com/p/home.html?m=1&m=1"
    const val DONATE = "https://asifur-dev.blogspot.com/p/donate.html?m=1"
    const val EMAIL = "asifur2580@gmail.com"

    /** Opens a URL in the system browser. Needs no INTERNET permission: the browser does the networking. */
    fun openLink(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            NotificationHelper.toast(context, "No browser found")
        }
    }

    fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    fun reportBug(context: Context) {
        val body = "\n\n--- Device info ---\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "App version: ${versionName(context)}\n\nDescribe the bug:\n"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "CCompiler Pro Bug Report")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            NotificationHelper.toast(context, "No email app found")
        }
    }
}
