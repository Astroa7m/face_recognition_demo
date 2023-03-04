package com.astroscoding.facedetectiondemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class FaceSquareOverlay constructor(
    context: Context,
    attributes: AttributeSet,
) : View(context, attributes) {



    var rect: Rect? = Rect()
        set(value) {
            field = value
            invalidate()
        }
    private val rectPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.YELLOW
    }

    init {
        /*val typedArray = this.context.obtainStyledAttributes(
            attributes,
            R.styleable.FaceSquareOverlay
        )
        squareLeft = typedArray.getInt(R.styleable.FaceSquareOverlay_square_rect_left, 0)
        squareRight = typedArray.getInt(R.styleable.FaceSquareOverlay_square_rect_right, 0)
        squareTop = typedArray.getInt(R.styleable.FaceSquareOverlay_square_rect_top, 0)
        squareBottom = typedArray.getInt(R.styleable.FaceSquareOverlay_square_rect_bottom, 0)
        rect = Rect(left, top, right, bottom)
        typedArray.recycle()*/
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        rect?.let {
            canvas?.drawRect(
                it,
                rectPaint
            )
        }
    }


}