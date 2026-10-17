package com.ccompiler.pro.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.ccompiler.pro.R
import com.ccompiler.pro.settings.Palette
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.copyToClipboard
import com.ccompiler.pro.utils.dp

/** Mode A: editor on top, output below, draggable divider; ratio persisted. */
class SplitLayoutView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    LinearLayout(ctx, attrs), PaneLayout {

    init {
        orientation = VERTICAL
        LayoutInflater.from(ctx).inflate(R.layout.layout_split, this, true)
    }

    private val editorSlot = findViewById<FrameLayout>(R.id.split_editor_slot)
    private val outputSlot = findViewById<FrameLayout>(R.id.split_output_slot)
    private val divider = findViewById<LinearLayout>(R.id.split_divider)
    private val label = findViewById<TextView>(R.id.split_label)
    private val btnCopy = findViewById<TextView>(R.id.split_copy)
    private val btnClear = findViewById<TextView>(R.id.split_clear)
    private var output: OutputView? = null
    private var ratio = 0.6f

    override val view: View get() = this

    init {
        outputSlot.minimumHeight = context.dp(72)
        editorSlot.minimumHeight = context.dp(96)
        setRatio(Prefs.float(context, Prefs.SPLIT_RATIO, 0.6f), false)

        divider.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); true }
                MotionEvent.ACTION_MOVE -> {
                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    val usable = (height - divider.height).coerceAtLeast(1)
                    val y = e.rawY - loc[1] - divider.height / 2f
                    setRatio((y / usable).coerceIn(0.2f, 0.85f), false)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { setRatio(ratio, true); true }
                else -> false
            }
        }
        btnCopy.setOnClickListener {
            val t = output?.text().orEmpty()
            if (t.isNotEmpty()) { copyToClipboard(context, "output", t); NotificationHelper.toast(context, "Output copied") }
        }
        btnClear.setOnClickListener { output?.clear() }
    }

    private fun setRatio(r: Float, save: Boolean) {
        ratio = r
        (editorSlot.layoutParams as LayoutParams).weight = r
        (outputSlot.layoutParams as LayoutParams).weight = 1f - r
        requestLayout()
        if (save) Prefs.put(context, Prefs.SPLIT_RATIO, r)
    }

    override fun attach(editor: EditorView, output: OutputView) {
        this.output = output
        (editor.parent as? ViewGroup)?.removeView(editor)
        (output.parent as? ViewGroup)?.removeView(output)
        editorSlot.addView(editor, FrameLayout.LayoutParams(-1, -1))
        outputSlot.addView(output, FrameLayout.LayoutParams(-1, -1))
    }

    override fun detach() {
        editorSlot.removeAllViews()
        outputSlot.removeAllViews()
    }

    override fun onRunStarted() {}
    override fun handleBack(): Boolean = false

    override fun applyTheme(p: Palette) {
        divider.setBackgroundColor(ColorUtils.blendARGB(p.bg, p.accent, 0.25f))
        label.setTextColor(p.text)
        btnCopy.setTextColor(p.text)
        btnClear.setTextColor(p.text)
        setBackgroundColor(p.bg)
    }
}
