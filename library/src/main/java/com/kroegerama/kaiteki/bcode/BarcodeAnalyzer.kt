package com.kroegerama.kaiteki.bcode

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.kroegerama.kaiteki.bcode.views.ScannerOverlay

internal interface ResultListener {
    fun onResult(result: Result, imageWidth: Int, imageHeight: Int, imageRotation: Int)
    fun onNoResult()
}

internal class BarcodeAnalyzer(
    private val listener: ResultListener,
    private val reader: MultiFormatReader,
    private val scannerOverlay: ScannerOverlay
) : ImageAnalysis.Analyzer {

    var enabled = true
    var inverted = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!enabled) return

        //YUV_420 is normally the input type here, but other YUV types are also supported in theory
        if (ImageFormat.YUV_420_888 != imageProxy.format && ImageFormat.YUV_422_888 != imageProxy.format && ImageFormat.YUV_444_888 != imageProxy.format) {
            Log.e(TAG, "Unexpected format: ${imageProxy.format}")
            listener.onNoResult()
            return
        }
        val byteBuffer = imageProxy.image?.planes?.firstOrNull()?.buffer
        if (byteBuffer == null) {
            listener.onNoResult()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val scannerRect = getScannerRectToPreviewViewRelation(Size(imageProxy.width, imageProxy.height), rotation)

        val image = imageProxy.image ?: return

        val cropRect = image.getCropRectAccordingToRotation(scannerRect, rotation)
        image.cropRect = cropRect

        val data = YuvNV21Util.yuv420toNV21(image)

        val width = cropRect.width()
        val height = cropRect.height()

        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false).let {
            if (inverted) it.invert() else it
        }
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decodeWithState(bitmap)
            listener.onResult(result, width, height, imageProxy.imageInfo.rotationDegrees)
        } catch (e: Exception) {
            listener.onNoResult()
        }
        imageProxy.close()
    }

    private fun getScannerRectToPreviewViewRelation(proxySize: Size, rotation: Int): ScannerRectToPreviewViewRelation {
        return when (rotation) {
            0, 180 -> {
                val size = scannerOverlay.size
                val width = size.width
                val height = size.height
                val previewHeight = width / (proxySize.width.toFloat() / proxySize.height)
                val heightDeltaTop = (previewHeight - height) / 2

                val scannerRect = scannerOverlay.scanRect
                val rectStartX = scannerRect.left
                val rectStartY = heightDeltaTop + scannerRect.top

                ScannerRectToPreviewViewRelation(
                    rectStartX / width,
                    rectStartY / previewHeight,
                    scannerRect.width() / width,
                    scannerRect.height() / previewHeight
                )
            }
            90, 270 -> {
                val size = scannerOverlay.size
                val width = size.width
                val height = size.height
                val previewWidth = height / (proxySize.width.toFloat() / proxySize.height)
                val widthDeltaLeft = (previewWidth - width) / 2

                val scannerRect = scannerOverlay.scanRect
                val rectStartX = widthDeltaLeft + scannerRect.left
                val rectStartY = scannerRect.top

                ScannerRectToPreviewViewRelation(
                    rectStartX / previewWidth,
                    rectStartY / height,
                    scannerRect.width() / previewWidth,
                    scannerRect.height() / height
                )
            }
            else -> throw IllegalArgumentException("Rotation degree ($rotation) not supported!")
        }
    }

    data class ScannerRectToPreviewViewRelation(
        val relativePosX: Float,
        val relativePosY: Float,
        val relativeWidth: Float,
        val relativeHeight: Float
    )

    private fun Image.getCropRectAccordingToRotation(scannerRect: ScannerRectToPreviewViewRelation, rotation: Int): Rect {
        return when (rotation) {
            0 -> {
                val startX = (scannerRect.relativePosX * this.width).toInt()
                val numberPixelW = (scannerRect.relativeWidth * this.width).toInt()
                val startY = (scannerRect.relativePosY * this.height).toInt()
                val numberPixelH = (scannerRect.relativeHeight * this.height).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            90 -> {
                val startX = (scannerRect.relativePosY * this.width).toInt()
                val numberPixelW = (scannerRect.relativeHeight * this.width).toInt()
                val numberPixelH = (scannerRect.relativeWidth * this.height).toInt()
                val startY = height - (scannerRect.relativePosX * this.height).toInt() - numberPixelH
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            180 -> {
                val numberPixelW = (scannerRect.relativeWidth * this.width).toInt()
                val startX = (this.width - scannerRect.relativePosX * this.width - numberPixelW).toInt()
                val numberPixelH = (scannerRect.relativeHeight * this.height).toInt()
                val startY = (height - scannerRect.relativePosY * this.height - numberPixelH).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            270 -> {
                val numberPixelW = (scannerRect.relativeHeight * this.width).toInt()
                val numberPixelH = (scannerRect.relativeWidth * this.height).toInt()
                val startX = (this.width - scannerRect.relativePosY * this.width - numberPixelW).toInt()
                val startY = (scannerRect.relativePosX * this.height).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            else -> throw IllegalArgumentException("Rotation degree ($rotation) not supported!")
        }
    }

    companion object {
        private const val TAG = "BarcodeAnalyzer"
    }
}