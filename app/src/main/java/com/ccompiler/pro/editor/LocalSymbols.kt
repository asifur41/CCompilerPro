package com.ccompiler.pro.editor

data class LocalSymbol(val name: String, val isFunction: Boolean, val count: Int)

/** Finds the names the user has written in the current file, so autocomplete can offer them. Pure logic. */
object LocalSymbols {
    private val keywords = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum", "extern",
        "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return", "short", "signed",
        "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
        "bool", "true", "false", "NULL", "size_t", "FILE", "main", "define", "include"
    )
    private val ident = Regex("[A-Za-z_][A-Za-z0-9_]*")
    const val MAX_CHARS = 100_000

    /** Comments and string/char contents become spaces; preprocessor lines are dropped except #define bodies. */
    internal fun mask(src: String): String {
        val b = StringBuilder(src.length)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            val d = if (i + 1 < n) src[i + 1] else '\u0000'
            if (c == '/' && d == '/') {
                while (i < n && src[i] != '\n') { b.append(' '); i++ }
            } else if (c == '/' && d == '*') {
                b.append("  "); i += 2
                while (i < n && !(src[i] == '*' && i + 1 < n && src[i + 1] == '/')) { b.append(if (src[i] == '\n') '\n' else ' '); i++ }
                if (i < n) { b.append("  "); i += 2 }
            } else if (c == '"' || c == '\'') {
                b.append(c); i++
                while (i < n && src[i] != c && src[i] != '\n') {
                    if (src[i] == '\\' && i + 1 < n) { b.append("  "); i += 2 } else { b.append(' '); i++ }
                }
                if (i < n && src[i] == c) { b.append(c); i++ }
            } else { b.append(c); i++ }
        }
        val lines = b.toString().split('\n')
        return lines.joinToString("\n") { l ->
            val t = l.trimStart()
            when {
                !t.startsWith("#") -> l
                t.removePrefix("#").trimStart().startsWith("define") -> t.removePrefix("#").trimStart().removePrefix("define")
                else -> ""
            }
        }
    }

    fun extract(code: String, reserved: Set<String> = emptySet()): List<LocalSymbol> {
        if (code.length > MAX_CHARS) return emptyList()
        val text = mask(code)
        val counts = HashMap<String, Int>()
        val fn = HashSet<String>()
        for (m in ident.findAll(text)) {
            val name = m.value
            if (name.length < 3 || name in keywords || name in reserved) continue
            counts[name] = (counts[name] ?: 0) + 1
            var j = m.range.last + 1
            while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
            if (j < text.length && text[j] == '(') fn.add(name)
        }
        return counts.entries
            .map { LocalSymbol(it.key, it.key in fn, it.value) }
            .sortedWith(compareBy({ -it.count }, { it.name }))
            .take(200)
    }
}
