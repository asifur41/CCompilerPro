package com.ccompiler.pro.utils

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.util.Locale

object DataTools {
    private fun size(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { size(it) } ?: 0L)

    private fun tempFolders(ctx: Context): List<File> =
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir, File(ctx.filesDir, "run"))

    /** Deletes only temporary files (compiler leftovers, shared images). Your projects are not touched. Returns bytes freed. */
    fun clearCache(ctx: Context): Long {
        val before = tempFolders(ctx).sumOf { size(it) }
        for (d in tempFolders(ctx)) d.listFiles()?.forEach { it.deleteRecursively() }
        return before
    }

    fun format(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    }

    /** Erases ALL projects, settings and caches of this app. Cannot be undone. */
    fun deleteAllData(ctx: Context) {
        try {
            ctx.getExternalFilesDir(null)?.listFiles()?.forEach { it.deleteRecursively() }
            ctx.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            ctx.filesDir.listFiles()?.forEach { it.deleteRecursively() }
            ctx.getSharedPreferences("ccompiler_pro", Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Exception) {
        }
        try {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
        } catch (_: Exception) {
        }
    }
}
