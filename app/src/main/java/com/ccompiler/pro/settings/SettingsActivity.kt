package com.ccompiler.pro.settings

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ccompiler.pro.R
import com.ccompiler.pro.utils.CrashLog
import com.ccompiler.pro.utils.LinkOpener
import com.ccompiler.pro.utils.Prefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this)
        ThemeManager.applyNightMode(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_settings)

        val p = ThemeManager.palette(this)
        window.statusBarColor = p.bg
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = p.isLight
        findViewById<LinearLayout>(R.id.settings_root).setBackgroundColor(p.bg)
        findViewById<LinearLayout>(R.id.settings_bar).setBackgroundColor(p.editorBg)
        findViewById<TextView>(R.id.settings_heading).setTextColor(p.text)
        findViewById<ImageButton>(R.id.settings_back).apply {
            setColorFilter(p.text)
            setOnClickListener { finish() }
        }

        val items = listOf(
            SettingItem.Header("Appearance"),
            SettingItem.Choice(Prefs.LAYOUT_MODE, "Layout Mode", LayoutModeManager.names, LayoutModeManager.SPLIT),
            SettingItem.Choice(Prefs.THEME, "Theme Mode", ThemeManager.names, 0),
            SettingItem.Choice(Prefs.FONT, "Font Family", FontManager.names, 0),
            SettingItem.SliderRow(Prefs.FONT_SIZE, "Font Size", 12f, 28f, 14f),

            SettingItem.Header("Editor"),
            SettingItem.Toggle(Prefs.HIGHLIGHT, "Code Highlighting", true),
            SettingItem.Toggle(Prefs.AUTO_FORMAT, "Auto-format on Run", false),
            SettingItem.Toggle(Prefs.LINE_NUMBERS, "Show Line Numbers", true),
            SettingItem.Toggle(Prefs.SYMBOL_PANEL, "Show Symbol Panel", true),
            SettingItem.Toggle(Prefs.WORD_WRAP, "Word Wrap", false),

            SettingItem.Header("Auto-save"),
            SettingItem.Toggle(Prefs.AUTOSAVE, "Enable auto-save", true),
            SettingItem.Choice(Prefs.AUTOSAVE_INTERVAL, "Auto-save interval", listOf("3 seconds", "5 seconds", "10 seconds"), 1, dropdown = true),
            SettingItem.Toggle(Prefs.AUTOSAVE_INDICATOR, "Show autosave indicator in status bar", true),

            SettingItem.Header("Developer"),
            SettingItem.Info("Name", "Md Asifur Rahman"),
            SettingItem.Info("Email", LinkOpener.EMAIL),
            SettingItem.Info("Country", "Bangladesh"),
            SettingItem.Action("Visit Developer Website", null) { LinkOpener.openLink(this, LinkOpener.WEBSITE) },

            SettingItem.Header("Support"),
            SettingItem.Action("Join Community", "Chat with other users on WhatsApp") {
                LinkOpener.openLink(this, LinkOpener.COMMUNITY)
            },
            SettingItem.Action("Download the latest APK", "Direct download of the newest version") {
                LinkOpener.openLink(this, LinkOpener.APK_DOWNLOAD)
            },
            SettingItem.Action("Visit GitHub releases", "Installed: ${LinkOpener.versionName(this)}. See what is new.") {
                LinkOpener.openLink(this, LinkOpener.RELEASES)
            },
            SettingItem.Action("Leave a review", "Tell others what you think of the app") {
                LinkOpener.openLink(this, LinkOpener.REVIEW)
            },
            SettingItem.Action("Report a Bug", null) { LinkOpener.reportBug(this) },
            SettingItem.Action("Donate", null) { LinkOpener.openLink(this, LinkOpener.DONATE) },
            SettingItem.Action("Replay tutorial", null) {
                startActivity(android.content.Intent(this, com.ccompiler.pro.ui.OnboardingActivity::class.java))
            },

            SettingItem.Info("Version", LinkOpener.versionName(this))
        )

        val list = findViewById<RecyclerView>(R.id.settings_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = SettingsAdapter(this, items, p) { key ->
            if (key == Prefs.THEME) {
                ThemeManager.applyNightMode(this)
                recreate()
            }
        }
    }
}
