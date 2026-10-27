package com.ccompiler.pro.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.ccompiler.pro.R
import com.ccompiler.pro.settings.Palette
import com.ccompiler.pro.settings.ThemeManager
import com.ccompiler.pro.utils.dp
import com.ccompiler.pro.utils.roundedBg

/** The strip of open files (only shown when "Open several files" is on in Settings). */
class TabBar(ctx: Context) : HorizontalScrollView(ctx) {
    var onSelect: ((Int) -> Unit)? = null
    var onClose: ((Int) -> Unit)? = null
    var onNew: (() -> Unit)? = null

    private val row = LinearLayout(ctx)
    private var palette: Palette = ThemeManager.palette(ctx)
    private var titles: List<String> = emptyList()
    private var active = -1

    init {
        isHorizontalScrollBarEnabled = false
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        addView(row, LayoutParams(-2, -1))
    }

    fun update(newTitles: List<String>, newActive: Int) {
        if (newTitles == titles && newActive == active) return
        titles = newTitles
        active = newActive
        rebuild()
    }

    fun applyTheme(p: Palette) {
        palette = p
        setBackgroundColor(ColorUtils.blendARGB(p.bg, p.editorBg, 0.5f))
        rebuild()
    }

    private fun rebuild() {
        row.removeAllViews()
        titles.forEachIndexed { i, title ->
            val tab = LinearLayout(context)
            tab.orientation = LinearLayout.HORIZONTAL
            tab.gravity = Gravity.CENTER_VERTICAL
            tab.background = roundedBg(
                ColorUtils.setAlphaComponent(palette.accent, if (i == active) 120 else 35), context.dp(8).toFloat()
            )
            tab.setPadding(context.dp(12), 0, context.dp(2), 0)
            val tv = TextView(context)
            tv.text = title
            tv.setSingleLine()
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.maxWidth = context.dp(150)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            tv.setTextColor(palette.text)
            if (i == active) tv.setTypeface(tv.typeface, Typeface.BOLD)
            val x = ImageView(context)
            x.setImageResource(R.drawable.ic_close)
            x.setColorFilter(ColorUtils.setAlphaComponent(palette.text, 190))
            x.setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
            x.contentDescription = "Close tab"
            tab.addView(tv)
            tab.addView(x, LinearLayout.LayoutParams(context.dp(30), context.dp(30)))
            tab.setOnClickListener { onSelect?.invoke(i) }
            x.setOnClickListener { onClose?.invoke(i) }
            row.addView(tab, LinearLayout.LayoutParams(-2, context.dp(32)).apply {
                setMargins(context.dp(3), context.dp(4), context.dp(3), context.dp(4))
            })
        }
        val plus = ImageView(context)
        plus.setImageResource(R.drawable.ic_add)
        plus.setColorFilter(palette.text)
        plus.setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        plus.contentDescription = "New file"
        plus.setOnClickListener { onNew?.invoke() }
        row.addView(plus, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        post { row.getChildAt(active)?.let { smoothScrollTo((it.left - context.dp(40)).coerceAtLeast(0), 0) } }
    }
}
