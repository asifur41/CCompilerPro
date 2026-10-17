package com.ccompiler.pro.utils

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.content.FileProvider
import com.ccompiler.pro.editor.CSyntaxHighlighter
import com.ccompiler.pro.settings.Palette
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/** "Share Code..." — text or image, optionally with the compiler output, via the native share sheet. */
object ShareHelper {

    fun show(
        activity: Activity, code: String, output: String, fileName: String,
        palette: Palette, highlight: Boolean, typeface: Typeface
    ) {
        val rbText = RadioButton(activity).apply { text = "Text"; id = 1 }
        val rbImage = RadioButton(activity).apply { text = "Image (PNG)"; id = 2 }
        val group = RadioGroup(activity).apply {
            addView(rbText); addView(rbImage)
            check(1)
        }
        val include = CheckBox(activity).apply {
            text = "Include compiler output"
            isEnabled = output.isNotBlank()
            isChecked = output.isNotBlank()
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(8), activity.dp(24), 0)
            addView(group, ViewGroup.LayoutParams(-1, -2))
            addView(include, ViewGroup.LayoutParams(-1, -2))
        }
        MaterialAlertDialogBuilder(activity).setTitle("Share Code").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Share") { _, _ ->
                val withOut = include.isChecked && output.isNotBlank()
                if (group.checkedRadioButtonId == 2) shareImage(activity, code, if (withOut) output else "", fileName, palette, highlight, typeface)
                else shareText(activity, code, if (withOut) output else "", fileName)
            }.show()
    }

    private fun shareText(activity: Activity, code: String, output: String, fileName: String) {
        val body = if (output.isBlank()) code else "$code\n\n/* ---- Output ---- */\n${output.trimEnd()}\n"
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        activity.startActivity(Intent.createChooser(i, "Share code"))
    }

    private fun shareImage(
        activity: Activity, code: String, output: String, fileName: String,
        palette: Palette, highlight: Boolean, typeface: Typeface
    ) {
        val width = 1080
        val pad = 40
        val maxHeight = 10000
        val inner = width - 2 * pad

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 30f; color = palette.text }
        val codeText = SpannableStringBuilder(code)
        if (highlight) CSyntaxHighlighter(palette).highlight(codeText)
        val codeLayout = StaticLayout.Builder.obtain(codeText, 0, codeText.length, paint, inner)
            .setLineSpacing(0f, 1.15f).build()

        val titlePaint = TextPaint(paint).apply { color = palette.accent; textSize = 32f; this.typeface = Typeface.create(typeface, Typeface.BOLD) }
        val titleH = 56

        var outLayout: StaticLayout? = null
        val outPaint = TextPaint(paint).apply { color = palette.comment; textSize = 28f }
        if (output.isNotBlank()) {
            val t = output.trimEnd()
            outLayout = StaticLayout.Builder.obtain(t, 0, t.length, outPaint, inner).setLineSpacing(0f, 1.15f).build()
        }
        val outBlock = if (outLayout != null) 70 + outLayout.height else 0
        val total = (pad * 2 + titleH + codeLayout.height + outBlock).coerceAtMost(maxHeight)

        val bmp = Bitmap.createBitmap(width, total, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(palette.editorBg)
        canvas.drawText(fileName, pad.toFloat(), (pad + 30).toFloat(), titlePaint)
        canvas.save()
        canvas.translate(pad.toFloat(), (pad + titleH).toFloat())
        codeLayout.draw(canvas)
        canvas.restore()
        if (outLayout != null) {
            val y = (pad + titleH + codeLayout.height + 20).toFloat()
            val line = TextPaint().apply { color = palette.gutter; strokeWidth = 2f }
            canvas.drawLine(pad.toFloat(), y, (width - pad).toFloat(), y, line)
            canvas.drawText("OUTPUT", pad.toFloat(), y + 36f, titlePaint.apply { textSize = 24f })
            canvas.save()
            canvas.translate(pad.toFloat(), y + 50f)
            outLayout.draw(canvas)
            canvas.restore()
        }

        val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "code_${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        if (codeLayout.height + outBlock + pad * 2 + titleH > maxHeight) {
            NotificationHelper.toast(activity, "Image was cropped to fit — share as text for long code")
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(i, "Share code image"))
    }
}
