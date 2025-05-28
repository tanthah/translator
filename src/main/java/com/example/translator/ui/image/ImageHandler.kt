package com.example.translator.ui.image

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ImageHandler(private val activity: ImageTranslationBaseActivity) {

    private var selectedImageBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null

    // Touch handling for zoom and pan
    private val scaleGestureDetector by lazy {
        ScaleGestureDetector(activity, ScaleListener())
    }
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Image transformation matrix
    private val imageMatrix = Matrix()
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f

    companion object {
        private const val TAG = "ImageHandler"
        private const val REQUEST_IMAGE_GALLERY = 1001
        private const val REQUEST_IMAGE_CAMERA = 1002
        private const val CAMERA_PERMISSION_CODE = 100
        private const val STORAGE_PERMISSION_CODE = 101
    }

    fun selectFromGallery() {
        if (checkStoragePermission()) {
            openGallery()
        } else {
            requestStoragePermission()
        }
    }

    fun openCamera() {
        if (checkCameraPermission()) {
            openCameraIntent()
        } else {
            requestCameraPermission()
        }
    }

    fun confirmCrop() {
        selectedImageBitmap?.let { bitmap ->
            try {
                val cropRect = activity.cropOverlay.getCropRect()
                croppedBitmap = cropBitmap(bitmap, cropRect)
                Toast.makeText(activity, "Area selected. Tap translate to process.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error cropping image", e)
                Toast.makeText(activity, "Failed to crop image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getBitmapToProcess(): Bitmap? {
        return croppedBitmap ?: selectedImageBitmap
    }

    fun setupImageTouchListeners() {
        activity.ivSelectedImage.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) {
                        val deltaX = event.x - lastTouchX
                        val deltaY = event.y - lastTouchY

                        translateX += deltaX
                        translateY += deltaY

                        updateImageMatrix()

                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    true
                }
                else -> false
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f) // Increased max zoom for better text visibility

            updateImageMatrix()
            return true
        }
    }

    private fun updateImageMatrix() {
        imageMatrix.reset()
        imageMatrix.postScale(scaleFactor, scaleFactor)
        imageMatrix.postTranslate(translateX, translateY)
        activity.ivSelectedImage.imageMatrix = imageMatrix
    }

    fun showImagePreviewMode(bitmap: Bitmap) {
        activity.uiHandler.showImagePreviewMode()

        selectedImageBitmap = bitmap

        // Improve image quality for better text recognition
        val enhancedBitmap = enhanceImageForTextRecognition(bitmap)
        selectedImageBitmap = enhancedBitmap

        // Set image and fit to ImageView
        activity.ivSelectedImage.setImageBitmap(enhancedBitmap)
        fitImageToView(enhancedBitmap)

        // Show crop overlay
        activity.cropOverlay.visibility = android.view.View.VISIBLE
        activity.btnTranslate.isEnabled = true
    }

    private fun enhanceImageForTextRecognition(bitmap: Bitmap): Bitmap {
        try {
            Log.d(TAG, "Enhancing image for text recognition")

            // Create a mutable copy of the bitmap
            val config = if (bitmap.config != null) bitmap.config else Bitmap.Config.ARGB_8888
            val enhancedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)

            val canvas = Canvas(enhancedBitmap)
            val paint = Paint()

            // Apply contrast and brightness adjustments
            val colorMatrix = ColorMatrix()

            // Increase contrast and brightness for better OCR
            val contrast = 1.3f
            val brightness = 15f

            colorMatrix.setScale(contrast, contrast, contrast, 1f)

            val brightnessMatrix = ColorMatrix()
            brightnessMatrix.set(floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))

            colorMatrix.postConcat(brightnessMatrix)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            // If the image is too small, scale it up for better recognition
            val minDimension = 800
            val currentMinDimension = minOf(enhancedBitmap.width, enhancedBitmap.height)

            return if (currentMinDimension < minDimension) {
                val scaleFactorUp = minDimension.toFloat() / currentMinDimension
                val newWidth = (enhancedBitmap.width * scaleFactorUp).toInt()
                val newHeight = (enhancedBitmap.height * scaleFactorUp).toInt()

                Bitmap.createScaledBitmap(enhancedBitmap, newWidth, newHeight, true)
            } else {
                enhancedBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing image", e)
            return bitmap
        }
    }

    private fun fitImageToView(bitmap: Bitmap) {
        val imageView = activity.ivSelectedImage
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        if (viewWidth == 0f || viewHeight == 0f) {
            imageView.post {
                fitImageToView(bitmap)
            }
            return
        }

        val scaleX = viewWidth / bitmapWidth
        val scaleY = viewHeight / bitmapHeight
        scaleFactor = minOf(scaleX, scaleY)

        translateX = (viewWidth - bitmapWidth * scaleFactor) / 2
        translateY = (viewHeight - bitmapHeight * scaleFactor) / 2

        updateImageMatrix()
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            activity.startActivityForResult(intent, REQUEST_IMAGE_GALLERY)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery", e)
            Toast.makeText(activity, "Failed to open gallery", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCameraIntent() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivityForResult(intent, REQUEST_IMAGE_CAMERA)
            } else {
                Toast.makeText(activity, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            Toast.makeText(activity, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cropBitmap(bitmap: Bitmap, cropRect: RectF): Bitmap? {
        return try {
            val values = FloatArray(9)
            imageMatrix.getValues(values)

            val scaleX = values[Matrix.MSCALE_X]
            val scaleY = values[Matrix.MSCALE_Y]
            val transX = values[Matrix.MTRANS_X]
            val transY = values[Matrix.MTRANS_Y]

            val x = ((cropRect.left - transX) / scaleX).toInt().coerceAtLeast(0)
            val y = ((cropRect.top - transY) / scaleY).toInt().coerceAtLeast(0)
            val width = (cropRect.width() / scaleX).toInt().coerceAtMost(bitmap.width - x)
            val height = (cropRect.height() / scaleY).toInt().coerceAtMost(bitmap.height - y)

            if (width > 0 && height > 0) {
                Bitmap.createBitmap(bitmap, x, y, width, height)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating cropped bitmap", e)
            bitmap
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_GALLERY -> {
                    data?.data?.let { uri ->
                        try {
                            val bitmap = loadBitmapFromUri(uri)
                            bitmap?.let {
                                showImagePreviewMode(it)
                            } ?: run {
                                Toast.makeText(activity, "Failed to load image", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading image from gallery", e)
                            Toast.makeText(activity, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                REQUEST_IMAGE_CAMERA -> {
                    try {
                        val bitmap = data?.extras?.get("data") as? Bitmap
                        bitmap?.let {
                            val enhancedBitmap = enhanceImageForTextRecognition(it)
                            showImagePreviewMode(enhancedBitmap)
                        } ?: run {
                            Toast.makeText(activity, "Failed to capture image", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing camera result", e)
                        Toast.makeText(activity, "Failed to process camera image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)

                options.inSampleSize = calculateInSampleSize(options, 1200, 1200)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888

                activity.contentResolver.openInputStream(uri)?.use { newInputStream ->
                    android.graphics.BitmapFactory.decodeStream(newInputStream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCameraIntent()
                } else {
                    Toast.makeText(activity, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(activity, "Storage permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun resetImageState() {
        selectedImageBitmap = null
        croppedBitmap = null
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        imageMatrix.reset()
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), STORAGE_PERMISSION_CODE)
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
        }
    }
}