package com.example.translator.ui.image

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.translator.R

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, R.color.primary_color)
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(100, 33, 150, 243) // Semi-transparent blue
    }

    private val overlayPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(120, 0, 0, 0) // Semi-transparent black
    }

    private var cropRect = RectF()
    private var isDragging = false
    private var dragHandle = DragHandle.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val handleRadius = 30f
    private val minCropSize = 100f

    enum class DragHandle {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Initialize crop rect to center 70% of the view
        val margin = minOf(w, h) * 0.15f
        cropRect.set(
            margin,
            margin,
            w - margin,
            h - margin
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Draw overlay outside crop area
        canvas.drawRect(0f, 0f, w, cropRect.top, overlayPaint)
        canvas.drawRect(0f, cropRect.bottom, w, h, overlayPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, overlayPaint)

        // Draw crop rectangle
        canvas.drawRect(cropRect, paint)

        // Draw corner handles
        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)

        // Draw grid lines
        drawGridLines(canvas)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, fillPaint)
        canvas.drawCircle(x, y, handleRadius, paint)
    }

    private fun drawGridLines(canvas: Canvas) {
        val thirdWidth = cropRect.width() / 3
        val thirdHeight = cropRect.height() / 3

        // Vertical lines
        canvas.drawLine(
            cropRect.left + thirdWidth, cropRect.top,
            cropRect.left + thirdWidth, cropRect.bottom,
            paint
        )
        canvas.drawLine(
            cropRect.left + 2 * thirdWidth, cropRect.top,
            cropRect.left + 2 * thirdWidth, cropRect.bottom,
            paint
        )

        // Horizontal lines
        canvas.drawLine(
            cropRect.left, cropRect.top + thirdHeight,
            cropRect.right, cropRect.top + thirdHeight,
            paint
        )
        canvas.drawLine(
            cropRect.left, cropRect.top + 2 * thirdHeight,
            cropRect.right, cropRect.top + 2 * thirdHeight,
            paint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragHandle = getHandleAt(event.x, event.y)
                isDragging = dragHandle != DragHandle.NONE
                return isDragging
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY

                    when (dragHandle) {
                        DragHandle.TOP_LEFT -> {
                            cropRect.left = (cropRect.left + deltaX).coerceAtMost(cropRect.right - minCropSize)
                            cropRect.top = (cropRect.top + deltaY).coerceAtMost(cropRect.bottom - minCropSize)
                        }
                        DragHandle.TOP_RIGHT -> {
                            cropRect.right = (cropRect.right + deltaX).coerceAtLeast(cropRect.left + minCropSize)
                            cropRect.top = (cropRect.top + deltaY).coerceAtMost(cropRect.bottom - minCropSize)
                        }
                        DragHandle.BOTTOM_LEFT -> {
                            cropRect.left = (cropRect.left + deltaX).coerceAtMost(cropRect.right - minCropSize)
                            cropRect.bottom = (cropRect.bottom + deltaY).coerceAtLeast(cropRect.top + minCropSize)
                        }
                        DragHandle.BOTTOM_RIGHT -> {
                            cropRect.right = (cropRect.right + deltaX).coerceAtLeast(cropRect.left + minCropSize)
                            cropRect.bottom = (cropRect.bottom + deltaY).coerceAtLeast(cropRect.top + minCropSize)
                        }
                        DragHandle.CENTER -> {
                            val newLeft = cropRect.left + deltaX
                            val newTop = cropRect.top + deltaY
                            val newRight = cropRect.right + deltaX
                            val newBottom = cropRect.bottom + deltaY

                            // Keep crop rect within bounds
                            if (newLeft >= 0 && newRight <= width && newTop >= 0 && newBottom <= height) {
                                cropRect.offset(deltaX, deltaY)
                            }
                        }
                        else -> { /* Do nothing */ }
                    }

                    // Keep crop rect within view bounds
                    cropRect.left = cropRect.left.coerceAtLeast(0f)
                    cropRect.top = cropRect.top.coerceAtLeast(0f)
                    cropRect.right = cropRect.right.coerceAtMost(width.toFloat())
                    cropRect.bottom = cropRect.bottom.coerceAtMost(height.toFloat())

                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                dragHandle = DragHandle.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getHandleAt(x: Float, y: Float): DragHandle {
        val tolerance = handleRadius * 1.5f

        // Check corner handles first
        if (isPointNear(x, y, cropRect.left, cropRect.top, tolerance)) {
            return DragHandle.TOP_LEFT
        }
        if (isPointNear(x, y, cropRect.right, cropRect.top, tolerance)) {
            return DragHandle.TOP_RIGHT
        }
        if (isPointNear(x, y, cropRect.left, cropRect.bottom, tolerance)) {
            return DragHandle.BOTTOM_LEFT
        }
        if (isPointNear(x, y, cropRect.right, cropRect.bottom, tolerance)) {
            return DragHandle.BOTTOM_RIGHT
        }

        // Check if touch is inside crop area for moving
        if (cropRect.contains(x, y)) {
            return DragHandle.CENTER
        }

        return DragHandle.NONE
    }

    private fun isPointNear(x: Float, y: Float, targetX: Float, targetY: Float, tolerance: Float): Boolean {
        val dx = x - targetX
        val dy = y - targetY
        return dx * dx + dy * dy <= tolerance * tolerance
    }

    fun getCropRect(): RectF {
        return RectF(cropRect)
    }

    fun setCropRect(rect: RectF) {
        cropRect.set(rect)
        invalidate()
    }
}