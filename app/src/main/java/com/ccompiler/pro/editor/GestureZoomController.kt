package com.ccompiler.pro.editor

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Two-finger pinch zoom. Text size is clamped to 12..28 sp (shown as a percentage of the 14 sp
 * baseline, i.e. ~86%..200%). The change is smoothed while pinching and persisted when it ends.
 */
class GestureZoomController(
    ctx: Context,
    private val getSize: () -> Float,
    private val onSize: (size: Float, finished: Boolean) -> Unit
) {
    companion object {
        const val MIN_SP = 12f
        const val MAX_SP = 28f
        const val BASE_SP = 14f
    }

    var enabled = true
    private var smoothed = 14f

    private val detector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            smoothed = getSize()
            return enabled
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (!enabled) return false
            val target = (smoothed * d.scaleFactor).coerceIn(MIN_SP, MAX_SP)
            smoothed += (target - smoothed) * 0.6f
            onSize(smoothed, false)
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            onSize(smoothed.coerceIn(MIN_SP, MAX_SP), true)
        }
    })

    /** Feed every touch event; returns true while a pinch is in progress. */
    fun onTouch(ev: MotionEvent): Boolean {
        if (!enabled) return false
        detector.onTouchEvent(ev)
        return detector.isInProgress
    }
}
