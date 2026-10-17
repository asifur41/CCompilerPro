package com.ccompiler.pro.editor

import android.os.SystemClock
import android.widget.EditText

/** Snapshot-based history (max 100 levels). Rapid typing within 800ms is coalesced into one step. */
class UndoRedoManager(private val edit: EditText, private val maxLevels: Int = 100) {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var current = ""
    private var lastChange = 0L
    private var applying = false

    fun reset(text: String) {
        undoStack.clear()
        redoStack.clear()
        current = text
        lastChange = 0L
    }

    fun onTextChanged(text: String) {
        if (applying || text == current) return
        val now = SystemClock.uptimeMillis()
        if (undoStack.isEmpty() || now - lastChange > 800) {
            undoStack.addLast(current)
            while (undoStack.size > maxLevels) undoStack.removeFirst()
        }
        lastChange = now
        current = text
        redoStack.clear()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        redoStack.addLast(current)
        apply(undoStack.removeLast())
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        undoStack.addLast(current)
        apply(redoStack.removeLast())
        return true
    }

    private fun apply(text: String) {
        val old = edit.text?.toString() ?: ""
        var p = 0
        val max = minOf(old.length, text.length)
        while (p < max && old[p] == text[p]) p++
        var s = 0
        while (s < max - p && old[old.length - 1 - s] == text[text.length - 1 - s]) s++
        applying = true
        edit.setText(text)
        edit.setSelection((text.length - s).coerceIn(0, text.length))
        applying = false
        current = text
        lastChange = 0L
    }
}
