package com.ccompiler.pro.utils

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.widget.TextView
import com.ccompiler.pro.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object WarningDialog {

    fun show(ctx: Context, title: String, message: String, positive: String, onConfirm: () -> Unit) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_warning, null)
        v.findViewById<TextView>(R.id.warn_title).text = title
        v.findViewById<TextView>(R.id.warn_message).text = message
        MaterialAlertDialogBuilder(ctx)
            .setView(v)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positive) { _, _ ->
                try { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
                onConfirm()
            }
            .show()
    }

    fun clearEditor(ctx: Context, onConfirm: () -> Unit) =
        show(ctx, "⚠ Clear Editor", "This will erase all code. Continue?", "OK", onConfirm)

    fun discardChanges(ctx: Context, onConfirm: () -> Unit) =
        show(ctx, "⚠ Discard Changes", "Unsaved changes will be lost. Continue?", "Discard", onConfirm)

    fun deleteFile(ctx: Context, name: String, onConfirm: () -> Unit) =
        show(ctx, "⚠ Delete", "Delete '$name'? Cannot be undone.", "Delete", onConfirm)

    fun overwriteFile(ctx: Context, name: String, onConfirm: () -> Unit) =
        show(ctx, "⚠ Overwrite", "'$name' exists. Overwrite?", "Overwrite", onConfirm)

    fun exitWithoutSave(ctx: Context, onConfirm: () -> Unit) =
        show(ctx, "⚠ Exit", "Unsaved changes. Exit anyway?", "Exit", onConfirm)

    fun rename(ctx: Context, from: String, to: String, onConfirm: () -> Unit) =
        show(ctx, "Rename", "Rename '$from' to '$to'?", "Rename", onConfirm)
}
