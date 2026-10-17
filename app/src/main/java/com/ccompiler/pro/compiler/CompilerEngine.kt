package com.ccompiler.pro.compiler

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Toolchain = a TinyCC executable built for Android arm64 by GitHub Actions (scripts/build_tcc.sh),
 * shipped as jniLibs/arm64-v8a/libtccexe.so (Android only allows executing files from there).
 * Programs are type-checked with `tcc -c` and executed with `tcc -run` (compiled to memory and run
 * in that separate process), so no linker, assembler or writable-directory executable is needed.
 */
object Toolchain {
    fun nativeDir(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir)
    /** Developer override: an executable named `tcc` pushed to files/toolchain/bin. */
    fun userDir(ctx: Context) = File(ctx.filesDir, "toolchain/bin")
    fun sysroot(ctx: Context) = File(ctx.filesDir, "toolchain/sysroot")

    fun tcc(ctx: Context): File? {
        val packaged = File(nativeDir(ctx), "libtccexe.so")
        if (packaged.exists()) return packaged
        val user = File(userDir(ctx), "tcc")
        return if (user.exists()) user else null
    }

    /** Copies assets/toolchain (C headers) to private storage once per header version. */
    @Synchronized
    fun ensureExtracted(ctx: Context): File {
        val root = sysroot(ctx)
        val wanted = try {
            ctx.assets.open("toolchain/version.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            return root
        }
        val marker = File(root, ".version")
        if (marker.exists() && marker.readText().trim() == wanted) return root
        root.deleteRecursively()
        root.mkdirs()
        copyAssetDir(ctx, "toolchain", root)
        marker.writeText(wanted)
        return root
    }

    private fun copyAssetDir(ctx: Context, path: String, dest: File) {
        val names = ctx.assets.list(path) ?: return
        dest.mkdirs()
        for (n in names) {
            val child = "$path/$n"
            val sub = ctx.assets.list(child)
            if (sub != null && sub.isNotEmpty()) {
                copyAssetDir(ctx, child, File(dest, n))
            } else {
                ctx.assets.open(child).use { input -> File(dest, n).outputStream().use { input.copyTo(it) } }
            }
        }
    }

    fun baseArgs(ctx: Context): List<String>? {
        val t = tcc(ctx) ?: return null
        val root = ensureExtracted(ctx)
        return listOf(
            t.absolutePath, "-nostdinc", "-nostdlib",
            "-I" + File(root, "include").absolutePath,
            "-I" + File(root, "tccinclude").absolutePath
        )
    }
}

class CompilerEngine(private val ctx: Context) {
    @Volatile private var proc: Process? = null
    @Volatile private var cancelled = false

    fun isAvailable(): Boolean = Toolchain.tcc(ctx) != null

    fun missingMessage(): String =
        "The C compiler (libtccexe.so) is not inside this APK.\n\n" +
            "It is built by the GitHub Actions workflow (scripts/build_tcc.sh). " +
            "Rebuild the APK from the latest workflow run. See README.md.\n"

    fun cancel() {
        cancelled = true
        proc?.destroyForcibly()
    }

    /**
     * Command that compiles `source` to memory and runs it. It runs a tiny wrapper that includes the
     * user's file, renames its main(), and calls it after switching stdout to unbuffered mode.
     * Without that, `printf("Enter a number: ")` would sit in a buffer while the program waits for input.
     * Errors inside the user's file keep their own file name and line numbers.
     */
    fun runCommand(source: File): List<String> {
        val wrapper = File(source.parentFile, "prog_run.c")
        wrapper.writeText(
            "#define main __ccp_user_main\n" +
                "#include \"${source.name}\"\n" +
                "#undef main\n" +
                "#include <stdio.h>\n" +
                "int main(int argc, char **argv) {\n" +
                "    setvbuf(stdout, 0, _IONBF, 0);\n" +
                "    return ((int (*)(int, char **))__ccp_user_main)(argc, argv);\n" +
                "}\n"
        )
        return (Toolchain.baseArgs(ctx) ?: emptyList()) + listOf("-run", wrapper.name)
    }

    private fun environment(pb: ProcessBuilder) {
        val env = pb.environment()
        env.clear()
        env["PATH"] = "/system/bin"
        env["HOME"] = ctx.filesDir.absolutePath
        env["TMPDIR"] = ctx.cacheDir.absolutePath
    }

    /** Syntax/semantic check: `tcc -Wall -c -o <output> <source>`. */
    fun compile(source: File, output: File, timeoutSec: Long = 15): CompilationResult {
        cancelled = false
        val start = System.currentTimeMillis()
        val base = Toolchain.baseArgs(ctx)
            ?: return CompilationResult(false, missingMessage(), emptyList(), 0)
        return try {
            val pb = ProcessBuilder(base + listOf("-Wall", "-c", "-o", output.name, source.name))
            pb.directory(source.parentFile)
            pb.redirectErrorStream(true)
            environment(pb)
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
            val ok = finished && !cancelled && p.exitValue() == 0
            val note = when {
                cancelled -> "\nCompilation cancelled."
                !finished -> "\nCompilation timed out after ${timeoutSec}s."
                else -> ""
            }
            CompilationResult(ok, text + note, ErrorParser.parse(text, source.name), System.currentTimeMillis() - start, !finished, cancelled)
        } catch (e: Exception) {
            proc = null
            CompilationResult(
                false,
                "Could not start the compiler: ${e.message}\n" +
                    "(libtccexe.so must be an arm64 executable; check the Actions log of scripts/build_tcc.sh.)",
                emptyList(), System.currentTimeMillis() - start
            )
        }
    }
}
