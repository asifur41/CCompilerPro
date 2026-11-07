package com.ccompiler.pro.compiler

/**
 * "Program arguments": turns the text of the optional box into a list of values, and builds the small C
 * starter file that hands them to the user's main(argc, argv). Pure logic (no Android code).
 *
 * The values are written straight into the starter file as C strings, so no shell, no command line and no
 * stdin is involved: spaces, quotes, * / \ and non-English letters arrive exactly as typed.
 */
object ArgsTools {
    const val MAX_ARGS = 64
    const val MAX_ARG_BYTES = 1000
    const val MAX_TOTAL_BYTES = 8000

    data class Parsed(val args: List<String>, val error: String? = null)

    /**
     * Rules: spaces/tabs separate values. "double quotes" keep spaces inside one value and can touch other text
     * (a"b c"d is one value). 'single quotes' work the same but only at the start of a value, so an apostrophe
     * inside a word (don't) stays a normal character. Inside double quotes \" is a quote and \\ is a backslash;
     * elsewhere a backslash is just a backslash. "" is an empty value. A missing closing quote is tolerated.
     */
    fun parse(input: String): Parsed {
        val args = ArrayList<String>()
        val cur = StringBuilder()
        var inArg = false
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            if (c == '\u0000') {
                i++                                   // a NUL character can never be part of a C string
            } else if (c == '"') {
                inArg = true
                i++
                while (i < n && input[i] != '"') {
                    if (input[i] == '\\' && i + 1 < n && (input[i + 1] == '"' || input[i + 1] == '\\')) {
                        cur.append(input[i + 1])
                        i += 2
                    } else {
                        if (input[i] != '\u0000') cur.append(input[i])
                        i++
                    }
                }
                if (i < n) i++                        // the closing quote
            } else if (c == '\'' && !inArg) {          // 'single quotes' only open at the start of a value, so don't stays don't
                inArg = true
                i++
                while (i < n && input[i] != '\'') {
                    if (input[i] != '\u0000') cur.append(input[i])
                    i++
                }
                if (i < n) i++
            } else if (c.isWhitespace()) {
                if (inArg) {
                    args.add(cur.toString())
                    cur.setLength(0)
                    inArg = false
                }
                i++
            } else {
                inArg = true
                cur.append(c)
                i++
            }
        }
        if (inArg) args.add(cur.toString())

        if (args.size > MAX_ARGS) return Parsed(emptyList(), "too many values (the limit is $MAX_ARGS).")
        var total = 0
        for (a in args) {
            val bytes = a.toByteArray(Charsets.UTF_8).size
            if (bytes > MAX_ARG_BYTES) return Parsed(emptyList(), "a value is too long (the limit is $MAX_ARG_BYTES bytes).")
            total += bytes
        }
        if (total > MAX_TOTAL_BYTES) return Parsed(emptyList(), "the values are too long together (the limit is $MAX_TOTAL_BYTES bytes).")
        return Parsed(args)
    }

    /** The inside of a C string literal for [s]: printable ASCII as is, everything else as 3-digit octal bytes. */
    fun cEscape(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val v = b.toInt() and 0xFF
            if (v == 0x22) {
                sb.append("\\\"")
            } else if (v == 0x5C) {
                sb.append("\\\\")
            } else if (v in 0x20..0x7E) {
                sb.append(v.toChar())
            } else {
                sb.append('\\')
                sb.append('0' + ((v shr 6) and 7))
                sb.append('0' + ((v shr 3) and 7))
                sb.append('0' + (v and 7))
            }
        }
        return sb.toString()
    }

    /**
     * The starter file that is run: it includes the user's program under another name for main(), then calls it with
     * argc/argv built from [programName] (= argv[0]) and [args] (= argv[1], argv[2], ...). stdout is made unbuffered
     * so a prompt such as printf("Enter: ") is shown before the program waits for input.
     */
    fun buildRunWrapper(userFile: String, programName: String, args: List<String>): String {
        val all = listOf(programName) + args
        val sb = StringBuilder()
        sb.append("#define main __ccp_user_main\n")
        sb.append("#include \"").append(userFile).append("\"\n")
        sb.append("#undef main\n")
        sb.append("#include <stdio.h>\n")
        for ((i, a) in all.withIndex()) {
            sb.append("static char __ccp_a").append(i).append("[] = \"").append(cEscape(a)).append("\";\n")
        }
        sb.append("static char *__ccp_argv[] = { ")
        for (i in all.indices) sb.append("__ccp_a").append(i).append(", ")
        sb.append("0 };\n")
        sb.append("int main(int argc, char **argv) {\n")
        sb.append("    setvbuf(stdout, 0, _IONBF, 0);\n")
        sb.append("    return ((int (*)(int, char **))__ccp_user_main)(").append(all.size).append(", __ccp_argv);\n")
        sb.append("}\n")
        return sb.toString()
    }
}
