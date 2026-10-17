package com.ccompiler.pro.files

import android.app.Activity
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.WarningDialog
import com.ccompiler.pro.utils.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/** Dialog-based project browser, folder picker and search. */
object FileBrowser {

    private fun promptText(activity: Activity, title: String, initial: String, onOk: (String) -> Unit) {
        val et = EditText(activity).apply {
            setText(initial)
            setSelection(initial.length)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val box = LinearLayout(activity).apply {
            setPadding(activity.dp(24), activity.dp(8), activity.dp(24), 0)
            addView(et, LinearLayout.LayoutParams(-1, -2))
        }
        MaterialAlertDialogBuilder(activity).setTitle(title).setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString()) }
            .show()
    }

    fun showOpenDialog(
        activity: Activity,
        onOpen: (File) -> Unit,
        onImport: () -> Unit,
        onChanged: (old: File, new: File?) -> Unit
    ) {
        val root = StorageHelper.projectsRoot(activity)
        var dir = root
        val rows = ArrayList<String>()
        var entries: List<File?> = emptyList()   // null = parent, special handled by index
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, rows)
        val lv = ListView(activity)
        lv.adapter = adapter

        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle("Open file")
            .setView(lv)
            .setPositiveButton("Import…") { _, _ -> onImport() }
            .setNeutralButton("New folder", null)
            .setNegativeButton("Cancel", null)
            .create()

        // index 0 = search row, then optional parent row, then entries
        fun render() {
            dlg.setTitle("Open file  ${FileManager.relative(root, dir)}")
            rows.clear()
            rows.add("🔍  Search in all folders…")
            val list = ArrayList<File?>()
            if (dir != root) {
                rows.add("⬆  ..")
                list.add(null)
            }
            for (f in FileManager.list(dir)) {
                rows.add((if (f.isDirectory) "📁  " else "📄  ") + f.name)
                list.add(f)
            }
            entries = list
            if (list.isEmpty() || (list.size == 1 && dir != root)) rows.add("(empty — use Import… or Save As)")
            adapter.notifyDataSetChanged()
        }

        fun entryAt(position: Int): Pair<Boolean, File?> {
            // returns (isParentRow, file)
            val i = position - 1
            if (i < 0 || i >= entries.size) return Pair(false, null)
            val e = entries[i]
            return if (e == null) Pair(true, null) else Pair(false, e)
        }

        lv.setOnItemClickListener { _, _, pos, _ ->
            if (pos == 0) {
                dlg.dismiss()
                showSearchDialog(activity, onOpen)
                return@setOnItemClickListener
            }
            val (isParent, f) = entryAt(pos)
            when {
                isParent -> { dir = dir.parentFile ?: root; render() }
                f == null -> {}
                f.isDirectory -> { dir = f; render() }
                else -> { dlg.dismiss(); onOpen(f) }
            }
        }
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            val (_, f) = entryAt(pos)
            if (f == null) return@setOnItemLongClickListener true
            val ops = if (f.isDirectory) arrayOf("Rename", "Delete") else arrayOf("Rename", "Duplicate", "Delete")
            MaterialAlertDialogBuilder(activity).setTitle(f.name).setItems(ops) { _, which ->
                when (ops[which]) {
                    "Rename" -> promptText(activity, "Rename", f.name) { newName ->
                        val clean = StorageHelper.sanitizeName(newName)
                        if (clean == null) {
                            NotificationHelper.toast(activity, "Invalid name")
                        } else {
                            val shown = if (f.isDirectory) clean else StorageHelper.ensureCExtension(clean)
                            WarningDialog.rename(activity, f.name, shown) {
                                val nf = FileManager.rename(f, newName)
                                if (nf == null) NotificationHelper.toast(activity, "Rename failed (name taken?)")
                                else onChanged(f, nf)
                                render()
                            }
                        }
                    }
                    "Duplicate" -> {
                        if (FileManager.duplicate(f) == null) NotificationHelper.toast(activity, "Duplicate failed")
                        render()
                    }
                    "Delete" -> WarningDialog.deleteFile(activity, f.name) {
                        if (FileManager.delete(f)) onChanged(f, null) else NotificationHelper.toast(activity, "Delete failed")
                        render()
                    }
                }
            }.show()
            true
        }
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                promptText(activity, "New folder", "") { name ->
                    if (FileManager.createFolder(dir, name) == null) NotificationHelper.toast(activity, "Could not create folder")
                    render()
                }
            }
        }
        render()
        dlg.show()
    }

    fun showSearchDialog(activity: Activity, onOpen: (File) -> Unit) {
        val root = StorageHelper.projectsRoot(activity)
        val et = EditText(activity).apply { hint = "File name or text inside code"; inputType = InputType.TYPE_CLASS_TEXT; setSingleLine() }
        val results = ArrayList<File>()
        val rows = ArrayList<String>()
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, rows)
        val lv = ListView(activity); lv.adapter = adapter
        val info = TextView(activity).apply { setPadding(0, activity.dp(6), 0, activity.dp(6)) }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0)
            addView(et, LinearLayout.LayoutParams(-1, -2))
            addView(info, LinearLayout.LayoutParams(-1, -2))
            addView(lv, LinearLayout.LayoutParams(-1, activity.dp(260)))
        }
        val dlg = MaterialAlertDialogBuilder(activity).setTitle("Search").setView(box)
            .setPositiveButton("Search", null).setNegativeButton("Close", null).create()
        fun doSearch() {
            results.clear(); rows.clear()
            results.addAll(FileManager.search(root, et.text.toString()))
            results.forEach { rows.add(FileManager.relative(root, it)) }
            info.text = "${results.size} result(s)"
            adapter.notifyDataSetChanged()
        }
        lv.setOnItemClickListener { _, _, pos, _ -> dlg.dismiss(); onOpen(results[pos]) }
        dlg.setOnShowListener { dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { doSearch() } }
        dlg.show()
    }

    /** Save As: file name + destination folder, with overwrite warning. */
    fun showSaveAsDialog(activity: Activity, suggestedName: String, onSave: (File) -> Unit) {
        val root = StorageHelper.projectsRoot(activity)
        val et = EditText(activity).apply {
            setText(suggestedName); setSelection(0, suggestedName.substringBeforeLast('.').length)
            inputType = InputType.TYPE_CLASS_TEXT; setSingleLine()
        }
        val spinner = Spinner(activity)
        var folders = FileManager.folders(root)
        fun fillSpinner(select: File?) {
            folders = FileManager.folders(root)
            val labels = folders.map { FileManager.relative(root, it) }
            spinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
            val idx = folders.indexOf(select).takeIf { it >= 0 } ?: 0
            spinner.setSelection(idx)
        }
        fillSpinner(root)
        val newFolder = TextView(activity).apply {
            text = "＋ New folder"
            setPadding(0, activity.dp(10), 0, activity.dp(4))
            setOnClickListener {
                promptText(activity, "New folder", "") { n ->
                    val parent = folders[spinner.selectedItemPosition]
                    val nf = FileManager.createFolder(parent, n)
                    if (nf == null) NotificationHelper.toast(activity, "Could not create folder") else fillSpinner(nf)
                }
            }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(8), activity.dp(24), 0)
            addView(et, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(activity).apply { text = "Folder"; setPadding(0, activity.dp(12), 0, 0) })
            addView(spinner, LinearLayout.LayoutParams(-1, -2))
            addView(newFolder, LinearLayout.LayoutParams(-2, -2))
        }
        val dlg = MaterialAlertDialogBuilder(activity).setTitle("Save As").setView(box)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val clean = StorageHelper.sanitizeName(et.text.toString())
                if (clean == null) {
                    et.error = "Enter a file name"
                    return@setOnClickListener
                }
                val target = File(folders[spinner.selectedItemPosition], StorageHelper.ensureCExtension(clean))
                dlg.dismiss()
                if (target.exists()) WarningDialog.overwriteFile(activity, target.name) { onSave(target) } else onSave(target)
            }
        }
        dlg.show()
    }
}
