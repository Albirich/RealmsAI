package com.example.RealmsAI

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

class StarfieldView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Star(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speed: Float,
        var offset: Float
    )

    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val stars = mutableListOf<Star>()
    private val starCount = 100
    private var time = 0f

    // Animator to drive the twinkling smoothly
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000 // 3 seconds per cycle (infinite loop)
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time += 0.05f // Increment time for math
            invalidate()  // Redraw the view
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        createStars(w, h)
        if (!animator.isRunning) animator.start()
    }

    private fun createStars(w: Int, h: Int) {
        stars.clear()
        for (i in 0 until starCount) {
            stars.add(
                Star(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    radius = Random.nextFloat() * 4f + 1f, // Size 1px to 5px
                    speed = Random.nextFloat() * 0.1f + 0.2f, // Twinkle speed
                    offset = Random.nextFloat() * 100f // Random starting phase
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Fill background with deep space color (or rely on XML background)
        // drawing transparently allows layering behind other images

        for (star in stars) {
            // Math to make stars twinkle (Sine wave based on time + offset)
            val brightness = (sin(time * star.speed + star.offset) + 1f) / 2f
            // Map brightness (0.0-1.0) to Alpha (0-255). Min opacity 50 so they don't vanish fully.
            val alpha = (brightness * 205 + 50).toInt()

            paint.alpha = alpha
            canvas.drawCircle(star.x, star.y, star.radius, paint)
        }
    }

    // Clean up animation to save battery when screen closes
    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}