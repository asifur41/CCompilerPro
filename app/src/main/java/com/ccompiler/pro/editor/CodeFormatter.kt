package com.ccompiler.pro.editor

/** Brace-based re-indentation (4 spaces) plus light spacing normalisation. */
object CodeFormatter {
    private const val INDENT = "    "

    private class Res(val text: String, val opens: Int, val closes: Int, val leading: Int, val inBlock: Boolean)

    private fun analyze(line: String, startBlock: Boolean): Res {
        val sb = StringBuilder()
        var inBlock = startBlock
        var inStr = false
        var inChr = false
        var opens = 0
        var closes = 0
        var leading = 0
        var lead = true
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            val nx = if (i + 1 < line.length) line[i + 1] else '\u0000'
            if (inBlock) {
                sb.append(ch)
                if (ch == '*' && nx == '/') { sb.append('/'); i += 2; inBlock = false; continue }
                i++; continue
            }
            if (inStr || inChr) {
                sb.append(ch)
                if (ch == '\\' && i + 1 < line.length) { sb.append(nx); i += 2; continue }
                if (inStr && ch == '"') inStr = false else if (inChr && ch == '\'') inChr = false
                i++; continue
            }
            when {
                ch == '/' && nx == '/' -> { sb.append(line.substring(i)); i = line.length }
                ch == '/' && nx == '*' -> { sb.append("/*"); i += 2; inBlock = true; lead = false }
                ch == '"' -> { inStr = true; lead = false; sb.append(ch); i++ }
                ch == '\'' -> { inChr = true; lead = false; sb.append(ch); i++ }
                ch == '{' -> {
                    opens++; lead = false
                    if (sb.isNotEmpty() && sb.last() == ')') sb.append(' ')
                    sb.append(ch); i++
                }
                ch == '}' -> {
                    closes++
                    if (lead) leading++
                    sb.append(ch); i++
                }
                ch == ',' -> {
                    lead = false
                    sb.append(',')
                    if (nx != ' ' && nx != '\t' && nx != '\u0000') sb.append(' ')
                    i++
                }
                ch == ' ' || ch == '\t' -> {
                    if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    i++
                }
                else -> { lead = false; sb.append(ch); i++ }
            }
        }
        return Res(sb.toString().trimEnd(), opens, closes, leading, inBlock)
    }

    fun format(src: String): String {
        val lines = src.replace("\r\n", "\n").split("\n")
        val out = StringBuilder()
        var depth = 0
        var inBlock = false
        var blank = 0
        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                blank++
                if (blank <= 1 && out.isNotEmpty()) out.append('\n')
                continue
            }
            blank = 0
            if (!inBlock && trimmed.startsWith("#")) {
                out.append(trimmed).append('\n')
                continue
            }
            val startedInBlock = inBlock
            val r = analyze(trimmed, inBlock)
            inBlock = r.inBlock
            val d = (depth - r.leading).coerceAtLeast(0)
            val indent = INDENT.repeat(d) + if (startedInBlock && r.text.startsWith("*")) " " else ""
            out.append(indent).append(r.text).append('\n')
            depth = (depth + r.opens - r.closes).coerceAtLeast(0)
        }
        var result = out.toString().trimEnd('\n')
        if (src.endsWith("\n") || result.isNotEmpty()) result += "\n"
        return result
    }
}
