package com.ccompiler.pro.compiler

data class Diagnostic(val line: Int, val column: Int, val severity: String, val message: String)

data class CompilationResult(
    val success: Boolean,
    val output: String,
    val diagnostics: List<Diagnostic>,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false
)

data class ExecResult(
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val memoryKilled: Boolean = false,
    val cancelled: Boolean = false,
    val outputLimit: Boolean = false
)

/** Parses "file:LINE: error: message" lines (tcc format; the optional ":COL:" of gcc/clang is tolerated). */
object ErrorParser {
    private val re = Regex("^([^\\s:][^:\\n]*):(\\d+):(?:(\\d+):)?\\s*(error|warning)\\s*:\\s*(.*)$")

    /** Groups: 1 = file, 2 = line, 3 = column (may be empty), 4 = severity, 5 = message. */
    fun matchLine(raw: String): MatchResult? = re.find(raw.trimEnd('\r'))

    /** All diagnostics that belong to [fileName] (compared by base name), in output order. */
    fun parse(output: String, fileName: String): List<Diagnostic> {
        val out = ArrayList<Diagnostic>()
        for (raw in output.lines()) {
            val m = matchLine(raw) ?: continue
            if (m.groupValues[1].substringAfterLast('/') != fileName) continue
            val line = m.groupValues[2].toIntOrNull() ?: continue
            out.add(Diagnostic(line, m.groupValues[3].toIntOrNull() ?: 0, m.groupValues[4], m.groupValues[5].trim()))
        }
        return out
    }
}

/** Plain-English explanations. Key = lower-case fragment of the compiler message; first match wins. */
object ErrorHints {
    private const val BRACKETS =
        "Brackets don't match: every ( [ { needs a matching ) ] }. Look above this line for an unclosed bracket, " +
            "or for one too many."

    private val table: Map<String, String> = linkedMapOf(
        "';' expected" to
            "A statement is missing its ending semicolon (;). Check the end of this line and the line above it " +
            "– the compiler often reports the problem on the line AFTER the missing semicolon.",
        "undeclared" to
            "This name hasn't been declared. Check the spelling and capital letters, and declare the variable " +
            "with a type (for example: int x;) before using it, inside a place where it is visible.",
        "implicit declaration" to
            "You called a function the compiler hasn't seen. Add the right #include (<stdio.h> for printf/scanf, " +
            "<stdlib.h> for malloc/rand, <string.h> for strlen/strcpy, <math.h> for sqrt/pow) or declare the " +
            "function above main().",
        "too many arguments" to
            "You passed more arguments than the function accepts. Compare this call with the function's declaration.",
        "too few arguments" to
            "You passed fewer arguments than the function needs. Compare this call with the function's declaration.",
        "')' expected" to BRACKETS,
        "'}' expected" to BRACKETS,
        "']' expected" to BRACKETS,
        "'(' expected" to BRACKETS,
        "'{' expected" to BRACKETS,
        "'[' expected" to BRACKETS,
        "declaration expected" to
            "Something is written where only a declaration or function may appear – usually an extra } above, " +
            "or code placed outside of a function.",
        "missing terminating" to "A string or character literal is missing its closing quote (\" or ').",
        "lvalue expected" to
            "The left side of = (or ++/--) must be a variable, not a value or an expression.",
        "incompatible" to
            "The types don't match (for example assigning text to a number). Check the variable types on this line.",
        "redefinition" to "A variable or function with this name is already defined. Rename one of them.",
        "not found" to
            "That file isn't available. Only the standard C headers (stdio.h, stdlib.h, string.h, math.h, ctype.h, " +
            "limits.h, stdint.h, time.h, errno.h, assert.h, stdbool.h, stdarg.h, stddef.h) are included."
    )

    fun hintFor(message: String): String? {
        val m = message.lowercase()
        for ((key, hint) in table) if (m.contains(key)) return hint
        return null
    }
}
