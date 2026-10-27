package com.ccompiler.pro.editor

/**
 * Learns which symbol buttons (; ( ) { } ...) are used most and puts them first. Pure logic, no Android code.
 * Counts are kept in the same order as [DEFAULT] and stored as "v1:3,0,12,...".
 */
object SymbolUsage {
    val DEFAULT: List<String> = listOf(
        ";", "(", ")", "{", "}", ",", "\"", "=", "%", "&", "*", "Tab",
        "[", "]", "<", ">", "+", "-", "!", "/", "'", "#", "|"
    )
    /** The first row holds this many buttons. */
    const val ROW1 = 12

    fun parse(stored: String): IntArray {
        val zero = IntArray(DEFAULT.size)
        if (!stored.startsWith("v1:")) return zero
        val parts = stored.removePrefix("v1:").split(",")
        if (parts.size != DEFAULT.size) return zero
        return IntArray(DEFAULT.size) { parts[it].toIntOrNull()?.coerceAtLeast(0) ?: 0 }
    }

    fun serialize(counts: IntArray): String = "v1:" + counts.joinToString(",")

    /** Most used first; equal counts keep the default order. */
    fun order(counts: IntArray): List<String> =
        DEFAULT.indices.sortedWith(compareBy({ -counts[it] }, { it })).map { DEFAULT[it] }

    fun bump(counts: IntArray, symbol: String): IntArray {
        val i = DEFAULT.indexOf(symbol)
        val out = counts.copyOf()
        if (i < 0) return out
        out[i]++
        if (out[i] > 1_000_000) for (k in out.indices) out[k] = out[k] / 2
        return out
    }
}
