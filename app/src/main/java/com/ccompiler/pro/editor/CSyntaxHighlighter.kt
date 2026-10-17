package com.ccompiler.pro.editor

import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.ccompiler.pro.settings.Palette

/** Regex-based C highlighter: comments, strings/chars, preprocessor, keywords, types, numbers. */
class CSyntaxHighlighter(var palette: Palette) {

    class HlSpan(color: Int) : ForegroundColorSpan(color)

    companion object {
        private val KEYWORDS = listOf(
            "auto", "break", "case", "const", "continue", "default", "do", "else", "enum", "extern", "for", "goto",
            "if", "inline", "register", "restrict", "return", "sizeof", "static", "struct", "switch", "typedef",
            "union", "volatile", "while", "true", "false", "NULL"
        )
        private val TYPES = listOf(
            "int", "char", "short", "long", "float", "double", "void", "signed", "unsigned", "bool", "_Bool",
            "size_t", "FILE", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t"
        )
        private val KW_RE = Regex("\\b(?:" + KEYWORDS.joinToString("|") + ")\\b")
        private val TYPE_RE = Regex("\\b(?:" + TYPES.joinToString("|") + ")\\b")
        private val NUM_RE = Regex("\\b(?:0[xX][0-9a-fA-F]+|\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)[uUlLfF]*\\b")
        private val PRE_RE = Regex("(?m)^[ \\t]*#[ \\t]*[A-Za-z_]+")
        private val INC_RE = Regex("(?m)^[ \\t]*#[ \\t]*include[ \\t]*(<[^>\\n]*>)")
        // comments and string/char literals in ONE pass so each is recognised in the right context
        private val LITERAL_RE = Regex("/\\*[\\s\\S]*?(?:\\*/|\\z)|//[^\\n]*|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?")
        private const val FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    }

    fun clear(text: Spannable) {
        for (s in text.getSpans(0, text.length, HlSpan::class.java)) text.removeSpan(s)
    }

    fun highlight(text: Spannable) {
        clear(text)
        val src = text.toString()
        val covered = BooleanArray(src.length)

        fun mark(start: Int, endExclusive: Int, color: Int) {
            if (endExclusive <= start) return
            text.setSpan(HlSpan(color), start, endExclusive, FLAGS)
            for (i in start until endExclusive) covered[i] = true
        }

        for (m in LITERAL_RE.findAll(src)) {
            val v = m.value
            val color = if (v.startsWith("//") || v.startsWith("/*")) palette.comment else palette.string
            mark(m.range.first, m.range.last + 1, color)
        }
        for (m in PRE_RE.findAll(src)) {
            if (!covered[m.range.first]) mark(m.range.first, m.range.last + 1, palette.preproc)
        }
        for (m in INC_RE.findAll(src)) {
            val g = m.groups[1]?.range ?: continue
            if (!covered[g.first]) mark(g.first, g.last + 1, palette.string)
        }
        fun paint(re: Regex, color: Int) {
            for (m in re.findAll(src)) {
                if (covered[m.range.first]) continue
                text.setSpan(HlSpan(color), m.range.first, m.range.last + 1, FLAGS)
            }
        }
        paint(KW_RE, palette.keyword)
        paint(TYPE_RE, palette.type)
        paint(NUM_RE, palette.number)
    }
}
