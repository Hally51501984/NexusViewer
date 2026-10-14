package org.fossify.gallery.activities

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.graphics.drawable.toDrawable
import androidx.exifinterface.media.ExifInterface
import androidx.print.PrintHelper
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.AppBarLayout
import org.fossify.gallery.dialogs.PickDirectoryDialog
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.dialogs.RenameItemDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getDataColumn
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFinalUriFromPath
import org.fossify.commons.extensions.getImageResolution
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.getResolution
import org.fossify.commons.extensions.getUriMimeType
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.getCompressionFormat
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isJpg
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPortrait
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.scanPathRecursively
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.tryGenericMimeType
import org.fossify.commons.extensions.updateBrightness
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.REQUEST_EDIT_IMAGE
import org.fossify.commons.helpers.REQUEST_SET_AS
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databinding.ActivityMediumBinding
import org.fossify.gallery.dialogs.DeleteWithRememberDialog
import org.fossify.gallery.dialogs.SaveAsDialog
import org.fossify.gallery.dialogs.SlideshowDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.fixDateTaken
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getShortcutImage
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.hideSystemUI
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.launchResizeImageDialog
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.restoreRecycleBinPath
import org.fossify.gallery.extensions.saveRotatedImageToFile
import org.fossify.gallery.extensions.setAs
import org.fossify.gallery.extensions.shareMediumPath
import org.fossify.gallery.extensions.showFileOnMap
import org.fossify.gallery.extensions.showSystemUI
import org.fossify.gallery.extensions.toggleFileVisibility
import org.fossify.gallery.extensions.tryCopyMoveFilesTo
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateFavorite
import org.fossify.gallery.extensions.updateFavoritePaths
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.BOTTOM_ACTION_CHANGE_ORIENTATION
import org.fossify.gallery.helpers.BOTTOM_ACTION_COPY
import org.fossify.gallery.helpers.BOTTOM_ACTION_DELETE
import org.fossify.gallery.helpers.BOTTOM_ACTION_EDIT
import org.fossify.gallery.helpers.BOTTOM_ACTION_MOVE
import org.fossify.gallery.helpers.BOTTOM_ACTION_PROPERTIES
import org.fossify.gallery.helpers.BOTTOM_ACTION_RENAME
import org.fossify.gallery.helpers.BOTTOM_ACTION_RESIZE
import org.fossify.gallery.helpers.BOTTOM_ACTION_ROTATE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SET_AS
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHARE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHOW_ON_MAP
import org.fossify.gallery.helpers.BOTTOM_ACTION_SLIDESHOW
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_FAVORITE
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_VISIBILITY
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.DEFAULT_STATIC_IMAGE_ADVANCE_DELAY
import org.fossify.gallery.helpers.DefaultPageTransformer
import org.fossify.gallery.helpers.ExifHelper
import org.fossify.gallery.helpers.FadePageTransformer
import org.fossify.gallery.helpers.GifCropHelper
import org.fossify.gallery.helpers.GO_TO_NEXT_ITEM
import org.fossify.gallery.helpers.GO_TO_PREV_ITEM
import org.fossify.gallery.helpers.HIDE_SYSTEM_UI_DELAY
import org.fossify.gallery.helpers.IS_VIEW_INTENT
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.helpers.LosslessCropHelper
import org.fossify.gallery.helpers.MAX_PRINT_SIDE_SIZE
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.PORTRAIT_PATH
import java.io.File
import java.io.FileOutputStream
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.REPEAT_FOLDER
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.ROTATE_BY_DEVICE_ROTATION
import org.fossify.gallery.helpers.ROTATE_BY_SYSTEM_SETTING
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_NEXT_ITEM
import org.fossify.gallery.helpers.SHOW_PREV_ITEM
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.SLIDESHOW_ANIMATION_FADE
import org.fossify.gallery.helpers.SLIDESHOW_ANIMATION_NONE
import org.fossify.gallery.helpers.SLIDESHOW_ANIMATION_SLIDE
import org.fossify.gallery.helpers.SLIDESHOW_DEFAULT_INTERVAL
import org.fossify.gallery.helpers.SLIDESHOW_FADE_DURATION
import org.fossify.gallery.helpers.SLIDESHOW_SLIDE_DURATION
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.getPermissionToRequest
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import kotlin.math.min

@Suppress("UNCHECKED_CAST")
class ViewPagerActivity : BaseViewerActivity(), ViewPager.OnPageChangeListener, ViewPagerFragment.FragmentListener {
    companion object {
        private const val REQUEST_VIEW_VIDEO = 1
        private const val SAVED_PATH = "current_path"
    }

    private var mPath = ""
    private var mDirectory = ""
    private var mIsFullScreen = false
    private var mPos = -1
    private var mShowAll = false
    private var mIsSlideshowActive = false
    private var mPrevHashcode = 0

    private var mSlideshowHandler = Handler()
    private var mSlideshowInterval = SLIDESHOW_DEFAULT_INTERVAL
    private var mSlideshowMoveBackwards = false
    private var mSlideshowMedia = mutableListOf<Medium>()
    private var mAreSlideShowMediaVisible = false
    private var mRandomSlideshowStopped = false

    private var mImageAdvanceHandler = Handler(Looper.getMainLooper())
    private var mImageAdvanceRunnable: Runnable? = null

    private var mIsOrientationLocked = false

    private var mMediaFiles = ArrayList<Medium>()
    private var mFavoritePaths = ArrayList<String>()
    private var mIgnoredPaths = ArrayList<String>()
    private var mOriginalBrightness: Float? = null
    private var isInPlaceCropping = false
    private var isInPlaceCropSnapEnabled = true

    private val binding by viewBinding(ActivityMediumBinding::inflate)

    override val contentHolder: View
        get() = binding.fragmentHolder

    override val appBarLayout: AppBarLayout
        get() = binding.mediumViewerAppbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(
            padBottomSystem = listOf(
                binding.bottomActions.bottomActionsWrapper,
                binding.cropActionsBar
            ),
        )

        onBackPressedDispatcher.addCallback(this) {
            if (isInPlaceCropping) {
                cancelInPlaceCrop()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        window.decorView.setBackgroundColor(Color.BLACK)

        val savedPath = savedInstanceState?.getString(SAVED_PATH).orEmpty()
        if (isExternalIntent()) {
            // 外部Intent起動: メニュー処理やパーミッションダイアログの待機を全スキップして直ちに初期化！
            initViewPager(savedPath = savedPath)
            window.decorView.post {
                setupOptionsMenu()
                refreshMenuItems()
            }
        } else {
            setupOptionsMenu()
            refreshMenuItems()
            (MediaActivity.mMedia.clone() as ArrayList<ThumbnailItem>).filterIsInstanceTo(mMediaFiles, Medium::class.java)
            requestMediaPermissions {
                initViewPager(savedPath = savedPath)
            }
            initFavorites()
        }

        binding.viewPager.apply {
            isCurrentItemVideo = { getCurrentMedium()?.isVideo() == true || getCurrentFragment() is VideoFragment }
            onTwoFingerSwipe = { showFolderPickerOverlay() }
        }
    }

    private fun showFolderPickerOverlay() {
        PickDirectoryDialog(
            activity = this,
            sourcePath = mDirectory,
            showOtherFolderButton = true,
            showFavoritesBin = false,
            isPickingCopyMoveDestination = false,
            isPickingFolderForWidget = false
        ) { selectedPath ->
            if (selectedPath.isNotEmpty() && selectedPath != mDirectory) {
                mDirectory = selectedPath
                mPath = ""
                mPos = 0
                mMediaFiles.clear()
                refreshViewPager(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasPermission(getPermissionToRequest())) {
            finish()
            return
        }

        initBottomActions()
        mOriginalBrightness = window.updateBrightness(config.maxBrightness, mOriginalBrightness)
        setupOrientation()
        refreshMenuItems()

        val filename = getCurrentMedium()?.name ?: mPath.getFilenameFromPath()
        binding.mediumViewerToolbar.title = filename
        scheduleImageAdvance()
    }

    override fun onPause() {
        super.onPause()
        stopSlideshow()
        cancelImageAdvance()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelImageAdvance()
        ColorModeHelper.resetColorMode(this)

        if (intent.extras?.containsKey(IS_VIEW_INTENT) == true) {
            config.temporarilyShowHidden = false
        }

        if (config.isThirdPartyIntent) {
            config.isThirdPartyIntent = false

            if (intent.extras == null || isExternalIntent()) {
                mMediaFiles.clear()
            }
        }
    }

    fun refreshMenuItems() {
        val currentMedium = getCurrentMedium() ?: return
        currentMedium.isFavorite = mFavoritePaths.contains(currentMedium.path)
        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0

        runOnUiThread {
            val rotationDegrees = getCurrentPhotoFragment()?.mCurrentRotationDegrees ?: 0
            binding.mediumViewerToolbar.menu.apply {
                findItem(R.id.menu_properties)?.isVisible = visibleBottomActions and BOTTOM_ACTION_PROPERTIES == 0
                findItem(R.id.menu_delete)?.isVisible = visibleBottomActions and BOTTOM_ACTION_DELETE == 0
                findItem(R.id.menu_share)?.isVisible = visibleBottomActions and BOTTOM_ACTION_SHARE == 0
                findItem(R.id.menu_edit)?.isVisible = (visibleBottomActions and BOTTOM_ACTION_EDIT == 0 || currentMedium.isGIF()) && !currentMedium.isSVG()
                findItem(R.id.menu_rename)?.isVisible = visibleBottomActions and BOTTOM_ACTION_RENAME == 0
                findItem(R.id.menu_rotate)?.isVisible = currentMedium.isImage() && visibleBottomActions and BOTTOM_ACTION_ROTATE == 0
                findItem(R.id.menu_copy_to_clipboard)?.isVisible = currentMedium.isImage()
                findItem(R.id.menu_copy_to)?.isVisible = visibleBottomActions and BOTTOM_ACTION_COPY == 0
                findItem(R.id.menu_move_to)?.isVisible = visibleBottomActions and BOTTOM_ACTION_MOVE == 0
                findItem(R.id.menu_add_to_favorites)?.isVisible =
                    !currentMedium.isFavorite && visibleBottomActions and BOTTOM_ACTION_TOGGLE_FAVORITE == 0

                findItem(R.id.menu_remove_from_favorites)?.isVisible =
                    currentMedium.isFavorite && visibleBottomActions and BOTTOM_ACTION_TOGGLE_FAVORITE == 0

                findItem(R.id.menu_change_orientation)?.isVisible = rotationDegrees == 0 && visibleBottomActions and BOTTOM_ACTION_CHANGE_ORIENTATION == 0
                findItem(R.id.menu_rotate)?.setShowAsAction(
                    if (rotationDegrees != 0) {
                        MenuItem.SHOW_AS_ACTION_ALWAYS
                    } else {
                        MenuItem.SHOW_AS_ACTION_IF_ROOM
                    }
                )
            }

            updateAutoAdvanceIconColor()

            if (visibleBottomActions != 0) {
                updateBottomActionIcons(currentMedium)
            }
        }
    }

    private fun setupOptionsMenu() {
        binding.mediumViewerToolbar.apply {
            setTitleTextColor(Color.WHITE)
            overflowIcon = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_three_dots_vector, Color.WHITE)
            navigationIcon = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_arrow_left_vector, Color.WHITE)
        }

        updateMenuItemColors(binding.mediumViewerToolbar.menu, forceWhiteIcons = true)
        updateAutoAdvanceIconColor()
        binding.mediumViewerToolbar.setOnMenuItemClickListener { menuItem ->
            if (getCurrentMedium() == null) {
                return@setOnMenuItemClickListener true
            }

            when (menuItem.itemId) {
                R.id.menu_auto_advance -> toggleAutoAdvanceStaticImages()
                R.id.menu_copy_to -> checkMediaManagementAndCopy(true)
                R.id.menu_move_to -> moveFileTo()
                R.id.menu_open_with -> openPath(getCurrentPath(), true)
                R.id.menu_share -> shareMediumPath(getCurrentPath())
                R.id.menu_delete -> checkDeleteConfirmation()
                R.id.menu_rename -> checkMediaManagementAndRename()
                R.id.menu_edit -> openEditor(getCurrentPath())
                R.id.menu_properties -> showProperties()
                R.id.menu_rotate_right -> rotateImage(90)
                R.id.menu_rotate_left -> rotateImage(-90)
                R.id.menu_rotate_one_eighty -> rotateImage(180)
                R.id.menu_add_to_favorites -> toggleFavorite()
                R.id.menu_remove_from_favorites -> toggleFavorite()
                R.id.menu_force_portrait -> toggleOrientation(SCREEN_ORIENTATION_PORTRAIT)
                R.id.menu_force_landscape -> toggleOrientation(SCREEN_ORIENTATION_LANDSCAPE)
                R.id.menu_force_landscape_reverse -> toggleOrientation(SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
                R.id.menu_default_orientation -> toggleOrientation(SCREEN_ORIENTATION_UNSPECIFIED)
                R.id.menu_settings -> launchSettings()
                R.id.menu_copy_to_clipboard -> copyImageToClipboard()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        binding.mediumViewerToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    /**
     * 静止画5秒自動送りモードのON/OFF切り替え
     */
    private fun toggleAutoAdvanceStaticImages() {
        config.autoAdvanceStaticImages = !config.autoAdvanceStaticImages
        updateAutoAdvanceIconColor()
        if (config.autoAdvanceStaticImages) {
            toast(R.string.auto_advance_on)
            scheduleImageAdvance()
        } else {
            toast(R.string.auto_advance_off)
            cancelImageAdvance()
        }
    }

    private fun updateAutoAdvanceIconColor() {
        val color = if (config.autoAdvanceStaticImages) getProperPrimaryColor() else Color.WHITE
        binding.mediumViewerToolbar.menu.findItem(R.id.menu_auto_advance)?.icon?.applyColorFilter(color)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE && resultCode == Activity.RESULT_OK && resultData != null) {
            mPos = -1
            mPrevHashcode = 0
            refreshViewPager()
        } else if (requestCode == REQUEST_SET_AS && resultCode == Activity.RESULT_OK) {
            toast(R.string.wallpaper_set_successfully)
        } else if (requestCode == REQUEST_VIEW_VIDEO && resultCode == Activity.RESULT_OK && resultData != null) {
            if (resultData.getBooleanExtra(GO_TO_NEXT_ITEM, false)) {
                goToNextItem()
            } else if (resultData.getBooleanExtra(GO_TO_PREV_ITEM, false)) {
                goToPrevItem()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        initBottomActionsLayout()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_PATH, getCurrentPath())
    }

    private fun initViewPager(savedPath: String) {
        val uri = intent.data
        if (uri != null && savedPath.isEmpty()) {
            val uriString = uri.toString()
            if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
                mPath = intent.extras!!.getString(REAL_FILE_PATH).orEmpty()
            }
            if (mPath.isEmpty() && uri.scheme == "file") {
                mPath = uri.path.orEmpty()
            }
            if (mPath.isEmpty()) {
                val decoded = try { Uri.decode(uriString) } catch (e: Exception) { uriString }
                if (decoded.contains("/storage/")) {
                    val guessedPath = decoded.substring(decoded.indexOf("/storage/"))
                    if (getDoesFilePathExist(guessedPath)) {
                        mPath = guessedPath
                    }
                }
            }
            if (mPath.isEmpty()) {
                mPath = applicationContext.getRealPathFromURI(uri).orEmpty()
            }
            if (mPath.isEmpty()) {
                mPath = getDataColumn(uri).orEmpty()
            }
            if (mPath.isEmpty()) {
                mPath = uri.path.orEmpty()
            }
            if (mPath.isEmpty()) {
                mPath = uriString
            }
        } else if (savedPath.isNotEmpty()) {
            mPath = savedPath
        } else {
            try {
                mPath = intent.getStringExtra(PATH).orEmpty()
                mShowAll = config.showAll && (mPath.isNotEmpty() && !mPath.startsWith(recycleBinPath))
            } catch (e: Exception) {
                showErrorToast(e)
                finish()
                return
            }
        }

        if (mPath.isEmpty()) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        if (mPath.isPortrait() && getPortraitPath() == "") {
            val newIntent = Intent(this, ViewPagerActivity::class.java)
            newIntent.putExtras(intent!!.extras!!)
            newIntent.putExtra(PORTRAIT_PATH, mPath)
            newIntent.putExtra(PATH, "${mPath.getParentPath().getParentPath()}/${mPath.getFilenameFromPath()}")

            startActivity(newIntent)
            finish()
            return
        }

        val isContent = mPath.startsWith("content://") || (uri != null && uri.scheme == "content")
        if (!isContent && !getDoesFilePathExist(mPath) && getPortraitPath() == "") {
            finish()
            return
        }

        showSystemUI()

        if (intent.getBooleanExtra(SKIP_AUTHENTICATION, false) || isExternalIntent()) {
            initContinue()
        } else {
            handleLockedFolderOpening(mPath.getParentPath()) { success ->
                if (success) {
                    initContinue()
                } else {
                    finish()
                }
            }
        }
    }

    private fun initContinue() {
        if (intent.extras?.containsKey(IS_VIEW_INTENT) == true) {
            if (isShowHiddenFlagNeeded()) {
                if (!config.isHiddenPasswordProtectionOn) {
                    config.temporarilyShowHidden = true
                }
            }

            config.isThirdPartyIntent = true
        }

        val isShowingFavorites = intent.getBooleanExtra(SHOW_FAVORITES, false)
        val isShowingRecycleBin = intent.getBooleanExtra(SHOW_RECYCLE_BIN, false)
        mDirectory = when {
            isShowingFavorites -> FAVORITES
            isShowingRecycleBin -> RECYCLE_BIN
            else -> mPath.getParentPath()
        }
        binding.mediumViewerToolbar.title = mPath.getFilenameFromPath()

        binding.viewPager.onGlobalLayout {
            if (!isDestroyed) {
                checkSlideshowOnEnter()
            }
        }

        var isFastScanSuccess = false
        if (isExternalIntent() && mPath.isNotEmpty() && mDirectory != FAVORITES && mDirectory != RECYCLE_BIN) {
            val parentFile = File(mDirectory)
            if (parentFile.exists() && parentFile.isDirectory) {
                val mediaFiles = ArrayList<Medium>()
                val files = parentFile.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && (file.absolutePath.isMediaFile() || file.name.endsWith(".wav", true) || file.name.endsWith(".mp3", true) || file.name.endsWith(".flac", true) || file.name.endsWith(".m4a", true) || file.name.endsWith(".aac", true) || file.name.endsWith(".ogg", true))) {
                            val filename = file.name
                            val path = file.absolutePath
                            val type = getTypeFromPath(path)
                            val medium = Medium(null, filename, path, mDirectory, file.lastModified(), file.lastModified(), file.length(), type, 0, false, 0L, 0L)
                            mediaFiles.add(medium)
                        }
                    }
                }

                if (mediaFiles.isNotEmpty()) {
                    MediaFetcher(applicationContext).sortMedia(mediaFiles, config.getFolderSorting(mDirectory))
                    mMediaFiles = mediaFiles
                    mPos = mMediaFiles.indexOfFirst { it.path == mPath }
                    if (mPos == -1) {
                        val targetMedium = Medium(null, mPath.getFilenameFromPath(), mPath, mDirectory, 0, 0, 0, getTypeFromPath(mPath), 0, false, 0L, 0L)
                        mMediaFiles.add(0, targetMedium)
                        mPos = 0
                    }
                    refreshUI(mMediaFiles, refetchViewPagerPosition = false)
                    isFastScanSuccess = true
                }
            }
        }

        // Fast Scan に失敗した場合（SAF等のURIで直接listFilesできない場合）のフォールバック
        if (!isFastScanSuccess && mMediaFiles.isEmpty() && mPath.isNotEmpty() && mDirectory != FAVORITES) {
            val filename = mPath.getFilenameFromPath()
            val folder = mPath.getParentPath()
            val type = getTypeFromPath(mPath)
            val medium = Medium(null, filename, mPath, folder, 0, 0, 0, type, 0, false, 0L, 0L)
            mMediaFiles.add(medium)
            gotMedia(mMediaFiles as ArrayList<ThumbnailItem>, refetchViewPagerPosition = true)
        }

        if (!isFastScanSuccess) {
            refreshViewPager(true)
        }
        binding.viewPager.offscreenPageLimit = 2

        if (config.blackBackground) {
            binding.fragmentHolder.background = Color.BLACK.toDrawable()
            binding.viewPager.background = Color.BLACK.toDrawable()
        }

        if (config.hideSystemUI) {
            binding.viewPager.onGlobalLayout {
                Handler().postDelayed({
                    fragmentClicked()
                }, HIDE_SYSTEM_UI_DELAY)
            }
        }

        if (
            intent.action == "com.android.camera.action.REVIEW"
            || intent.action == MediaStore.ACTION_REVIEW
        ) {
            ensureBackgroundThread {
                if (mediaDB.getMediaFromPath(mPath).isEmpty()) {
                    val filename = mPath.getFilenameFromPath()
                    val parent = mPath.getParentPath()
                    val type = getTypeFromPath(mPath)
                    val isFavorite = favoritesDB.isFavorite(mPath)
                    val duration = if (type == TYPE_VIDEOS) getDuration(mPath) ?: 0 else 0
                    val ts = System.currentTimeMillis()
                    val medium = Medium(null, filename, mPath, parent, ts, ts, File(mPath).length(), type, duration, isFavorite, 0, 0L)
                    mediaDB.insert(medium)
                }
            }
        }
    }

    private fun getTypeFromPath(path: String): Int {
        return when {
            path.isVideoFast() || path.endsWith(".wav", true) -> TYPE_VIDEOS
            path.isGif() -> TYPE_GIFS
            path.isSvg() -> TYPE_SVGS
            path.isRawFast() -> TYPE_RAWS
            path.isPortrait() -> TYPE_PORTRAITS
            else -> TYPE_IMAGES
        }
    }

    private fun initBottomActions() {
        initBottomActionButtons()
        initBottomActionsLayout()
    }

    private fun initFavorites() {
        ensureBackgroundThread {
            mFavoritePaths = getFavoritePaths()
        }
    }

    private fun setupOrientation() {
        if (!mIsOrientationLocked) {
            if (config.screenRotation == ROTATE_BY_DEVICE_ROTATION) {
                requestedOrientation = SCREEN_ORIENTATION_SENSOR
            } else if (config.screenRotation == ROTATE_BY_SYSTEM_SETTING) {
                requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun updatePagerItems(media: MutableList<Medium>) {
        val pagerAdapter = MyPagerAdapter(this, supportFragmentManager, media)
        if (!isDestroyed) {
            pagerAdapter.shouldInitFragment = mPos < 5
            binding.viewPager.apply {
                // must remove the listener before changing adapter, otherwise it might cause `mPos` to be set to 0
                removeOnPageChangeListener(this@ViewPagerActivity)
                adapter = pagerAdapter
                pagerAdapter.shouldInitFragment = true
                addOnPageChangeListener(this@ViewPagerActivity)
                currentItem = mPos
            }
        }
    }

    private fun checkSlideshowOnEnter() {
        if (intent.getBooleanExtra(SLIDESHOW_START_ON_ENTER, false)) {
            initSlideshow()
        }
    }

    private fun initSlideshow() {
        SlideshowDialog(this) {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        if (getMediaForSlideshow()) {
            binding.viewPager.onGlobalLayout {
                if (!isDestroyed) {
                    if (config.slideshowAnimation == SLIDESHOW_ANIMATION_FADE) {
                        binding.viewPager.setPageTransformer(false, FadePageTransformer())
                    }

                    hideSystemUI()
                    if (!mIsFullScreen) {
                        mIsFullScreen = true
                        fullscreenToggled()
                    }
                    mRandomSlideshowStopped = false
                    mSlideshowInterval = config.slideshowInterval
                    mSlideshowMoveBackwards = config.slideshowMoveBackwards
                    mIsSlideshowActive = true
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    scheduleSwipe()
                }
            }
        }
    }

    private fun goToNextMedium(forward: Boolean) {
        val oldPosition = binding.viewPager.currentItem
        val newPosition = if (forward) oldPosition + 1 else oldPosition - 1
        if (newPosition == -1 || newPosition > binding.viewPager.adapter!!.count - 1) {
            slideshowEnded(forward)
        } else {
            binding.viewPager.setCurrentItem(newPosition, false)
        }
    }

    private fun animatePagerTransition(forward: Boolean) {
        val oldPosition = binding.viewPager.currentItem
        val animator = ValueAnimator.ofInt(0, binding.viewPager.width)
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                if (binding.viewPager.isFakeDragging) {
                    try {
                        binding.viewPager.endFakeDrag()
                    } catch (ignored: Exception) {
                        stopSlideshow()
                    }

                    if (binding.viewPager.currentItem == oldPosition) {
                        slideshowEnded(forward)
                    }
                }
            }

            override fun onAnimationCancel(animation: Animator) {
                binding.viewPager.endFakeDrag()
            }

            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationRepeat(animation: Animator) {}
        })

        if (config.slideshowAnimation == SLIDESHOW_ANIMATION_SLIDE) {
            animator.interpolator = DecelerateInterpolator()
            animator.duration = SLIDESHOW_SLIDE_DURATION
        } else {
            animator.duration = SLIDESHOW_FADE_DURATION
        }

        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            var oldDragPosition = 0
            override fun onAnimationUpdate(animation: ValueAnimator) {
                if (binding.viewPager.isFakeDragging == true) {
                    val dragPosition = animation.animatedValue as Int
                    val dragOffset = dragPosition - oldDragPosition
                    oldDragPosition = dragPosition
                    try {
                        binding.viewPager.fakeDragBy(dragOffset * (if (forward) -1f else 1f))
                    } catch (e: Exception) {
                        stopSlideshow()
                    }
                }
            }
        })

        binding.viewPager.beginFakeDrag()
        animator.start()
    }

    private fun slideshowEnded(forward: Boolean) {
        if (config.loopSlideshow) {
            if (forward) {
                binding.viewPager.setCurrentItem(0, false)
            } else {
                binding.viewPager.setCurrentItem(binding.viewPager.adapter!!.count - 1, false)
            }
        } else {
            stopSlideshow()
            toast(R.string.slideshow_ended)
        }
    }

    private fun stopSlideshow() {
        if (mIsSlideshowActive) {
            binding.viewPager.setPageTransformer(false, DefaultPageTransformer())
            mIsSlideshowActive = false
            showSystemUI()
            mSlideshowHandler.removeCallbacksAndMessages(null)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mAreSlideShowMediaVisible = false

            if (config.slideshowRandomOrder) {
                mRandomSlideshowStopped = true
            }
        }
    }

    private fun scheduleImageAdvance() {
        cancelImageAdvance()
        if (mIsSlideshowActive || isInPlaceCropping) return
        if (!config.autoAdvanceStaticImages && config.videoRepeatMode != REPEAT_FOLDER) return

        val medium = getCurrentMedium() ?: return
        // 静止画のみが対象: 動画やGIFアニメーションは自動移行しない
        if (medium.isVideo() || medium.isGIF()) return

        val totalCount = binding.viewPager.adapter?.count ?: 0
        if (totalCount <= 1) return

        val delaySeconds = config.staticImageAdvanceDelay.takeIf { it > 0 } ?: DEFAULT_STATIC_IMAGE_ADVANCE_DELAY

        val runnable = Runnable {
            if (!isDestroyed && !isFinishing && (config.autoAdvanceStaticImages || config.videoRepeatMode == REPEAT_FOLDER)) {
                val current = getCurrentMedium()
                if (current != null && !current.isVideo() && !current.isGIF()) {
                    goToNextItem()
                }
            }
        }
        mImageAdvanceRunnable = runnable
        mImageAdvanceHandler.postDelayed(runnable, delaySeconds * 1000L)
    }

    private fun cancelImageAdvance() {
        mImageAdvanceRunnable?.let {
            mImageAdvanceHandler.removeCallbacks(it)
        }
        mImageAdvanceRunnable = null
    }

    private fun scheduleSwipe() {
        mSlideshowHandler.removeCallbacksAndMessages(null)
        if (mIsSlideshowActive) {
            if (getCurrentMedium()!!.isImage() || getCurrentMedium()!!.isGIF() || getCurrentMedium()!!.isPortrait()) {
                mSlideshowHandler.postDelayed({
                    if (mIsSlideshowActive && !isDestroyed) {
                        swipeToNextMedium()
                    }
                }, mSlideshowInterval * 1000L)
            } else {
                (getCurrentFragment() as? VideoFragment)!!.playVideo()
            }
        }
    }

    private fun swipeToNextMedium() {
        if (config.slideshowAnimation == SLIDESHOW_ANIMATION_NONE) {
            goToNextMedium(!mSlideshowMoveBackwards)
        } else {
            animatePagerTransition(!mSlideshowMoveBackwards)
        }
    }

    private fun getMediaForSlideshow(): Boolean {
        mSlideshowMedia = mMediaFiles.filter {
            it.isImage() || it.isPortrait() || (config.slideshowIncludeVideos && it.isVideo() || (config.slideshowIncludeGIFs && it.isGIF()))
        }.toMutableList()

        if (config.slideshowRandomOrder) {
            mSlideshowMedia.shuffle()
            mPos = 0
        } else {
            mPath = getCurrentPath()
            mPos = getPositionInList(mSlideshowMedia)
        }

        return if (mSlideshowMedia.isEmpty()) {
            toast(R.string.no_media_for_slideshow)
            false
        } else {
            updatePagerItems(mSlideshowMedia)
            mAreSlideShowMediaVisible = true
            true
        }
    }

    private fun moveFileTo() {
        handleDeletePasswordProtection {
            checkMediaManagementAndCopy(false)
        }
    }

    private fun checkMediaManagementAndCopy(isCopyOperation: Boolean) {
        handleMediaManagementPrompt {
            copyMoveTo(isCopyOperation)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val currPath = getCurrentPath()
        if (!isCopyOperation && currPath.startsWith(recycleBinPath)) {
            toast(org.fossify.commons.R.string.moving_recycle_bin_items_disabled, Toast.LENGTH_LONG)
            return
        }

        val fileDirItems = arrayListOf(FileDirItem(currPath, currPath.getFilenameFromPath()))
        tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            val newPath = "$it/${currPath.getFilenameFromPath()}"
            rescanPaths(arrayListOf(newPath)) {
                fixDateTaken(arrayListOf(newPath), false)
            }

            config.tempFolderPath = ""
            if (!isCopyOperation) {
                refreshViewPager()
                updateFavoritePaths(fileDirItems, it)
            }
        }
    }


    private fun rotateImage(degrees: Int) {
        val currentPath = getCurrentPath()
        if (needsStupidWritePermissions(currentPath)) {
            handleSAFDialog(currentPath) {
                if (it) {
                    rotateBy(degrees)
                }
            }
        } else {
            rotateBy(degrees)
        }
    }

    private fun rotateBy(degrees: Int) {
        getCurrentPhotoFragment()?.rotateImageViewBy(degrees)
        refreshMenuItems()
    }

    private fun toggleOrientation(orientation: Int) {
        requestedOrientation = orientation
        mIsOrientationLocked = orientation != SCREEN_ORIENTATION_UNSPECIFIED
        refreshMenuItems()
    }

    private fun getChangeOrientationIcon(): Int {
        return if (mIsOrientationLocked) {
            if (requestedOrientation == SCREEN_ORIENTATION_PORTRAIT) {
                org.fossify.commons.R.drawable.ic_orientation_portrait_vector
            } else {
                org.fossify.commons.R.drawable.ic_orientation_landscape_vector
            }
        } else {
            org.fossify.commons.R.drawable.ic_orientation_auto_vector
        }
    }



    private fun getCurrentPhotoFragment() = getCurrentFragment() as? PhotoFragment

    private fun getPortraitPath() = intent.getStringExtra(PORTRAIT_PATH) ?: ""

    private fun isShowHiddenFlagNeeded(): Boolean {
        val file = File(mPath)
        if (file.isHidden) {
            return true
        }

        var parent = file.parentFile ?: return false
        while (true) {
            if (parent.isHidden || parent.list()?.any { it.startsWith(NOMEDIA) } == true) {
                return true
            }

            if (parent.absolutePath == "/") {
                break
            }
            parent = parent.parentFile ?: return false
        }

        return false
    }

    private fun getCurrentFragment() = (binding.viewPager.adapter as? MyPagerAdapter)?.getCurrentFragment(binding.viewPager.currentItem)

    private fun showProperties() {
        if (getCurrentMedium() != null) {
            PropertiesDialog(this, getCurrentPath(), false)
        }
    }

    private fun initBottomActionsLayout() {
        if (config.bottomActions) {
            binding.bottomActions.root.beVisible()
        } else {
            binding.bottomActions.root.beGone()
        }
    }

    private fun initBottomActionButtons() {
        val currentMedium = getCurrentMedium()
        val isEditable = currentMedium?.let { it.isImage() || it.isGIF() } == true

        // 1. 左90°回転 (直接ワンタップ上書き保存)
        binding.bottomActions.bottomRotate.beVisibleIf(isEditable)
        binding.bottomActions.bottomRotate.setOnLongClickListener { toast(R.string.rotate_left); true }
        binding.bottomActions.bottomRotate.setOnClickListener {
            rotateCurrentPhotoLeft()
        }

        // 2. 鏡像反転 (直接ワンタップ上書き保存)
        binding.bottomActions.bottomFlipHorizontal.beVisibleIf(isEditable)
        binding.bottomActions.bottomFlipHorizontal.setOnLongClickListener { toast(R.string.flip_horizontally); true }
        binding.bottomActions.bottomFlipHorizontal.setOnClickListener {
            flipCurrentPhotoHorizontal()
        }

        // 3. トリミング (Viewerインプレースクロップ)
        binding.bottomActions.bottomCrop.beVisibleIf(isEditable)
        binding.bottomActions.bottomCrop.setOnLongClickListener { toast(R.string.crop); true }
        binding.bottomActions.bottomCrop.setOnClickListener {
            startInPlaceCrop()
        }

        // 4. 削除
        binding.bottomActions.bottomDelete.beVisible()
        binding.bottomActions.bottomDelete.setOnLongClickListener { toast(org.fossify.commons.R.string.delete); true }
        binding.bottomActions.bottomDelete.setOnClickListener {
            checkDeleteConfirmation()
        }

        // 5. 共有
        binding.bottomActions.bottomShare.beVisible()
        binding.bottomActions.bottomShare.setOnLongClickListener { toast(org.fossify.commons.R.string.share); true }
        binding.bottomActions.bottomShare.setOnClickListener {
            shareMediumPath(getCurrentPath())
        }

        // 6. お気に入り
        binding.bottomActions.bottomFavorite.beVisibleIf(currentMedium?.getIsInRecycleBin() == false)
        binding.bottomActions.bottomFavorite.setOnLongClickListener { toast(R.string.toggle_favorite); true }
        binding.bottomActions.bottomFavorite.setOnClickListener {
            toggleFavorite()
        }

        // インプレース・トリミングバーの初期化
        binding.cropCancel.setOnClickListener {
            cancelInPlaceCrop()
        }
        binding.cropSnapToggle.setOnClickListener {
            toggleInPlaceCropSnap()
        }
        binding.cropConfirm.setOnClickListener {
            confirmInPlaceCrop()
        }
    }

    private fun updateBottomActionIcons(medium: Medium?) {
        if (medium == null) {
            return
        }

        val favoriteIcon =
            if (medium.isFavorite) org.fossify.commons.R.drawable.ic_star_vector else org.fossify.commons.R.drawable.ic_star_outline_vector
        binding.bottomActions.bottomFavorite.setImageResource(favoriteIcon)

        val isEditable = medium.isImage() || medium.isGIF()
        binding.bottomActions.bottomRotate.beVisibleIf(isEditable)
        binding.bottomActions.bottomFlipHorizontal.beVisibleIf(isEditable)
        binding.bottomActions.bottomCrop.beVisibleIf(isEditable)
    }

    /**
     * 左90°回転の直接ワンタップ上書き保存
     */
    private fun rotateCurrentPhotoLeft() {
        val currentPath = getCurrentPath()
        if (currentPath.isEmpty()) return
        toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            if (currentPath.isJpg()) {
                val success = ExifHelper.saveLosslessOrientation(
                    context = this,
                    targetUri = Uri.fromFile(File(currentPath)),
                    rotationDegrees = -90,
                    isFlippedHorizontally = false,
                    isFlippedVertically = false
                )
                runOnUiThread {
                    if (success) {
                        getCurrentPhotoFragment()?.reloadCurrentPhoto()
                        toast(R.string.rotate_saved)
                    } else {
                        toast(R.string.image_editing_failed)
                    }
                }
            } else if (currentPath.isGif()) {
                rotateOrFlipGifDirect(currentPath, -90, false)
            } else {
                rotateAndOverwriteBitmap(currentPath, -90, false)
            }
        }
    }

    /**
     * 鏡像反転（水平反転）の直接ワンタップ上書き保存
     */
    private fun flipCurrentPhotoHorizontal() {
        val currentPath = getCurrentPath()
        if (currentPath.isEmpty()) return
        toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            if (currentPath.isJpg()) {
                val success = ExifHelper.saveLosslessOrientation(
                    context = this,
                    targetUri = Uri.fromFile(File(currentPath)),
                    rotationDegrees = 0,
                    isFlippedHorizontally = true,
                    isFlippedVertically = false
                )
                runOnUiThread {
                    if (success) {
                        getCurrentPhotoFragment()?.reloadCurrentPhoto()
                        toast(R.string.flip_saved)
                    } else {
                        toast(R.string.image_editing_failed)
                    }
                }
            } else if (currentPath.isGif()) {
                rotateOrFlipGifDirect(currentPath, 0, true)
            } else {
                rotateAndOverwriteBitmap(currentPath, 0, true)
            }
        }
    }

    /**
     * GIFアニメーションの全フレーム回転・反転直接上書き処理
     */
    private fun rotateOrFlipGifDirect(path: String, rotationDegrees: Int, flipH: Boolean) {
        try {
            val targetFile = File(path)
            val tempFile = File(cacheDir, "gif_trans_${System.currentTimeMillis()}.gif")
            val fos = FileOutputStream(tempFile)
            val success = GifCropHelper.rotateOrFlipGif(
                context = this,
                inputUri = Uri.fromFile(targetFile),
                outputStream = fos,
                rotationDegrees = rotationDegrees,
                flipHorizontal = flipH
            )
            if (success && tempFile.exists() && tempFile.length() > 0) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                runOnUiThread {
                    getCurrentPhotoFragment()?.reloadCurrentPhoto()
                    toast(if (flipH) R.string.flip_saved else R.string.rotate_saved)
                }
            } else {
                tempFile.delete()
                runOnUiThread { toast(R.string.image_editing_failed) }
            }
        } catch (e: Exception) {
            runOnUiThread { toast(R.string.image_editing_failed) }
        }
    }

    /**
     * JPEG以外の画像フォーマット用直接上書き関数
     */
    private fun rotateAndOverwriteBitmap(path: String, rotationDegrees: Int, flipH: Boolean) {
        try {
            val file = File(path)
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap == null) {
                runOnUiThread { toast(R.string.image_editing_failed) }
                return
            }
            val matrix = android.graphics.Matrix()
            if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())
            if (flipH) matrix.postScale(-1f, 1f)
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            FileOutputStream(file).use { out ->
                transformed.compress(path.getCompressionFormat(), 100, out)
            }
            runOnUiThread {
                getCurrentPhotoFragment()?.reloadCurrentPhoto()
                toast(if (flipH) R.string.flip_saved else R.string.rotate_saved)
            }
        } catch (e: Exception) {
            runOnUiThread { toast(R.string.image_editing_failed) }
        }
    }

    /**
     * インプレース・トリミングの開始
     */
    private fun startInPlaceCrop() {
        val currentPath = getCurrentPath()
        if (currentPath.isEmpty()) return
        cancelImageAdvance()
        isInPlaceCropping = true
        isInPlaceCropSnapEnabled = true
        updateCropSnapButton()

        binding.cropImageView.beVisible()
        binding.cropImageView.setImageUriAsync(Uri.fromFile(File(currentPath)))
        binding.cropImageView.guidelines = CropImageView.Guidelines.ON

        binding.cropActionsBar.beVisible()
        binding.bottomActions.root.beGone()
        binding.mediumViewerAppbar.beGone()
    }

    /**
     * インプレース・トリミングのキャンセル
     */
    private fun cancelInPlaceCrop() {
        isInPlaceCropping = false
        binding.cropImageView.beGone()
        binding.cropImageView.setImageBitmap(null)
        binding.cropActionsBar.beGone()
        binding.bottomActions.root.beVisible()
        binding.mediumViewerAppbar.beVisible()
        scheduleImageAdvance()
    }

    /**
     * ロスレス吸着（16pxスナップ）の切り替え
     */
    private fun toggleInPlaceCropSnap() {
        isInPlaceCropSnapEnabled = !isInPlaceCropSnapEnabled
        updateCropSnapButton()
        toast(if (isInPlaceCropSnapEnabled) R.string.lossless_snap_on else R.string.lossless_snap_off)
    }

    private fun updateCropSnapButton() {
        val color = if (isInPlaceCropSnapEnabled) getProperPrimaryColor() else Color.WHITE
        binding.cropSnapToggle.applyColorFilter(color)
    }

    /**
     * トリミングの確定（直接上書き保存）
     */
    private fun confirmInPlaceCrop() {
        val currentPath = getCurrentPath()
        if (currentPath.isEmpty()) return
        val cropRect = binding.cropImageView.cropRect ?: return
        val wholeRect = binding.cropImageView.wholeImageRect ?: return
        val isJpg = currentPath.isJpg()
        val isGif = currentPath.isGif()

        toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            try {
                val targetFile = File(currentPath)
                var saved = false

                if (isJpg && isInPlaceCropSnapEnabled) {
                    val snappedRect = LosslessCropHelper.snapRectToMcu(cropRect, wholeRect.width(), wholeRect.height())
                    val tempFile = File(cacheDir, "crop_temp_${System.currentTimeMillis()}.jpg")
                    val success = LosslessCropHelper.cropJpegLossless(
                        context = this,
                        inputUri = Uri.fromFile(targetFile),
                        outputUri = Uri.fromFile(tempFile),
                        rect = snappedRect
                    )
                    if (success && tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        saved = true
                    }
                } else if (isGif) {
                    val tempFile = File(cacheDir, "crop_temp_${System.currentTimeMillis()}.gif")
                    val fos = FileOutputStream(tempFile)
                    val success = GifCropHelper.cropGif(
                        context = this,
                        inputUri = Uri.fromFile(targetFile),
                        outputStream = fos,
                        cropRect = cropRect
                    )
                    if (success && tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        saved = true
                    }
                }

                if (!saved) {
                    val croppedBitmap = binding.cropImageView.getCroppedImage()
                    if (croppedBitmap != null) {
                        FileOutputStream(targetFile).use { out ->
                            croppedBitmap.compress(currentPath.getCompressionFormat(), 100, out)
                        }
                        saved = true
                    }
                }

                runOnUiThread {
                    cancelInPlaceCrop()
                    if (saved) {
                        getCurrentPhotoFragment()?.reloadCurrentPhoto()
                        toast(R.string.crop_saved)
                    } else {
                        toast(R.string.image_editing_failed)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    cancelInPlaceCrop()
                    toast(R.string.image_editing_failed)
                }
            }
        }
    }

    private fun toggleFavorite() {
        val medium = getCurrentMedium() ?: return
        medium.isFavorite = !medium.isFavorite
        ensureBackgroundThread {
            updateFavorite(medium.path, medium.isFavorite)
            if (medium.isFavorite) {
                mFavoritePaths.add(medium.path)
            } else {
                mFavoritePaths.remove(medium.path)
            }

            runOnUiThread {
                refreshMenuItems()
            }
        }
    }

    private fun copyImageToClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java) as ClipboardManager

        val imagePath = getCurrentMedium()?.path ?: return
        val clip = ClipData.newUri(contentResolver, "Image", getFinalUriFromPath(imagePath, BuildConfig.APPLICATION_ID))
        clipboard.setPrimaryClip(clip)
    }

    private fun checkDeleteConfirmation() {
        if (getCurrentMedium() == null) {
            return
        }

        handleMediaManagementPrompt {
            if (config.tempSkipDeleteConfirmation || config.skipDeleteConfirmation) {
                deleteConfirmed()
            } else {
                askConfirmDelete()
            }
        }
    }

    private fun askConfirmDelete() {
        val fileDirItem = getCurrentMedium()?.toFileDirItem() ?: return
        val size = fileDirItem.getProperSize(this, countHidden = true).formatSize()
        val filename = "\"${getCurrentPath().getFilenameFromPath()}\""
        val filenameAndSize = "$filename ($size)"

        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val message = String.format(resources.getString(baseString), filenameAndSize)

        DeleteWithRememberDialog(this, message, false) { remember, _ ->
            config.tempSkipDeleteConfirmation = remember
            deleteConfirmed()
        }
    }

    private fun deleteConfirmed() {
        val currentMedium = getCurrentMedium()
        val path = currentMedium?.path ?: return
        if (getIsPathDirectory(path) || !path.isMediaFile()) {
            return
        }

        val fileDirItem = currentMedium.toFileDirItem()
        handleDeletion(fileDirItem)
    }

    private fun handleDeletion(fileDirItem: FileDirItem) {
        checkManageMediaOrHandleSAFDialogSdk30(fileDirItem.path) {
            if (!it) {
                return@checkManageMediaOrHandleSAFDialogSdk30
            }

            mIgnoredPaths.add(fileDirItem.path)
            val media = mMediaFiles.filter { !mIgnoredPaths.contains(it.path) } as ArrayList<Medium>
            if (media.isNotEmpty()) {
                runOnUiThread {
                    refreshUI(media, false)
                }
            }

            if (media.size == 1) {
                onPageSelected(0)
            }

            tryDeleteFileDirItem(fileDirItem, false, true) {
                mIgnoredPaths.remove(fileDirItem.path)
                if (media.isEmpty()) {
                    deleteDirectoryIfEmpty()
                    finish()
                }
            }
        }
    }

    private fun isDirEmpty(media: ArrayList<Medium>): Boolean {
        return if (media.isEmpty()) {
            deleteDirectoryIfEmpty()
            finish()
            true
        } else {
            false
        }
    }

    private fun checkMediaManagementAndRename() {
        handleMediaManagementPrompt {
            renameFile()
        }
    }

    private fun renameFile() {
        val oldPath = getCurrentPath()

        val isSDOrOtgRootFolder = isAStorageRootFolder(oldPath.getParentPath()) && !oldPath.startsWith(internalStoragePath)
        if (isRPlus() && isSDOrOtgRootFolder && !isExternalStorageManager()) {
            toast(org.fossify.commons.R.string.rename_in_sd_card_system_restriction, Toast.LENGTH_LONG)
            return
        }

        RenameItemDialog(this, oldPath) {
            getCurrentMedia().getOrNull(mPos)?.apply {
                path = it
                name = it.getFilenameFromPath()
            }

            ensureBackgroundThread {
                updateDBMediaPath(oldPath, it)
            }
            updateActionbarTitle()
        }
    }

    private fun refreshViewPager(refetchPosition: Boolean = false) {
        val isRandomSorting = config.getFolderSorting(mDirectory) and SORT_BY_RANDOM != 0
        if (!isRandomSorting || isExternalIntent()) {
            GetMediaAsynctask(applicationContext, mDirectory, isPickImage = false, isPickVideo = false, showAll = mShowAll) {
                gotMedia(it, refetchViewPagerPosition = refetchPosition)
            }.execute()
        }
    }

    private fun gotMedia(thumbnailItems: ArrayList<ThumbnailItem>, ignorePlayingVideos: Boolean = false, refetchViewPagerPosition: Boolean = false) {
        val media = thumbnailItems.asSequence().filter {
            it is Medium && !mIgnoredPaths.contains(it.path)
        }.map { it as Medium }.toMutableList() as ArrayList<Medium>

        if (isDirEmpty(media) || media.hashCode() == mPrevHashcode) {
            return
        }

        val isPlaying = (getCurrentFragment() as? VideoFragment)?.mIsPlaying == true
        if (!ignorePlayingVideos && isPlaying) {
            return
        }

        if (isExternalIntent() && mMediaFiles.size > 1 && media.size == mMediaFiles.size) {
            return
        }

        refreshUI(media, refetchViewPagerPosition)
    }

    private fun refreshUI(media: ArrayList<Medium>, refetchViewPagerPosition: Boolean) {
        mPrevHashcode = media.hashCode()
        mMediaFiles = media

        if (refetchViewPagerPosition || mPos == -1) {
            mPos = getPositionInList(media)
            if (mPos == -1) {
                min(mPos, media.lastIndex)
            }
        }

        updateActionbarTitle()
        updatePagerItems(mMediaFiles.toMutableList())

        refreshMenuItems()
        checkOrientation()
        initBottomActions()
        scheduleImageAdvance()
    }

    private fun getPositionInList(items: MutableList<Medium>): Int {
        mPos = 0
        for ((i, medium) in items.withIndex()) {
            val portraitPath = getPortraitPath()
            if (portraitPath != "") {
                val portraitPaths = File(portraitPath).parentFile?.list()
                if (portraitPaths != null) {
                    for (path in portraitPaths) {
                        if (medium.name == path) {
                            return i
                        }
                    }
                }
            } else if (medium.path.equals(mPath, true)) {
                return i
            }
        }
        return mPos
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mDirectory, mDirectory.getFilenameFromPath(), File(mDirectory).isDirectory)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                        scanPathRecursively(mDirectory)
                    }
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun checkOrientation() {
        if (!mIsOrientationLocked && config.screenRotation == ROTATE_BY_ASPECT_RATIO) {
            var flipSides = false
            try {
                val pathToLoad = getCurrentPath()
                val exif = ExifInterface(pathToLoad)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
                flipSides = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
            } catch (e: Exception) {
            }
            val resolution = applicationContext.getResolution(getCurrentPath()) ?: return
            val width = if (flipSides) resolution.y else resolution.x
            val height = if (flipSides) resolution.x else resolution.y
            if (width > height) {
                requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            } else if (width < height) {
                requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun fragmentClicked() {
        mIsFullScreen = !mIsFullScreen
        checkSystemUI()
        fullscreenToggled()
        scheduleImageAdvance()
    }

    override fun videoEnded(): Boolean {
        if (mIsSlideshowActive) {
            swipeToNextMedium()
        }
        return mIsSlideshowActive
    }

    override fun isSlideShowActive() = mIsSlideshowActive

    override fun isFullScreen() = mIsFullScreen

    override fun goToPrevItem() {
        val totalCount = binding.viewPager.adapter?.count ?: 0
        if (totalCount > 1) {
            val prev = if (binding.viewPager.currentItem > 0) binding.viewPager.currentItem - 1 else totalCount - 1
            binding.viewPager.setCurrentItem(prev, false)
            checkOrientation()
        }
    }

    override fun goToNextItem() {
        val totalCount = binding.viewPager.adapter?.count ?: 0
        if (totalCount > 1) {
            val next = if (binding.viewPager.currentItem < totalCount - 1) binding.viewPager.currentItem + 1 else 0
            binding.viewPager.setCurrentItem(next, false)
            checkOrientation()
        }
    }

    override fun launchViewVideoIntent(path: String) {
        hideKeyboard()
        ensureBackgroundThread {
            val newUri = getFinalUriFromPath(path, BuildConfig.APPLICATION_ID) ?: return@ensureBackgroundThread
            val mimeType = getUriMimeType(path, newUri)
            Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(newUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(IS_FROM_GALLERY, true)
                putExtra(REAL_FILE_PATH, path)
                putExtra(SHOW_PREV_ITEM, binding.viewPager.currentItem != 0)
                putExtra(SHOW_NEXT_ITEM, binding.viewPager.currentItem != mMediaFiles.lastIndex)

                try {
                    startActivityForResult(this, REQUEST_VIEW_VIDEO)
                } catch (e: ActivityNotFoundException) {
                    if (!tryGenericMimeType(this, mimeType, newUri)) {
                        toast(org.fossify.commons.R.string.no_app_found)
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun checkSystemUI() {
        if (mIsFullScreen) {
            hideSystemUI()
        } else {
            stopSlideshow()
            showSystemUI()
        }
    }

    private fun fullscreenToggled() {
        binding.viewPager.adapter?.let {
            (it as MyPagerAdapter).toggleFullscreen(mIsFullScreen)
            val newAlpha = if (mIsFullScreen) 0f else 1f
            binding.topShadow.animate().alpha(newAlpha).start()
            binding.bottomActions.root.animate().alpha(newAlpha).withStartAction {
                binding.bottomActions.root.beVisible()
            }.withEndAction {
                binding.bottomActions.root.beVisibleIf(newAlpha == 1f)
            }.start()

            binding.mediumViewerAppbar.animate().alpha(newAlpha).withStartAction {
                binding.mediumViewerAppbar.beVisible()
            }.withEndAction {
                binding.mediumViewerAppbar.beVisibleIf(newAlpha == 1f)
            }.start()
        }
    }

    private fun updateActionbarTitle() {
        runOnUiThread {
            val medium = getCurrentMedium()
            if (medium != null) {
                binding.mediumViewerToolbar.title = medium.path.getFilenameFromPath()
            }
        }
    }

    private fun getCurrentMedium(): Medium? {
        return if (getCurrentMedia().isEmpty() || mPos == -1) {
            null
        } else {
            getCurrentMedia()[min(mPos, getCurrentMedia().lastIndex)]
        }
    }

    private fun getCurrentMedia() = if (mAreSlideShowMediaVisible || mRandomSlideshowStopped) mSlideshowMedia else mMediaFiles

    private fun getCurrentPath() = getCurrentMedium()?.path ?: ""

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        if (mPos != position) {
            val oldPos = mPos
            mPos = position
            (binding.viewPager.adapter as? MyPagerAdapter)?.let { adapter ->
                (adapter.getCurrentFragment(oldPos) as? VideoFragment)?.pauseVideo()
            }
            updateActionbarTitle()
            refreshMenuItems()
            scheduleSwipe()
            scheduleImageAdvance()
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            cancelImageAdvance()
        } else if (state == ViewPager.SCROLL_STATE_IDLE && getCurrentMedium() != null) {
            checkOrientation()
            scheduleImageAdvance()
        }
    }

    private fun isExternalIntent(): Boolean {
        return !intent.getBooleanExtra(IS_FROM_GALLERY, false)
    }
}
