package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import pl.droidsonroids.gif.GifDrawable
import java.io.OutputStream

/**
 * GIFアニメーションの切り抜き（クロップ）ヘルパー。
 * 全フレームを同一の枠で切り抜き、元のディレイ・ループ設定を維持して保存する。
 */
object GifCropHelper {

    fun cropGif(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        cropRect: Rect,
        rotationDegrees: Int = 0
    ): Boolean {
        var gifDrawable: GifDrawable? = null
        return try {
            gifDrawable = GifDrawable(context.contentResolver, inputUri)
            val frameCount = gifDrawable.numberOfFrames
            val loopCount = gifDrawable.loopCount

            // 切り抜き矩形が画像範囲内に収まるようにクリップ
            val safeLeft = cropRect.left.coerceIn(0, gifDrawable.intrinsicWidth - 1)
            val safeTop = cropRect.top.coerceIn(0, gifDrawable.intrinsicHeight - 1)
            val safeWidth = cropRect.width().coerceAtMost(gifDrawable.intrinsicWidth - safeLeft)
            val safeHeight = cropRect.height().coerceAtMost(gifDrawable.intrinsicHeight - safeTop)

            if (safeWidth <= 0 || safeHeight <= 0) {
                return false
            }

            val encoder = AnimatedGifEncoder()
            encoder.setRepeat(loopCount)
            encoder.setQuality(1) // 最高品質パレット
            if (!encoder.start(outputStream)) {
                return false
            }

            val baseBitmap = Bitmap.createBitmap(
                gifDrawable.intrinsicWidth,
                gifDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(baseBitmap)
            gifDrawable.setBounds(0, 0, gifDrawable.intrinsicWidth, gifDrawable.intrinsicHeight)

            for (i in 0 until frameCount) {
                gifDrawable.seekToFrame(i)
                gifDrawable.draw(canvas)

                var croppedBitmap = Bitmap.createBitmap(
                    baseBitmap,
                    safeLeft,
                    safeTop,
                    safeWidth,
                    safeHeight
                )

                if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                    }
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

                encoder.setDelay(gifDrawable.getFrameDuration(i))
                encoder.addFrame(croppedBitmap)
                croppedBitmap.recycle()
            }

            baseBitmap.recycle()
            encoder.finish()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                gifDrawable?.recycle()
            } catch (_: Exception) {}
        }
    }
}
