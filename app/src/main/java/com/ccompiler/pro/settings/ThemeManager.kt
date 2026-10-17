package com.ccompiler.pro.settings

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import com.ccompiler.pro.utils.Prefs

data class Palette(
    val name: String,
    val bg: Int,
    val text: Int,
    val editorBg: Int,
    val accent: Int,
    val keyword: Int,
    val string: Int,
    val comment: Int,
    val number: Int,
    val type: Int,
    val preproc: Int,
    val gutter: Int,
    val isLight: Boolean
)

object ThemeManager {
    val names = listOf("Day", "Night", "Bluish", "Greenish", "Reddish")

    private fun c(s: String) = Color.parseColor(s)

    private val palettes = listOf(
        Palette("Day", c("#FFFFFF"), c("#000000"), c("#FAFAFA"), c("#6200DD"), c("#0000FF"), c("#006000"), c("#008000"),
            c("#098658"), c("#267F99"), c("#AF00DB"), c("#999999"), true),
        Palette("Night", c("#1E1E1E"), c("#E0E0E0"), c("#2D2D2D"), c("#BB86FC"), c("#569CD6"), c("#CE9178"), c("#6A9955"),
            c("#B5CEA8"), c("#4EC9B0"), c("#C586C0"), c("#858585"), false),
        Palette("Bluish", c("#0A1628"), c("#E6F2FF"), c("#0F2340"), c("#2196F3"), c("#7EC8FF"), c("#98FF98"), c("#4A84AA"),
            c("#FFD479"), c("#5FD3D3"), c("#C79CFF"), c("#4A84AA"), false),
        Palette("Greenish", c("#0D1A0D"), c("#E6FFE6"), c("#112211"), c("#4CAF50"), c("#9CFF9C"), c("#FFBFFF"), c("#6AA857"),
            c("#FFE08A"), c("#7FE0C0"), c("#E0A8FF"), c("#6AA857"), false),
        Palette("Reddish", c("#1A0A0A"), c("#FFE6E6"), c("#221111"), c("#F44336"), c("#FF9C9C"), c("#FFFF98"), c("#8A5757"),
            c("#A8E6A1"), c("#FFB38A"), c("#FFA8D8"), c("#8A5757"), false)
    )

    fun index(ctx: Context): Int = Prefs.int(ctx, Prefs.THEME, 0).coerceIn(0, palettes.size - 1)

    fun palette(ctx: Context): Palette = palettes[index(ctx)]

    /** Day = light system mode, everything else = dark, so dialogs and widgets match. */
    fun applyNightMode(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (palette(ctx).isLight) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        )
    }
}
