package com.kroegerama.kaiteki.bcode.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Size
import android.view.View
import com.kroegerama.kaiteki.bcode.dpToPxF
import kotlin.properties.Delegates

interface ScannerOverlay {
    val size: Size
    val scanRect: RectF
}

class CustomViewFinder @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), ScannerOverlay {

    companion object {
        private const val FRAME_HEIGHT_PERCENT = .40f
        private const val FRAME_WIDTH_PERCENT = .80f
    }

    private val scrimPaint: Paint = Paint().apply {
        color = Color.parseColor("#99000000")
        isAntiAlias = true
    }

    private val eraserPaint: Paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private var frameRect: RectF by Delegates.notNull()

    override val size: Size
        get() = Size(width, height)

    override val scanRect: RectF
        get() = frameRect

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas?) {
        if (canvas != null) {
            calculateFrameRect()
            drawBackground(canvas, frameRect)
        }
    }

    private fun calculateFrameRect() {
        val frameHeight = height * FRAME_HEIGHT_PERCENT
        val frameWidth = width * FRAME_WIDTH_PERCENT

        val startX = (width - frameWidth) / 2
        val startY = (height - frameHeight) / 2
        val endX = startX + frameWidth
        val endY = startY + frameHeight

        frameRect = RectF(startX, startY, endX, endY)
    }

    private fun drawBackground(canvas: Canvas, frameRect: RectF) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        eraserPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(frameRect, 15.dpToPxF, 15.dpToPxF, eraserPaint)

        eraserPaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(frameRect, 15.dpToPxF, 15.dpToPxF, eraserPaint)
    }
}
