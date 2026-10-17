package com.ccompiler.pro.utils

import android.content.Context

/** Thin SharedPreferences wrapper plus all preference keys used by the app. */
object Prefs {
    const val LAYOUT_MODE = "layout_mode"          // 0 = split, 1 = flip
    const val THEME = "theme"                      // 0..4
    const val FONT = "font"                        // 0..3
    const val FONT_SIZE = "font_size"              // 12..28 sp
    const val HIGHLIGHT = "highlight"
    const val AUTO_FORMAT = "auto_format"
    const val LINE_NUMBERS = "line_numbers"
    const val SYMBOL_PANEL = "symbol_panel"
    const val WORD_WRAP = "word_wrap"
    const val SPLIT_RATIO = "split_ratio"
    const val AUTOSAVE = "autosave"
    const val AUTOSAVE_INTERVAL = "autosave_interval" // index: 0=3s 1=5s 2=10s
    const val AUTOSAVE_INDICATOR = "autosave_indicator"
    const val ONBOARDED = "onboarded"
    const val CURRENT_FILE = "current_file"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("ccompiler_pro", Context.MODE_PRIVATE)

    fun str(c: Context, k: String, d: String): String = sp(c).getString(k, d) ?: d
    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun float(c: Context, k: String, d: Float): Float = sp(c).getFloat(k, d)
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)

    fun put(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
    fun put(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun put(c: Context, k: String, v: Float) = sp(c).edit().putFloat(k, v).apply()
    fun put(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun remove(c: Context, k: String) = sp(c).edit().remove(k).apply()

    fun autosaveIntervalMs(c: Context): Long = when (int(c, AUTOSAVE_INTERVAL, 1)) {
        0 -> 3000L
        2 -> 10000L
        else -> 5000L
    }
}
