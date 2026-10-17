package com.ccompiler.pro.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object ImportExportHandler {

    fun queryName(ctx: Context, uri: Uri): String? {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    /** Copies a picked .c/.h document into the private project folder. Returns null if rejected. */
    fun importUri(ctx: Context, uri: Uri, destDir: File): File? {
        val rawName = queryName(ctx, uri) ?: return null
        val name = StorageHelper.sanitizeName(rawName) ?: return null
        if (!StorageHelper.hasCExtension(name)) return null
        destDir.mkdirs()
        var target = File(destDir, name)
        var i = 1
        while (target.exists()) {
            target = File(destDir, "${File(name).nameWithoutExtension}_$i.${File(name).extension}")
            i++
        }
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        } ?: return null
        return target
    }

    fun exportToUri(ctx: Context, uri: Uri, text: String): Boolean = try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        true
    } catch (e: Exception) {
        false
    }

    fun listSamples(ctx: Context): List<String> =
        (ctx.assets.list("samples") ?: emptyArray()).filter { it.endsWith(".c") }.sorted()

    fun readSample(ctx: Context, name: String): String =
        ctx.assets.open("samples/$name").bufferedReader().use { it.readText() }
}
