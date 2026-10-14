package org.fossify.gallery.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.canhub.cropper.CropImageView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.getCompressionFormat
import org.fossify.commons.extensions.getFileOutputStream
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.sharePathIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityEditBinding
import org.fossify.gallery.dialogs.OtherAspectRatioDialog
import org.fossify.gallery.dialogs.SaveAsDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.ensureWritablePath
import org.fossify.gallery.extensions.fixDateTaken
import org.fossify.gallery.extensions.getCompressionFormatFromUri
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.extensions.proposeNewFilePath
import org.fossify.gallery.extensions.readExif
import org.fossify.gallery.extensions.resolveUriScheme
import org.fossify.gallery.extensions.showContentDescriptionOnLongClick
import org.fossify.gallery.extensions.writeBitmapToCache
import org.fossify.gallery.extensions.writeExif
import org.fossify.gallery.helpers.ASPECT_RATIO_FOUR_THREE
import org.fossify.gallery.helpers.ASPECT_RATIO_FREE
import org.fossify.gallery.helpers.ASPECT_RATIO_ONE_ONE
import org.fossify.gallery.helpers.ASPECT_RATIO_OTHER
import org.fossify.gallery.helpers.ASPECT_RATIO_SIXTEEN_NINE
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.ExifHelper
import org.fossify.gallery.helpers.GifCropHelper
import org.fossify.gallery.helpers.LosslessCropHelper
import org.fossify.gallery.helpers.getPermissionToRequest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

class EditActivity : BaseCropActivity() {
    companion object {
        init {
            System.loadLibrary("NativeImageProcessor")
        }

        private const val ASPECT_X = "aspectX"
        private const val ASPECT_Y = "aspectY"
        private const val CROP = "crop"
    }

    private lateinit var saveUri: Uri
    private var uri: Uri? = null
    private var lastOtherAspectRatio: Pair<Float, Float>? = null
    private var currAspectRatio = ASPECT_RATIO_FREE
    private var isShowingAspectRatios = false
    private var isSnapEnabled = true
    private var isCropIntent = false
    private var isEditingWithThirdParty = false
    private var isSharingBitmap = false
    private var oldExif: ExifInterface? = null
    private var originalUri: Uri? = null
    private val binding by viewBinding(ActivityEditBinding::inflate)
    private var overwriteRequested = false

    override val cropImageView: CropImageView
        get() = binding.cropImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        isCropIntent = intent.extras?.get(CROP) == "true"
        setupEdgeToEdge(
            padBottomSystem = listOf(binding.bottomEditorCropRotateActions.root)
        )

        if (checkAppSideloading()) {
            return
        }

        setupOptionsMenu()
        handlePermission(getPermissionToRequest()) {
            if (!it) {
                toast(org.fossify.commons.R.string.no_storage_permissions)
                finish()
            }
            initEditActivity()
        }
    }

    override fun onResume() {
        super.onResume()
        isEditingWithThirdParty = false
        setupTopAppBar(binding.editorAppbar, NavigationIcon.Arrow)
    }

    override fun onStop() {
        super.onStop()
        if (isEditingWithThirdParty) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ColorModeHelper.resetColorMode(this)
    }

    private fun setupOptionsMenu() {
        binding.editorToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save_as -> startSaveFlow(overwrite = false)
                R.id.overwrite_original -> startSaveFlow(overwrite = true)
                R.id.edit -> editWith()
                R.id.share -> shareImage()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun initEditActivity() {
        if (intent.data == null) {
            toast(R.string.invalid_image_path)
            finish()
            return
        }

        uri = intent.data!!
        originalUri = uri
        if (uri!!.scheme != "file" && uri!!.scheme != "content") {
            toast(R.string.unknown_file_location)
            finish()
            return
        }

        val extras = intent.extras
        if (extras?.containsKey(REAL_FILE_PATH) == true) {
            val realPath = intent.extras!!.getString(REAL_FILE_PATH)
            uri = when {
                isPathOnOTG(realPath!!) -> uri
                realPath.startsWith("file:/") -> realPath.toUri()
                else -> Uri.fromFile(File(realPath))
            }
        } else {
            (getRealPathFromURI(uri!!))?.apply {
                uri = Uri.fromFile(File(this))
            }
        }

        saveUri = when {
            extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true
                && extras.get(MediaStore.EXTRA_OUTPUT) is Uri -> extras.get(MediaStore.EXTRA_OUTPUT) as Uri
            else -> uri!!
        }

        if (isCropIntent) {
            binding.editorToolbar.menu.findItem(R.id.overwrite_original).isVisible = false
        }

        loadCropImageView()
        setupBottomActions()

        if (config.lastEditorCropAspectRatio == ASPECT_RATIO_OTHER) {
            if (config.lastEditorCropOtherAspectRatioX == 0f) {
                config.lastEditorCropOtherAspectRatioX = 1f
            }
            if (config.lastEditorCropOtherAspectRatioY == 0f) {
                config.lastEditorCropOtherAspectRatioY = 1f
            }
            lastOtherAspectRatio = Pair(config.lastEditorCropOtherAspectRatioX, config.lastEditorCropOtherAspectRatioY)
        }
        updateAspectRatio(config.lastEditorCropAspectRatio)
        updateSnapButton()
    }

    private fun loadCropImageView() {
        binding.defaultImageView.beGone()
        binding.cropImageView.apply {
            beVisible()
            setImageUriAsync(uri)
            guidelines = CropImageView.Guidelines.ON

            if (isCropIntent && shouldCropSquare()) {
                currAspectRatio = ASPECT_RATIO_ONE_ONE
                setFixedAspectRatio(true)
                binding.bottomEditorCropRotateActions.bottomAspectRatio.beGone()
            }
        }

        // HDR カラーモード適用
        Glide.with(this)
            .asBitmap()
            .load(uri)
            .apply(RequestOptions().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE))
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                    ColorModeHelper.resetColorMode(this@EditActivity)
                    return false
                }

                override fun onResourceReady(
                    bitmap: Bitmap,
                    model: Any,
                    target: Target<Bitmap>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    ColorModeHelper.setColorModeForImage(this@EditActivity, bitmap, config.ultraHdrRendering)
                    return false
                }
            }).submit()
    }

    private fun setOldExif() {
        oldExif = readExif(uri!!)
    }

    private fun setupBottomActions() {
        binding.bottomEditorCropRotateActions.bottomRotateLeft.setOnClickListener {
            binding.cropImageView.rotateImage(-90)
        }

        binding.bottomEditorCropRotateActions.bottomRotateRight.setOnClickListener {
            binding.cropImageView.rotateImage(90)
        }

        binding.bottomEditorCropRotateActions.bottomFlipHorizontally.setOnClickListener {
            binding.cropImageView.flipImageHorizontally()
        }

        binding.bottomEditorCropRotateActions.bottomFlipVertically.setOnClickListener {
            binding.cropImageView.flipImageVertically()
        }

        binding.bottomEditorCropRotateActions.bottomAspectRatio.setOnClickListener {
            isShowingAspectRatios = !isShowingAspectRatios
            binding.bottomAspectRatios.root.beVisibleIf(isShowingAspectRatios)
            binding.cropImageView.guidelines = if (isShowingAspectRatios) {
                CropImageView.Guidelines.ON
            } else {
                CropImageView.Guidelines.ON_TOUCH
            }
            updateCropRotateActionButtons()
        }

        binding.bottomEditorCropRotateActions.bottomSnapToggle.setOnClickListener {
            isSnapEnabled = !isSnapEnabled
            updateSnapButton()
            toast(if (isSnapEnabled) R.string.lossless_snap_on else R.string.lossless_snap_off)
        }

        arrayOf(
            binding.bottomEditorCropRotateActions.bottomRotateLeft,
            binding.bottomEditorCropRotateActions.bottomRotateRight,
            binding.bottomEditorCropRotateActions.bottomFlipHorizontally,
            binding.bottomEditorCropRotateActions.bottomFlipVertically,
            binding.bottomEditorCropRotateActions.bottomAspectRatio,
            binding.bottomEditorCropRotateActions.bottomSnapToggle
        ).forEach {
            it.showContentDescriptionOnLongClick()
        }

        setupAspectRatioButtons()
    }

    private fun updateSnapButton() {
        val color = if (isSnapEnabled) getProperPrimaryColor() else Color.WHITE
        binding.bottomEditorCropRotateActions.bottomSnapToggle.applyColorFilter(color)
    }

    private fun setupAspectRatioButtons() {
        binding.bottomAspectRatios.bottomAspectRatioFree.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_FREE)
        }

        binding.bottomAspectRatios.bottomAspectRatioOneOne.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_ONE_ONE)
        }

        binding.bottomAspectRatios.bottomAspectRatioFourThree.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_FOUR_THREE)
        }

        binding.bottomAspectRatios.bottomAspectRatioSixteenNine.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_SIXTEEN_NINE)
        }

        binding.bottomAspectRatios.bottomAspectRatioOther.setOnClickListener {
            OtherAspectRatioDialog(this, lastOtherAspectRatio) {
                lastOtherAspectRatio = it
                config.lastEditorCropOtherAspectRatioX = it.first
                config.lastEditorCropOtherAspectRatioY = it.second
                updateAspectRatio(ASPECT_RATIO_OTHER)
            }
        }

        updateAspectRatioButtons()
    }

    private fun updateCropRotateActionButtons() {
        val color = if (isShowingAspectRatios) getProperPrimaryColor() else Color.WHITE
        binding.bottomEditorCropRotateActions.bottomAspectRatio.applyColorFilter(color)
    }

    private fun updateAspectRatio(aspectRatio: Int) {
        currAspectRatio = aspectRatio
        config.lastEditorCropAspectRatio = aspectRatio
        updateAspectRatioButtons()

        binding.cropImageView.apply {
            if (aspectRatio == ASPECT_RATIO_FREE) {
                setFixedAspectRatio(false)
            } else {
                val newAspectRatio = when (aspectRatio) {
                    ASPECT_RATIO_ONE_ONE -> Pair(1f, 1f)
                    ASPECT_RATIO_FOUR_THREE -> Pair(4f, 3f)
                    ASPECT_RATIO_SIXTEEN_NINE -> Pair(16f, 9f)
                    else -> Pair(lastOtherAspectRatio!!.first, lastOtherAspectRatio!!.second)
                }
                setAspectRatio(newAspectRatio.first.toInt(), newAspectRatio.second.toInt())
            }
        }
    }

    private fun updateAspectRatioButtons() {
        arrayOf(
            binding.bottomAspectRatios.bottomAspectRatioFree,
            binding.bottomAspectRatios.bottomAspectRatioOneOne,
            binding.bottomAspectRatios.bottomAspectRatioFourThree,
            binding.bottomAspectRatios.bottomAspectRatioSixteenNine,
            binding.bottomAspectRatios.bottomAspectRatioOther,
        ).forEach {
            it.setTextColor(Color.WHITE)
        }

        val currentAspectRatioButton = when (currAspectRatio) {
            ASPECT_RATIO_FREE -> binding.bottomAspectRatios.bottomAspectRatioFree
            ASPECT_RATIO_ONE_ONE -> binding.bottomAspectRatios.bottomAspectRatioOneOne
            ASPECT_RATIO_FOUR_THREE -> binding.bottomAspectRatios.bottomAspectRatioFourThree
            ASPECT_RATIO_SIXTEEN_NINE -> binding.bottomAspectRatios.bottomAspectRatioSixteenNine
            else -> binding.bottomAspectRatios.bottomAspectRatioOther
        }
        currentAspectRatioButton.setTextColor(getProperPrimaryColor())
    }

    private fun shouldCropSquare(): Boolean {
        val extras = intent.extras
        return if (extras != null && extras.containsKey(ASPECT_X) && extras.containsKey(ASPECT_Y)) {
            extras.getInt(ASPECT_X) == extras.getInt(ASPECT_Y)
        } else {
            false
        }
    }

    private fun isJpegFile(): Boolean {
        val mimeType = contentResolver.getType(uri ?: return false)
        if (mimeType?.equals("image/jpeg", ignoreCase = true) == true) return true
        val path = uri?.path?.lowercase() ?: ""
        return path.endsWith(".jpg") || path.endsWith(".jpeg")
    }

    private fun isGifFile(): Boolean {
        val mimeType = contentResolver.getType(uri ?: return false)
        if (mimeType?.equals("image/gif", ignoreCase = true) == true) return true
        val path = uri?.path?.lowercase() ?: ""
        return path.endsWith(".gif")
    }

    /**
     * 保存フロー。
     * 1. 回転・反転のみ（クロップなし）: EXIF Orientation による完全ロスレス（0劣化・瞬時保存）
     * 2. クロップあり（スナップONのJPEG）: ajpegtran による完全ロスレスビットストリーム切り出し
     * 3. クロップあり（GIF）: 全フレーム保持のアニメーションロスレス切り出し
     * 4. その他（スナップOFFのJPEG、PNG等）: 最高画質（Quality 100）切り出し
     */
    private fun startSaveFlow(overwrite: Boolean) {
        overwriteRequested = overwrite
        setOldExif()

        val wholeRect = binding.cropImageView.wholeImageRect
        val cropRect = binding.cropImageView.cropRect
        val rotation = binding.cropImageView.rotatedDegrees
        val isFlippedH = binding.cropImageView.isFlippedHorizontally
        val isFlippedV = binding.cropImageView.isFlippedVertically

        // クロップ範囲が画像全体とほぼ同一か（微小な誤差許容）
        val isCropped = cropRect != null && wholeRect != null && (
            abs(cropRect.left - wholeRect.left) > 4 ||
            abs(cropRect.top - wholeRect.top) > 4 ||
            abs(cropRect.width() - wholeRect.width()) > 4 ||
            abs(cropRect.height() - wholeRect.height()) > 4
        )

        // 1. クロップなしで回転・反転のみの場合（JPEG）
        if (!isCropped && isJpegFile()) {
            saveLosslessOrientationOnly(overwrite, rotation, isFlippedH, isFlippedV)
            return
        }

        // 2. GIFアニメーションのクロップ
        if (isGifFile()) {
            val targetRect = cropRect ?: wholeRect ?: Rect(0, 0, 100, 100)
            saveCroppedGif(overwrite, targetRect, rotation)
            return
        }

        // 3. JPEG の完全ロスレスクロップ（スナップON）
        if (isJpegFile() && isSnapEnabled && cropRect != null && wholeRect != null) {
            val snappedRect = LosslessCropHelper.snapRectToMcu(cropRect, wholeRect.width(), wholeRect.height())
            saveLosslessJpegCrop(overwrite, snappedRect, rotation, isFlippedH, isFlippedV)
            return
        }

        // 4. スナップOFF（自由切り抜き）またはその他のフォーマット（PNG等）
        cropImage()
    }

    /**
     * ピクセルデータに一切触れず、EXIF Orientation のみ更新して瞬時保存（完全ロスレス）
     */
    private fun saveLosslessOrientationOnly(
        overwrite: Boolean,
        rotationDegrees: Int,
        isFlippedH: Boolean,
        isFlippedV: Boolean
    ) {
        toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            try {
                if (overwrite) {
                    val success = ExifHelper.saveLosslessOrientation(
                        context = this,
                        targetUri = saveUri,
                        rotationDegrees = rotationDegrees,
                        isFlippedHorizontally = isFlippedH,
                        isFlippedVertically = isFlippedV
                    )
                    runOnUiThread {
                        if (success) {
                            scanFinalPath(saveUri.path ?: "")
                        } else {
                            // 失敗時は通常保存にフォールバック
                            cropImage()
                        }
                    }
                } else {
                    resolveSaveAsPath { destPath ->
                        ensureBackgroundThread {
                            try {
                                val destFile = File(destPath)
                                copyUriToFile(uri!!, destFile)
                                val destUri = Uri.fromFile(destFile)
                                val success = ExifHelper.saveLosslessOrientation(
                                    context = this,
                                    targetUri = destUri,
                                    rotationDegrees = rotationDegrees,
                                    isFlippedHorizontally = isFlippedH,
                                    isFlippedVertically = isFlippedV
                                )
                                runOnUiThread {
                                    if (success) {
                                        scanFinalPath(destPath)
                                    } else {
                                        cropImage()
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread { cropImage() }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { cropImage() }
            }
        }
    }

    /**
     * ajpegtran による完全ロスレスクロップ
     */
    private fun saveLosslessJpegCrop(
        overwrite: Boolean,
        cropRect: Rect,
        rotationDegrees: Int,
        isFlippedH: Boolean,
        isFlippedV: Boolean
    ) {
        toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            try {
                val tempOutputFile = File(cacheDir, "lossless_crop_${System.currentTimeMillis()}.jpg")
                val tempOutputUri = Uri.fromFile(tempOutputFile)

                val cropSuccess = LosslessCropHelper.cropJpegLossless(
                    context = this,
                    inputUri = uri!!,
                    outputUri = tempOutputUri,
                    rect = cropRect
                )

                if (!cropSuccess || !tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                    // ネイティブ切り出し失敗時は通常保存へ安全にフォールバック
                    runOnUiThread { cropImage() }
                    return@ensureBackgroundThread
                }

                // 回転・反転が指定されている場合はEXIFを更新
                if (rotationDegrees != 0 || isFlippedH || isFlippedV) {
                    ExifHelper.saveLosslessOrientation(
                        context = this,
                        targetUri = tempOutputUri,
                        rotationDegrees = rotationDegrees,
                        isFlippedHorizontally = isFlippedH,
                        isFlippedVertically = isFlippedV
                    )
                }

                if (overwrite) {
                    resolveUriScheme(
                        uri = saveUri,
                        onPath = { path ->
                            ensureWritablePath(targetPath = path, confirmOverwrite = false) {
                                val targetFile = File(it)
                                tempOutputFile.copyTo(targetFile, overwrite = true)
                                tempOutputFile.delete()
                                runOnUiThread { scanFinalPath(it) }
                            }
                        },
                        onContentUri = { contentUri ->
                            contentResolver.openOutputStream(contentUri, "wt")?.use { out ->
                                FileInputStream(tempOutputFile).use { input ->
                                    input.copyTo(out)
                                }
                            }
                            tempOutputFile.delete()
                            runOnUiThread {
                                setResult(RESULT_OK, intent)
                                toast(org.fossify.commons.R.string.file_saved)
                                finish()
                            }
                        }
                    )
                } else {
                    resolveSaveAsPath { destPath ->
                        ensureBackgroundThread {
                            val targetFile = File(destPath)
                            tempOutputFile.copyTo(targetFile, overwrite = true)
                            tempOutputFile.delete()
                            runOnUiThread { scanFinalPath(destPath) }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { cropImage() }
            }
        }
    }

    /**
     * GIFアニメーションの全フレームロスレスクロップ
     */
    private fun saveCroppedGif(overwrite: Boolean, cropRect: Rect, rotationDegrees: Int) {
        toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            try {
                val tempOutputFile = File(cacheDir, "cropped_gif_${System.currentTimeMillis()}.gif")
                val fos = FileOutputStream(tempOutputFile)

                val success = GifCropHelper.cropGif(
                    context = this,
                    inputUri = uri!!,
                    outputStream = fos,
                    cropRect = cropRect,
                    rotationDegrees = rotationDegrees
                )

                if (!success || !tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                    runOnUiThread { cropImage() }
                    return@ensureBackgroundThread
                }

                if (overwrite) {
                    resolveUriScheme(
                        uri = saveUri,
                        onPath = { path ->
                            ensureWritablePath(targetPath = path, confirmOverwrite = false) {
                                val targetFile = File(it)
                                tempOutputFile.copyTo(targetFile, overwrite = true)
                                tempOutputFile.delete()
                                runOnUiThread { scanFinalPath(it) }
                            }
                        },
                        onContentUri = { contentUri ->
                            contentResolver.openOutputStream(contentUri, "wt")?.use { out ->
                                FileInputStream(tempOutputFile).use { input ->
                                    input.copyTo(out)
                                }
                            }
                            tempOutputFile.delete()
                            runOnUiThread {
                                setResult(RESULT_OK, intent)
                                toast(org.fossify.commons.R.string.file_saved)
                                finish()
                            }
                        }
                    )
                } else {
                    resolveSaveAsPath { destPath ->
                        ensureBackgroundThread {
                            val targetFile = File(destPath)
                            tempOutputFile.copyTo(targetFile, overwrite = true)
                            tempOutputFile.delete()
                            runOnUiThread { scanFinalPath(destPath) }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { cropImage() }
            }
        }
    }

    private fun copyUriToFile(sourceUri: Uri, destFile: File) {
        val input: InputStream? = if (sourceUri.scheme == "content") {
            contentResolver.openInputStream(sourceUri)
        } else {
            FileInputStream(File(sourceUri.path ?: ""))
        }
        input?.use { inStream ->
            FileOutputStream(destFile).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    override fun onImageCropped(bitmap: Bitmap?, error: Exception?) {
        if (isFinishing || isDestroyed) return
        if (error != null || bitmap == null) {
            toast("${getString(R.string.image_editing_failed)}: ${error?.message}")
            return
        }

        setOldExif()

        if (isSharingBitmap) {
            isSharingBitmap = false
            shareBitmap(bitmap)
            return
        }

        if (isCropIntent) {
            resolveUriScheme(
                uri = saveUri,
                onPath = { saveBitmapToPath(bitmap, it, true) },
                onContentUri = {
                    saveBitmapToContentUri(bitmap, it, showSavingToast = true, isCropCommit = true)
                }
            )
            return
        }

        saveBitmap(overwriteRequested, bitmap, showSavingToast = true)
    }

    private fun shareImage() {
        ensureBackgroundThread {
            if (binding.cropImageView.isVisible()) {
                isSharingBitmap = true
                runOnUiThread {
                    cropImage()
                }
            }
        }
    }

    private fun shareBitmap(bitmap: Bitmap) {
        writeBitmapToCache(saveUri, bitmap) {
            if (it != null) {
                sharePathIntent(it, BuildConfig.APPLICATION_ID)
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        }
    }

    private fun resolveSaveAsPath(callback: (String) -> Unit) {
        runOnUiThread {
            resolveUriScheme(
                uri = saveUri,
                onPath = {
                    SaveAsDialog(this, it, true, callback = callback)
                },
                onContentUri = {
                    val (path, append) = proposeNewFilePath(it)
                    SaveAsDialog(this, path, append, callback = callback)
                }
            )
        }
    }

    private fun saveBitmap(overwrite: Boolean, bitmap: Bitmap, showSavingToast: Boolean = true) {
        if (overwrite) {
            resolveUriScheme(
                uri = saveUri,
                onPath = { path ->
                    ensureWritablePath(targetPath = path, confirmOverwrite = false) {
                        saveBitmapToPath(bitmap, it, showSavingToast)
                    }
                },
                onContentUri = { contentUri ->
                    saveBitmapToContentUri(bitmap, contentUri, showSavingToast, isCropCommit = false)
                }
            )
        } else {
            resolveSaveAsPath { path ->
                saveBitmapToPath(bitmap, path, showSavingToast)
            }
        }
    }

    private fun finishCropResultForContent(uri: Uri) {
        val result = Intent().apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun saveBitmapToPath(bitmap: Bitmap, path: String, showSavingToast: Boolean) {
        try {
            ensureBackgroundThread {
                val file = File(path)
                val fileDirItem = FileDirItem(path, path.getFilenameFromPath())
                try {
                    val out = FileOutputStream(file)
                    saveBitmapToFile(file, bitmap, out, showSavingToast)
                } catch (e: Exception) {
                    getFileOutputStream(fileDirItem, true) {
                        if (it != null) {
                            saveBitmapToFile(file, bitmap, it, showSavingToast)
                        } else {
                            toast(R.string.image_editing_failed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: OutOfMemoryError) {
            toast(org.fossify.commons.R.string.out_of_memory_error)
        }
    }

    private fun saveBitmapToFile(file: File, bitmap: Bitmap, out: OutputStream, showSavingToast: Boolean) {
        if (showSavingToast) {
            toast(org.fossify.commons.R.string.saving)
        }

        out.use {
            // ユーザー指定の最高画質（Quality 100）で保存
            bitmap.compress(file.absolutePath.getCompressionFormat(), 100, out)
        }

        writeExif(oldExif, file.toUri())
        setResult(RESULT_OK, intent)
        scanFinalPath(file.absolutePath)
    }

    private fun saveBitmapToContentUri(
        bitmap: Bitmap,
        uri: Uri,
        showSavingToast: Boolean,
        isCropCommit: Boolean
    ) {
        if (showSavingToast) {
            toast(org.fossify.commons.R.string.saving)
        }

        ensureBackgroundThread {
            var out: OutputStream? = null
            try {
                out = contentResolver.openOutputStream(uri, "wt")
                    ?: contentResolver.openOutputStream(uri)
                if (out == null) {
                    val (path, append) = proposeNewFilePath(uri)
                    runOnUiThread {
                        SaveAsDialog(this, path, append) { path ->
                            saveBitmapToPath(bitmap, path, showSavingToast)
                        }
                    }
                    return@ensureBackgroundThread
                }

                bitmap.compress(getCompressionFormatFromUri(uri), 100, out)
                out.flush()
                writeExif(oldExif, uri)

                runOnUiThread {
                    if (isCropCommit) {
                        finishCropResultForContent(uri)
                    } else {
                        setResult(RESULT_OK, intent)
                        toast(org.fossify.commons.R.string.file_saved)
                        finish()
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
            } finally {
                try { out?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun editWith() {
        openEditor(uri.toString(), true)
        isEditingWithThirdParty = true
    }

    private fun scanFinalPath(path: String) {
        val paths = arrayListOf(path)
        rescanPaths(paths) {
            fixDateTaken(paths, false)
            setResult(RESULT_OK, intent)
            toast(org.fossify.commons.R.string.file_saved)
            finish()
        }
    }
}
