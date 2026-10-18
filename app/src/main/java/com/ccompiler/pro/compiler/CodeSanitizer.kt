package com.ccompiler.pro.compiler

/**
 * Static source check run BEFORE compiling. It is a best-effort guard (regex based) and is not a
 * security boundary by itself: the real isolation is Android's per-app sandbox.
 */
object CodeSanitizer {
    data class Violation(val line: Int, val message: String)

    private val callRules: List<Pair<Regex, String>> = listOf(
        Regex("\\bsystem\\s*\\(") to "system() is not allowed",
        Regex("\\b(execl|execlp|execle|execv|execvp|execvpe|execve|fexecve)\\s*\\(") to "exec-family calls are not allowed",
        Regex("\\b(popen|pclose)\\s*\\(") to "popen() is not allowed",
        Regex("\\b(fork|vfork|clone)\\s*\\(") to "creating child processes is not allowed",
        Regex("\\bposix_spawnp?\\s*\\(") to "spawning processes is not allowed",
        Regex("\\b(socket|socketpair|connect|bind|listen|accept|accept4|send|sendto|sendmsg|recv|recvfrom|recvmsg|getaddrinfo|gethostbyname|gethostbyaddr)\\s*\\(") to
            "socket/network calls are not allowed",
        Regex("\\b(syscall|ptrace|dlopen)\\s*\\(") to "low-level system calls are not allowed",
        Regex("##") to "token pasting (##) is not allowed"
    )
    private val includeRule = Regex("#\\s*include\\s*[<\"](sys/socket\\.h|netinet/[^>\"]*|arpa/inet\\.h|netdb\\.h|sys/un\\.h|spawn\\.h|sys/ptrace\\.h)[>\"]")
    private val pathRule = Regex("(/proc/|/sys/|/dev/)")

    /** Returns (code with comments blanked, code with comments AND string/char contents blanked). */
    private fun scan(src: String): Pair<String, String> {
        val a = StringBuilder(src.length)
        val b = StringBuilder(src.length)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            val d = if (i + 1 < n) src[i + 1] else '\u0000'
            if (c == '/' && d == '/') {
                while (i < n && src[i] != '\n') { a.append(' '); b.append(' '); i++ }
            } else if (c == '/' && d == '*') {
                a.append("  "); b.append("  "); i += 2
                while (i < n && !(src[i] == '*' && i + 1 < n && src[i + 1] == '/')) {
                    val ch = if (src[i] == '\n') '\n' else ' '
                    a.append(ch); b.append(ch); i++
                }
                if (i < n) { a.append("  "); b.append("  "); i += 2 }
            } else if (c == '"' || c == '\'') {
                a.append(c); b.append(c); i++
                while (i < n && src[i] != c && src[i] != '\n') {
                    if (src[i] == '\\' && i + 1 < n) {
                        a.append(src[i]).append(src[i + 1]); b.append("  "); i += 2
                    } else {
                        a.append(src[i]); b.append(' '); i++
                    }
                }
                if (i < n && src[i] == c) { a.append(c); b.append(c); i++ }
            } else {
                a.append(c); b.append(c); i++
            }
        }
        return Pair(a.toString(), b.toString())
    }

    private fun lineOf(text: String, index: Int): Int {
        var line = 1
        for (i in 0 until index.coerceAtMost(text.length)) if (text[i] == '\n') line++
        return line
    }

    private val identifier = Regex("[A-Za-z_]\\w*")

    /** True if the program itself defines a function with this name (a real definition with a body). */
    private fun definedByUser(name: String, code: String): Boolean =
        Regex("\\b(?:void|int|char|short|long|float|double|unsigned|signed|size_t|bool|struct\\s+\\w+|\\w+_t)[\\s*]+" +
            Regex.escape(name) + "\\s*\\([^{};]*\\)\\s*\\{").containsMatchIn(code)

    /** True for `obj.name(` and `ptr->name(`: a struct member / function pointer, not the libc function. */
    private fun isMemberCall(code: String, index: Int): Boolean {
        var j = index - 1
        while (j >= 0 && (code[j] == ' ' || code[j] == '\t')) j--
        return j >= 0 && (code[j] == '.' || (code[j] == '>' && j > 0 && code[j - 1] == '-'))
    }

    fun check(source: String): List<Violation> {
        val (withStrings, masked) = scan(source)
        val out = ArrayList<Violation>()
        for ((re, msg) in callRules) {
            for (m in re.findAll(masked)) {
                val name = identifier.find(m.value)?.value
                if (name != null && (isMemberCall(masked, m.range.first) || definedByUser(name, masked))) continue
                out.add(Violation(lineOf(masked, m.range.first), msg))
            }
        }
        for (m in includeRule.findAll(withStrings)) out.add(Violation(lineOf(withStrings, m.range.first), "network/process header is not allowed"))
        for (m in pathRule.findAll(withStrings)) out.add(Violation(lineOf(withStrings, m.range.first), "access to /proc/, /sys/ and /dev/ is not allowed"))
        return out.sortedBy { it.line }.distinctBy { it.line to it.message }
    }
}
