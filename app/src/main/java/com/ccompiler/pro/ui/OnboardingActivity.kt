package com.ccompiler.pro.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ccompiler.pro.R
import com.ccompiler.pro.settings.ThemeManager
import com.ccompiler.pro.utils.Prefs
import com.google.android.material.button.MaterialButton

/** 4-screen intro: [Skip] [Next →] [Get Started]. */
class OnboardingActivity : AppCompatActivity() {

    private data class Page(val emoji: String, val text: String)

    private val pages = listOf(
        Page("⌨️", "Write C code anywhere, anytime — 100% offline"),
        Page("▶️", "Swipe or tap Run to see output instantly"),
        Page("🎨", "Customize themes, fonts, and layout in Settings"),
        Page("💾", "Auto-save protects your work — you'll never lose code")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyNightMode(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        Prefs.put(this, Prefs.ONBOARDED, true)   // shown once; "Replay tutorial" in Settings shows it again
        val p = ThemeManager.palette(this)
        findViewById<LinearLayout>(R.id.onb_root).setBackgroundColor(p.bg)
        window.statusBarColor = p.bg

        val pager = findViewById<ViewPager2>(R.id.onb_pager)
        val dots = findViewById<TextView>(R.id.onb_dots)
        val skip = findViewById<Button>(R.id.onb_skip)
        val next = findViewById<MaterialButton>(R.id.onb_next)
        val start = findViewById<MaterialButton>(R.id.onb_start)
        dots.setTextColor(p.accent)
        skip.setTextColor(p.text)
        val tint = ColorStateList.valueOf(p.accent)
        val fg = if (ColorUtils.calculateLuminance(p.accent) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        for (b in listOf(next, start)) { b.backgroundTintList = tint; b.setTextColor(fg) }

        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun getItemCount() = pages.size
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
                h.itemView.findViewById<TextView>(R.id.onb_emoji).text = pages[i].emoji
                h.itemView.findViewById<TextView>(R.id.onb_text).apply { text = pages[i].text; setTextColor(p.text) }
            }
        }

        fun update(pos: Int) {
            dots.text = pages.indices.joinToString("  ") { if (it == pos) "●" else "○" }
            val last = pos == pages.size - 1
            next.visibility = if (last) View.GONE else View.VISIBLE
            skip.visibility = if (last) View.INVISIBLE else View.VISIBLE
            start.visibility = if (last) View.VISIBLE else View.GONE
        }
        update(0)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = update(position)
        })
        next.setOnClickListener { pager.currentItem = pager.currentItem + 1 }
        skip.setOnClickListener { finishOnboarding() }
        start.setOnClickListener { finishOnboarding() }
    }

    private fun finishOnboarding() {
        Prefs.put(this, Prefs.ONBOARDED, true)
        finish()
    }
}
