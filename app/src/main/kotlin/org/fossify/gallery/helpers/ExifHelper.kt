package org.fossify.gallery.helpers

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ExifHelper {

    /**
     * 現在の EXIF Orientation を時計回りに [degrees] 度（90, 180, 270）回転させた新しい Orientation を計算する。
     */
    fun rotateOrientation(currentOrientation: Int, degrees: Int): Int {
        val normalizedDegrees = ((degrees % 360) + 360) % 360
        return when (normalizedDegrees) {
            90 -> rotateOrientation90(currentOrientation)
            180 -> rotateOrientation180(currentOrientation)
            270 -> rotateOrientation270(currentOrientation)
            else -> currentOrientation
        }
    }

    private fun rotateOrientation90(orientation: Int): Int {
        return when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> ExifInterface.ORIENTATION_ROTATE_90
            ExifInterface.ORIENTATION_ROTATE_90 -> ExifInterface.ORIENTATION_ROTATE_180
            ExifInterface.ORIENTATION_ROTATE_180 -> ExifInterface.ORIENTATION_ROTATE_270
            ExifInterface.ORIENTATION_ROTATE_270 -> ExifInterface.ORIENTATION_NORMAL
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifInterface.ORIENTATION_TRANSPOSE
            ExifInterface.ORIENTATION_TRANSPOSE -> ExifInterface.ORIENTATION_FLIP_VERTICAL
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifInterface.ORIENTATION_TRANSVERSE
            ExifInterface.ORIENTATION_TRANSVERSE -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            else -> ExifInterface.ORIENTATION_ROTATE_90
        }
    }

    private fun rotateOrientation180(orientation: Int): Int {
        return rotateOrientation90(rotateOrientation90(orientation))
    }

    private fun rotateOrientation270(orientation: Int): Int {
        return rotateOrientation90(rotateOrientation180(orientation))
    }

    /**
     * 現在の EXIF Orientation を水平反転（左右反転）させた新しい Orientation を計算する。
     */
    fun flipOrientationHorizontal(orientation: Int): Int {
        return when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifInterface.ORIENTATION_NORMAL
            ExifInterface.ORIENTATION_ROTATE_180 -> ExifInterface.ORIENTATION_FLIP_VERTICAL
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifInterface.ORIENTATION_ROTATE_180
            ExifInterface.ORIENTATION_ROTATE_90 -> ExifInterface.ORIENTATION_TRANSPOSE
            ExifInterface.ORIENTATION_TRANSPOSE -> ExifInterface.ORIENTATION_ROTATE_90
            ExifInterface.ORIENTATION_ROTATE_270 -> ExifInterface.ORIENTATION_TRANSVERSE
            ExifInterface.ORIENTATION_TRANSVERSE -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        }
    }

    /**
     * 現在の EXIF Orientation を垂直反転（上下反転）させた新しい Orientation を計算する。
     */
    fun flipOrientationVertical(orientation: Int): Int {
        return when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> ExifInterface.ORIENTATION_FLIP_VERTICAL
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifInterface.ORIENTATION_NORMAL
            ExifInterface.ORIENTATION_ROTATE_180 -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifInterface.ORIENTATION_ROTATE_180
            ExifInterface.ORIENTATION_ROTATE_90 -> ExifInterface.ORIENTATION_TRANSVERSE
            ExifInterface.ORIENTATION_TRANSVERSE -> ExifInterface.ORIENTATION_ROTATE_90
            ExifInterface.ORIENTATION_ROTATE_270 -> ExifInterface.ORIENTATION_TRANSPOSE
            ExifInterface.ORIENTATION_TRANSPOSE -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_FLIP_VERTICAL
        }
    }

    /**
     * 回転および反転操作を一括して適用した新しい EXIF Orientation を計算する。
     */
    fun calculateOrientation(
        currentOrientation: Int,
        rotationDegrees: Int,
        isFlippedHorizontally: Boolean,
        isFlippedVertically: Boolean
    ): Int {
        var orient = currentOrientation
        if (rotationDegrees != 0) {
            orient = rotateOrientation(orient, rotationDegrees)
        }
        if (isFlippedHorizontally) {
            orient = flipOrientationHorizontal(orient)
        }
        if (isFlippedVertically) {
            orient = flipOrientationVertical(orient)
        }
        return orient
    }

    /**
     * 指定されたファイルパスの EXIF Orientation を取得する。
     */
    fun getOrientation(filePath: String): Int {
        return try {
            val exif = ExifInterface(filePath)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * 指定されたファイルパスの EXIF Orientation を直接更新して保存する（完全ロスレス・ピクセル再エンコードなし）。
     */
    fun saveLosslessOrientation(filePath: String, newOrientation: Int): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canWrite()) {
                return false
            }
            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, newOrientation.toString())
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Context と Uri を指定して EXIF Orientation を回転・反転更新する（完全ロスレス・ピクセル再エンコードなし）。
     */
    fun saveLosslessOrientation(
        context: Context,
        targetUri: Uri,
        rotationDegrees: Int,
        isFlippedHorizontally: Boolean,
        isFlippedVertically: Boolean
    ): Boolean {
        return try {
            if (targetUri.scheme == "file") {
                val path = targetUri.path ?: return false
                val currentOrient = getOrientation(path)
                val newOrient = calculateOrientation(
                    currentOrientation = currentOrient,
                    rotationDegrees = rotationDegrees,
                    isFlippedHorizontally = isFlippedHorizontally,
                    isFlippedVertically = isFlippedVertically
                )
                saveLosslessOrientation(path, newOrient)
            } else if (targetUri.scheme == "content") {
                val pfd = context.contentResolver.openFileDescriptor(targetUri, "rw") ?: return false
                pfd.use {
                    val exif = ExifInterface(it.fileDescriptor)
                    val currentOrient = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val newOrient = calculateOrientation(
                        currentOrientation = currentOrient,
                        rotationDegrees = rotationDegrees,
                        isFlippedHorizontally = isFlippedHorizontally,
                        isFlippedVertically = isFlippedVertically
                    )
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, newOrient.toString())
                    exif.saveAttributes()
                }
                true
            } else {
                val path = targetUri.path ?: return false
                val currentOrient = getOrientation(path)
                val newOrient = calculateOrientation(
                    currentOrientation = currentOrient,
                    rotationDegrees = rotationDegrees,
                    isFlippedHorizontally = isFlippedHorizontally,
                    isFlippedVertically = isFlippedVertically
                )
                saveLosslessOrientation(path, newOrient)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
