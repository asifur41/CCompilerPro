package com.ccompiler.pro.settings

import android.content.Context
import com.ccompiler.pro.ui.FlipLayoutView
import com.ccompiler.pro.ui.PaneLayout
import com.ccompiler.pro.ui.SplitLayoutView
import com.ccompiler.pro.utils.Prefs

object LayoutModeManager {
    const val SPLIT = 0
    const val FLIP = 1
    val names = listOf("Split View", "Flip View")

    fun mode(ctx: Context): Int = if (Prefs.int(ctx, Prefs.LAYOUT_MODE, SPLIT) == FLIP) FLIP else SPLIT

    fun create(ctx: Context, mode: Int): PaneLayout =
        if (mode == FLIP) FlipLayoutView(ctx) else SplitLayoutView(ctx)
}
