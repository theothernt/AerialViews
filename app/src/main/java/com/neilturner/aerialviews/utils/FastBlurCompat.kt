package com.neilturner.aerialviews.utils

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * A Kotlin port of the FastBlur algorithm used by Lottie-Android.
 * https://raw.githubusercontent.com/geomaster/lottie-android/4f2ae709a8228f48bd60d55eaa6c595e62f234b5/lottie/src/main/java/com/airbnb/lottie/utils/FastBlur.java
 *
 * Used as a fallback for API < 31 where RenderEffect is not available.
 * Performs a fast horizontal + vertical box blur in O(w×h) time.
 */
internal object FastBlurCompat {
    /**
     * Blurs [bitmap] in-place using a box blur with the given [radius].
     * This is an approximation of a Gaussian blur and operates on the full
     * pixel area of the bitmap.
     */
    fun applyBlur(
        bitmap: Bitmap,
        radius: Int,
    ) {
        if (radius < 1) return
        val width = bitmap.width
        val height = bitmap.height
        if (width < 2 || height < 2) return

        val safeRadius = minOf(radius, width - 1, height - 1)
        if (safeRadius < 1) return

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val scratch = IntArray(width * height)
        val rect = Rect(0, 0, width, height)
        blurPass(pixels, scratch, width * 4, rect, safeRadius)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun blurPass(
        pixelsInOut: IntArray,
        scratch: IntArray,
        byteStride: Int,
        rect: Rect,
        radius: Int,
    ) {
        val kernelSize = 2 * radius + 1
        val stride = byteStride / 4
        if (rect.width() >= kernelSize) {
            horizontalPass(pixelsInOut, scratch, stride, rect, radius)
        } else {
            naiveHorizontalPass(pixelsInOut, scratch, stride, rect, radius)
        }
        if (rect.height() >= kernelSize) {
            verticalPass(scratch, pixelsInOut, stride, rect, radius)
        } else {
            naiveVerticalPass(scratch, pixelsInOut, stride, rect, radius)
        }
    }

    // region Horizontal passes

    private fun initialAccumulateHorizontal(
        src: IntArray,
        sumsByChannel: IntArray,
        rowStart: Int,
        radius: Int,
    ) {
        val startVal = src[rowStart]
        sumsByChannel[0] = (radius + 1) * (startVal and 0xff)
        sumsByChannel[1] = (radius + 1) * ((startVal shr 8) and 0xff)
        sumsByChannel[2] = (radius + 1) * ((startVal shr 16) and 0xff)
        sumsByChannel[3] = (radius + 1) * ((startVal shr 24) and 0xff)
        for (i in 1..radius) {
            val v = src[rowStart + i]
            sumsByChannel[0] += v and 0xff
            sumsByChannel[1] += (v shr 8) and 0xff
            sumsByChannel[2] += (v shr 16) and 0xff
            sumsByChannel[3] += (v shr 24) and 0xff
        }
    }

    private fun naiveHorizontalPass(
        src: IntArray,
        dst: IntArray,
        stride: Int,
        rect: Rect,
        radius: Int,
    ) {
        val kernelSize = 2 * radius + 1
        val sumsByChannel = IntArray(4)
        val firstPixel = rect.top * stride + rect.left
        val height = rect.height()
        val width = rect.width()
        for (y in 0 until height) {
            val rowStart = firstPixel + y * stride
            val lastPixel = rowStart + width - 1
            initialAccumulateHorizontal(src, sumsByChannel, rowStart, radius)
            var leftPixelOffset = -radius
            var rightPixelOffset = radius + 1
            var x = 0
            while (x < width) {
                val base = rowStart + x
                val baseLeft = maxOf(base + leftPixelOffset, rowStart)
                val baseRight = minOf(base + rightPixelOffset, lastPixel)
                val newVal =
                    (sumsByChannel[0] / kernelSize) or
                        (sumsByChannel[1] / kernelSize shl 8) or
                        (sumsByChannel[2] / kernelSize shl 16) or
                        (sumsByChannel[3] / kernelSize shl 24)
                dst[base] = newVal
                val left = src[baseLeft]
                val right = src[baseRight]
                sumsByChannel[0] += -(left and 0xff) + (right and 0xff)
                sumsByChannel[1] += -((left shr 8) and 0xff) + ((right shr 8) and 0xff)
                sumsByChannel[2] += -((left shr 16) and 0xff) + ((right shr 16) and 0xff)
                sumsByChannel[3] += -((left shr 24) and 0xff) + ((right shr 24) and 0xff)
                x++
                leftPixelOffset++
                rightPixelOffset++
            }
        }
    }

    private fun horizontalPass(
        src: IntArray,
        dst: IntArray,
        stride: Int,
        rect: Rect,
        radius: Int,
    ) {
        val kernelSize = 2 * radius + 1
        val sumsByChannel = IntArray(4)
        val firstPixel = rect.top * stride + rect.left
        val width = rect.width()
        val height = rect.height()
        for (y in 0 until height) {
            val rowStart = firstPixel + y * stride
            initialAccumulateHorizontal(src, sumsByChannel, rowStart, radius)
            var x = 0
            // Left clamped region
            while (x < radius) {
                val base = rowStart + x
                val baseRight = base + radius + 1
                dst[base] =
                    (sumsByChannel[0] / kernelSize) or
                    (sumsByChannel[1] / kernelSize shl 8) or
                    (sumsByChannel[2] / kernelSize shl 16) or
                    (sumsByChannel[3] / kernelSize shl 24)
                val left = src[rowStart]
                val right = src[baseRight]
                sumsByChannel[0] += -(left and 0xff) + (right and 0xff)
                sumsByChannel[1] += -((left shr 8) and 0xff) + ((right shr 8) and 0xff)
                sumsByChannel[2] += -((left shr 16) and 0xff) + ((right shr 16) and 0xff)
                sumsByChannel[3] += -((left shr 24) and 0xff) + ((right shr 24) and 0xff)
                x++
            }
            // Unclamped region
            while (x < width - radius - 1) {
                val base = rowStart + x
                val baseLeft = base - radius
                val baseRight = base + radius + 1
                val newVal =
                    (sumsByChannel[0] / kernelSize) or
                        (sumsByChannel[1] / kernelSize shl 8) or
                        (sumsByChannel[2] / kernelSize shl 16) or
                        (sumsByChannel[3] / kernelSize shl 24)
                dst[base] = newVal
                val left = src[baseLeft]
                val right = src[baseRight]
                sumsByChannel[0] += -(left and 0xff) + (right and 0xff)
                sumsByChannel[1] += -((left shr 8) and 0xff) + ((right shr 8) and 0xff)
                sumsByChannel[2] += -((left shr 16) and 0xff) + ((right shr 16) and 0xff)
                sumsByChannel[3] += -((left shr 24) and 0xff) + ((right shr 24) and 0xff)
                x++
            }
            // Right clamped region
            val lastPixel = rowStart + width - 1
            while (x < width) {
                val base = rowStart + x
                val baseLeft = base - radius
                dst[base] =
                    (sumsByChannel[0] / kernelSize) or
                    (sumsByChannel[1] / kernelSize shl 8) or
                    (sumsByChannel[2] / kernelSize shl 16) or
                    (sumsByChannel[3] / kernelSize shl 24)
                val left = src[baseLeft]
                val right = src[lastPixel]
                sumsByChannel[0] += -(left and 0xff) + (right and 0xff)
                sumsByChannel[1] += -((left shr 8) and 0xff) + ((right shr 8) and 0xff)
                sumsByChannel[2] += -((left shr 16) and 0xff) + ((right shr 16) and 0xff)
                sumsByChannel[3] += -((left shr 24) and 0xff) + ((right shr 24) and 0xff)
                x++
            }
        }
    }

    // endregion

    // region Vertical passes

    private fun initialAccumulateVertical(
        src: IntArray,
        sumsByChannel: IntArray,
        columnStart: Int,
        stride: Int,
        radius: Int,
    ) {
        val startVal = src[columnStart]
        sumsByChannel[0] = (radius + 1) * (startVal and 0xff)
        sumsByChannel[1] = (radius + 1) * ((startVal shr 8) and 0xff)
        sumsByChannel[2] = (radius + 1) * ((startVal shr 16) and 0xff)
        sumsByChannel[3] = (radius + 1) * ((startVal shr 24) and 0xff)
        for (i in 1..radius) {
            val v = src[columnStart + stride * i]
            sumsByChannel[0] += v and 0xff
            sumsByChannel[1] += (v shr 8) and 0xff
            sumsByChannel[2] += (v shr 16) and 0xff
            sumsByChannel[3] += (v shr 24) and 0xff
        }
    }

    private fun naiveVerticalPass(
        src: IntArray,
        dst: IntArray,
        stride: Int,
        rect: Rect,
        radius: Int,
    ) {
        val kernelSize = 2 * radius + 1
        val sumsByChannel = IntArray(4)
        val firstPixel = stride * rect.top + rect.left
        val width = rect.width()
        val height = rect.height()
        for (x in 0 until width) {
            val columnStart = firstPixel + x
            val lastPixel = columnStart + stride * (height - 1)
            initialAccumulateVertical(src, sumsByChannel, columnStart, stride, radius)
            var y = 0
            while (y < height) {
                val base = columnStart + stride * y
                val baseTop = maxOf(base + (-radius) * stride, columnStart)
                val baseBottom = minOf(base + (radius + 1) * stride, lastPixel)
                val newVal =
                    (sumsByChannel[0] / kernelSize) or
                        (sumsByChannel[1] / kernelSize shl 8) or
                        (sumsByChannel[2] / kernelSize shl 16) or
                        (sumsByChannel[3] / kernelSize shl 24)
                dst[base] = newVal
                val top = src[baseTop]
                val bottom = src[baseBottom]
                sumsByChannel[0] += -(top and 0xff) + (bottom and 0xff)
                sumsByChannel[1] += -((top shr 8) and 0xff) + ((bottom shr 8) and 0xff)
                sumsByChannel[2] += -((top shr 16) and 0xff) + ((bottom shr 16) and 0xff)
                sumsByChannel[3] += -((top shr 24) and 0xff) + ((bottom shr 24) and 0xff)
                y++
            }
        }
    }

    private fun verticalPass(
        src: IntArray,
        dst: IntArray,
        stride: Int,
        rect: Rect,
        radius: Int,
    ) {
        val kernelSize = 2 * radius + 1
        val sumsByChannel = IntArray(4)
        val firstPixel = stride * rect.top + rect.left
        val width = rect.width()
        val height = rect.height()
        for (x in 0 until width) {
            val columnStart = firstPixel + x
            val lastPixel = columnStart + stride * (height - 1)
            initialAccumulateVertical(src, sumsByChannel, columnStart, stride, radius)
            var y = 0
            // Top clamped
            while (y < radius) {
                val base = columnStart + stride * y
                val baseBottom = base + (radius + 1) * stride
                dst[base] =
                    (sumsByChannel[0] / kernelSize) or
                    (sumsByChannel[1] / kernelSize shl 8) or
                    (sumsByChannel[2] / kernelSize shl 16) or
                    (sumsByChannel[3] / kernelSize shl 24)
                val top = src[columnStart]
                val bottom = src[baseBottom]
                sumsByChannel[0] += -(top and 0xff) + (bottom and 0xff)
                sumsByChannel[1] += -((top shr 8) and 0xff) + ((bottom shr 8) and 0xff)
                sumsByChannel[2] += -((top shr 16) and 0xff) + ((bottom shr 16) and 0xff)
                sumsByChannel[3] += -((top shr 24) and 0xff) + ((bottom shr 24) and 0xff)
                y++
            }
            // Unclamped
            while (y < height - radius - 1) {
                val base = columnStart + stride * y
                val baseTop = base + (-radius) * stride
                val baseBottom = base + (radius + 1) * stride
                val newVal =
                    (sumsByChannel[0] / kernelSize) or
                        (sumsByChannel[1] / kernelSize shl 8) or
                        (sumsByChannel[2] / kernelSize shl 16) or
                        (sumsByChannel[3] / kernelSize shl 24)
                dst[base] = newVal
                val top = src[baseTop]
                val bottom = src[baseBottom]
                sumsByChannel[0] += -(top and 0xff) + (bottom and 0xff)
                sumsByChannel[1] += -((top shr 8) and 0xff) + ((bottom shr 8) and 0xff)
                sumsByChannel[2] += -((top shr 16) and 0xff) + ((bottom shr 16) and 0xff)
                sumsByChannel[3] += -((top shr 24) and 0xff) + ((bottom shr 24) and 0xff)
                y++
            }
            // Bottom clamped
            while (y < height) {
                val base = columnStart + stride * y
                val baseTop = base + (-radius) * stride
                dst[base] =
                    (sumsByChannel[0] / kernelSize) or
                    (sumsByChannel[1] / kernelSize shl 8) or
                    (sumsByChannel[2] / kernelSize shl 16) or
                    (sumsByChannel[3] / kernelSize shl 24)
                val top = src[baseTop]
                val bottom = src[lastPixel]
                sumsByChannel[0] += -(top and 0xff) + (bottom and 0xff)
                sumsByChannel[1] += -((top shr 8) and 0xff) + ((bottom shr 8) and 0xff)
                sumsByChannel[2] += -((top shr 16) and 0xff) + ((bottom shr 16) and 0xff)
                sumsByChannel[3] += -((top shr 24) and 0xff) + ((bottom shr 24) and 0xff)
                y++
            }
        }
    }

    // endregion
}
