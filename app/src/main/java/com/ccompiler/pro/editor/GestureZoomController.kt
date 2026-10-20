package com.ccompiler.pro.editor

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Two-finger pinch zoom. Text size is clamped to 12..28 sp (shown as a percentage of the 14 sp baseline).
 * While pinching, the size follows the distance between the fingers directly:
 *     size = size at the start x (distance now / distance at the start)
 * and snaps to whole sp, so what you see while pinching is exactly what gets saved when you let go.
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
    private var startSize = BASE_SP
    private var startSpan = 1f
    private var applied = BASE_SP

    private val detector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            if (!enabled) return false
            startSize = getSize()
            startSpan = d.currentSpan.coerceAtLeast(1f)
            applied = startSize
            return true
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (!enabled) return false
            val wanted = Math.round(startSize * d.currentSpan / startSpan).toFloat().coerceIn(MIN_SP, MAX_SP)
            if (wanted != applied) {          // only re-layout the text when the size really changes
                applied = wanted
                onSize(wanted, false)
            }
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            onSize(applied.coerceIn(MIN_SP, MAX_SP), true)
        }
    })

    /** Feed every touch event of the editor area. Returns true while a pinch is in progress. */
    fun observe(ev: MotionEvent): Boolean {
        if (!enabled) return false
        detector.onTouchEvent(ev)
        return detector.isInProgress
    }
}
