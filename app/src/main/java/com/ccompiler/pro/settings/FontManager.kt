package com.ccompiler.pro.settings

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.ccompiler.pro.utils.Prefs

object FontManager {
    val names = listOf("Roboto Mono", "Fira Code", "JetBrains Mono", "Courier Prime")
    private val resNames = listOf("roboto_mono", "fira_code", "jetbrains_mono", "courier_prime")

    fun index(ctx: Context): Int = Prefs.int(ctx, Prefs.FONT, 0).coerceIn(0, names.size - 1)

    /** Loads the bundled font from res/font; falls back to system monospace if the file was not bundled. */
    fun typeface(ctx: Context): Typeface {
        val id = ctx.resources.getIdentifier(resNames[index(ctx)], "font", ctx.packageName)
        if (id != 0) {
            try {
                ResourcesCompat.getFont(ctx, id)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return Typeface.MONOSPACE
    }
}
