package com.ccompiler.pro.ui

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.ccompiler.pro.settings.FontManager
import com.ccompiler.pro.settings.Palette
import com.ccompiler.pro.settings.ThemeManager
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.dp

/** Terminal-like output pane with auto-scroll and an optional stdin box. */
class OutputView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {

    enum class Kind { OUT, ERR, INFO, OK }

    private val scroll = ScrollView(ctx)
    private val tv = TextView(ctx)
    private val stdinBox = EditText(ctx)
    private val buf = SpannableStringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var palette: Palette = ThemeManager.palette(ctx)
    private val flushTask = Runnable { flush() }

    init {
        orientation = VERTICAL
        tv.setTextIsSelectable(true)
        tv.setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        scroll.addView(tv, android.view.ViewGroup.LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, 0, 1f))

        stdinBox.hint = "Program input (stdin) — optional"
        stdinBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        stdinBox.minLines = 1
        stdinBox.maxLines = 3
        stdinBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        stdinBox.setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        addView(stdinBox, LayoutParams(-1, -2))
        applySettings()
        applyTheme(palette)
    }

    fun applySettings() {
        val tf = FontManager.typeface(context)
        tv.typeface = tf
        stdinBox.typeface = tf
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Prefs.float(context, Prefs.FONT_SIZE, 14f).coerceIn(12f, 28f) - 1f)
    }

    fun applyTheme(p: Palette) {
        palette = p
        setBackgroundColor(p.editorBg)
        tv.setTextColor(p.text)
        stdinBox.setTextColor(p.text)
        stdinBox.setHintTextColor(ColorUtils.setAlphaComponent(p.text, 110))
        stdinBox.setBackgroundColor(ColorUtils.blendARGB(p.editorBg, p.accent, 0.12f))
    }

    private fun colorFor(kind: Kind): Int = when (kind) {
        Kind.OUT -> palette.text
        Kind.ERR -> if (palette.isLight) Color.parseColor("#C62828") else Color.parseColor("#FF7B7B")
        Kind.INFO -> palette.comment
        Kind.OK -> palette.accent
    }

    /** Thread-safe. */
    fun append(text: String, kind: Kind = Kind.OUT) {
        if (Looper.myLooper() == Looper.getMainLooper()) doAppend(text, kind) else handler.post { doAppend(text, kind) }
    }

    private fun doAppend(text: String, kind: Kind) {
        val start = buf.length
        buf.append(text)
        buf.setSpan(ForegroundColorSpan(colorFor(kind)), start, buf.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (buf.length > 300_000) buf.delete(0, buf.length - 250_000)
        handler.removeCallbacks(flushTask)
        handler.postDelayed(flushTask, 40)
    }

    private fun flush() {
        tv.text = buf
        scroll.post { scroll.scrollTo(0, tv.bottom) }
    }

    fun clear() {
        buf.clear()
        buf.clearSpans()
        handler.removeCallbacks(flushTask)
        tv.text = ""
    }

    fun text(): String = buf.toString()

    fun stdin(): String {
        val s = stdinBox.text?.toString() ?: ""
        return if (s.isEmpty() || s.endsWith("\n")) s else s + "\n"
    }
}
