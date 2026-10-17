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
import androidx.core.view.WindowInsetsControllerCompat
import com.ccompiler.pro.compiler.CodeSanitizer
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
import com.ccompiler.pro.utils.LinkOpener
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.Prefs
import com.ccompiler.pro.utils.ShareHelper
import com.ccompiler.pro.utils.WarningDialog
import com.ccompiler.pro.utils.hideKeyboard
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var host: FrameLayout
    private lateinit var editor: EditorView
    private lateinit var output: OutputView
    private lateinit var compiler: CompilerEngine
    private val executor = ExecutorEngine()

    private var pane: PaneLayout? = null
    private var paneMode = -1
    private var currentFile: File? = null
    private var untitledName = "untitled.c"
    private var savedText = ""
    private var running = false
    private var keyboardVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private val autosaveTask = Runnable { doAutoSave() }

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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })
        root.viewTreeObserver.addOnGlobalLayoutListener { checkKeyboard() }

        restoreState(savedInstanceState)
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("code", editor.getText())
        outState.putString("savedText", savedText)
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
                editor.setText(editor.defaultCode)
                savedText = editor.defaultCode
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

        editor.applySettings()
        editor.applyTheme(p)
        output.applySettings()
        output.applyTheme(p)
        applyLayoutMode()
        pane?.applyTheme(p)
        updateRunButton()
        scheduleAutosave()
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
            editor.setZoomEnabled(!visible)     // pinch-zoom is disabled while the keyboard is up
        }
    }

    // ------------------------------------------------------------------ state helpers

    private fun isModified() = editor.getText() != savedText

    private fun updateTitle() {
        tvTitle.text = (if (isModified()) "● " else "") + (currentFile?.name ?: untitledName)
    }

    private fun updateRunButton() {
        btnRun.text = if (running) "Stop" else "Run"
        btnRun.setIconResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
    }

    private fun onEditorChanged() {
        updateTitle()
        scheduleAutosave()
    }

    private fun confirmDiscardThen(block: () -> Unit) {
        if (isModified()) WarningDialog.discardChanges(this) { block() } else block()
    }

    private fun onBack() {
        if (pane?.handleBack() == true) return
        if (isModified()) WarningDialog.exitWithoutSave(this) { finish() } else finish()
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
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new -> confirmDiscardThen { newFile() }
                R.id.menu_open -> openFileDialog()
                R.id.menu_samples -> samplesDialog()
                R.id.menu_save -> save()
                R.id.menu_save_as -> saveAs()
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
                    "Developer: Md Asifur Rahman\nBangladesh\n${LinkOpener.EMAIL}"
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

    private fun newFile() {
        editor.setText(editor.defaultCode)
        savedText = editor.defaultCode
        untitledName = "untitled.c"
        setCurrent(null)
        StorageHelper.draftFile(this).delete()
        output.clear()
        updateTitle()
    }

    private fun loadFile(f: File) {
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
            onOpen = { f -> confirmDiscardThen { loadFile(f) } },
            onImport = { pickImport() },
            onChanged = { old, new -> onFileChanged(old, new) }
        )
    }

    private fun onFileChanged(old: File, new: File?) {
        val cur = currentFile?.absolutePath ?: return
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
        confirmDiscardThen { loadFile(f) }
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
                confirmDiscardThen {
                    val t = ImportExportHandler.readSample(this, names[which])
                    editor.setText(t)
                    savedText = t
                    untitledName = names[which]
                    setCurrent(null)
                    StorageHelper.draftFile(this).delete()
                    output.clear()
                    updateTitle()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ compile & run

    private fun runProgram() {
        if (running) {
            compiler.cancel()
            executor.cancel()
            return
        }
        editor.ensureNotEmpty()
        if (Prefs.bool(this, Prefs.AUTO_FORMAT, false)) editor.format()
        editor.hideKeyboard()
        val code = editor.getText()
        output.clear()
        pane?.onRunStarted()

        val violations = CodeSanitizer.check(code)
        if (violations.isNotEmpty()) {
            output.append("Blocked by security check:\n", OutputView.Kind.ERR)
            violations.forEach { output.append("  line ${it.line}: ${it.message}\n", OutputView.Kind.ERR) }
            return
        }
        if (!compiler.isAvailable()) {
            output.append(compiler.missingMessage(), OutputView.Kind.ERR)
            return
        }

        val name = currentFile?.name ?: untitledName
        val stdin = output.stdin()
        running = true
        updateRunButton()

        Thread {
            val dir = StorageHelper.workDir(this)
            val src = File(dir, "prog.c")
            val exe = File(dir, "prog")
            try {
                src.writeText(code)
                exe.delete()
                runOnUiThread { output.append("Compiling $name …\n", OutputView.Kind.INFO) }
                val res = compiler.compile(src, exe)
                val text = res.output.replace("prog.c", name).trimEnd()
                if (text.isNotEmpty()) {
                    output.append(text + "\n", if (res.success) OutputView.Kind.INFO else OutputView.Kind.ERR)
                }
                if (res.success) {
                    output.append("Running…\n\n", OutputView.Kind.INFO)
                    val r = executor.run(exe, stdin, dir) { s, isErr ->
                        output.append(s, if (isErr) OutputView.Kind.ERR else OutputView.Kind.OUT)
                    }
                    val secs = String.format(Locale.US, "%.2f", r.durationMs / 1000.0)
                    val msg = when {
                        r.cancelled -> "\n[Stopped]\n"
                        r.timedOut -> "\n[Killed: exceeded ${ExecutorEngine.TIMEOUT_SEC}s time limit]\n"
                        r.memoryKilled -> "\n[Killed: exceeded ${ExecutorEngine.MEMORY_LIMIT_KB / 1024} MB memory limit]\n"
                        else -> "\n[Process exited with code ${r.exitCode} in ${secs}s]\n"
                    }
                    val bad = r.cancelled || r.timedOut || r.memoryKilled || r.exitCode != 0
                    output.append(msg, if (bad) OutputView.Kind.ERR else OutputView.Kind.OK)
                } else {
                    output.append("\n[Compilation failed]\n", OutputView.Kind.ERR)
                }
            } catch (e: Exception) {
                output.append("Error: ${e.message}\n", OutputView.Kind.ERR)
            } finally {
                runOnUiThread {
                    running = false
                    updateRunButton()
                }
            }
        }.start()
    }
}
