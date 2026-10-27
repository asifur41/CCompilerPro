package com.ccompiler.pro

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.ccompiler.pro.editor.UndoRedoManager
import androidx.core.view.WindowInsetsControllerCompat
import com.ccompiler.pro.compiler.CodeSanitizer
import com.ccompiler.pro.compiler.CompilationResult
import com.ccompiler.pro.compiler.ExecResult
import com.ccompiler.pro.compiler.CompilerEngine
import com.ccompiler.pro.compiler.ExecutorEngine
import com.ccompiler.pro.files.FileBrowser
import com.ccompiler.pro.files.FileManager
import com.ccompiler.pro.files.ImportExportHandler
import com.ccompiler.pro.files.StorageHelper
import com.ccompiler.pro.menu.EditorMenu
import com.ccompiler.pro.settings.FontManager
import com.ccompiler.pro.settings.LayoutModeManager
import com.ccompiler.pro.settings.SettingsActivity
import com.ccompiler.pro.settings.ThemeManager
import com.ccompiler.pro.ui.EditorView
import com.ccompiler.pro.ui.OnboardingActivity
import com.ccompiler.pro.ui.OutputView
import com.ccompiler.pro.ui.PaneLayout
import com.ccompiler.pro.utils.CrashLog
import com.ccompiler.pro.utils.LinkOpener
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.ShareHelper
import com.ccompiler.pro.utils.WarningDialog
import com.ccompiler.pro.utils.dp
import com.ccompiler.pro.utils.hideKeyboard
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var btnMenu: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var btnRun: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var host: FrameLayout
    private lateinit var editor: EditorView
    private lateinit var output: OutputView
    private lateinit var compiler: CompilerEngine
    private val executor = ExecutorEngine()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runJob: Job? = null
    /** Incremented by every Run/Stop. Anything belonging to an older generation is ignored. */
    @Volatile private var runGeneration = 0

    private var pane: PaneLayout? = null
    private var paneMode = -1
    private var currentFile: File? = null
    private var untitledName = "untitled.c"
    private var savedText = ""
    private var running = false
    private var keyboardVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private val autosaveTask = Runnable { doAutoSave() }

    // ---- open documents (tabs). The ACTIVE one lives in currentFile / untitledName / savedText and the editor;
    // the list below is only used when "Open several files" is switched on in Settings.
    private class Doc(
        var file: File?, var untitled: String, var savedText: String, var text: String,
        var selection: Int = 0, var history: UndoRedoManager.State? = null
    ) {
        val modified: Boolean get() = text != savedText
    }

    private val docs = ArrayList<Doc>()
    private var activeDoc = 0
    private val maxTabs = 12

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) r.data?.data?.let { onImportPicked(it) }
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) r.data?.data?.let {
            val ok = ImportExportHandler.exportToUri(this, it, editor.getText())
            NotificationHelper.toast(this, if (ok) "File exported" else "Export failed")
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()          // must come before super.onCreate
        CrashLog.install(this)
        ThemeManager.applyNightMode(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        topBar = findViewById(R.id.top_bar)
        btnMenu = findViewById(R.id.btn_menu)
        tvTitle = findViewById(R.id.tv_title)
        btnRun = findViewById(R.id.btn_run)
        host = findViewById(R.id.pane_host)

        compiler = CompilerEngine(this)
        editor = EditorView(this)
        output = OutputView(this)
        editor.onContentChanged = { onEditorChanged() }
        editor.onFindRequested = { EditorMenu.showFindReplace(this, editor) }
        editor.onMoreRequested = { EditorMenu.show(this, it, editor) }

        btnMenu.setOnClickListener { showMenu(it) }
        btnRun.setOnClickListener { runProgram() }
        btnStop = MaterialButton(this).apply {
            text = "Stop"
            visibility = View.GONE
            setIconResource(R.drawable.ic_stop)
            setOnClickListener { stopProgram() }
        }
        topBar.addView(btnStop, topBar.indexOfChild(btnRun),
            LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(8) })
        output.onSend = { executor.sendInput(it) }
        output.onEof = { executor.closeInput() }
        output.onLineClick = { jumpToLine(it) }
        editor.onTabSelected = { switchTab(it) }
        editor.onTabClosed = { closeTab(it) }
        editor.onTabNew = { promptNewFile() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
        root.viewTreeObserver.addOnGlobalLayoutListener { checkKeyboard() }

        restoreState(savedInstanceState)
        restoreTabs()
        applyAll()
        updateTitle()

        if (savedInstanceState == null && !Prefs.bool(this, Prefs.ONBOARDED, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.applyNightMode(this)
        applyAll()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autosaveTask)
        if (Prefs.bool(this, Prefs.AUTOSAVE, true)) doAutoSave(showIndicator = false)
        persistTabs()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        executor.shutdown()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val code = editor.getText()
        if (code.length <= 150_000) {            // larger text is restored from the file / autosave draft instead
            outState.putString("code", code)
            outState.putString("savedText", savedText)
        }
        outState.putString("file", currentFile?.absolutePath)
        outState.putString("untitled", untitledName)
    }

    private fun restoreState(state: Bundle?) {
        val code = state?.getString("code")
        if (code != null) {
            currentFile = state.getString("file")?.let { File(it) }?.takeIf { it.exists() }
            untitledName = state.getString("untitled") ?: "untitled.c"
            savedText = state.getString("savedText") ?: ""
            editor.setText(code)
            return
        }
        val path = Prefs.str(this, Prefs.CURRENT_FILE, "")
        val f = if (path.isNotEmpty()) File(path) else null
        val draft = StorageHelper.draftFile(this)
        when {
            f != null && f.exists() -> {
                currentFile = f
                savedText = FileManager.read(f)
                editor.setText(savedText)
            }
            draft.exists() && draft.length() > 0 -> {
                editor.setText(draft.readText())
                savedText = ""            // an unsaved draft counts as modified
            }
            else -> {
                editor.setText(editor.starterCode)      // very first launch only: Hello World
                savedText = editor.starterCode
            }
        }
    }

    // ------------------------------------------------------------------ theme / layout

    private fun applyAll() {
        val p = ThemeManager.palette(this)
        window.statusBarColor = p.bg
        WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = p.isLight
        root.setBackgroundColor(p.bg)
        topBar.setBackgroundColor(p.editorBg)
        tvTitle.setTextColor(p.text)
        btnMenu.setColorFilter(p.text)
        btnRun.backgroundTintList = ColorStateList.valueOf(p.accent)
        val fg = if (ColorUtils.calculateLuminance(p.accent) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        btnRun.setTextColor(fg)
        btnRun.iconTint = ColorStateList.valueOf(fg)
        btnStop.backgroundTintList = ColorStateList.valueOf(0xFFD32F2F.toInt())
        btnStop.setTextColor(0xFFFFFFFF.toInt())
        btnStop.iconTint = ColorStateList.valueOf(0xFFFFFFFF.toInt())

        editor.applySettings()
        editor.applyTheme(p)
        output.applySettings()
        output.applyTheme(p)
        applyLayoutMode()
        pane?.applyTheme(p)
        updateRunButton()
        scheduleAutosave()
        collapseTabsIfOff()
        refreshTabs()
    }

    private fun applyLayoutMode() {
        val mode = LayoutModeManager.mode(this)
        if (mode == paneMode) return
        pane?.detach()
        host.removeAllViews()
        val p = LayoutModeManager.create(this, mode)
        pane = p
        paneMode = mode
        host.addView(p.view, FrameLayout.LayoutParams(-1, -1))
        p.attach(editor, output)
        p.applyTheme(ThemeManager.palette(this))
    }

    private fun checkKeyboard() {
        val r = Rect()
        root.getWindowVisibleDisplayFrame(r)
        val screenH = root.rootView.height
        val visible = screenH - r.height() > screenH * 0.15f
        if (visible != keyboardVisible) {
            keyboardVisible = visible
            // pinch-zoom now also works while typing (it used to be switched off while the keyboard was open)
        }
    }

    // ------------------------------------------------------------------ state helpers

    private fun isModified() = editor.getText() != savedText

    private fun updateTitle() {
        tvTitle.text = (if (isModified()) "• " else "") + (currentFile?.name ?: untitledName)
        refreshTabs()
    }

    private fun updateRunButton() {
        // While a run is active the main button becomes "Restart" (terminates the old process, starts anew)
        // and a separate Stop button appears.
        btnRun.text = if (running) "Restart" else "Run"
        btnRun.setIconResource(R.drawable.ic_play)
        btnStop.visibility = if (running) View.VISIBLE else View.GONE
    }

    private fun onEditorChanged() {
        updateTitle()
        scheduleAutosave()
    }

    private fun confirmDiscardThen(block: () -> Unit) {
        if (isModified()) WarningDialog.discardChanges(this) { block() } else block()
    }

    /** Replacing the open file needs a warning; opening in a new tab does not. */
    private fun replaceGuard(block: () -> Unit) {
        if (tabsOn()) block() else confirmDiscardThen(block)
    }

    /** "Settings" with a small gear drawn after the word (a text symbol would be drawn wrongly on some phones). */
    private fun withIcon(text: String, resId: Int): CharSequence {
        val d = ContextCompat.getDrawable(this, resId)?.mutate() ?: return text
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        val color = if (tv.resourceId != 0) {
            ContextCompat.getColorStateList(this, tv.resourceId)?.defaultColor ?: 0xFFFFFFFF.toInt()
        } else {
            tv.data
        }
        DrawableCompat.setTint(d, color)
        val size = dp(18)
        d.setBounds(0, 0, size, size)
        val s = SpannableString("$text  ")
        s.setSpan(ImageSpan(d, ImageSpan.ALIGN_BOTTOM), s.length - 1, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    // ------------------------------------------------------------------ tabs (Settings > Editor > "Open several files")

    private fun tabsOn(): Boolean = Prefs.bool(this, Prefs.TABS, false)

    private fun snapshotActive() =
        Doc(currentFile, untitledName, savedText, editor.getText(), editor.selectionStart(), editor.exportHistory())

    private fun ensureDocs() {
        if (docs.isEmpty()) {
            docs.add(snapshotActive())
            activeDoc = 0
        }
    }

    private fun storeActive() {
        ensureDocs()
        if (activeDoc !in docs.indices) activeDoc = 0
        docs[activeDoc] = snapshotActive()
    }

    private fun showDoc(index: Int) {
        val d = docs[index]
        activeDoc = index
        setCurrent(d.file)
        untitledName = d.untitled
        savedText = d.savedText
        editor.restoreDocument(d.text, d.selection, d.history)
        updateTitle()
    }

    /** Leaves the active file: its edits stay in memory and, for a real file with auto-save on, are written to disk. */
    private fun leaveActive() {
        storeActive()
        val d = docs[activeDoc]
        val f = d.file
        if (f != null && d.modified && Prefs.bool(this, Prefs.AUTOSAVE, true)) {
            try {
                FileManager.write(f, d.text)
                d.savedText = d.text
            } catch (_: Exception) {
            }
        }
    }

    private fun switchTab(index: Int) {
        ensureDocs()
        if (index == activeDoc || index !in docs.indices) return
        leaveActive()
        showDoc(index)
    }

    private fun openInNewTab(file: File?, name: String, text: String) {
        ensureDocs()
        if (docs.size >= maxTabs) {
            NotificationHelper.toast(this, "Close a tab first (up to $maxTabs files can be open)")
            return
        }
        leaveActive()
        docs.add(Doc(file, name, text, text))
        showDoc(docs.lastIndex)
    }

    private fun openFileInTab(f: File) {
        ensureDocs()
        val existing = docs.indices.firstOrNull { i ->
            (if (i == activeDoc) currentFile else docs[i].file)?.absolutePath == f.absolutePath
        }
        if (existing != null) {
            switchTab(existing)
            return
        }
        try {
            openInNewTab(f, f.name, FileManager.read(f))
        } catch (e: Exception) {
            NotificationHelper.toast(this, "Could not open ${f.name}")
        }
    }

    private fun closeTab(index: Int) {
        ensureDocs()
        if (index !in docs.indices) return
        if (index == activeDoc) storeActive()
        val d = docs[index]
        val close = {
            docs.removeAt(index)
            when {
                docs.isEmpty() -> {
                    docs.add(Doc(null, "untitled.c", editor.defaultCode, editor.defaultCode))
                    showDoc(0)
                }
                index == activeDoc -> showDoc(minOf(index, docs.lastIndex))
                else -> {
                    if (index < activeDoc) activeDoc--
                    refreshTabs()
                }
            }
        }
        if (d.modified) WarningDialog.discardChanges(this) { close() } else close()
    }

    private fun refreshTabs() {
        if (!tabsOn()) {
            editor.hideTabs()
            return
        }
        ensureDocs()
        val titles = docs.indices.map { i ->
            val mod = if (i == activeDoc) isModified() else docs[i].modified
            val name = if (i == activeDoc) (currentFile?.name ?: untitledName) else (docs[i].file?.name ?: docs[i].untitled)
            (if (mod) "• " else "") + name
        }
        editor.showTabs(titles, activeDoc)
    }

    private fun anyModified(): Boolean =
        isModified() || (tabsOn() && docs.indices.any { it != activeDoc && docs[it].modified })

    /** Remember which files are open, so the tabs come back after the app was closed. */
    private fun persistTabs() {
        if (!tabsOn() || docs.size < 2) {
            Prefs.remove(this, Prefs.OPEN_TABS)
            return
        }
        storeActive()
        Prefs.put(this, Prefs.OPEN_TABS, docs.mapNotNull { it.file?.absolutePath }.joinToString("|"))
    }

    private fun restoreTabs() {
        if (!tabsOn()) return
        ensureDocs()
        val saved = Prefs.str(this, Prefs.OPEN_TABS, "")
        if (saved.isEmpty()) return
        val have = docs.mapNotNull { it.file?.absolutePath }.toMutableSet()
        for (path in saved.split("|")) {
            if (path.isEmpty() || path in have || docs.size >= maxTabs) continue
            val f = File(path)
            if (!f.exists()) continue
            try {
                val t = FileManager.read(f)
                docs.add(Doc(f, f.name, t, t))
                have.add(path)
            } catch (_: Exception) {
            }
        }
    }

    /** Tabs were switched off in Settings: only the file on screen stays (edited files are saved first). */
    private fun collapseTabsIfOff() {
        if (tabsOn()) return
        if (docs.size > 1) {
            storeActive()
            for ((i, d) in docs.withIndex()) {
                val f = d.file
                if (i != activeDoc && f != null && d.modified && Prefs.bool(this, Prefs.AUTOSAVE, true)) {
                    try { FileManager.write(f, d.text) } catch (_: Exception) {}
                }
            }
        }
        docs.clear()
    }

    private fun onBack() {
        if (pane?.handleBack() == true) return
        if (anyModified()) WarningDialog.exitWithoutSave(this) { finish() } else finish()
    }

    // ------------------------------------------------------------------ auto-save

    private fun scheduleAutosave() {
        handler.removeCallbacks(autosaveTask)
        if (!Prefs.bool(this, Prefs.AUTOSAVE, true) || !isModified()) return
        handler.postDelayed(autosaveTask, Prefs.autosaveIntervalMs(this))
    }

    private fun doAutoSave(showIndicator: Boolean = true) {
        if (!isModified()) return
        val text = editor.getText()
        if (text.isBlank()) return
        try {
            val f = currentFile
            val label: String
            if (f != null) {
                FileManager.write(f, text)
                savedText = text
                label = "Saved"
            } else {
                StorageHelper.draftFile(this).writeText(text)
                label = "Draft saved"
            }
            if (showIndicator && Prefs.bool(this, Prefs.AUTOSAVE_INDICATOR, true)) {
                val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                editor.setStatusRight("✓ Auto-$label $t".replace("Auto-Saved", "Auto-saved").replace("Auto-Draft saved", "Draft auto-saved"))
            }
            updateTitle()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ menu

    private fun showMenu(anchor: android.view.View) {
        val pm = PopupMenu(this, anchor)
        menuInflater.inflate(R.menu.main_menu, pm.menu)
        pm.menu.findItem(R.id.menu_settings)?.title = withIcon("Settings", R.drawable.ic_settings)
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new -> replaceGuard { promptNewFile() }
                R.id.menu_open -> openFileDialog()
                R.id.menu_samples -> samplesDialog()
                R.id.menu_save -> save()
                R.id.menu_save_as -> saveAs()
                R.id.menu_rename -> renameCurrentFile()
                R.id.menu_export -> exportFile()
                R.id.menu_share -> ShareHelper.show(
                    this, editor.getText(), output.text(), currentFile?.name ?: untitledName,
                    ThemeManager.palette(this), Prefs.bool(this, Prefs.HIGHLIGHT, true), FontManager.typeface(this)
                )
                R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.menu_report -> LinkOpener.reportBug(this)
                R.id.menu_donate -> LinkOpener.openLink(this, LinkOpener.DONATE)
                R.id.menu_about -> showAbout()
            }
            true
        }
        pm.show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("CCompiler Pro")
            .setMessage(
                "Version ${LinkOpener.versionName(this)}\n\nOffline C compiler & editor.\n\n" +
                    "Developer: Md Asifur Rahman\nBangladesh\n${LinkOpener.EMAIL}\n\n" +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), memory page size ${LinkOpener.pageSize()}"
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Website") { _, _ -> LinkOpener.openLink(this, LinkOpener.WEBSITE) }
            .show()
    }

    // ------------------------------------------------------------------ files

    private fun setCurrent(f: File?) {
        currentFile = f
        if (f != null) Prefs.put(this, Prefs.CURRENT_FILE, f.absolutePath) else Prefs.remove(this, Prefs.CURRENT_FILE)
    }

    /** New File: asks for a name, creates the file (containing only #include <stdio.h>) and opens it. */
    private fun promptNewFile() {
        val root = StorageHelper.projectsRoot(this)
        FileBrowser.promptFileName(
            this, "Input file name", "", "Create",
            check = { name -> if (File(root, name).exists()) "A file named $name already exists" else null }
        ) { name ->
            val f = File(root, name)
            try {
                FileManager.write(f, editor.defaultCode)
                output.clear()
                loadFile(f)
                editor.moveCursorToEnd()
            } catch (e: Exception) {
                NotificationHelper.toast(this, "Could not create $name")
            }
        }
    }

    /**
     * Rename Current File. A file on disk is renamed in place (unsaved edits stay unsaved). Text that has no
     * file yet (untitled.c or a sample) is given the chosen name and saved as a real file.
     */
    private fun renameCurrentFile() {
        val cur = currentFile
        val folder = cur?.parentFile ?: StorageHelper.projectsRoot(this)
        FileBrowser.promptFileName(
            this, "Rename current file", cur?.name ?: untitledName, "Rename",
            check = { name ->
                val target = File(folder, name)
                when {
                    cur != null && target.absolutePath == cur.absolutePath -> null
                    target.exists() -> "A file named $name already exists"
                    else -> null
                }
            }
        ) { name ->
            try {
                if (cur != null) {
                    if (name != cur.name) {
                        val renamed = FileManager.rename(cur, name)
                        if (renamed == null) {
                            NotificationHelper.toast(this, "Rename failed")
                            return@promptFileName
                        }
                        setCurrent(renamed)
                    }
                } else {
                    val target = File(folder, name)
                    FileManager.write(target, editor.getText())
                    savedText = editor.getText()
                    setCurrent(target)
                    StorageHelper.draftFile(this).delete()
                }
                updateTitle()
                NotificationHelper.toast(this, "Renamed to $name")
            } catch (e: Exception) {
                NotificationHelper.toast(this, "Rename failed: ${e.message}")
            }
        }
    }

    private fun loadFile(f: File) {
        if (tabsOn()) { openFileInTab(f); return }
        try {
            val t = FileManager.read(f)
            editor.setText(t)
            savedText = t
            setCurrent(f)
            StorageHelper.draftFile(this).delete()
            updateTitle()
        } catch (e: Exception) {
            NotificationHelper.toast(this, "Could not open ${f.name}")
        }
    }

    private fun openFileDialog() {
        FileBrowser.showOpenDialog(
            this,
            onOpen = { f -> replaceGuard { loadFile(f) } },
            onImport = { pickImport() },
            onChanged = { old, new -> onFileChanged(old, new) }
        )
    }

    private fun onFileChanged(old: File, new: File?) {
        val o0 = old.absolutePath
        for (i in docs.indices) {            // other open tabs
            if (i == activeDoc) continue
            val d = docs[i]
            val p0 = d.file?.absolutePath ?: continue
            if (p0 == o0 || p0.startsWith(o0 + File.separator)) {
                if (new == null) { d.untitled = File(p0).name; d.file = null; d.savedText = "" }
                else d.file = File(new.absolutePath + p0.removePrefix(o0))
            }
        }
        val cur = currentFile?.absolutePath ?: run { refreshTabs(); return }
        val o = old.absolutePath
        if (cur == o || cur.startsWith(o + File.separator)) {
            if (new == null) {
                setCurrent(null)
                untitledName = File(cur).name
                savedText = ""        // content is no longer on disk -> counts as modified
            } else {
                setCurrent(File(new.absolutePath + cur.removePrefix(o)))
            }
            updateTitle()
        }
    }

    private fun save() {
        val f = currentFile
        if (f == null) { saveAs(); return }
        editor.ensureNotEmpty()
        try {
            FileManager.write(f, editor.getText())
            savedText = editor.getText()
            updateTitle()
            NotificationHelper.toast(this, "Saved ${f.name}")
        } catch (e: Exception) {
            NotificationHelper.toast(this, "Save failed: ${e.message}")
        }
    }

    private fun saveAs() {
        editor.ensureNotEmpty()
        FileBrowser.showSaveAsDialog(this, currentFile?.name ?: untitledName) { target ->
            try {
                FileManager.write(target, editor.getText())
                savedText = editor.getText()
                setCurrent(target)
                StorageHelper.draftFile(this).delete()
                updateTitle()
                NotificationHelper.toast(this, "Saved ${target.name}")
            } catch (e: Exception) {
                NotificationHelper.toast(this, "Save failed: ${e.message}")
            }
        }
    }

    private fun pickImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/x-csrc", "text/x-chdr", "text/x-c", "text/plain", "application/octet-stream"))
        }
        importLauncher.launch(i)
    }

    private fun onImportPicked(uri: Uri) {
        val f = ImportExportHandler.importUri(this, uri, StorageHelper.projectsRoot(this))
        if (f == null) {
            NotificationHelper.toast(this, "Only .c and .h files can be imported")
            return
        }
        NotificationHelper.toast(this, "Imported ${f.name}")
        replaceGuard { loadFile(f) }
    }

    private fun exportFile() {
        editor.ensureNotEmpty()
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/x-csrc"
            putExtra(Intent.EXTRA_TITLE, currentFile?.name ?: untitledName)
            if (Build.VERSION.SDK_INT >= 26) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"))
            }
        }
        exportLauncher.launch(i)
    }

    private fun samplesDialog() {
        val names = ImportExportHandler.listSamples(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("Sample Programs")
            .setItems(names.toTypedArray()) { _, which ->
                replaceGuard {
                    val t = ImportExportHandler.readSample(this, names[which])
                    if (tabsOn()) {
                        openInNewTab(null, names[which], t)
                    } else {
                        editor.setText(t)
                        savedText = t
                        untitledName = names[which]
                        setCurrent(null)
                        StorageHelper.draftFile(this).delete()
                        output.clear()
                        updateTitle()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ compile & run

    private fun finishRun(gen: Int) {
        if (gen != runGeneration) return
        running = false
        updateRunButton()
    }

    /** Tap on a compiler error: leave the output pane (Flip mode) and show the line in the editor. */
    private fun jumpToLine(line: Int) {
        pane?.handleBack()
        editor.goToLine(line)
    }

    private fun stopProgram() {
        compiler.cancel()
        if (executor.isRunning()) {
            executor.stop()                      // onFinished(cancelled) is delivered and ends the run
        } else {
            runGeneration++                      // we were still compiling
            runJob?.cancel()
            output.append("\n[Stopped]\n", OutputView.Kind.ERR)
            output.setInputVisible(false)
            running = false
            updateRunButton()
        }
    }

    /** Exit codes 128+N (or -N) mean the program was killed by signal N. */
    private fun crashHint(code: Int): String? = when (code) {
        139, -11 -> "The program crashed (segmentation fault): usually a NULL or bad pointer, or an array index out of range."
        134, -6 -> "The program called abort(): for example a failed assert(), or a bad free() / double free."
        136, -8 -> "The program crashed with an arithmetic error: usually a division by zero."
        135, -7 -> "The program crashed (bus error): usually a bad pointer or misaligned memory access."
        132, -4 -> "The program tried to run an illegal instruction: often caused by memory corruption."
        else -> null
    }

    private fun listenerFor(gen: Int) = object : ExecutorEngine.Listener {
        override fun onOutput(text: String, isErr: Boolean) {
            if (gen == runGeneration) output.append(text, if (isErr) OutputView.Kind.ERR else OutputView.Kind.OUT)
        }

        override fun onInputEcho(text: String) {
            if (gen == runGeneration) output.append(text + "\n", OutputView.Kind.INFO)
        }

        override fun onWaitingForInput(waiting: Boolean) {
            if (gen == runGeneration) output.setInputVisible(waiting)
        }

        override fun onFinished(result: ExecResult) {
            if (gen != runGeneration) return
            val secs = String.format(Locale.US, "%.2f", result.durationMs / 1000.0)
            val msg = when {
                result.cancelled -> "\n[Stopped]\n"
                result.outputLimit -> "\n[Killed: too much output]\n"
                result.timedOut -> "\n[Killed: exceeded the ${ExecutorEngine.TIMEOUT_SEC}s run-time limit]\n"
                result.memoryKilled -> "\n[Killed: exceeded ${ExecutorEngine.MEMORY_LIMIT_KB / 1024} MB memory limit]\n"
                else -> "\n[Process exited with code ${result.exitCode} in ${secs}s]\n"
            }
            val bad = result.cancelled || result.outputLimit || result.timedOut || result.memoryKilled || result.exitCode != 0
            output.append(msg, if (bad) OutputView.Kind.ERR else OutputView.Kind.OK)
            if (!result.cancelled && !result.timedOut && !result.memoryKilled && !result.outputLimit) {
                crashHint(result.exitCode)?.let { output.append("💡 $it\n", OutputView.Kind.OK) }
            }
            output.setInputVisible(false)
            finishRun(gen)
        }
    }

    /**
     * Compile (type-check) and run. Pressing Run while an earlier run is still active restarts:
     * the old compile/process is terminated first (SIGTERM, then SIGKILL) on a background coroutine,
     * and nothing from the old run can touch the screen afterwards (generation check).
     */
    private fun runProgram() {
        val gen = ++runGeneration
        compiler.cancel()
        runJob?.cancel()

        editor.ensureNotEmpty()
        if (Prefs.bool(this, Prefs.AUTO_FORMAT, false)) editor.format()
        editor.hideKeyboard()
        val code = editor.getText()
        val name = currentFile?.name ?: untitledName
        editor.clearErrorLines()
        output.clear()
        output.setInputVisible(false)
        pane?.onRunStarted()
        running = true
        updateRunButton()

        runJob = uiScope.launch {
            // 1) make sure the previous process is really gone (off the UI thread)
            withContext(Dispatchers.IO) { executor.stopSilently() }
            if (gen != runGeneration) return@launch

            // 2) safety checks
            val violations = CodeSanitizer.check(code)
            if (violations.isNotEmpty()) {
                output.append("Blocked by security check:\n", OutputView.Kind.ERR)
                violations.forEach { output.append("  line ${it.line}: ${it.message}\n", OutputView.Kind.ERR) }
                editor.setErrorLines(violations.map { it.line }.toSet())
                finishRun(gen)
                return@launch
            }
            if (!Regex("\\bmain\\s*\\(").containsMatchIn(code)) {
                output.append("No main() function found. A C program needs: int main() { ... }\n", OutputView.Kind.ERR)
                finishRun(gen)
                return@launch
            }
            if (!compiler.isAvailable()) {
                output.append(compiler.missingMessage(), OutputView.Kind.ERR)
                finishRun(gen)
                return@launch
            }

            // 3) compile on a background thread
            output.append("Compiling $name …\n", OutputView.Kind.INFO)
            val dir = StorageHelper.workDir(this@MainActivity)
            val src = File(dir, "prog.c")
            val obj = File(dir, "prog.o")
            val res: CompilationResult = withContext(Dispatchers.IO) {
                try {
                    src.writeText(code)
                    obj.delete()
                    compiler.compile(src, obj)
                } catch (e: Exception) {
                    CompilationResult(false, "Error: ${e.message}", emptyList(), 0)
                }
            }
            if (gen != runGeneration) return@launch

            if (res.output.isNotBlank()) {
                output.appendCompilerOutput(res.output.trimEnd(), src.name, name,
                    if (res.success) OutputView.Kind.INFO else OutputView.Kind.ERR)
            }
            if (!res.success) {
                editor.setErrorLines(res.diagnostics.filter { it.severity == "error" }.map { it.line }.toSet())
                val hasLinks = res.diagnostics.any { it.severity == "error" }
                output.append(
                    if (hasLinks) "\n[Compilation failed — tap an error to jump to its line]\n" else "\n[Compilation failed]\n",
                    OutputView.Kind.ERR
                )
                finishRun(gen)
                return@launch
            }

            // 4) run (background coroutines inside ExecutorEngine; callbacks come back on the main thread)
            output.append("Running…\n\n", OutputView.Kind.INFO)
            val cmd = withContext(Dispatchers.IO) { compiler.runCommand(src) }
            if (gen != runGeneration) return@launch
            executor.start(cmd, dir, listenerFor(gen)) { gen == runGeneration }
        }
    }
}
