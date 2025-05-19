package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.RealmsAI.views.GuidelinesView
import java.io.File
import java.io.FileOutputStream

class CropperActivity : AppCompatActivity() {
    private lateinit var userImage: ImageView

    // Matrix and gesture
    private val imageMatrix = Matrix()
    private val savedMatrix = Matrix()
    private var mode = NONE
    private var startX = 0f
    private var startY = 0f
    private var oldDist = 1f
    private var mid = PointF()

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cropper)
        userImage = findViewById(R.id.userImage)

        // Set up guidelines
        val charHeight = intent.getFloatExtra("CHARACTER_HEIGHT_FEET", 6.0f)
        val guidelinesView = findViewById<GuidelinesView>(R.id.guidelinesOverlay)
        guidelinesView.highlightedHeightFeet = charHeight

        // Load image
        val imageUri = intent.getParcelableExtra<Uri>("EXTRA_IMAGE_URI")
        if (imageUri != null) {
            userImage.setImageURI(imageUri)
        } else {
            userImage.setImageResource(R.drawable.default_01)
        }

        userImage.scaleType = ImageView.ScaleType.MATRIX
        userImage.imageMatrix = imageMatrix

        // Save/crop button
        findViewById<Button>(R.id.saveCropButton).setOnClickListener {
            val croppedBitmap = getCroppedBitmap()
            val croppedUri = saveBitmapToCache(croppedBitmap)
            val resultIntent = Intent().apply {
                putExtra("CROPPED_IMAGE_URI", croppedUri)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        // Gesture handling (pinch to zoom & pan)
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                imageMatrix.postScale(scale, scale, detector.focusX, detector.focusY)
                userImage.imageMatrix = imageMatrix
                return true
            }
        })

        userImage.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(imageMatrix)
                    startX = event.x
                    startY = event.y
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(imageMatrix)
                        mid = midPoint(event)
                        mode = ZOOM
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        imageMatrix.set(savedMatrix)
                        val dx = event.x - startX
                        val dy = event.y - startY
                        imageMatrix.postTranslate(dx, dy)
                    } else if (mode == ZOOM && event.pointerCount >= 2) {
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            imageMatrix.set(savedMatrix)
                            val scale = newDist / oldDist
                            imageMatrix.postScale(scale, scale, mid.x, mid.y)
                        }
                    }
                    userImage.imageMatrix = imageMatrix
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                }
            }
            true
        }
    }

    /** Helper: Get the spacing between two fingers */
    private fun spacing(event: MotionEvent): Float {
        return if (event.pointerCount < 2) 0f else {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            kotlin.math.sqrt(x * x + y * y)
        }
    }

    /** Helper: Get the midpoint between two fingers */
    private fun midPoint(event: MotionEvent): PointF {
        return if (event.pointerCount >= 2) {
            PointF(
                (event.getX(0) + event.getX(1)) / 2f,
                (event.getY(0) + event.getY(1)) / 2f
            )
        } else {
            PointF()
        }
    }

    /** Crop visible region of the ImageView */
    private fun getCroppedBitmap(): Bitmap {
        val drawable = userImage.drawable as BitmapDrawable
        val originalBitmap = drawable.bitmap

        val matrix = userImage.imageMatrix
        val values = FloatArray(9)
        matrix.getValues(values)

        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        // Get crop rectangle in VIEW coordinates
        val viewWidth = userImage.width.toFloat()
        val viewHeight = userImage.height.toFloat()

        // LEFT: verticalGuide33 at 33% of width
        val cropLeftView = viewWidth * 0.33f
        // TOP: horizontalGuide8ft at 15% of height
        val cropTopView = viewHeight * 0.15f

        // RIGHT/BOTTOM: the bottom/right of the image (optionally minus message/chat overlays if needed)
        val cropRightView = viewWidth
        val cropBottomView = viewHeight // Or (viewHeight - chatBoxHeight) if you want

        // Convert to BITMAP coordinates using the image matrix
        fun toBitmapX(viewX: Float): Float = (viewX - transX) / scaleX
        fun toBitmapY(viewY: Float): Float = (viewY - transY) / scaleY

        val leftBitmap   = toBitmapX(cropLeftView).toInt().coerceIn(0, originalBitmap.width - 1)
        val topBitmap    = toBitmapY(cropTopView).toInt().coerceIn(0, originalBitmap.height - 1)
        val rightBitmap  = toBitmapX(cropRightView).toInt().coerceIn(leftBitmap+1, originalBitmap.width)
        val bottomBitmap = toBitmapY(cropBottomView).toInt().coerceIn(topBitmap+1, originalBitmap.height)

        val widthBitmap = rightBitmap - leftBitmap
        val heightBitmap = bottomBitmap - topBitmap

        return Bitmap.createBitmap(
            originalBitmap,
            leftBitmap,
            topBitmap,
            widthBitmap,
            heightBitmap
        )
    }


    /** Save bitmap to cache and return Uri */
    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val file = File(cacheDir, "cropped_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return Uri.fromFile(file)
    }
}
