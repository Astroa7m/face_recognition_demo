package com.astroscoding.facedetectiondemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View





class FaceOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var faceRect: RectF? = null
    private val strokePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    fun setFaceRect(rectF: RectF?) {
        faceRect = rectF?.let {
            // Adjust coordinates to match canvas coordinate system
            RectF(
                it.left * width,
                it.top * height,
                it.right * width,
                it.bottom * height
            )
        }
        invalidate() // force a redraw
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        faceRect?.let {
            canvas?.drawRoundRect(it, 16f, 16f, strokePaint)
        }
    }
}
