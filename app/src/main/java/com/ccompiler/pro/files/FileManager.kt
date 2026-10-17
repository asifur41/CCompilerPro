package com.ccompiler.pro.files

import java.io.File

object FileManager {

    fun list(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isDirectory || StorageHelper.hasCExtension(it.name) }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

    fun createFolder(parent: File, name: String): File? {
        val n = StorageHelper.sanitizeName(name) ?: return null
        val f = File(parent, n)
        return if (f.exists() || f.mkdirs()) f else null
    }

    fun read(f: File): String = f.readText()

    fun write(f: File, text: String) {
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    fun delete(f: File): Boolean = f.deleteRecursively()

    fun rename(f: File, newName: String): File? {
        val clean = StorageHelper.sanitizeName(newName) ?: return null
        val n = if (f.isDirectory) clean else StorageHelper.ensureCExtension(clean)
        val target = File(f.parentFile, n)
        if (target.exists()) return null
        return if (f.renameTo(target)) target else null
    }

    fun duplicate(f: File): File? {
        if (f.isDirectory) return null
        val base = f.nameWithoutExtension
        val ext = f.extension
        var i = 1
        var target: File
        do {
            val suffix = if (i == 1) "_copy" else "_copy$i"
            target = File(f.parentFile, "$base$suffix.$ext")
            i++
        } while (target.exists())
        f.copyTo(target)
        return target
    }

    /** Root plus every sub folder (for the Save As folder picker). */
    fun folders(root: File): List<File> {
        val out = ArrayList<File>()
        out.add(root)
        root.walkTopDown().filter { it.isDirectory && it != root }.sortedBy { it.path.lowercase() }.forEach { out.add(it) }
        return out
    }

    fun relative(root: File, f: File): String {
        val r = f.absolutePath.removePrefix(root.absolutePath).trimStart(File.separatorChar)
        return if (r.isEmpty()) "/" else "/$r"
    }

    /** Case-insensitive search by file name or file contents across all folders. */
    fun search(root: File, query: String, limit: Int = 50): List<File> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        val out = ArrayList<File>()
        for (f in root.walkTopDown()) {
            if (out.size >= limit) break
            if (!f.isFile || !StorageHelper.hasCExtension(f.name)) continue
            if (f.name.lowercase().contains(q)) {
                out.add(f)
            } else {
                try {
                    if (f.length() < 1_000_000 && f.readText().lowercase().contains(q)) out.add(f)
                } catch (_: Exception) {
                }
            }
        }
        return out
    }
}
