package com.ccompiler.pro.compiler

import android.content.Context
import android.system.Os
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Locates the bundled native C toolchain.
 *
 * Contract (see README "Compiler toolchain"):
 *   app/src/main/jniLibs/arm64-v8a/libchibicc.so  -> compiler driver, invoked as `chibicc -o <out> <src.c>`
 *   libas.so / libld.so / libcc.so ...            -> helper tools the driver calls by name (as, ld, cc)
 * Android only lets an app execute files from nativeLibraryDir, so tools are packaged as lib*.so and
 * symlinked under their real names into a private bin directory that is put first on PATH.
 */
object Toolchain {
    val tools = listOf("chibicc", "cc", "as", "ld", "ar", "strip")

    fun nativeDir(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir)
    /** Developer override: tools pushed with `adb` into files/toolchain/bin (works because targetSdk = 28). */
    fun userDir(ctx: Context) = File(ctx.filesDir, "toolchain/bin")
    fun sysroot(ctx: Context) = File(ctx.filesDir, "toolchain/sysroot")
    private fun activeDir(ctx: Context) = File(ctx.filesDir, "toolchain/active")

    private fun locate(ctx: Context, name: String): File? {
        val packaged = File(nativeDir(ctx), "lib$name.so")
        if (packaged.exists()) return packaged
        val user = File(userDir(ctx), name)
        return if (user.exists()) user else null
    }

    fun compiler(ctx: Context): File? = locate(ctx, "chibicc")

    /** Rebuilds the PATH directory of symlinks and returns it. */
    fun prepare(ctx: Context): File {
        val dir = activeDir(ctx)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        for (t in tools) {
            val src = locate(ctx, t) ?: continue
            try {
                Os.symlink(src.absolutePath, File(dir, t).absolutePath)
            } catch (_: Exception) {
            }
        }
        return dir
    }
}

class CompilerEngine(private val ctx: Context) {
    @Volatile private var proc: Process? = null
    @Volatile private var cancelled = false

    fun isAvailable(): Boolean = Toolchain.compiler(ctx) != null

    fun missingMessage(): String =
        "No C toolchain is installed in this build.\n\n" +
            "CCompiler Pro expects a compiler driver packaged as libchibicc.so (plus libas.so / libld.so)\n" +
            "in app/src/main/jniLibs/arm64-v8a/. See README.md, section \"Compiler toolchain\".\n"

    fun cancel() {
        cancelled = true
        proc?.destroyForcibly()
    }

    fun compile(source: File, output: File, timeoutSec: Long = 15): CompilationResult {
        cancelled = false
        val start = System.currentTimeMillis()
        val cc = Toolchain.compiler(ctx)
            ?: return CompilationResult(false, missingMessage(), emptyList(), 0)
        val bin = Toolchain.prepare(ctx)
        return try {
            val pb = ProcessBuilder(cc.absolutePath, "-o", output.absolutePath, source.absolutePath)
            pb.directory(source.parentFile)
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env.clear()
            env["PATH"] = bin.absolutePath + ":/system/bin"
            env["HOME"] = ctx.filesDir.absolutePath
            env["TMPDIR"] = ctx.cacheDir.absolutePath
            env["CC_SYSROOT"] = Toolchain.sysroot(ctx).absolutePath
            val p = pb.start()
            proc = p
            val sb = StringBuilder()
            val reader = Thread {
                try {
                    p.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(2048)
                        while (true) {
                            val n = r.read(buf)
                            if (n < 0) break
                            if (sb.length < 200_000) sb.append(buf, 0, n)
                        }
                    }
                } catch (_: Exception) {
                }
            }
            reader.start()
            val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) p.destroyForcibly()
            reader.join(1000)
            proc = null
            val text = sb.toString()
            val diags = parseDiagnostics(text)
            val ok = finished && !cancelled && p.exitValue() == 0 && output.exists()
            val note = when {
                cancelled -> "\nCompilation cancelled."
                !finished -> "\nCompilation timed out after ${timeoutSec}s."
                else -> ""
            }
            CompilationResult(ok, text + note, diags, System.currentTimeMillis() - start, !finished, cancelled)
        } catch (e: Exception) {
            proc = null
            CompilationResult(
                false,
                "Could not start the compiler: ${e.message}\n" +
                    "(If this says 'Permission denied', the toolchain file is not executable or Android blocked it.)",
                emptyList(), System.currentTimeMillis() - start
            )
        }
    }

    private val diagRe = Regex("^[^:\\n]*:(\\d+):(?:(\\d+):)?\\s*(error|warning|note)?:?\\s*(.*)$", RegexOption.MULTILINE)

    private fun parseDiagnostics(text: String): List<Diagnostic> =
        diagRe.findAll(text).map {
            Diagnostic(
                it.groupValues[1].toIntOrNull() ?: 0,
                it.groupValues[2].toIntOrNull() ?: 0,
                it.groupValues[3].ifEmpty { "error" },
                it.groupValues[4]
            )
        }.toList()
}
