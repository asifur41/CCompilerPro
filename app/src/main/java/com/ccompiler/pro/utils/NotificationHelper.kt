package com.ccompiler.pro.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

object NotificationHelper {
    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    fun snack(view: View, msg: String) {
        Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
    }
}
