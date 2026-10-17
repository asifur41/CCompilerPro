package com.ccompiler.pro.compiler

import java.io.File
import java.util.concurrent.TimeUnit

/** Runs the compiled binary in a separate process with timeout, CPU limit and best-effort memory polling. */
class ExecutorEngine {
    @Volatile private var proc: Process? = null
    @Volatile private var cancelled = false

    companion object {
        const val TIMEOUT_SEC = 30L
        const val MEMORY_LIMIT_KB = 256L * 1024
        const val MAX_OUTPUT_CHARS = 500_000
    }

    fun cancel() {
        cancelled = true
        proc?.destroyForcibly()
    }

    private fun pidOf(p: Process): Int = try {
        val f = p.javaClass.getDeclaredField("pid")
        f.isAccessible = true
        f.getInt(p)
    } catch (_: Throwable) {
        -1
    }

    private fun rssKb(pid: Int): Long {
        if (pid <= 0) return -1
        return try {
            File("/proc/$pid/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.filter { it.isDigit() }?.toLongOrNull() ?: -1L
            }
        } catch (_: Exception) {
            -1
        }
    }

    fun run(exe: File, stdin: String, workDir: File, onChunk: (String, Boolean) -> Unit): ExecResult {
        cancelled = false
        val start = System.currentTimeMillis()
        val pb = ProcessBuilder("/system/bin/sh", "-c", "ulimit -t 40 2>/dev/null; exec \"\$0\"", exe.absolutePath)
        pb.directory(workDir)
        val env = pb.environment()
        env.clear()
        env["PATH"] = "/system/bin"
        env["HOME"] = workDir.absolutePath
        env["TMPDIR"] = workDir.absolutePath
        val p = try {
            pb.start()
        } catch (e: Exception) {
            onChunk("Could not start program: ${e.message}\n", true)
            return ExecResult(-1, System.currentTimeMillis() - start)
        }
        proc = p

        val total = java.util.concurrent.atomic.AtomicInteger(0)
        fun pump(stream: java.io.InputStream, isErr: Boolean) = Thread {
            try {
                stream.bufferedReader().use { r ->
                    val buf = CharArray(2048)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        val t = total.addAndGet(n)
                        if (t <= MAX_OUTPUT_CHARS) onChunk(String(buf, 0, n), isErr)
                        else if (t - n <= MAX_OUTPUT_CHARS) onChunk("\n[output truncated]\n", true)
                    }
                }
            } catch (_: Exception) {
            }
        }.also { it.start() }

        val tOut = pump(p.inputStream, false)
        val tErr = pump(p.errorStream, true)
        Thread {
            try {
                p.outputStream.use { it.write(stdin.toByteArray()) }
            } catch (_: Exception) {
            }
        }.start()

        val pid = pidOf(p)
        var timedOut = false
        var memKilled = false
        while (true) {
            if (p.waitFor(500, TimeUnit.MILLISECONDS)) break
            if (System.currentTimeMillis() - start > TIMEOUT_SEC * 1000) { timedOut = true; p.destroyForcibly(); break }
            val rss = rssKb(pid)
            if (rss > MEMORY_LIMIT_KB) { memKilled = true; p.destroyForcibly(); break }
        }
        try { p.waitFor(2, TimeUnit.SECONDS) } catch (_: Exception) {}
        tOut.join(1000)
        tErr.join(1000)
        proc = null
        val code = try { p.exitValue() } catch (_: Exception) { -1 }
        return ExecResult(code, System.currentTimeMillis() - start, timedOut, memKilled, cancelled)
    }
}
