package com.ccompiler.pro.compiler

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs one program at a time as a child process.
 *
 * Threading: every blocking operation (reading stdout/stderr, writing stdin, waiting for exit,
 * killing) happens on Dispatchers.IO coroutines. Listener callbacks are delivered on the main
 * thread. The UI thread never touches a pipe.
 */
class ExecutorEngine {

    interface Listener {
        fun onOutput(text: String, isErr: Boolean)
        /** The line the user typed with [sendInput] (so the UI can echo it, like a terminal). */
        fun onInputEcho(text: String)
        /** True while the process looks blocked on stdin; the UI shows its input field only then. */
        fun onWaitingForInput(waiting: Boolean)
        fun onFinished(result: ExecResult)
    }

    companion object {
        const val TIMEOUT_SEC = 30L                    // counts only time the program is actually busy
        const val MEMORY_LIMIT_KB = 256L * 1024
        const val MAX_OUTPUT_CHARS = 500_000
        private const val IDLE_MS = 300L               // silence before we consider "waiting for input"
        private const val MAX_WAIT_FOR_INPUT_MS = 10 * 60 * 1000L
        private const val TICK_MS = 100L
    }

    private class Session(val process: Process, val pidFile: File, val listener: Listener) {
        @Volatile var pid = -1
        @Volatile var cancelled = false        // user pressed Stop -> result is still reported
        @Volatile var replaced = false         // superseded by a newer run -> stays silent
        @Volatile var lastOutputAt = SystemClock.elapsedRealtime()
        @Volatile var waiting = false
        @Volatile var outputLimit = false
        val outChars = AtomicInteger(0)
        val inputQueue = Channel<ByteArray?>(Channel.UNLIMITED)   // null = close stdin (EOF)
        var job: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    @Volatile private var current: Session? = null

    fun isRunning(): Boolean = current != null

    // ------------------------------------------------------------------ public API

    /**
     * Starts [command]. Any process that is still alive is terminated first (SIGTERM, then SIGKILL),
     * all on a background coroutine. [shouldStart] is checked after the old process is gone, so a
     * run that was superseded in the meantime never starts.
     */
    fun start(command: List<String>, workDir: File, listener: Listener, shouldStart: () -> Boolean = { true }) {
        scope.launch {
            startMutex.withLock {
                terminateCurrent(silent = true)
                if (shouldStart()) launchSession(command, workDir, listener)
            }
        }
    }

    /** Terminates the running process without reporting anything (used before a new run). */
    suspend fun stopSilently() {
        startMutex.withLock { terminateCurrent(silent = true) }
    }

    /** User pressed Stop: terminate cleanly; [Listener.onFinished] is still delivered (cancelled = true). */
    fun stop() {
        val s = current ?: return
        s.cancelled = true
        scope.launch { terminate(s) }
    }

    /** Queues one line for the program's stdin. Never blocks the caller. */
    fun sendInput(text: String) {
        val s = current ?: return
        s.inputQueue.trySend((text + "\n").toByteArray(Charsets.UTF_8))
        scope.launch(Dispatchers.Main) { if (!s.replaced && current === s) s.listener.onInputEcho(text) }
    }

    /** Sends EOF (like Ctrl+D) by closing the program's stdin. */
    fun closeInput() {
        current?.inputQueue?.trySend(null)
    }

    /** Called from Activity.onDestroy: kill immediately and stop all coroutines. */
    fun shutdown() {
        current?.let {
            it.replaced = true
            try { it.process.destroyForcibly() } catch (_: Exception) {}
        }
        scope.cancel()
    }

    // ------------------------------------------------------------------ internals

    private suspend fun terminateCurrent(silent: Boolean) {
        val s = current ?: return
        if (silent) s.replaced = true
        terminate(s)
        withTimeoutOrNull(3000) { s.job?.join() }
        if (current === s) current = null
    }

    /** SIGTERM first, SIGKILL if it has not exited after 400 ms. Blocking: call from IO only. */
    private fun terminate(s: Session) {
        val p = s.process
        try {
            if (s.pid > 0) Os.kill(s.pid, OsConstants.SIGTERM) else p.destroy()
        } catch (_: Exception) {
            try { p.destroy() } catch (_: Exception) {}
        }
        try {
            if (!p.waitFor(400, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly()
                p.waitFor(1500, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
        }
    }

    private suspend fun post(s: Session, f: () -> Unit) {
        if (s.replaced) return
        withContext(Dispatchers.Main) { if (!s.replaced) f() }
    }

    private fun launchSession(command: List<String>, workDir: File, listener: Listener) {
        val pidFile = File(workDir, ".ccp_pid")
        pidFile.delete()
        // The shell records its pid, then `exec` replaces it with the program (same pid).
        val script = "echo \$\$ > \"\$CCP_PIDFILE\"; ulimit -t 40 2>/dev/null; exec \"\$@\""
        val pb = ProcessBuilder(listOf("/system/bin/sh", "-c", script, "sh") + command)
        pb.directory(workDir)
        val env = pb.environment()
        env.clear()
        env["PATH"] = "/system/bin"
        env["HOME"] = workDir.absolutePath
        env["TMPDIR"] = workDir.absolutePath
        env["CCP_PIDFILE"] = pidFile.absolutePath
        val p = try {
            pb.start()
        } catch (e: Exception) {
            scope.launch(Dispatchers.Main) {
                listener.onOutput("Could not start program: ${e.message}\n", true)
                listener.onFinished(ExecResult(-1, 0))
            }
            return
        }

        val s = Session(p, pidFile, listener)
        current = s
        s.job = scope.launch {
            val started = SystemClock.elapsedRealtime()

            val outJob = launch { pump(s, p.inputStream, false) }
            val errJob = launch { pump(s, p.errorStream, true) }
            val writer = launch {                      // single writer => input order is preserved
                try {
                    for (bytes in s.inputQueue) {
                        if (bytes == null) { p.outputStream.close(); break }
                        p.outputStream.write(bytes)
                        p.outputStream.flush()
                    }
                } catch (_: Exception) {
                }
            }

            var activeMs = 0L
            var waitedMs = 0L
            var timedOut = false
            var memKilled = false
            var notWaitingTicks = 0
            var tick = 0
            var last = SystemClock.elapsedRealtime()

            while (p.isAlive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                tick++
                if (s.pid <= 0) s.pid = readPid(pidFile)

                val nowWaiting = (now - s.lastOutputAt >= IDLE_MS) && isSleeping(s.pid)
                if (nowWaiting) {
                    notWaitingTicks = 0
                    waitedMs += dt
                    if (!s.waiting) { s.waiting = true; post(s) { listener.onWaitingForInput(true) } }
                } else {
                    notWaitingTicks++
                    activeMs += dt
                    // hysteresis: hide only after ~200 ms of not waiting, so the field doesn't flicker
                    if (s.waiting && notWaitingTicks >= 2) { s.waiting = false; post(s) { listener.onWaitingForInput(false) } }
                }

                if (activeMs > TIMEOUT_SEC * 1000 || waitedMs > MAX_WAIT_FOR_INPUT_MS) { timedOut = true; break }
                if (tick % 5 == 0) {
                    val rss = rssKb(s.pid)
                    if (rss > MEMORY_LIMIT_KB) { memKilled = true; break }
                }
            }
            if (p.isAlive) terminate(s)

            s.inputQueue.close()
            writer.cancel()
            withTimeoutOrNull(1500) { outJob.join(); errJob.join() }

            val code = try { p.exitValue() } catch (_: Exception) { -1 }
            val result = ExecResult(
                code, SystemClock.elapsedRealtime() - started,
                timedOut, memKilled, s.cancelled, s.outputLimit
            )
            if (s.waiting) { s.waiting = false; post(s) { listener.onWaitingForInput(false) } }
            if (current === s) current = null
            post(s) { listener.onFinished(result) }
        }
    }

    /** Reads one stream on an IO thread; blocking here is fine, the UI is never involved. */
    private suspend fun pump(s: Session, stream: InputStream, isErr: Boolean) {
        try {
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val buf = CharArray(2048)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                s.lastOutputAt = SystemClock.elapsedRealtime()
                val total = s.outChars.addAndGet(n)
                if (total <= MAX_OUTPUT_CHARS) {
                    val chunk = String(buf, 0, n)
                    post(s) { s.listener.onOutput(chunk, isErr) }
                } else if (!s.outputLimit) {
                    s.outputLimit = true
                    post(s) { s.listener.onOutput("\n[output limit reached]\n", true) }
                    scope.launch { terminate(s) }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun readPid(f: File): Int = try { f.readText().trim().toIntOrNull() ?: -1 } catch (_: Exception) { -1 }

    /** /proc/<pid>/stat state 'S' = sleeping (a pipe read shows up like this). Unknown -> assume yes. */
    private fun isSleeping(pid: Int): Boolean {
        if (pid <= 0) return true
        return try {
            val stat = File("/proc/$pid/stat").readText()
            val state = stat.substringAfterLast(')').trim().firstOrNull() ?: return true
            state == 'S'
        } catch (_: Exception) {
            true
        }
    }

    private fun rssKb(pid: Int): Long {
        if (pid <= 0) return -1
        return try {
            File("/proc/$pid/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }?.filter { it.isDigit() }?.toLongOrNull() ?: -1L
            }
        } catch (_: Exception) {
            -1
        }
    }
}
