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

private const val OUTPUT_WIDTH = 750
private const val OUTPUT_HEIGHT = 1805
private const val FEET_RANGE = 8.5f // 0 ft at bottom, 8'6" at top
private const val PX_PER_FOOT = OUTPUT_HEIGHT / FEET_RANGE // ≈ 212.35 px/ft

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
            val guidelinesView = findViewById<GuidelinesView>(R.id.guidelinesOverlay)
            guidelinesView.post {
                val croppedBitmap = cropVisibleGuidelinesRegion()
                val croppedUri = saveBitmapToCache(croppedBitmap)
                val resultIntent = Intent().apply {
                    putExtra("CROPPED_IMAGE_URI", croppedUri)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
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

    private fun cropVisibleGuidelinesRegion(): Bitmap {
        // Output: always 750x1805
        val OUTPUT_WIDTH = 750
        val OUTPUT_HEIGHT = 1805

        // 1. Blank output canvas
        val outputBitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawARGB(0, 0, 0, 0)

        // 2. Guidelines area in preview
        val guidelinesView = findViewById<GuidelinesView>(R.id.guidelinesOverlay)
        val previewW = guidelinesView.width.toFloat()
        val previewH = guidelinesView.height.toFloat()
        val marginTop = previewH * 0.1f
        val marginBottom = previewH * 0.02f
        val usableH = previewH - marginTop - marginBottom
        val leftPx = previewW * 0.155f
        val rightPx = previewW * 0.845f
        val usableW = rightPx - leftPx

        val srcRect = android.graphics.RectF(
            leftPx,
            marginTop,
            rightPx,
            marginTop + usableH
        )
        val dstRect = android.graphics.RectF(
            0f,
            0f,
            OUTPUT_WIDTH.toFloat(),
            OUTPUT_HEIGHT.toFloat()
        )
        val boxToOutputMatrix = Matrix()
        boxToOutputMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)

        // 3. Combine with user pan/zoom matrix
        val userMatrix = userImage.imageMatrix
        val finalMatrix = Matrix()
        finalMatrix.set(userMatrix)
        finalMatrix.postConcat(boxToOutputMatrix)

        // 4. Draw user image with correct transform
        val drawable = userImage.drawable as BitmapDrawable
        val originalBitmap = drawable.bitmap

        canvas.setMatrix(finalMatrix)
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        // 5. Done! Output is always 750x1805, chart exactly fills output.
        return outputBitmap
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
