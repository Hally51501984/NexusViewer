package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource
import java.io.File
import java.io.OutputStream

/**
 * GIFアニメーションの正確・高速な編集（切り抜き・回転・反転）ヘルパー。
 * pl.droidsonroids.gif.GifDecoder（Cネイティブ）により全フレームを正確にデコードし、
 * AnimatedGifEncoder で再構成して保存する。
 */
object GifCropHelper {

    private fun getInputSource(context: Context, uri: Uri): InputSource {
        val path = uri.path
        return if (path != null && File(path).exists()) {
            InputSource.FileSource(File(path))
        } else {
            InputSource.UriSource(context.contentResolver, uri)
        }
    }

    /**
     * GIFアニメーションの切り抜き（クロップ）
     */
    fun cropGif(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        cropRect: Rect,
        rotationDegrees: Int = 0,
        wholeImageWidth: Int = 0,
        wholeImageHeight: Int = 0
    ): Boolean {
        var decoder: GifDecoder? = null
        return try {
            val inputSource = getInputSource(context, inputUri)
            decoder = GifDecoder(inputSource)
            val imgWidth = decoder.width
            val imgHeight = decoder.height
            val frameCount = decoder.numberOfFrames
            val loopCount = decoder.loopCount

            val actualCropRect = if (wholeImageWidth > 0 && wholeImageHeight > 0) {
                val scaleX = imgWidth.toFloat() / wholeImageWidth.toFloat()
                val scaleY = imgHeight.toFloat() / wholeImageHeight.toFloat()
                Rect(
                    (cropRect.left * scaleX).toInt(),
                    (cropRect.top * scaleY).toInt(),
                    (cropRect.right * scaleX).toInt(),
                    (cropRect.bottom * scaleY).toInt()
                )
            } else {
                cropRect
            }

            val safeLeft = actualCropRect.left.coerceIn(0, imgWidth - 1)
            val safeTop = actualCropRect.top.coerceIn(0, imgHeight - 1)
            val safeWidth = actualCropRect.width().coerceAtMost(imgWidth - safeLeft)
            val safeHeight = actualCropRect.height().coerceAtMost(imgHeight - safeTop)

            if (safeWidth <= 0 || safeHeight <= 0) {
                return false
            }

            val encoder = AnimatedGifEncoder()
            encoder.setRepeat(loopCount)
            encoder.setQuality(10) // 高品質・高速サンプリング (NeuQuant sample=10)
            if (!encoder.start(outputStream)) {
                return false
            }

            val frameBitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
            val matrix = if (rotationDegrees != 0) {
                Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            } else null

            for (i in 0 until frameCount) {
                frameBitmap.eraseColor(Color.TRANSPARENT)
                decoder.seekToFrame(i, frameBitmap)

                var croppedBitmap = Bitmap.createBitmap(
                    frameBitmap,
                    safeLeft,
                    safeTop,
                    safeWidth,
                    safeHeight
                )

                if (matrix != null) {
                    val rotatedBitmap = Bitmap.createBitmap(
                        croppedBitmap,
                        0,
                        0,
                        croppedBitmap.width,
                        croppedBitmap.height,
                        matrix,
                        true
                    )
                    croppedBitmap.recycle()
                    croppedBitmap = rotatedBitmap
                }

                encoder.setDelay(decoder.getFrameDuration(i))
                encoder.addFrame(croppedBitmap)
                croppedBitmap.recycle()
            }

            frameBitmap.recycle()
            encoder.finish()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                decoder?.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * GIFアニメーションの全フレーム回転または鏡像反転処理
     */
    fun rotateOrFlipGif(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        rotationDegrees: Int = 0,
        flipHorizontal: Boolean = false
    ): Boolean {
        var decoder: GifDecoder? = null
        return try {
            val inputSource = getInputSource(context, inputUri)
            decoder = GifDecoder(inputSource)
            val imgWidth = decoder.width
            val imgHeight = decoder.height
            val frameCount = decoder.numberOfFrames
            val loopCount = decoder.loopCount

            val encoder = AnimatedGifEncoder()
            encoder.setRepeat(loopCount)
            encoder.setQuality(10) // 高品質・高速サンプリング
            if (!encoder.start(outputStream)) {
                return false
            }

            val frameBitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
            val matrix = Matrix()
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }
            if (flipHorizontal) {
                matrix.postScale(-1f, 1f)
            }

            for (i in 0 until frameCount) {
                frameBitmap.eraseColor(Color.TRANSPARENT)
                decoder.seekToFrame(i, frameBitmap)

                val transformedBitmap = Bitmap.createBitmap(
                    frameBitmap,
                    0,
                    0,
                    imgWidth,
                    imgHeight,
                    matrix,
                    true
                )

                encoder.setDelay(decoder.getFrameDuration(i))
                encoder.addFrame(transformedBitmap)
                transformedBitmap.recycle()
            }

            frameBitmap.recycle()
            encoder.finish()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                decoder?.recycle()
            } catch (_: Exception) {}
        }
    }
}
