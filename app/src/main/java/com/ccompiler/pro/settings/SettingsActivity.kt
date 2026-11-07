package com.ccompiler.pro.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ccompiler.pro.R
import com.ccompiler.pro.files.StorageHelper
import com.ccompiler.pro.files.ZipTools
import com.ccompiler.pro.utils.CrashLog
import com.ccompiler.pro.utils.DataTools
import com.ccompiler.pro.utils.LinkOpener
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.WarningDialog
import com.ccompiler.pro.utils.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var deleteTimer: CountDownTimer? = null
    private var deleteDialog: AlertDialog? = null

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) r.data?.data?.let { doBackup(it) }
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) r.data?.data?.let { askRestoreMode(it) }
    }

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

        // Drop-down groups: the first two rows of each group are always visible, tapping the header shows the rest.
        // Developer and Support stay outside the groups.
        val items = listOf(
            SettingItem.Group("look", "Look & Feel", listOf(
                SettingItem.Choice(Prefs.THEME, "Theme", ThemeManager.names, 0, lockedKey = Prefs.MONOKAI, lockedLabel = "Monokai is on"),
                SettingItem.SliderRow(Prefs.FONT_SIZE, "Font size", 12f, 28f, 14f),
                SettingItem.Toggle(Prefs.MONOKAI, "Monokai theme", false, "Replaces your theme with one of 4 Monokai colour sets below. Works offline."),
                SettingItem.Choice(
                    Prefs.MONOKAI_VARIANT, "Monokai Variant", ThemeManager.monokaiVariantNames, 0, dropdown = true,
                    requiresKey = Prefs.MONOKAI, requiresLabel = "Off"
                ),
                SettingItem.Choice(Prefs.FONT, "Font family", FontManager.names, 0),
                SettingItem.Choice(Prefs.LAYOUT_MODE, "Layout", LayoutModeManager.names, LayoutModeManager.SPLIT)
            )),
            SettingItem.Group("editor", "Editor", listOf(
                SettingItem.Toggle(Prefs.HIGHLIGHT, "Code highlighting", true),
                SettingItem.Toggle(Prefs.LINE_NUMBERS, "Line numbers", true),
                SettingItem.Toggle(Prefs.WORD_WRAP, "Word wrap", false),
                SettingItem.Toggle(Prefs.AUTO_FORMAT, "Auto-format on Run", false),
                SettingItem.Toggle(Prefs.TABS, "Open several files (tabs)", false, "Shows tabs so you can switch between files. Off by default."),
                SettingItem.Toggle(Prefs.ARGS_ENABLED, "Program arguments", false, "Adds a box next to Run for values passed to main(argc, argv). Off by default.")
            )),
            SettingItem.Group("typing", "Typing helpers", listOf(
                SettingItem.Toggle(Prefs.SNIPPETS, "Code snippets", true, "Type for, while, if, main... and tap to insert a ready template."),
                SettingItem.Toggle(Prefs.SYMBOL_PANEL, "Symbol buttons", true),
                SettingItem.Toggle(Prefs.SMART_SYMBOLS, "Smart symbol order", true, "The symbols you use most move to the front."),
                SettingItem.Action("Reset symbol order", "Go back to the default order") {
                    Prefs.remove(this, Prefs.SYMBOL_COUNTS)
                    NotificationHelper.toast(this, "Symbol order reset")
                }
            )),
            SettingItem.Group("autosave", "Auto-save", listOf(
                SettingItem.Toggle(Prefs.AUTOSAVE, "Enable auto-save", true),
                SettingItem.Choice(Prefs.AUTOSAVE_INTERVAL, "Auto-save interval", listOf("3 seconds", "5 seconds", "10 seconds"), 1, dropdown = true),
                SettingItem.Toggle(Prefs.AUTOSAVE_INDICATOR, "Show autosave indicator in status bar", true)
            )),
            SettingItem.Group("storage", "Storage & Backup", listOf(
                SettingItem.Action("Back up all projects", "Save every file into one ZIP") { startBackup() },
                SettingItem.Action("Restore from backup", "Load a ZIP made by this app") { startRestore() },
                SettingItem.Action("Clear temporary files", "Frees space. Your code is not touched.") { clearTemp() },
                SettingItem.Action("Delete all app data", "Erases ALL files and settings. 2-minute countdown.", warning = true) { confirmDeleteAll() }
            )),
            SettingItem.Group("about", "About & Legal", listOf(
                SettingItem.Action("Open-source licenses", "TinyCC, fonts and libraries") { showTextAsset("Open-source licenses", "licenses.txt") },
                SettingItem.Action("Privacy statement", "What this app does and does not do") { showTextAsset("Privacy statement", "privacy.txt") }
            )),

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
                startActivity(Intent(this, com.ccompiler.pro.ui.OnboardingActivity::class.java))
            },

            SettingItem.Info("Version", LinkOpener.versionName(this))
        )

        val list = findViewById<RecyclerView>(R.id.settings_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = SettingsAdapter(this, items, p) { key ->
            if (key == Prefs.THEME || key == Prefs.MONOKAI || key == Prefs.MONOKAI_VARIANT) {
                ThemeManager.applyNightMode(this)
                recreate()
            }
        }
    }

    // ------------------------------------------------------------------ backup / restore

    private fun startBackup() {
        val root = StorageHelper.projectsRoot(this)
        if (!root.walkTopDown().any { it.isFile && StorageHelper.hasCExtension(it.name) }) {
            NotificationHelper.toast(this, "There are no files to back up yet")
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        backupLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "CCompilerPro-backup-$stamp.zip")
        })
    }

    private fun doBackup(uri: Uri) {
        val root = StorageHelper.projectsRoot(this)
        uiScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { ZipTools.backup(root, it) } ?: -1
                } catch (e: Exception) {
                    -1
                }
            }
            NotificationHelper.toast(this@SettingsActivity, if (n >= 0) "Backed up $n file(s)" else "Backup failed")
        }
    }

    private fun startRestore() {
        restoreLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        })
    }

    private fun askRestoreMode(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restore from backup")
            .setMessage("If a file with the same name already exists, what should happen?")
            .setPositiveButton("Keep both") { _, _ -> doRestore(uri, false) }
            .setNeutralButton("Replace it") { _, _ -> doRestore(uri, true) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doRestore(uri: Uri, replace: Boolean) {
        val root = StorageHelper.projectsRoot(this)
        uiScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val r = contentResolver.openInputStream(uri)?.use { ZipTools.restore(it, root, replace) }
                    if (r == null) "Could not read the backup"
                    else "Restored ${r.restored} file(s)" + if (r.skipped > 0) ", skipped ${r.skipped} unsafe or unsupported item(s)" else ""
                } catch (e: Exception) {
                    "Restore failed: ${e.message}"
                }
            }
            NotificationHelper.toast(this@SettingsActivity, message)
        }
    }

    // ------------------------------------------------------------------ temporary files / delete everything

    private fun clearTemp() {
        val freed = DataTools.clearCache(this)
        NotificationHelper.toast(this, "Temporary files cleared (${DataTools.format(freed)})")
    }

    private fun confirmDeleteAll() {
        WarningDialog.show(
            this, "Delete all app data",
            "This erases ALL your projects, settings and saved files on this phone. It cannot be undone.\n\n" +
                "A 2-minute countdown starts next. You can cancel it at any time before it ends.",
            "Start countdown"
        ) { startDeleteCountdown() }
    }

    private fun startDeleteCountdown() {
        cancelDelete(null)
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_warning)
            setColorFilter(0xFFFFB300.toInt())
        }
        val time = TextView(this).apply {
            textSize = 40f
            gravity = Gravity.CENTER
            text = "2:00"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val message = TextView(this).apply {
            text = "Everything will be erased when the timer reaches zero.\nTap \"Cancel delete\" to stop it."
            gravity = Gravity.CENTER
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(time)
            addView(message)
        }
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle("Deleting all app data")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Cancel delete") { _, _ -> cancelDelete("Delete cancelled. Nothing was erased.") }
            .create()
        deleteDialog = dlg
        dlg.show()
        deleteTimer = object : CountDownTimer(120_000, 1000) {
            override fun onTick(msLeft: Long) {
                val s = (msLeft + 999) / 1000
                time.text = String.format(Locale.US, "%d:%02d", s / 60, s % 60)
            }

            override fun onFinish() {
                deleteTimer = null
                dlg.dismiss()
                deleteDialog = null
                DataTools.deleteAllData(this@SettingsActivity)
                finishAffinity()
            }
        }.start()
    }

    private fun cancelDelete(message: String?) {
        val running = deleteTimer != null
        deleteTimer?.cancel()
        deleteTimer = null
        deleteDialog?.dismiss()
        deleteDialog = null
        if (running && message != null) NotificationHelper.toast(this, message)
    }

    // ------------------------------------------------------------------ text pages

    private fun showTextAsset(title: String, asset: String) {
        val content = try {
            assets.open(asset).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Could not load $asset"
        }
        val tv = TextView(this).apply {
            text = content
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val sv = ScrollView(this)
        sv.addView(tv)
        MaterialAlertDialogBuilder(this).setTitle(title).setView(sv).setPositiveButton("Close", null).show()
    }

    override fun onStop() {
        super.onStop()
        if (deleteTimer != null) cancelDelete("Delete cancelled because you left this screen.")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelDelete(null)
        uiScope.cancel()
    }
}
