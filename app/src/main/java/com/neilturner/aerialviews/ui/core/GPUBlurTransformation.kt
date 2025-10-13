package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil3.size.Size
import coil3.transform.Transformation

class GPUBlurTransformation(
    private val radius: Float,
    private val context: Context
) : Transformation() {

    override val cacheKey: String = "gpu_blur_${radius}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return try {
            // Use RenderScript for hardware-accelerated blur
            blurWithRenderScript(input, radius)
        } catch (e: Exception) {
            // Fallback to optimized CPU blur if RenderScript fails
            blurWithOptimizedCPU(input, radius)
        }
    }
    
    private fun blurWithRenderScript(bitmap: Bitmap, radius: Float): Bitmap {
        val outputBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        val renderScript = RenderScript.create(context)
        val inputAllocation = Allocation.createFromBitmap(renderScript, bitmap)
        val outputAllocation = Allocation.createFromBitmap(renderScript, outputBitmap)
        
        val scriptIntrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        scriptIntrinsicBlur.setInput(inputAllocation)
        scriptIntrinsicBlur.setRadius(radius.coerceIn(0.1f, 25.0f))
        scriptIntrinsicBlur.forEach(outputAllocation)
        
        outputAllocation.copyTo(outputBitmap)
        
        renderScript.destroy()
        inputAllocation.destroy()
        outputAllocation.destroy()
        scriptIntrinsicBlur.destroy()
        
        return outputBitmap
    }
    
    private fun blurWithOptimizedCPU(bitmap: Bitmap, radius: Float): Bitmap {
        // Optimized CPU blur using downscaling for speed
        val scaleFactor = 0.5f // Downscale for faster processing
        val scaledWidth = (bitmap.width * scaleFactor).toInt()
        val scaledHeight = (bitmap.height * scaleFactor).toInt()
        
        // Create scaled bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // Apply fast box blur to scaled bitmap
        val blurredScaled = fastBoxBlur(scaledBitmap, (radius * scaleFactor).toInt())
        
        // Scale back up
        val outputBitmap = Bitmap.createScaledBitmap(blurredScaled, bitmap.width, bitmap.height, true)
        
        // Cleanup
        scaledBitmap.recycle()
        blurredScaled.recycle()
        
        return outputBitmap
    }
    
    private fun fastBoxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val output = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(bitmap.width * bitmap.height)
        val outputPixels = IntArray(bitmap.width * bitmap.height)
        
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // Horizontal pass
        for (y in 0 until bitmap.height) {
            var r = 0
            var g = 0
            var b = 0
            var a = 0
            var count = 0
            
            // Initialize first window
            for (x in 0..radius.coerceAtMost(bitmap.width - 1)) {
                val pixel = pixels[y * bitmap.width + x]
                r += (pixel shr 16) and 0xFF
                g += (pixel shr 8) and 0xFF
                b += pixel and 0xFF
                a += (pixel shr 24) and 0xFF
                count++
            }
            
            // Apply to first pixel
            outputPixels[y * bitmap.width] = (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
            
            // Slide window
            for (x in 1 until bitmap.width) {
                // Remove left pixel
                if (x - radius - 1 >= 0) {
                    val pixel = pixels[y * bitmap.width + x - radius - 1]
                    r -= (pixel shr 16) and 0xFF
                    g -= (pixel shr 8) and 0xFF
                    b -= pixel and 0xFF
                    a -= (pixel shr 24) and 0xFF
                    count--
                }
                
                // Add right pixel
                if (x + radius < bitmap.width) {
                    val pixel = pixels[y * bitmap.width + x + radius]
                    r += (pixel shr 16) and 0xFF
                    g += (pixel shr 8) and 0xFF
                    b += pixel and 0xFF
                    a += (pixel shr 24) and 0xFF
                    count++
                }
                
                outputPixels[y * bitmap.width + x] = (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
            }
        }
        
        // Vertical pass
        for (x in 0 until bitmap.width) {
            var r = 0
            var g = 0
            var b = 0
            var a = 0
            var count = 0
            
            // Initialize first window
            for (y in 0..radius.coerceAtMost(bitmap.height - 1)) {
                val pixel = outputPixels[y * bitmap.width + x]
                r += (pixel shr 16) and 0xFF
                g += (pixel shr 8) and 0xFF
                b += pixel and 0xFF
                a += (pixel shr 24) and 0xFF
                count++
            }
            
            // Apply to first pixel
            pixels[x] = (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
            
            // Slide window
            for (y in 1 until bitmap.height) {
                // Remove top pixel
                if (y - radius - 1 >= 0) {
                    val pixel = outputPixels[(y - radius - 1) * bitmap.width + x]
                    r -= (pixel shr 16) and 0xFF
                    g -= (pixel shr 8) and 0xFF
                    b -= pixel and 0xFF
                    a -= (pixel shr 24) and 0xFF
                    count--
                }
                
                // Add bottom pixel
                if (y + radius < bitmap.height) {
                    val pixel = outputPixels[(y + radius) * bitmap.width + x]
                    r += (pixel shr 16) and 0xFF
                    g += (pixel shr 8) and 0xFF
                    b += pixel and 0xFF
                    a += (pixel shr 24) and 0xFF
                    count++
                }
                
                pixels[y * bitmap.width + x] = (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
            }
        }
        
        output.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return output
    }
}
