package com.example.RealmsAI.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GuidelinesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val guidelinePaint = Paint().apply {
        color = Color.WHITE
        alpha = 120 // semi-transparent
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        alpha = 220
        textSize = 16f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
        isAntiAlias = true
    }

    // Heights (in feet) to draw guidelines at (you can make this customizable!)
    private val heightMarkers = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    var highlightedHeightFeet: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Silhouette covers from the bottom up to a "6 ft" marker
        val totalHeight = height.toFloat()
        val baseFeet = 12
        val marginTop = totalHeight * .1f  // give a bit of top margin
        val marginBottom = totalHeight * 0.02f
        val usableHeight = totalHeight - marginTop - marginBottom
        val pixelsPerFoot = usableHeight / (heightMarkers.maxOrNull()!!)

        // Draw each guideline and label
        for (feet in heightMarkers) {
            val y = totalHeight - marginBottom - (feet * pixelsPerFoot)
            // Draw the line across the screen
            canvas.drawLine(
                0f, y, width.toFloat(), y, guidelinePaint
            )
            // Draw the label at the left edge
            val label = "$feet ft"
            canvas.drawText(label, 12f * resources.displayMetrics.density, y - 8f, labelPaint)
        }
    }
}
