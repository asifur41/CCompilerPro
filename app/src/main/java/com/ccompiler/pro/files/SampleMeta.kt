package com.ccompiler.pro.files

/** Reads the title and the short explanation from the comment at the top of a sample program. Pure logic. */
object SampleMeta {
    data class Sample(val file: String, val title: String, val summary: String)

    /** Returns (title, summary). A header looks like: slash-star, " * Title: ...", " * explanation", star-slash. */
    fun parse(text: String, fileName: String): Pair<String, String> {
        var title = ""
        val summary = ArrayList<String>()
        for (raw in text.lines().take(12)) {
            val l = raw.trim()
            if (l == "/*") continue
            if (l.startsWith("*/") || l.endsWith("*/")) break
            val body = l.removePrefix("*").trim()
            if (title.isEmpty()) {
                if (body.startsWith("Title:")) title = body.removePrefix("Title:").trim()
            } else if (body.isNotEmpty()) {
                summary.add(body)
            }
        }
        if (title.isEmpty()) title = fileName.removeSuffix(".c").replace('_', ' ').replaceFirstChar { it.uppercase() }
        return Pair(title, summary.joinToString(" "))
    }
}
