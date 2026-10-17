package com.ccompiler.pro.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.dp
import com.ccompiler.pro.utils.selectableBg
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

sealed class SettingItem {
    data class Header(val title: String) : SettingItem()
    data class Toggle(val key: String, val title: String, val default: Boolean, val subtitle: String? = null) : SettingItem()
    data class Choice(val key: String, val title: String, val options: List<String>, val default: Int, val dropdown: Boolean = false) : SettingItem()
    data class SliderRow(val key: String, val title: String, val min: Float, val max: Float, val default: Float) : SettingItem()
    data class Info(val title: String, val value: String) : SettingItem()
    data class Action(val title: String, val subtitle: String?, val onClick: () -> Unit) : SettingItem()
}

class SettingsAdapter(
    private val ctx: Context,
    private val items: List<SettingItem>,
    private val palette: Palette,
    private val onChanged: (String) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.VH>() {

    class VH(val box: LinearLayout) : RecyclerView.ViewHolder(box)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = RecyclerView.LayoutParams(-1, -2)
        return VH(box)
    }

    override fun getItemCount() = items.size

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun sub(color: Int = palette.text) = ColorUtils.setAlphaComponent(color, 160)

    override fun onBindViewHolder(h: VH, position: Int) {
        val box = h.box
        box.removeAllViews()
        box.background = null
        box.setOnClickListener(null)
        box.isClickable = false
        val hp = ctx.dp(20)
        val vp = ctx.dp(12)
        box.setPadding(hp, vp, hp, vp)

        when (val item = items[position]) {
            is SettingItem.Header -> {
                box.setPadding(hp, ctx.dp(22), hp, ctx.dp(4))
                box.addView(label(item.title.uppercase(), 12f, palette.accent, true))
            }
            is SettingItem.Toggle -> {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.addView(label(item.title, 16f, palette.text))
                item.subtitle?.let { s -> col.addView(label(s, 12f, sub())) }
                val sw = SwitchCompat(ctx)
                sw.isChecked = Prefs.bool(ctx, item.key, item.default)
                sw.thumbTintList = ColorStateList.valueOf(palette.accent)
                sw.trackTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 110))
                sw.setOnCheckedChangeListener { _, on -> Prefs.put(ctx, item.key, on); onChanged(item.key) }
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(sw)
                box.addView(row)
            }
            is SettingItem.Choice -> bindChoice(box, item)
            is SettingItem.SliderRow -> {
                val v0 = Prefs.float(ctx, item.key, item.default).coerceIn(item.min, item.max)
                val title = label("${item.title}: ${v0.toInt()} px", 16f, palette.text)
                val slider = Slider(ctx).apply {
                    valueFrom = item.min; valueTo = item.max; stepSize = 1f
                    this.value = v0
                    thumbTintList = ColorStateList.valueOf(palette.accent)
                    trackActiveTintList = ColorStateList.valueOf(palette.accent)
                    trackInactiveTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 70))
                    addOnChangeListener { _, v, fromUser ->
                        title.text = "${item.title}: ${v.toInt()} px"
                        if (fromUser) { Prefs.put(ctx, item.key, v); onChanged(item.key) }
                    }
                }
                box.addView(title)
                box.addView(slider, LinearLayout.LayoutParams(-1, -2))
            }
            is SettingItem.Info -> {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(label(item.title, 15f, sub()), LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label(item.value, 15f, palette.text))
                box.addView(row)
            }
            is SettingItem.Action -> {
                box.background = ctx.selectableBg()
                box.isClickable = true
                box.setOnClickListener { item.onClick() }
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.addView(label(item.title, 16f, palette.text))
                item.subtitle?.let { s -> col.addView(label(s, 12f, sub())) }
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label("→", 18f, palette.accent))
                box.addView(row)
            }
        }
    }

    private fun bindChoice(box: LinearLayout, c: SettingItem.Choice) {
        val current = Prefs.int(ctx, c.key, c.default).coerceIn(0, c.options.size - 1)
        if (c.options.size <= 2 && !c.dropdown) {
            // segmented control: [ Split View | Flip View ]
            box.addView(label(c.title, 16f, palette.text))
            val group = MaterialButtonToggleGroup(ctx)
            group.isSingleSelection = true
            group.isSelectionRequired = true
            val ids = ArrayList<Int>()
            c.options.forEachIndexed { i, name ->
                val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                b.id = View.generateViewId()
                b.text = name
                b.setTextColor(palette.text)
                ids.add(b.id)
                group.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
            }
            group.check(ids[current])
            group.addOnButtonCheckedListener { _, id, checked ->
                if (checked) { Prefs.put(ctx, c.key, ids.indexOf(id)); onChanged(c.key) }
            }
            box.addView(group, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(6) })
        } else {
            // dropdown-style row
            box.background = ctx.selectableBg()
            box.isClickable = true
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val value = label(c.options[current] + "  ▾", 15f, palette.accent, true)
            row.addView(label(c.title, 16f, palette.text), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(value)
            box.addView(row)
            box.setOnClickListener {
                val sel = Prefs.int(ctx, c.key, c.default).coerceIn(0, c.options.size - 1)
                MaterialAlertDialogBuilder(ctx).setTitle(c.title)
                    .setSingleChoiceItems(c.options.toTypedArray(), sel) { d, which ->
                        Prefs.put(ctx, c.key, which)
                        value.text = c.options[which] + "  ▾"
                        d.dismiss()
                        onChanged(c.key)
                    }.setNegativeButton("Cancel", null).show()
            }
        }
    }
}
