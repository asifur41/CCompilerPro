package com.ccompiler.pro.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.ccompiler.pro.R
import com.ccompiler.pro.settings.Palette
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.copyToClipboard
import com.ccompiler.pro.utils.hideKeyboard
import kotlin.math.abs

/**
 * Mode B: one pane at a time. Run slides the output in (450 ms, decelerate, slight scale);
 * Back button, swipe-right or the system back key returns to the editor. Both panes stay alive,
 * so code state is preserved.
 */
class FlipLayoutView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    FrameLayout(ctx, attrs), PaneLayout {

    init {
        LayoutInflater.from(ctx).inflate(R.layout.layout_flip, this, true)
    }

    private val editorSlot = findViewById<FrameLayout>(R.id.flip_editor_slot)
    private val outputContainer = findViewById<LinearLayout>(R.id.flip_output_container)
    private val outputSlot = findViewById<FrameLayout>(R.id.flip_output_slot)
    private val header = findViewById<LinearLayout>(R.id.flip_header)
    private val btnBack = findViewById<TextView>(R.id.flip_back)
    private val title = findViewById<TextView>(R.id.flip_title)
    private val btnCopy = findViewById<TextView>(R.id.flip_copy)
    private var output: OutputView? = null
    private var outputShown = false

    override val view: View get() = this

    private val swipe = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (!outputShown || e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (dx > 160 && abs(dx) > abs(dy) * 1.5f && vx > 1000f) {
                showEditor()
                return true
            }
            return false
        }
    })

    init {
        btnBack.setOnClickListener { showEditor() }
        btnCopy.setOnClickListener {
            val t = output?.text().orEmpty()
            if (t.isNotEmpty()) { copyToClipboard(context, "output", t); NotificationHelper.toast(context, "Output copied") }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        swipe.onTouchEvent(ev)
        return false
    }

    override fun attach(editor: EditorView, output: OutputView) {
        this.output = output
        (editor.parent as? ViewGroup)?.removeView(editor)
        (output.parent as? ViewGroup)?.removeView(output)
        editorSlot.addView(editor, FrameLayout.LayoutParams(-1, -1))
        outputSlot.addView(output, FrameLayout.LayoutParams(-1, -1))
        outputShown = false
        editorSlot.visibility = View.VISIBLE
        outputContainer.visibility = View.GONE
    }

    override fun detach() {
        editorSlot.removeAllViews()
        outputSlot.removeAllViews()
    }

    override fun onRunStarted() {
        if (!outputShown) showOutput()
    }

    override fun handleBack(): Boolean {
        if (outputShown) { showEditor(); return true }
        return false
    }

    private fun showOutput() {
        outputShown = true
        hideKeyboard()
        outputContainer.visibility = View.VISIBLE
        outputContainer.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pane_slide_in))
        val out = AnimationUtils.loadAnimation(context, R.anim.pane_slide_out)
        out.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation?) {}
            override fun onAnimationRepeat(a: Animation?) {}
            override fun onAnimationEnd(a: Animation?) { if (outputShown) editorSlot.visibility = View.GONE }
        })
        editorSlot.startAnimation(out)
    }

    private fun showEditor() {
        if (!outputShown) return
        outputShown = false
        editorSlot.visibility = View.VISIBLE
        editorSlot.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pane_back_in))
        val out = AnimationUtils.loadAnimation(context, R.anim.pane_back_out)
        out.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation?) {}
            override fun onAnimationRepeat(a: Animation?) {}
            override fun onAnimationEnd(a: Animation?) { if (!outputShown) outputContainer.visibility = View.GONE }
        })
        outputContainer.startAnimation(out)
    }

    override fun applyTheme(p: Palette) {
        setBackgroundColor(p.bg)
        header.setBackgroundColor(ColorUtils.blendARGB(p.bg, p.accent, 0.25f))
        btnBack.setTextColor(p.text)
        btnBack.compoundDrawableTintList = ColorStateList.valueOf(p.text)
        title.setTextColor(p.text)
        btnCopy.setTextColor(p.text)
    }
}
