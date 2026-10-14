package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import fr.free.nrw.commons.ajpegtran.Jpegtran

object LosslessCropHelper {
    const val MCU_BLOCK_SIZE = 16

    /**
     * クロップ矩形を MCU (16px) 境界にスナップさせる。
     */
    fun snapRectToMcu(rect: Rect, imageWidth: Int, imageHeight: Int): Rect {
        val left = ((rect.left + MCU_BLOCK_SIZE / 2) / MCU_BLOCK_SIZE * MCU_BLOCK_SIZE).coerceIn(0, (imageWidth - MCU_BLOCK_SIZE).coerceAtLeast(0))
        val top = ((rect.top + MCU_BLOCK_SIZE / 2) / MCU_BLOCK_SIZE * MCU_BLOCK_SIZE).coerceIn(0, (imageHeight - MCU_BLOCK_SIZE).coerceAtLeast(0))

        var width = ((rect.width() + MCU_BLOCK_SIZE / 2) / MCU_BLOCK_SIZE * MCU_BLOCK_SIZE).coerceAtLeast(MCU_BLOCK_SIZE)
        var height = ((rect.height() + MCU_BLOCK_SIZE / 2) / MCU_BLOCK_SIZE * MCU_BLOCK_SIZE).coerceAtLeast(MCU_BLOCK_SIZE)

        if (left + width > imageWidth) {
            width = ((imageWidth - left) / MCU_BLOCK_SIZE * MCU_BLOCK_SIZE).coerceAtLeast(MCU_BLOCK_SIZE)
        }
        if (top + height > imageHeight) {
            height = ((imageHeight - top) / MCU_BLOCK_SIZE * MCU_BLOCK_SIZE).coerceAtLeast(MCU_BLOCK_SIZE)
        }
        return Rect(left, top, left + width, top + height)
    }

    /**
     * Jpegtran を使用して JPEG の完全ロスレス切り出し（ビットストリーム直接抽出）を実行する。
     */
    fun cropJpegLossless(context: Context, inputUri: Uri, outputUri: Uri, rect: Rect): Boolean {
        var jpegtran: Jpegtran? = null
        return try {
            jpegtran = Jpegtran(context, inputUri)
            jpegtran.crop(rect.left, rect.top, rect.width(), rect.height())
            jpegtran.save(outputUri)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                jpegtran?.cleanup()
            } catch (ignored: Exception) {}
        }
    }
}
