package com.ccompiler.pro.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.ccompiler.pro.R
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.dp
import com.ccompiler.pro.utils.roundedBg
import com.ccompiler.pro.utils.selectableBg
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

sealed class SettingItem {
    data class Header(val title: String) : SettingItem()
    data class Toggle(val key: String, val title: String, val default: Boolean, val subtitle: String? = null) : SettingItem()
    data class Choice(
        val key: String, val title: String, val options: List<String>, val default: Int, val dropdown: Boolean = false,
        val lockedKey: String? = null, val lockedLabel: String = "",
        // Opposite of lockedKey: this row is locked (shows requiresLabel instead of its value) UNTIL requiresKey is on.
        val requiresKey: String? = null, val requiresLabel: String = ""
    ) : SettingItem()
    data class SliderRow(val key: String, val title: String, val min: Float, val max: Float, val default: Float) : SettingItem()
    data class Info(val title: String, val value: String) : SettingItem()
    data class Action(val title: String, val subtitle: String?, val warning: Boolean = false, val onClick: () -> Unit) : SettingItem()

    /** A drop-down section. The first [previewCount] children are always visible; tapping the header shows all. */
    data class Group(val key: String, val title: String, val children: List<SettingItem>, val previewCount: Int = 2) : SettingItem()

    /** Internal: the header row of a [Group]. */
    data class GroupHeader(val group: Group, val open: Boolean) : SettingItem()
}

class SettingsAdapter(
    private val ctx: Context,
    private val items: List<SettingItem>,
    private val palette: Palette,
    private val onChanged: (String) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.VH>() {

    class VH(val box: LinearLayout) : RecyclerView.ViewHolder(box)

    private val expanded = HashSet<String>(Prefs.str(ctx, Prefs.SETTINGS_OPEN, "").split(",").filter { it.isNotEmpty() })
    private var rows: List<SettingItem> = emptyList()

    init {
        rebuild()
    }

    private fun rebuild() {
        val out = ArrayList<SettingItem>()
        for (item in items) {
            if (item is SettingItem.Group) {
                val open = item.key in expanded
                out.add(SettingItem.GroupHeader(item, open))
                out.addAll(if (open) item.children else item.children.take(item.previewCount))
            } else {
                out.add(item)
            }
        }
        rows = out
    }

    private fun toggleGroup(g: SettingItem.Group, headerPos: Int) {
        if (headerPos == RecyclerView.NO_POSITION) return
        val nowOpen = expanded.add(g.key)
        if (!nowOpen) expanded.remove(g.key)
        Prefs.put(ctx, Prefs.SETTINGS_OPEN, expanded.joinToString(","))
        val shown = g.previewCount.coerceAtMost(g.children.size)
        val extra = g.children.size - shown
        rebuild()
        notifyItemChanged(headerPos)
        if (extra > 0) {
            if (nowOpen) notifyItemRangeInserted(headerPos + 1 + shown, extra)
            else notifyItemRangeRemoved(headerPos + 1 + shown, extra)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = RecyclerView.LayoutParams(-1, -2)
        return VH(box)
    }

    override fun getItemCount() = rows.size

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun sub(color: Int = palette.text) = ColorUtils.setAlphaComponent(color, 160)

    private fun icon(res: Int, tint: Int, sizeDp: Int) = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val box = h.box
        box.removeAllViews()
        box.background = null
        box.setOnClickListener(null)
        box.isClickable = false
        (box.layoutParams as RecyclerView.LayoutParams).setMargins(0, 0, 0, 0)
        val hp = ctx.dp(20)
        val vp = ctx.dp(12)
        box.setPadding(hp, vp, hp, vp)

        when (val item = rows[position]) {
            is SettingItem.Group -> {}   // never shown directly: its header and rows are flattened into the list
            is SettingItem.GroupHeader -> bindGroupHeader(h, item)
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
                // ON = accent colour, OFF = grey, so the two states are clearly different
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
                val offThumb = ColorUtils.blendARGB(palette.bg, palette.text, 0.55f)
                sw.thumbTintList = ColorStateList(states, intArrayOf(palette.accent, offThumb))
                sw.trackTintList = ColorStateList(
                    states,
                    intArrayOf(ColorUtils.setAlphaComponent(palette.accent, 150), ColorUtils.setAlphaComponent(palette.text, 70))
                )
                sw.setOnCheckedChangeListener { _, on -> Prefs.put(ctx, item.key, on); onChanged(item.key) }
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(sw)
                box.addView(row)
            }
            is SettingItem.Choice -> bindChoice(box, item)
            is SettingItem.SliderRow -> {
                // round to a whole step: pinch-zoom may have saved e.g. 15.37, which Slider (stepSize 1) rejects with a crash
                val v0 = Math.round(Prefs.float(ctx, item.key, item.default)).toFloat().coerceIn(item.min, item.max)
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
                if (item.warning) {
                    val w = icon(R.drawable.ic_warning, 0xFFFFB300.toInt(), 24)
                    (w.layoutParams as LinearLayout.LayoutParams).marginEnd = ctx.dp(12)
                    row.addView(w)
                }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                val titleColor = if (item.warning) (if (palette.isLight) 0xFFC62828.toInt() else 0xFFFF7B7B.toInt()) else palette.text
                col.addView(label(item.title, 16f, titleColor, item.warning))
                item.subtitle?.let { s -> col.addView(label(s, 12f, sub())) }
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(icon(R.drawable.ic_chevron_right, palette.accent, 24))
                box.addView(row)
            }
        }
    }

    private fun bindGroupHeader(h: VH, item: SettingItem.GroupHeader) {
        val box = h.box
        val g = item.group
        (box.layoutParams as RecyclerView.LayoutParams).setMargins(ctx.dp(12), ctx.dp(14), ctx.dp(12), ctx.dp(2))
        box.setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(12), ctx.dp(12))
        box.background = roundedBg(ColorUtils.blendARGB(palette.bg, palette.accent, 0.16f), ctx.dp(12).toFloat())
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(g.title, 17f, palette.text, true))
        val more = g.children.size > g.previewCount
        val hint = if (more && !item.open) "Tap to see all ${g.children.size} options" else "${g.children.size} options"
        col.addView(label(hint, 12f, sub()))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (more) {
            row.addView(icon(if (item.open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, palette.accent, 28))
            box.isClickable = true
            box.setOnClickListener { toggleGroup(g, h.bindingAdapterPosition) }
        }
        box.addView(row)
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
            c.options.forEachIndexed { _, name ->
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
            // dropdown-style row (opens a list)
            box.background = ctx.selectableBg()
            box.isClickable = true
            // lockedKey = "locked while that preference is ON" (e.g. Theme while Monokai is on).
            // requiresKey = the opposite: locked UNTIL that preference is ON (e.g. Monokai Variant while Monokai is off).
            val lockedByOther = c.lockedKey != null && Prefs.bool(ctx, c.lockedKey, false)
            val lockedByMissingRequirement = c.requiresKey != null && !Prefs.bool(ctx, c.requiresKey, false)
            val locked = lockedByOther || lockedByMissingRequirement
            val lockedLabelText = if (lockedByMissingRequirement) c.requiresLabel else c.lockedLabel
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val value = label(if (locked) lockedLabelText else c.options[current], 15f, palette.accent, true)
            row.addView(label(c.title, 16f, palette.text), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(value)
            val chev = icon(R.drawable.ic_expand_more, palette.accent, 22)
            row.addView(chev)
            box.addView(row)
            box.setOnClickListener {
                val lockedNow = (c.lockedKey != null && Prefs.bool(ctx, c.lockedKey, false)) ||
                    (c.requiresKey != null && !Prefs.bool(ctx, c.requiresKey, false))
                if (lockedNow) {
                    val msg = if (c.requiresKey != null && !Prefs.bool(ctx, c.requiresKey, false))
                        "${c.title}: turn on Monokai theme first"
                    else
                        "${c.lockedLabel}: turn it off to change this"
                    NotificationHelper.toast(ctx, msg)
                    return@setOnClickListener
                }
                val sel = Prefs.int(ctx, c.key, c.default).coerceIn(0, c.options.size - 1)
                MaterialAlertDialogBuilder(ctx).setTitle(c.title)
                    .setSingleChoiceItems(c.options.toTypedArray(), sel) { d, which ->
                        Prefs.put(ctx, c.key, which)
                        value.text = c.options[which]
                        d.dismiss()
                        onChanged(c.key)
                    }.setNegativeButton("Cancel", null).show()
            }
        }
    }
}
