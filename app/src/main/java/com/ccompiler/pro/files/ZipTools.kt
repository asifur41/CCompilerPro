package com.ccompiler.pro.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup and restore of all .c/.h projects as one ZIP. Restore never trusts the names inside the ZIP. */
object ZipTools {
    const val MAX_ENTRIES = 5000
    const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    private val segment = Regex("[A-Za-z0-9_][A-Za-z0-9._ -]*")

    data class RestoreResult(val restored: Int, val skipped: Int)

    private fun isCode(name: String): Boolean {
        val l = name.lowercase()
        return l.endsWith(".c") || l.endsWith(".h")
    }

    /** Returns a safe relative path ("dir/file.c") or null if the entry must be skipped. */
    fun safeRelativePath(entryName: String): String? {
        val n = entryName.replace('\\', '/')
        if (n.isEmpty() || n.startsWith("/") || n.length > 200) return null
        val parts = n.split('/')
        if (parts.size > 8) return null
        for (p in parts) if (p.isEmpty() || !segment.matches(p)) return null     // also rejects "." and ".."
        if (!isCode(parts.last())) return null
        return parts.joinToString("/")
    }

    fun backup(root: File, out: OutputStream): Int {
        var count = 0
        ZipOutputStream(out).use { zip ->
            root.walkTopDown().filter { it.isFile && isCode(it.name) }.forEach { f ->
                val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        return count
    }

    private fun freeName(f: File): File {
        val base = f.nameWithoutExtension
        val ext = f.extension
        var i = 1
        var t: File
        do {
            t = File(f.parentFile, "${base}_restored${if (i > 1) i.toString() else ""}.$ext")
            i++
        } while (t.exists())
        return t
    }

    /** [replace] = overwrite files with the same name; otherwise the restored copy gets "_restored" in its name. */
    fun restore(input: InputStream, root: File, replace: Boolean): RestoreResult {
        var ok = 0
        var skipped = 0
        var total = 0L
        var entries = 0
        root.mkdirs()
        val rootCanon = root.canonicalPath
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries++
                if (entries > MAX_ENTRIES) { skipped++; break }
                if (e.isDirectory) continue
                val rel = safeRelativePath(e.name)
                if (rel == null) { skipped++; continue }
                var target = File(root, rel)
                if (!target.canonicalPath.startsWith(rootCanon + File.separator)) { skipped++; continue }
                if (target.exists() && !replace) target = freeName(target)
                target.parentFile?.mkdirs()
                target.outputStream().use { o ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val r = zip.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > MAX_TOTAL_BYTES) throw IOException("The backup is too large")
                        o.write(buf, 0, r)
                    }
                }
                ok++
            }
        }
        return RestoreResult(ok, skipped)
    }
}
