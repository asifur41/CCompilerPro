package com.ccompiler.pro.ui

import android.view.View
import com.ccompiler.pro.settings.Palette

/** Common contract for the two layout modes (Split View / Flip View). */
interface PaneLayout {
    val view: View
    fun attach(editor: EditorView, output: OutputView)
    fun detach()
    /** Called when Run is tapped (Flip mode slides to the output pane). */
    fun onRunStarted()
    /** Returns true if the back press was consumed. */
    fun handleBack(): Boolean
    fun applyTheme(p: Palette)
}
