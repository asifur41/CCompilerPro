package com.ccompiler.pro.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.ColorUtils
import com.ccompiler.pro.R
import com.ccompiler.pro.editor.CSyntaxHighlighter
import com.ccompiler.pro.editor.CodeFormatter
import com.ccompiler.pro.editor.CodeHints
import com.ccompiler.pro.editor.GestureZoomController
import com.ccompiler.pro.editor.UndoRedoManager
import com.ccompiler.pro.settings.FontManager
import com.ccompiler.pro.settings.Palette
import com.ccompiler.pro.settings.ThemeManager
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.dp
import com.ccompiler.pro.utils.roundedBg
import com.ccompiler.pro.utils.selectableBg
import java.util.regex.PatternSyntaxException

/**
 * The code editor: toolbar, line-number gutter, syntax highlighting, pinch zoom, undo/redo,
 * hint bar, symbol panel and a line:column status bar.
 */
class EditorView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {

    class CodeEditText(ctx: Context) : AppCompatEditText(ctx) {
        var onSelection: ((Int, Int) -> Unit)? = null
        override fun onSelectionChanged(selStart: Int, selEnd: Int) {
            super.onSelectionChanged(selStart, selEnd)
            onSelection?.invoke(selStart, selEnd)
        }
    }

    val defaultCode: String =
        ctx.resources.openRawResource(R.raw.default_hello_world).bufferedReader().use { it.readText() }

    private val handler = Handler(Looper.getMainLooper())
    private var palette: Palette = ThemeManager.palette(ctx)
    private val highlighter = CSyntaxHighlighter(palette)

    private val edit = CodeEditText(ctx)
    private val gutter = TextView(ctx)
    private val scroll = ScrollView(ctx)
    private val row = LinearLayout(ctx)
    private val holder = FrameLayout(ctx)
    private val hScroll = HorizontalScrollView(ctx)
    private val body = FrameLayout(ctx)
    private val zoomLabel = TextView(ctx)

    private val toolbar = LinearLayout(ctx)
    private val hintBar = HorizontalScrollView(ctx)
    private val hintRow = LinearLayout(ctx)
    private val symbolBar = HorizontalScrollView(ctx)
    private val symbolRow = LinearLayout(ctx)
    private val statusBar = LinearLayout(ctx)
    private val statusLeft = TextView(ctx)
    private val statusRight = TextView(ctx)

    private val undoMgr = UndoRedoManager(edit)
    private var loading = false
    private var highlightOn = true
    private var showLines = true
    private var wrapApplied: Boolean? = null
    private var textSizeSp = 14f
    private var currentHints: List<com.ccompiler.pro.editor.Hint> = emptyList()
    private var hintPrefixLen = 0

    var onContentChanged: (() -> Unit)? = null

    /** Highlight for a line the compiler complained about (cleared on the next edit). */
    private class ErrLineSpan : BackgroundColorSpan(0x44FF3B30)
    private var errorLines: Set<Int> = emptySet()
    private var gutterKey = ""

    private val zoom = GestureZoomController(ctx, { textSizeSp }, { s, done -> setTextSizeSp(s, done) })

    private val refreshTask = Runnable { refreshNow() }
    private val restoreTask = Runnable {
        if (getText().isEmpty()) {
            setText(defaultCode)
            onContentChanged?.invoke()
        }
    }
    private val hideZoomTask = Runnable { zoomLabel.visibility = View.GONE }
    private val clearStatusTask = Runnable { statusRight.text = "" }

    init {
        orientation = VERTICAL

        // ---- toolbar
        toolbar.orientation = HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        addToolbarButton("↶") { undo() }
        addToolbarButton("↷") { redo() }
        addToolbarButton("Format") { format() }
        addToolbarButton("Find") { onFindRequested?.invoke() }
        val spacer = View(ctx)
        toolbar.addView(spacer, LayoutParams(0, 1, 1f))
        addToolbarButton("⋮") { onMoreRequested?.invoke(it) }
        addView(toolbar, LayoutParams(-1, context.dp(40)))

        // ---- editor body
        edit.background = null
        edit.gravity = Gravity.TOP or Gravity.START
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        edit.setPadding(context.dp(8), context.dp(8), context.dp(12), context.dp(8))
        edit.imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        edit.filters = arrayOf(InputFilter { source, start, end, dest, dstart, _ ->
            if (end - start == 1 && source[start] == '\n') {
                val s = dest.toString()
                val ls = s.lastIndexOf('\n', dstart - 1) + 1
                val indent = s.substring(ls, dstart).takeWhile { it == ' ' || it == '\t' }
                val extra = if (dstart > 0 && s[dstart - 1] == '{') "    " else ""
                "\n$indent$extra"
            } else null
        })

        gutter.gravity = Gravity.END
        gutter.setPadding(context.dp(8), context.dp(8), context.dp(6), context.dp(8))

        row.orientation = HORIZONTAL
        row.addView(gutter, LayoutParams(-2, -1))
        row.addView(holder, LayoutParams(0, -2, 1f))
        scroll.isFillViewport = true
        scroll.addView(row, android.view.ViewGroup.LayoutParams(-1, -2))
        body.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        zoomLabel.visibility = View.GONE
        zoomLabel.setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
        zoomLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        body.addView(zoomLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, context.dp(8), context.dp(12), 0)
        })
        addView(body, LayoutParams(-1, 0, 1f))

        // ---- hint bar
        hintRow.orientation = HORIZONTAL
        hintBar.addView(hintRow)
        hintBar.isHorizontalScrollBarEnabled = false
        hintBar.visibility = View.GONE
        addView(hintBar, LayoutParams(-1, context.dp(40)))

        // ---- symbol panel
        symbolRow.orientation = HORIZONTAL
        symbolBar.addView(symbolRow)
        symbolBar.isHorizontalScrollBarEnabled = false
        val symbols = listOf("Tab", "{", "}", "(", ")", "[", "]", ";", ",", "&", "|", "*", "+", "-", "=", "<", ">", "!", "\"", "'", "#", "/", "%")
        for (s in symbols) {
            val b = TextView(context)
            b.text = s
            b.gravity = Gravity.CENTER
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            b.typeface = Typeface.MONOSPACE
            b.minWidth = context.dp(40)
            b.setPadding(context.dp(8), 0, context.dp(8), 0)
            b.setOnClickListener { insert(if (s == "Tab") "    " else s) }
            symbolRow.addView(b, LayoutParams(-2, context.dp(40)).apply { setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2)) })
        }
        addView(symbolBar, LayoutParams(-1, -2))

        // ---- status bar
        statusBar.orientation = HORIZONTAL
        statusBar.setPadding(context.dp(12), context.dp(3), context.dp(12), context.dp(3))
        statusLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        statusRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        statusRight.gravity = Gravity.END
        statusBar.addView(statusLeft, LayoutParams(0, -2, 1f))
        statusBar.addView(statusRight, LayoutParams(0, -2, 1f))
        addView(statusBar, LayoutParams(-1, -2))

        // ---- behaviour
        scroll.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) edit.minHeight = b - t
        }
        edit.onSelection = { _, _ -> updateCursorStatus(); updateHints() }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (loading) return
                if (errorLines.isNotEmpty()) clearErrorLines()
                undoMgr.onTextChanged(e?.toString() ?: "")
                scheduleRefresh()
                if (e.isNullOrEmpty()) {
                    handler.removeCallbacks(restoreTask)
                    handler.postDelayed(restoreTask, 1500)
                }
                onContentChanged?.invoke()
            }
        })
        edit.setOnFocusChangeListener { _, has ->
            if (!has && getText().isEmpty()) { setText(defaultCode); onContentChanged?.invoke() }
        }
        edit.setOnTouchListener { _, ev ->
            val consumed = zoom.onTouch(ev)
            if (consumed) parent?.requestDisallowInterceptTouchEvent(true)
            if (ev.actionMasked == MotionEvent.ACTION_UP) performClick()
            consumed
        }

        applySettings()
        applyTheme(palette)
        setText(defaultCode)
    }

    var onFindRequested: (() -> Unit)? = null
    var onMoreRequested: ((View) -> Unit)? = null

    private fun addToolbarButton(label: String, onClick: (View) -> Unit) {
        val b = TextView(context)
        b.text = label
        b.gravity = Gravity.CENTER
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        b.setPadding(context.dp(14), 0, context.dp(14), 0)
        b.background = context.selectableBg()
        b.setOnClickListener { onClick(it) }
        toolbar.addView(b, LayoutParams(-2, -1))
    }

    // ------------------------------------------------------------------ settings & theme

    fun applySettings() {
        val c = context
        val tf = FontManager.typeface(c)
        edit.typeface = tf
        gutter.typeface = tf
        setTextSizeSp(Prefs.float(c, Prefs.FONT_SIZE, 14f), false, showIndicator = false)
        highlightOn = Prefs.bool(c, Prefs.HIGHLIGHT, true)
        showLines = Prefs.bool(c, Prefs.LINE_NUMBERS, true)
        symbolBar.visibility = if (Prefs.bool(c, Prefs.SYMBOL_PANEL, true)) View.VISIBLE else View.GONE
        applyWrap(Prefs.bool(c, Prefs.WORD_WRAP, false))
        scheduleRefresh()
    }

    private fun applyWrap(wrap: Boolean) {
        if (wrapApplied == wrap) return
        wrapApplied = wrap
        (edit.parent as? android.view.ViewGroup)?.removeView(edit)
        hScroll.removeAllViews()
        holder.removeAllViews()
        edit.setHorizontallyScrolling(!wrap)
        if (wrap) {
            holder.addView(edit, FrameLayout.LayoutParams(-1, -2))
        } else {
            hScroll.isFillViewport = true
            hScroll.addView(edit, FrameLayout.LayoutParams(-2, -2))
            holder.addView(hScroll, FrameLayout.LayoutParams(-1, -2))
        }
    }

    fun applyTheme(p: Palette) {
        palette = p
        highlighter.palette = p
        val bar = ColorUtils.blendARGB(p.bg, p.editorBg, 0.5f)
        setBackgroundColor(p.editorBg)
        body.setBackgroundColor(p.editorBg)
        scroll.setBackgroundColor(p.editorBg)
        edit.setTextColor(p.text)
        edit.highlightColor = ColorUtils.setAlphaComponent(p.accent, 90)
        gutter.setTextColor(p.gutter)
        gutter.setBackgroundColor(ColorUtils.blendARGB(p.editorBg, p.bg, 0.5f))
        toolbar.setBackgroundColor(bar)
        for (i in 0 until toolbar.childCount) (toolbar.getChildAt(i) as? TextView)?.setTextColor(p.text)
        hintBar.setBackgroundColor(bar)
        symbolBar.setBackgroundColor(bar)
        for (i in 0 until symbolRow.childCount) {
            val b = symbolRow.getChildAt(i) as TextView
            b.setTextColor(p.text)
            b.background = roundedBg(ColorUtils.setAlphaComponent(p.accent, 50), context.dp(6).toFloat())
        }
        statusBar.setBackgroundColor(bar)
        statusLeft.setTextColor(p.gutter)
        statusRight.setTextColor(p.accent)
        zoomLabel.setTextColor(if (ColorUtils.calculateLuminance(p.accent) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        zoomLabel.background = roundedBg(p.accent, context.dp(12).toFloat())
        updateHints()
        refreshNow()
    }

    // ------------------------------------------------------------------ text API

    fun getText(): String = edit.text?.toString() ?: ""

    fun setText(t: String) {
        clearErrorLines()
        loading = true
        edit.setText(t)
        undoMgr.reset(t)
        loading = false
        edit.setSelection(0)
        scheduleRefresh()
    }

    fun ensureNotEmpty() {
        if (getText().isBlank()) {
            setText(defaultCode)
            onContentChanged?.invoke()
        }
    }

    fun clearToDefault() {
        setText(defaultCode)
        onContentChanged?.invoke()
    }

    fun insert(s: String, back: Int = 0) {
        val e = edit.text ?: return
        val a = minOf(edit.selectionStart, edit.selectionEnd).coerceAtLeast(0)
        val b = maxOf(edit.selectionStart, edit.selectionEnd).coerceAtLeast(0)
        e.replace(a, b, s)
        edit.setSelection((a + s.length - back).coerceIn(0, e.length))
    }

    fun selectAll() { edit.requestFocus(); edit.selectAll() }

    fun undo() { undoMgr.undo() }
    fun redo() { undoMgr.redo() }

    fun format() {
        val src = getText()
        val out = CodeFormatter.format(src)
        if (out != src) {
            val sel = edit.selectionStart.coerceAtLeast(0)
            edit.setText(out)
            edit.setSelection(minOf(sel, out.length))
        }
    }

    fun goToLine(n: Int) {
        val t = getText()
        var line = 1
        var idx = 0
        while (line < n && idx < t.length) {
            val nl = t.indexOf('\n', idx)
            if (nl < 0) { idx = t.length; break }
            idx = nl + 1
            line++
        }
        val pos = idx.coerceIn(0, t.length)
        edit.requestFocus()
        edit.setSelection(pos)
        // wait a moment: in Flip mode the editor pane is only just becoming visible again
        scroll.postDelayed({
            val l = edit.layout ?: return@postDelayed
            val y = l.getLineTop(l.getLineForOffset(pos)) + edit.paddingTop
            scroll.smoothScrollTo(0, (y - scroll.height / 3).coerceAtLeast(0))
        }, 90)
    }

    /** Marks [lines] (1-based) with a red gutter badge and a light red background in the text. */
    fun setErrorLines(lines: Set<Int>) {
        clearErrorSpans()
        errorLines = lines
        val e = edit.text
        if (e != null && lines.isNotEmpty()) {
            val text = e.toString()
            var n = 1
            var start = 0
            while (start <= text.length) {
                var end = text.indexOf('\n', start)
                if (end < 0) end = text.length
                if (n in lines && end > start) e.setSpan(ErrLineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (end >= text.length) break
                start = end + 1
                n++
            }
        }
        updateGutter()
    }

    fun clearErrorLines() {
        if (errorLines.isEmpty()) return
        clearErrorSpans()
        errorLines = emptySet()
        updateGutter()
    }

    private fun clearErrorSpans() {
        val e = edit.text ?: return
        for (sp in e.getSpans(0, e.length, ErrLineSpan::class.java)) e.removeSpan(sp)
    }

    fun setZoomEnabled(on: Boolean) { zoom.enabled = on }

    fun setStatusRight(text: String, flash: Boolean = true) {
        statusRight.text = text
        handler.removeCallbacks(clearStatusTask)
        if (flash) handler.postDelayed(clearStatusTask, 3000)
    }

    // ------------------------------------------------------------------ find / replace

    private fun pattern(query: String, regex: Boolean, matchCase: Boolean): Regex? = try {
        val opts = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        if (query.isEmpty()) null else Regex(if (regex) query else Regex.escape(query), opts)
    } catch (e: PatternSyntaxException) {
        null
    }

    /** Returns 1 if found, 0 if not found, -1 if the pattern is invalid. */
    fun findNext(query: String, regex: Boolean, matchCase: Boolean): Int {
        val re = pattern(query, regex, matchCase) ?: return if (query.isEmpty()) 0 else -1
        val t = getText()
        val from = edit.selectionEnd.coerceIn(0, t.length)
        val m = re.find(t, from) ?: re.find(t, 0) ?: return 0
        edit.requestFocus()
        edit.setSelection(m.range.first, m.range.last + 1)
        edit.bringPointIntoView(m.range.first)
        return 1
    }

    fun replaceCurrent(query: String, replacement: String, regex: Boolean, matchCase: Boolean): Int {
        val re = pattern(query, regex, matchCase) ?: return if (query.isEmpty()) 0 else -1
        val t = getText()
        val a = minOf(edit.selectionStart, edit.selectionEnd)
        val b = maxOf(edit.selectionStart, edit.selectionEnd)
        if (b > a) {
            val sel = t.substring(a, b)
            if (re.matchEntire(sel) != null) {
                val rep = if (regex) re.replaceFirst(sel, replacement) else replacement
                edit.text?.replace(a, b, rep)
                edit.setSelection(a + rep.length)
            }
        }
        return findNext(query, regex, matchCase)
    }

    /** Returns the number of replacements, or -1 for an invalid pattern. */
    fun replaceAll(query: String, replacement: String, regex: Boolean, matchCase: Boolean): Int {
        val re = pattern(query, regex, matchCase) ?: return if (query.isEmpty()) 0 else -1
        val t = getText()
        val count = re.findAll(t).count()
        if (count == 0) return 0
        val out = re.replace(t, if (regex) replacement else Regex.escapeReplacement(replacement))
        edit.setText(out)
        edit.setSelection(0)
        return count
    }

    // ------------------------------------------------------------------ zoom / refresh / hints

    private fun setTextSizeSp(sp: Float, persist: Boolean, showIndicator: Boolean = true) {
        val s = sp.coerceIn(GestureZoomController.MIN_SP, GestureZoomController.MAX_SP)
        textSizeSp = s
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, s)
        gutter.setTextSize(TypedValue.COMPLEX_UNIT_SP, s)
        if (persist) Prefs.put(context, Prefs.FONT_SIZE, s)
        if (showIndicator) {
            zoomLabel.text = "${Math.round(s / GestureZoomController.BASE_SP * 100)}%"
            zoomLabel.visibility = View.VISIBLE
            handler.removeCallbacks(hideZoomTask)
            handler.postDelayed(hideZoomTask, 900)
        }
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, 40)
    }

    private fun refreshNow() {
        val e = edit.text ?: return
        if (highlightOn && e.length < 60_000) highlighter.highlight(e) else highlighter.clear(e)
        updateGutter()
        updateHints()
        updateCursorStatus()
    }

    private fun updateGutter() {
        if (!showLines) { gutter.visibility = View.GONE; return }
        gutter.visibility = View.VISIBLE
        val l = edit.layout ?: return
        val t = edit.text ?: return
        val ss = SpannableStringBuilder()
        var n = 1
        for (i in 0 until l.lineCount) {
            val st = l.getLineStart(i)
            if (i == 0 || (st > 0 && t[st - 1] == '\n')) {
                val from = ss.length
                ss.append(n.toString())
                if (n in errorLines) {
                    ss.setSpan(BackgroundColorSpan(0xFFD32F2F.toInt()), from, ss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ss.setSpan(ForegroundColorSpan(Color.WHITE), from, ss.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                n++
            }
            ss.append('\n')
        }
        if (ss.isNotEmpty()) ss.delete(ss.length - 1, ss.length)
        val key = ss.toString() + "|" + errorLines.hashCode()
        if (key != gutterKey) {
            gutterKey = key
            gutter.text = ss
        }
    }

    private fun updateCursorStatus() {
        val t = edit.text ?: return
        val s = edit.selectionStart.coerceIn(0, t.length)
        var line = 1
        var last = -1
        for (i in 0 until s) if (t[i] == '\n') { line++; last = i }
        statusLeft.text = "Ln $line, Col ${s - last}"
    }

    private fun updateHints() {
        val t = edit.text
        val sel = edit.selectionStart
        if (t == null || sel < 0 || sel != edit.selectionEnd) { hintBar.visibility = View.GONE; return }
        var st = sel
        while (st > 0 && (t[st - 1].isLetterOrDigit() || t[st - 1] == '_')) st--
        val prefix = t.substring(st, sel)
        val hints = CodeHints.suggest(prefix)
        hintPrefixLen = prefix.length
        if (hints.isEmpty()) { currentHints = emptyList(); hintBar.visibility = View.GONE; return }
        if (hints != currentHints) {
            currentHints = hints
            hintRow.removeAllViews()
            for (h in hints) {
                val chip = TextView(context)
                chip.text = h.signature
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                chip.typeface = Typeface.MONOSPACE
                chip.setTextColor(palette.text)
                chip.gravity = Gravity.CENTER
                chip.setPadding(context.dp(12), 0, context.dp(12), 0)
                chip.background = roundedBg(ColorUtils.setAlphaComponent(palette.accent, 70), context.dp(14).toFloat())
                chip.setOnClickListener { applyHint(h) }
                hintRow.addView(chip, LayoutParams(-2, context.dp(30)).apply { setMargins(context.dp(4), context.dp(5), context.dp(4), context.dp(5)) })
            }
        }
        hintBar.visibility = View.VISIBLE
    }

    private fun applyHint(h: com.ccompiler.pro.editor.Hint) {
        val e = edit.text ?: return
        val sel = edit.selectionStart.coerceAtLeast(0)
        val start = (sel - hintPrefixLen).coerceAtLeast(0)
        e.replace(start, sel, h.insert)
        edit.setSelection((start + h.insert.length - h.back).coerceIn(0, e.length))
        hintBar.visibility = View.GONE
    }
}
