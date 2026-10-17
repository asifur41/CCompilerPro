package com.ccompiler.pro.ui

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.ccompiler.pro.compiler.ErrorHints
import com.ccompiler.pro.compiler.ErrorParser
import com.ccompiler.pro.settings.FontManager
import com.ccompiler.pro.settings.Palette
import com.ccompiler.pro.settings.ThemeManager
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.dp
import com.ccompiler.pro.utils.hideKeyboard
import com.ccompiler.pro.utils.selectableBg

/**
 * Terminal-like output pane.
 *  - auto-scrolls to the newest line
 *  - compiler errors are tappable (jump to line) and followed by a plain-English hint
 *  - an input row (text + Send + EOF) that is visible ONLY while the program waits for input
 */
class OutputView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {

    enum class Kind { OUT, ERR, INFO, OK }

    private val scroll = ScrollView(ctx)
    private val tv = TextView(ctx)
    private val inputRow = LinearLayout(ctx)
    private val inputBox = EditText(ctx)
    private val btnSend = TextView(ctx)
    private val btnEof = TextView(ctx)
    private val buf = SpannableStringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var palette: Palette = ThemeManager.palette(ctx)
    private val flushTask = Runnable { flush() }

    /** Called with the 1-based line number when a compiler error line is tapped. */
    var onLineClick: ((Int) -> Unit)? = null
    var onSend: ((String) -> Unit)? = null
    var onEof: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
        tv.setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        scroll.addView(tv, android.view.ViewGroup.LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, 0, 1f))

        inputRow.orientation = HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        inputRow.visibility = View.GONE
        inputBox.hint = "Program is waiting for input…"
        inputBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        inputBox.setSingleLine()
        inputBox.imeOptions = EditorInfo.IME_ACTION_SEND
        inputBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        inputBox.setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        inputBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) { doSend(); true } else false
        }
        for ((b, label) in listOf(btnSend to "Send", btnEof to "EOF")) {
            b.text = label
            b.gravity = Gravity.CENTER
            b.setPadding(context.dp(14), 0, context.dp(14), 0)
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
            b.background = context.selectableBg()
        }
        btnSend.setOnClickListener { doSend() }
        btnEof.setOnClickListener { onEof?.invoke() }
        inputRow.addView(inputBox, LayoutParams(0, -2, 1f))
        inputRow.addView(btnSend, LayoutParams(-2, context.dp(44)))
        inputRow.addView(btnEof, LayoutParams(-2, context.dp(44)))
        addView(inputRow, LayoutParams(-1, -2))

        applySettings()
        applyTheme(palette)
    }

    fun applySettings() {
        val tf = FontManager.typeface(context)
        tv.typeface = tf
        inputBox.typeface = tf
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Prefs.float(context, Prefs.FONT_SIZE, 14f).coerceIn(12f, 28f) - 1f)
    }

    fun applyTheme(p: Palette) {
        palette = p
        setBackgroundColor(p.editorBg)
        tv.setTextColor(p.text)
        inputRow.setBackgroundColor(ColorUtils.blendARGB(p.editorBg, p.accent, 0.15f))
        inputBox.setTextColor(p.text)
        inputBox.setHintTextColor(ColorUtils.setAlphaComponent(p.text, 120))
        inputBox.setBackgroundColor(Color.TRANSPARENT)
        btnSend.setTextColor(p.accent)
        btnEof.setTextColor(ColorUtils.setAlphaComponent(p.text, 170))
    }

    // ------------------------------------------------------------------ input row

    /** Show/hide the input row. Shown only while the process is running AND waiting for input. */
    fun setInputVisible(visible: Boolean) {
        if (visible == (inputRow.visibility == View.VISIBLE)) return
        inputRow.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            inputBox.requestFocus()
            inputBox.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputBox, InputMethodManager.SHOW_IMPLICIT)
            }
            scroll.post { scroll.scrollTo(0, tv.bottom) }
        } else {
            inputBox.setText("")
            inputBox.hideKeyboard()
        }
    }

    private fun doSend() {
        val t = inputBox.text?.toString() ?: ""
        inputBox.setText("")
        onSend?.invoke(t)
    }

    // ------------------------------------------------------------------ output

    private fun colorFor(kind: Kind): Int = when (kind) {
        Kind.OUT -> palette.text
        Kind.ERR -> if (palette.isLight) Color.parseColor("#C62828") else Color.parseColor("#FF7B7B")
        Kind.INFO -> palette.comment
        Kind.OK -> palette.accent
    }

    private fun onMain(f: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) f() else handler.post { f() }
    }

    /** Thread-safe. */
    fun append(text: String, kind: Kind = Kind.OUT) = onMain { doAppend(text, kind, 0) }

    /**
     * Appends compiler output: lines "<sourceName>:LINE: error|warning: msg" become tappable (they show
     * [displayName] instead of the temporary file name) and are followed by a one-line hint when known.
     */
    fun appendCompilerOutput(raw: String, sourceName: String, displayName: String, defaultKind: Kind) = onMain {
        val seen = HashSet<String>()
        for (line in raw.lines()) {
            if (line.isEmpty()) continue
            val m = ErrorParser.matchLine(line)
            val fileRange = m?.groups?.get(1)?.range
            val lineNo = m?.groupValues?.get(2)?.toIntOrNull()
            if (m != null && fileRange != null && lineNo != null &&
                m.groupValues[1].substringAfterLast('/') == sourceName
            ) {
                val shown = line.trimEnd('\r').replaceRange(fileRange, displayName)
                val kind = if (m.groupValues[4] == "warning") Kind.INFO else Kind.ERR
                doAppend(shown + "\n", kind, lineNo)
                ErrorHints.hintFor(m.groupValues[5])?.let { hint ->
                    if (seen.add(hint)) doAppend("    💡 $hint\n", Kind.OK, 0)
                }
            } else {
                doAppend(line.replace(sourceName, displayName) + "\n", defaultKind, 0)
            }
        }
    }

    private fun doAppend(text: String, kind: Kind, line: Int) {
        val start = buf.length
        buf.append(text)
        val color = colorFor(kind)
        if (line > 0) {
            val end = if (text.endsWith("\n")) buf.length - 1 else buf.length
            buf.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { onLineClick?.invoke(line) }
                override fun updateDrawState(ds: TextPaint) { ds.color = color; ds.isUnderlineText = true }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            buf.setSpan(ForegroundColorSpan(color), start, buf.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
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
}
