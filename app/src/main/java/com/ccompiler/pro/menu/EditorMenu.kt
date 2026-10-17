package com.ccompiler.pro.menu

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.ccompiler.pro.ui.EditorView
import com.ccompiler.pro.utils.NotificationHelper
import com.ccompiler.pro.utils.WarningDialog
import com.ccompiler.pro.utils.copyToClipboard
import com.ccompiler.pro.utils.dp
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Editor overflow menu (⋮) and the Find / Replace bottom sheet. */
object EditorMenu {

    fun show(activity: Activity, anchor: View, editor: EditorView) {
        val pm = PopupMenu(activity, anchor)
        pm.menu.add(0, 1, 0, "Select all")
        pm.menu.add(0, 2, 1, "Copy all")
        pm.menu.add(0, 3, 2, "Paste")
        pm.menu.add(0, 4, 3, "Go to line…")
        pm.menu.add(0, 5, 4, "Find / Replace")
        pm.menu.add(0, 6, 5, "Format code")
        pm.menu.add(0, 7, 6, "Clear editor")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> editor.selectAll()
                2 -> { copyToClipboard(activity, "code", editor.getText()); NotificationHelper.toast(activity, "Code copied") }
                3 -> {
                    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()
                    if (!t.isNullOrEmpty()) editor.insert(t)
                }
                4 -> goToLine(activity, editor)
                5 -> showFindReplace(activity, editor)
                6 -> editor.format()
                7 -> WarningDialog.clearEditor(activity) { editor.clearToDefault() }
            }
            true
        }
        pm.show()
    }

    private fun goToLine(activity: Activity, editor: EditorView) {
        val et = EditText(activity).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "Line number" }
        val box = LinearLayout(activity).apply {
            setPadding(activity.dp(24), activity.dp(8), activity.dp(24), 0)
            addView(et, LinearLayout.LayoutParams(-1, -2))
        }
        MaterialAlertDialogBuilder(activity).setTitle("Go to line").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Go") { _, _ -> et.text.toString().toIntOrNull()?.let { editor.goToLine(it) } }
            .show()
    }

    fun showFindReplace(activity: Activity, editor: EditorView) {
        val dialog = BottomSheetDialog(activity)
        val find = EditText(activity).apply { hint = "Find"; inputType = InputType.TYPE_CLASS_TEXT; setSingleLine() }
        val repl = EditText(activity).apply { hint = "Replace with"; inputType = InputType.TYPE_CLASS_TEXT; setSingleLine() }
        val regex = CheckBox(activity).apply { text = "Regex" }
        val matchCase = CheckBox(activity).apply { text = "Match case" }
        val status = TextView(activity).apply { setPadding(0, activity.dp(6), 0, activity.dp(6)) }

        fun report(r: Int, okMsg: String = "") {
            status.text = when (r) {
                -1 -> "Invalid regular expression"
                0 -> "No match"
                else -> okMsg
            }
        }
        fun btn(label: String, action: () -> Unit) = MaterialButton(activity).apply {
            text = label
            setOnClickListener { action() }
        }
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btn("Next") { report(editor.findNext(find.text.toString(), regex.isChecked, matchCase.isChecked)) },
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(btn("Replace") { report(editor.replaceCurrent(find.text.toString(), repl.text.toString(), regex.isChecked, matchCase.isChecked)) },
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(btn("All") {
                val n = editor.replaceAll(find.text.toString(), repl.text.toString(), regex.isChecked, matchCase.isChecked)
                report(n, "Replaced $n occurrence(s)")
                if (n > 0) status.text = "Replaced $n occurrence(s)"
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val opts = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(regex, LinearLayout.LayoutParams(0, -2, 1f))
            addView(matchCase, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
            addView(find); addView(repl); addView(opts); addView(buttons); addView(status)
        }
        dialog.setContentView(box)
        dialog.window?.setDimAmount(0f)
        dialog.show()
    }
}
