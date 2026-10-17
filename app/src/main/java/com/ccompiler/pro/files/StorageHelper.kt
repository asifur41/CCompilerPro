package com.ccompiler.pro.files

import android.content.Context
import java.io.File

/** All storage lives in app-private directories. No storage permissions are declared. */
object StorageHelper {
    /** /Android/data/com.ccompiler.pro/files/projects/ (falls back to internal storage). */
    fun projectsRoot(ctx: Context): File {
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "projects").apply { mkdirs() }
    }

    fun workDir(ctx: Context): File = File(ctx.filesDir, "run").apply { mkdirs() }

    fun draftFile(ctx: Context): File = File(ctx.filesDir, "autosave_draft.c")

    fun sanitizeName(name: String): String? {
        val s = name.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").trimStart('.')
        return if (s.isEmpty()) null else s
    }

    fun hasCExtension(name: String): Boolean {
        val l = name.lowercase()
        return l.endsWith(".c") || l.endsWith(".h")
    }

    fun ensureCExtension(name: String): String = if (hasCExtension(name)) name else "$name.c"
}
