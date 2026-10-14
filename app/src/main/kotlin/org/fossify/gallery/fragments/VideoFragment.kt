@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package org.fossify.gallery.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.fadeOut
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getVideoResolution
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.setDrawablesRelativeWithIntrinsicBounds
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.DEFAULT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.VideoActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.databinding.PagerVideoItemBinding
import org.fossify.gallery.extensions.audioManager
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getActionBarHeight
import org.fossify.gallery.extensions.getBottomActionsHeight
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.extensions.getFriendlyMessage
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.parseFileChannel
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.EXOPLAYER_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_BUFFER_FOR_PLAYBACK_MS
import org.fossify.gallery.helpers.EXOPLAYER_MAX_BUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_MIN_BUFFER_MS
import org.fossify.gallery.helpers.FAST_FORWARD_VIDEO_MS
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.REPEAT_FOLDER
import org.fossify.gallery.helpers.REPEAT_OFF
import org.fossify.gallery.helpers.REPEAT_ONE
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.interfaces.PlaybackSpeedListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.views.MediaSideScroll
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import kotlin.math.abs

class VideoFragment : ViewPagerFragment(),
    SeekBar.OnSeekBarChangeListener, PlaybackSpeedListener {
    companion object {
        private const val PROGRESS = "progress"
        private const val UPDATE_INTERVAL_MS = 250L
        private const val TOUCH_HOLD_DURATION_MS = 500L
        private const val TOUCH_HOLD_SPEED_MULTIPLIER = 2.0f
        private const val TOUCH_SLOP_DIVIDER = 3
    }

    private var mIsFullscreen = false
    private var mWasFragmentInit = false
    private var mIsPanorama = false
    private var mIsFragmentVisible = false
    private var mIsDragged = false
    private var mWasVideoStarted = false
    private var mWasPlayerInited = false
    private var mWasLastPositionRestored = false
    private var mPlayOnPrepared = false
    private var mIsPlayerPrepared = false
    private var mCurrTime = 0L
    private var mDuration = 0L
    private var mPositionWhenInit = 0L
    private var mPositionAtPause = 0L
    var mIsPlaying = false

    private var mExoPlayer: ExoPlayer? = null
    private var mVideoSize = Point(1, 1)
    private var mTimerHandler = Handler()

    private var mStoredShowExtendedDetails = false
    private var mStoredHideExtendedDetails = false
    private var mStoredBottomActions = true
    private var mStoredExtendedDetails = 0
    private var mStoredRememberLastVideoPosition = false
    private var mOriginalPlaybackSpeed = 1f
    private var mIsLongPressActive = false
    private var mHasAudio = true

    private var mIsSeeking = false
    private var mIsTouchInCenter = false
    private var mSeekStartX = 0f
    private var mSeekStartY = 0f
    private var mSeekStartPosition = 0L
    private var mTargetSeekPosition = 0L

    private var mIsVerticalScrolling = false
    private var mIsBrightnessScroll = false
    private var mTouchDownBrightness = 0.5f
    private var mTouchDownVolume = 0
    private var mMaxStreamVolume = 1
    private val mSlideInfoFadeHandler = Handler()

    private val mTouchHoldRunnable = Runnable {
        mView.parent.requestDisallowInterceptTouchEvent(true)
        // This code runs after the delay, only if the user is still holding down.
        mIsLongPressActive = true
        mOriginalPlaybackSpeed = mExoPlayer?.playbackParameters?.speed ?: mConfig.playbackSpeed
        mView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        updatePlaybackSpeed(TOUCH_HOLD_SPEED_MULTIPLIER)

        mPlaybackSpeedPill.fadeIn()
    }

    private lateinit var mTimeHolder: View
    private lateinit var mBrightnessSideScroll: MediaSideScroll
    private lateinit var mVolumeSideScroll: MediaSideScroll
    private lateinit var binding: PagerVideoItemBinding
    private lateinit var mView: View
    private lateinit var mMedium: Medium
    private lateinit var mConfig: Config
    private lateinit var mTextureView: TextureView
    private lateinit var mCurrTimeView: TextView
    private lateinit var mPlayPauseButton: ImageView
    private lateinit var mSeekBar: SeekBar
    private lateinit var mPlaybackSpeedPill: TextView
    private var mTouchSlop = 0
    private var mInitialX = 0f
    private var mInitialY = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val activity = requireActivity()
        val arguments = requireArguments()

        mMedium = arguments.getSerializable(MEDIUM) as Medium
        mConfig = context.config
        mTouchSlop = (ViewConfiguration.get(context).scaledTouchSlop) / TOUCH_SLOP_DIVIDER
        binding = PagerVideoItemBinding.inflate(inflater, container, false).apply {
            panoramaOutline.setOnClickListener { openPanorama() }
            bottomVideoTimeHolder.videoCurrTime.setOnClickListener { skip(false) }
            bottomVideoTimeHolder.videoDuration.setOnClickListener { skip(true) }
            videoHolder.setOnClickListener { toggleFullscreen() }
            videoPreview.setOnClickListener { toggleFullscreen() }
            bottomVideoTimeHolder.videoPlaybackSpeed.setOnClickListener { showPlaybackSpeedPicker() }
            bottomVideoTimeHolder.videoToggleMute.setOnClickListener {
                mConfig.muteVideos = !mConfig.muteVideos
                updatePlayerMuteState(showToast = true)
            }
            bottomVideoTimeHolder.videoToggleRepeat.setOnClickListener {
                cycleRepeatMode()
            }

            videoSurfaceFrame.controller.settings.swallowDoubleTaps = true

            videoPlayOutline.setOnClickListener {
                if (mConfig.gestureVideoPlayer) activity.launchGesturePlayer(mMedium.path) else togglePlayPause()
            }

            mPlayPauseButton = bottomVideoTimeHolder.videoTogglePlayPause
            mPlayPauseButton.setOnClickListener {
                togglePlayPause()
            }

            mSeekBar = bottomVideoTimeHolder.videoSeekbar
            mPlaybackSpeedPill = playbackSpeedPill
            mSeekBar.setOnSeekBarChangeListener(this@VideoFragment)
            // adding an empty click listener just to avoid ripple animation at toggling fullscreen
            mSeekBar.setOnClickListener { }

            mTimeHolder = bottomVideoTimeHolder.videoTimeHolder
            mCurrTimeView = bottomVideoTimeHolder.videoCurrTime
            mBrightnessSideScroll = videoBrightnessController
            mVolumeSideScroll = videoVolumeController
            mBrightnessSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }
            mVolumeSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }
            mTextureView = videoSurface

            val gestureDetector =
                GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!mConfig.allowInstantChange) {
                            toggleFullscreen()
                            return true
                        }

                        val viewWidth = root.width
                        val instantWidth = viewWidth / 7
                        val clickedX = e.rawX
                        when {
                            clickedX <= instantWidth -> listener?.goToPrevItem()
                            clickedX >= viewWidth - instantWidth -> listener?.goToNextItem()
                            else -> toggleFullscreen()
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        handleDoubleTap(e.rawX)
                        return true
                    }
                })

            videoPreview.setOnTouchListener { view, event ->
                handleEvent(event)
                false
            }

            videoSurfaceFrame.setOnTouchListener { view, event ->
                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val screenHeight = resources.displayMetrics.heightPixels.toFloat()
                if (videoSurfaceFrame.controller.state.zoom == 1f) {
                    handleEvent(event)

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val downRawX = event.rawX
                            val isLeftEdge = downRawX < screenWidth * 0.25f
                            val isRightEdge = downRawX > screenWidth * 0.75f
                            mIsTouchInCenter = (!isLeftEdge && !isRightEdge)

                            mSeekStartX = event.x
                            mSeekStartY = event.y
                            mIsSeeking = false
                            mIsVerticalScrolling = false
                            mSeekStartPosition = mExoPlayer?.currentPosition ?: mCurrTime
                            mTargetSeekPosition = mSeekStartPosition

                            // 画面左半分（明るさ）または右半分（音量）を判定
                            mIsBrightnessScroll = (downRawX < screenWidth * 0.5f)

                            val windowBrightness = activity.window.attributes.screenBrightness
                            mTouchDownBrightness = if (windowBrightness >= 0f) {
                                windowBrightness
                            } else {
                                try {
                                    Settings.System.getInt(
                                        context.contentResolver,
                                        Settings.System.SCREEN_BRIGHTNESS
                                    ) / 255f
                                } catch (e: Exception) {
                                    0.5f
                                }
                            }

                            val am = activity.audioManager
                            mMaxStreamVolume = maxOf(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 1)
                            mTouchDownVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - mSeekStartX
                            val dy = event.y - mSeekStartY

                            // 縦スワイプ（明るさ / 音量）実行中
                            if (mIsVerticalScrolling) {
                                val deltaPercent = -(dy / (screenHeight * 0.75f))
                                if (mIsBrightnessScroll) {
                                    val newBrightness = (mTouchDownBrightness + deltaPercent).coerceIn(0.01f, 1.0f)
                                    val percent = (newBrightness * 100).toInt()
                                    val attributes = activity.window.attributes
                                    attributes.screenBrightness = newBrightness
                                    activity.window.attributes = attributes

                                    binding.slideInfo.text = "☀️ 明るさ: $percent%"
                                } else {
                                    val addPoints = (deltaPercent * mMaxStreamVolume).toInt()
                                    val newVolume = (mTouchDownVolume + addPoints).coerceIn(0, mMaxStreamVolume)
                                    activity.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

                                    val percent = ((newVolume.toFloat() / mMaxStreamVolume.toFloat()) * 100).toInt()
                                    val icon = if (newVolume == 0) "🔇" else "🔊"
                                    binding.slideInfo.text = "$icon 音量: $percent%"
                                }
                                binding.slideInfo.alpha = 1f
                                return@setOnTouchListener true
                            }

                            // 横スワイプ（シーク）実行中
                            if (mIsSeeking) {
                                val maxSeekRange = (mDuration / 3).coerceIn(15_000L, 180_000L).coerceAtLeast(10_000L)
                                val seekDeltaMs = ((dx / (screenWidth * 0.5f)) * maxSeekRange).toLong()
                                mTargetSeekPosition = (mSeekStartPosition + seekDeltaMs).coerceIn(0L, mDuration)

                                val deltaSec = (mTargetSeekPosition - mSeekStartPosition) / 1000
                                val sign = if (deltaSec >= 0) "+$deltaSec" else "$deltaSec"
                                val currentFormatted = mTargetSeekPosition.getFormattedDuration()
                                val durationFormatted = mDuration.getFormattedDuration()
                                val icon = if (deltaSec >= 0) "▶▶" else "◀◀"

                                binding.slideInfo.text = "$icon $sign s\n$currentFormatted / $durationFormatted"
                                binding.slideInfo.alpha = 1f

                                mSeekBar.progress = mTargetSeekPosition.toInt()
                                mCurrTimeView.text = currentFormatted
                                return@setOnTouchListener true
                            }

                            // ジェスチャー判定
                            if (!mIsLongPressActive) {
                                // 縦スワイプ判定: 縦の移動量が大きく、横の1.2倍以上
                                if (abs(dy) > mTouchSlop && abs(dy) > abs(dx) * 1.2f) {
                                    mIsVerticalScrolling = true
                                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                                    videoSurfaceFrame.parent?.requestDisallowInterceptTouchEvent(true)
                                    return@setOnTouchListener true
                                }

                                // 横シーク判定: 中央領域で、横の移動量が大きく、縦の1.2倍以上
                                if (mIsTouchInCenter && abs(dx) > mTouchSlop && abs(dx) > abs(dy) * 1.2f) {
                                    mIsSeeking = true
                                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                                    videoSurfaceFrame.parent?.requestDisallowInterceptTouchEvent(true)
                                    return@setOnTouchListener true
                                }
                            }
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (mIsVerticalScrolling) {
                                mIsVerticalScrolling = false
                                mSlideInfoFadeHandler.removeCallbacksAndMessages(null)
                                mSlideInfoFadeHandler.postDelayed({
                                    binding.slideInfo.animate().alpha(0f).setDuration(300L).start()
                                }, 800L)
                                return@setOnTouchListener true
                            }

                            if (mIsSeeking) {
                                mIsSeeking = false
                                mExoPlayer?.seekTo(mTargetSeekPosition)
                                setPosition(mTargetSeekPosition)
                                binding.slideInfo.animate().alpha(0f).setDuration(300L).start()
                                return@setOnTouchListener true
                            }
                        }
                    }
                }

                if (mIsTouchInCenter) {
                    handleTouchHoldEvent(event)
                    if (mIsLongPressActive) {
                        return@setOnTouchListener true
                    }
                }

                gestureDetector.onTouchEvent(event)
                false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoHolder) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())

            val pillTopMargin = system.top + resources.getActionBarHeight(context) +
                resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()
            (mPlaybackSpeedPill.layoutParams as? RelativeLayout.LayoutParams)?.apply {
                setMargins(
                    0, pillTopMargin, 0, 0
                )
            }

            binding.bottomActionsDummy.updateLayoutParams<ViewGroup.LayoutParams> {
                height = resources.getBottomActionsHeight() + system.bottom
            }
            insets
        }

        mView = binding.root

        if (!arguments.getBoolean(SHOULD_INIT_FRAGMENT, true)) {
            return mView
        }

        storeStateVariables()
        if (mConfig.autoplayVideos) {
            binding.videoPreview.beGone()
        } else {
            Glide.with(context).load(mMedium.path).into(binding.videoPreview)
        }

        // setMenuVisibility is not called at VideoActivity (third party intent)
        if (!mIsFragmentVisible && activity is VideoActivity) {
            val shouldInit = arguments.getBoolean(SHOULD_INIT_FRAGMENT, true)
            if (shouldInit) {
                mIsFragmentVisible = true
            }
        }

        mIsFullscreen = listener?.isFullScreen() == true
        initTimeHolder()
        // checkIfPanorama() TODO: Implement panorama using a FOSS library

        ensureBackgroundThread {
            activity.getVideoResolution(mMedium.path)?.apply {
                mVideoSize.x = x
                mVideoSize.y = y
            }
        }

        if (mIsPanorama) {
            binding.apply {
                panoramaOutline.beVisible()
                videoPlayOutline.beGone()
                mVolumeSideScroll.beGone()
                mBrightnessSideScroll.beGone()
                Glide.with(context).load(mMedium.path).into(videoPreview)
            }
        }

        if (!mIsPanorama) {
            if (savedInstanceState != null) {
                mCurrTime = savedInstanceState.getLong(PROGRESS, 0L)
            }

            mWasFragmentInit = true
            setVideoSize()

            binding.apply {
                mBrightnessSideScroll.initialize(
                    activity,
                    slideInfo,
                    true,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToPrevItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        doSkip(false)
                    })
                mVolumeSideScroll.initialize(
                    activity,
                    slideInfo,
                    false,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToNextItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        doSkip(true)
                    })

                videoSurface.onGlobalLayout {
                    if (mIsFragmentVisible && mConfig.autoplayVideos) {
                        playVideo()
                    }
                }
            }
        }

        setupVideoDuration()
        if (mStoredRememberLastVideoPosition) {
            restoreLastVideoSavedPosition()
        }

        if (!mIsPanorama && mConfig.autoplayVideos && mIsFragmentVisible && mExoPlayer == null) {
            initExoPlayer()
        }

        return mView
    }

    override fun onResume() {
        super.onResume()
        mConfig =
            requireContext().config      // make sure we get a new config, in case the user changed something in the app settings
        requireActivity().updateTextColors(binding.videoHolder)
        val allowVideoGestures = mConfig.allowVideoGestures
        mTextureView.beGoneIf(mConfig.gestureVideoPlayer || mIsPanorama)
        binding.videoSurfaceFrame.beGoneIf(mTextureView.isGone())

        mVolumeSideScroll.beGone()
        mBrightnessSideScroll.beGone()

        checkExtendedDetails()
        initTimeHolder()
        storeStateVariables()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        pauseVideo()
        if (mStoredRememberLastVideoPosition && mIsFragmentVisible && mWasVideoStarted) {
            saveVideoProgress()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activity?.isChangingConfigurations == false) {
            cleanup()
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (!menuVisible) {
            pauseVideo()
        }

        mIsFragmentVisible = menuVisible
        val shouldPlayVideo = mWasFragmentInit && menuVisible && mConfig.autoplayVideos
        if (shouldPlayVideo) playVideo()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setVideoSize()
        binding.videoSurfaceFrame.onGlobalLayout {
            binding.videoSurfaceFrame.controller.resetState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(PROGRESS, mCurrTime)
    }

    private fun storeStateVariables() {
        mConfig.apply {
            mStoredShowExtendedDetails = showExtendedDetails
            mStoredHideExtendedDetails = hideExtendedDetails
            mStoredExtendedDetails = extendedDetails
            mStoredBottomActions = bottomActions
            mStoredRememberLastVideoPosition = rememberLastVideoPosition
        }
    }

    private fun saveVideoProgress() {
        if (!videoEnded()) {
            if (mExoPlayer != null) {
                mConfig.saveLastVideoPosition(
                    mMedium.path,
                    mExoPlayer!!.currentPosition.toInt() / 1000
                )
            } else {
                mConfig.saveLastVideoPosition(mMedium.path, mPositionAtPause.toInt() / 1000)
            }
        }
    }

    private fun restoreLastVideoSavedPosition() {
        val seconds = mConfig.getLastVideoPosition(mMedium.path)
        if (seconds > 0) {
            mPositionAtPause = seconds * 1000L
            setPosition(seconds * 1000L)
        }
    }

    private fun setupTimeHolder() {
        mSeekBar.max = mDuration.toInt()
        binding.bottomVideoTimeHolder.videoDuration.text = mDuration.getFormattedDuration()
        setupTimer()
    }

    private fun setupTimer() {
        activity?.runOnUiThread(object : Runnable {
            override fun run() {
                if (mExoPlayer != null && !mIsDragged && mIsPlaying) {
                    mCurrTime = mExoPlayer!!.currentPosition
                    mSeekBar.progress = mCurrTime.toInt()
                    mCurrTimeView.text = mCurrTime.getFormattedDuration()
                }

                mTimerHandler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        })
    }

    private fun buildMediaSource(): MediaSource {
        val isContentUri = mMedium.path.startsWith("content://")
        val uri = if (isContentUri) Uri.parse(mMedium.path) else Uri.fromFile(File(mMedium.path))

        val dataSourceFactory = DataSource.Factory {
            if (isContentUri) {
                ContentDataSource(requireContext())
            } else {
                FileDataSource()
            }
        }
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))
    }

    private fun initExoPlayer() {
        val shouldSkipInit = activity == null || mConfig.gestureVideoPlayer || mIsPanorama || mExoPlayer != null
        if (shouldSkipInit) return

        mPlayOnPrepared = mIsFragmentVisible

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                EXOPLAYER_MIN_BUFFER_MS,
                EXOPLAYER_MAX_BUFFER_MS,
                EXOPLAYER_BUFFER_FOR_PLAYBACK_MS,
                EXOPLAYER_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        mExoPlayer = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(DefaultMediaSourceFactory(requireContext()))
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setLoadControl(loadControl)
            .build()
            .apply {
                if (mConfig.videoRepeatMode == REPEAT_ONE && listener?.isSlideShowActive() == false) {
                    setMediaSources(listOf(buildMediaSource(), buildMediaSource()))
                    repeatMode = Player.REPEAT_MODE_ALL
                } else {
                    setMediaSource(buildMediaSource())
                    repeatMode = Player.REPEAT_MODE_OFF
                }

                setPlaybackSpeed(mConfig.playbackSpeed)
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true
                )
                playWhenReady = false
                prepare()

                setVideoTextureView(mTextureView)

                initListeners()
            }

        updatePlayerMuteState()
    }

    private fun ExoPlayer.initListeners() {
        addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mSeekBar.progress = 0
                mCurrTimeView.text = 0.getFormattedDuration()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                @Player.DiscontinuityReason reason: Int,
            ) {
                // Reset progress views when video loops.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    mSeekBar.progress = 0
                    mCurrTimeView.text = 0.getFormattedDuration()
                }
            }

            override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> videoPrepared()
                    Player.STATE_ENDED -> videoCompleted()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                mVideoSize.x = videoSize.width
                mVideoSize.y = (videoSize.height / videoSize.pixelWidthHeightRatio).toInt()
                setVideoSize()
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                binding.errorMessageHolder.errorMessage.apply {
                    if (error != null) {
                        binding.videoPreview.beGone()
                        binding.videoPlayOutline.beGone()
                        text = error.getFriendlyMessage(context)
                        setTextColor(if (context.config.blackBackground) Color.WHITE else context.getProperTextColor())
                        fadeIn()
                    } else {
                        beGone()
                        binding.videoPlayOutline.beGone()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                mHasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO)
                updatePlayerMuteState()
            }
        })
    }

    private fun toggleFullscreen() {
        listener?.fragmentClicked()
    }

    private fun handleDoubleTap(x: Float) {
        val viewWidth = mView.width
        val instantWidth = viewWidth / 7
        when {
            x <= instantWidth -> doSkip(false)
            x >= viewWidth - instantWidth -> doSkip(true)
            else -> togglePlayPause()
        }
    }

    private fun checkExtendedDetails() {
        if (mConfig.showExtendedDetails) {
            binding.videoDetails.apply {
                text = getMediumExtendedDetails(mMedium)
                beVisibleIf(text.isNotEmpty())
                alpha = if (!mConfig.hideExtendedDetails || !mIsFullscreen) 1f else 0f
                (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(this)
            }
        } else {
            binding.videoDetails.beGone()
        }
    }

    private fun initTimeHolder() {
        mTimeHolder.beGoneIf(mIsFullscreen)
        mTimeHolder.alpha = if (mIsFullscreen) 0f else 1f
        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(mTimeHolder)
    }

    private fun checkIfPanorama() {
        try {
            val fis = FileInputStream(File(mMedium.path))
            fis.use {
                requireContext().parseFileChannel(mMedium.path, it.channel, 0, 0, 0) {
                    mIsPanorama = true
                }
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
    }

    private fun openPanorama() {
        TODO("Panorama is not yet implemented.")
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        mIsFullscreen = isFullscreen

        mSeekBar.setOnSeekBarChangeListener(if (mIsFullscreen) null else this)
        arrayOf(
            binding.bottomVideoTimeHolder.videoCurrTime,
            binding.bottomVideoTimeHolder.videoDuration,
            binding.bottomVideoTimeHolder.videoTogglePlayPause,
            binding.bottomVideoTimeHolder.videoPlaybackSpeed,
            binding.bottomVideoTimeHolder.videoToggleMute
        ).forEach {
            it.isClickable = !mIsFullscreen
        }

        if (isFullscreen) {
            mTimeHolder.fadeOut(DEFAULT_ANIMATION_DURATION)
            binding.bottomActionsDummy.fadeOut(DEFAULT_ANIMATION_DURATION)
        } else {
            binding.bottomActionsDummy.beVisible()
            mTimeHolder.fadeIn(DEFAULT_ANIMATION_DURATION)
        }

        binding.videoDetails.apply {
            if (mStoredShowExtendedDetails && isVisible() && context != null && resources != null) {
                if (mStoredHideExtendedDetails) {
                    animate().alpha(if (isFullscreen) 0f else 1f).start()
                }
            }
        }
    }

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        childFragmentManager.beginTransaction().add(fragment, fragment::class.java.simpleName)
            .commit()
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        val isSlow = speed < 1f
        if (isSlow != binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag as? Boolean) {
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag = isSlow

            val drawableId =
                if (isSlow) R.drawable.ic_playback_speed_slow_vector else R.drawable.ic_playback_speed_vector
            context?.let {
                binding.bottomVideoTimeHolder.videoPlaybackSpeed
                    .setDrawablesRelativeWithIntrinsicBounds(
                        AppCompatResources.getDrawable(it, drawableId)
                    )
            }
        }

        @SuppressLint("SetTextI18n")
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
            "${DecimalFormat("#.##").format(speed)}x"
        mExoPlayer?.setPlaybackSpeed(speed)
    }

    private fun skip(forward: Boolean) {
        if (mIsPanorama) {
            return
        } else if (mExoPlayer == null) {
            playVideo()
            return
        }

        mPositionAtPause = 0L
        doSkip(forward)
    }

    private fun doSkip(forward: Boolean) {
        if (mExoPlayer == null) {
            return
        }

        val curr = mExoPlayer!!.currentPosition
        var newPosition =
            if (forward) curr + FAST_FORWARD_VIDEO_MS else curr - FAST_FORWARD_VIDEO_MS
        newPosition = newPosition.coerceIn(0, maxOf(mExoPlayer!!.duration, 0))
        setPosition(newPosition)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            val newPosition = progress.toLong()
            if (mExoPlayer != null) {
                if (!mWasPlayerInited) {
                    mPositionWhenInit = newPosition
                }
                setPosition(newPosition)
            }

            if (mExoPlayer == null) {
                mPositionAtPause = newPosition
                playVideo()
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (mExoPlayer == null) {
            return
        }

        mExoPlayer!!.playWhenReady = false
        mIsDragged = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (mIsPanorama) {
            openPanorama()
            return
        }

        if (mExoPlayer == null) {
            return
        }

        if (mIsPlaying) {
            mExoPlayer!!.playWhenReady = true
        }

        mIsDragged = false
    }

    private fun togglePlayPause() {
        if (activity == null || !isAdded) {
            return
        }

        val isActuallyPlaying = mExoPlayer?.isPlaying == true || mIsPlaying
        if (isActuallyPlaying) {
            pauseVideo()
        } else {
            playVideo()
        }
    }

    private fun updatePlayerMuteState(showToast: Boolean = false) {
        val isMuted = mConfig.muteVideos
        if (mHasAudio) {
            if (isMuted) mExoPlayer?.mute() else mExoPlayer?.unmute()
        } else if (showToast && mWasVideoStarted) {
            activity?.toast(R.string.video_no_sound)
        }

        val drawableId = when {
            !mHasAudio -> R.drawable.ic_vector_no_sound
            isMuted -> R.drawable.ic_vector_speaker_off
            else -> R.drawable.ic_vector_speaker_on
        }

        binding.bottomVideoTimeHolder.videoToggleMute.setImageResource(drawableId)
    }

    fun playVideo() {
        if (mExoPlayer == null) {
            mPlayOnPrepared = true
            initExoPlayer()
            return
        }

        if (binding.videoPreview.isVisible()) {
            binding.videoPreview.beGone()
        }

        val wasEnded = videoEnded()
        if (wasEnded) {
            setPosition(0)
        }

        if (mStoredRememberLastVideoPosition && !mWasLastPositionRestored) {
            mWasLastPositionRestored = true
            restoreLastVideoSavedPosition()
        }

        if (!wasEnded || !mConfig.loopVideos) {
            mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_pause_outline_vector)
        }

        if (!mWasVideoStarted) {
            binding.videoPlayOutline.beGone()
            mPlayPauseButton.beVisible()
            binding.bottomVideoTimeHolder.videoToggleMute.beVisible()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.beVisible()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
                "${DecimalFormat("#.##").format(mConfig.playbackSpeed)}x"
            updateRepeatModeUI()
        }

        mWasVideoStarted = true
        mIsPlaying = true
        updatePlayerMuteState()
        mExoPlayer?.playWhenReady = true
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun pauseVideo() {
        if (mExoPlayer == null) {
            return
        }

        mIsPlaying = false
        mExoPlayer?.playWhenReady = false
        mExoPlayer?.volume = 0f

        mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_play_outline_vector)
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mPositionAtPause = mExoPlayer?.currentPosition ?: 0L
    }

    private fun videoEnded(): Boolean {
        val currentPos = mExoPlayer?.currentPosition ?: 0
        val duration = mExoPlayer?.duration ?: 0
        return currentPos != 0L && currentPos >= duration
    }

    private fun setPosition(milliseconds: Long) {
        val curr = mExoPlayer?.currentPosition ?: 0L
        if (milliseconds != 0L || curr != 0L) {
            mExoPlayer?.seekTo(milliseconds)
        }
        mSeekBar.progress = milliseconds.toInt()
        mCurrTimeView.text = milliseconds.getFormattedDuration()

        if (!mIsPlaying) {
            mPositionAtPause = milliseconds
        }
    }

    private fun setupVideoDuration() {
        ensureBackgroundThread {
            mDuration = context?.getDuration(mMedium.path)?.times(1000L)?.coerceAtLeast(0L) ?: 0L

            activity?.runOnUiThread {
                setupTimeHolder()
                mSeekBar.progress = 0
                mCurrTimeView.text = 0.getFormattedDuration()
            }
        }
    }

    private fun videoPrepared() {
        if (mDuration == 0L) {
            mDuration = mExoPlayer!!.duration
            setupTimeHolder()
            if (mCurrTime != 0L) {
                setPosition(mCurrTime)
            } else {
                mSeekBar.progress = 0
                mCurrTimeView.text = 0.getFormattedDuration()
            }

            if (mIsFragmentVisible && (mConfig.autoplayVideos)) {
                playVideo()
            }
        }

        if (mPositionWhenInit != 0L && !mWasPlayerInited) {
            setPosition(mPositionWhenInit)
            mPositionWhenInit = 0
        }

        mIsPlayerPrepared = true
        if (mPlayOnPrepared && !mIsPlaying) {
            if (mPositionAtPause != 0L) {
                mExoPlayer?.seekTo(mPositionAtPause)
                mPositionAtPause = 0L
            }
            updatePlaybackSpeed(mConfig.playbackSpeed)
            playVideo()
        }
        mWasPlayerInited = true
        mPlayOnPrepared = false
    }

    private fun videoCompleted() {
        if (!isAdded || mExoPlayer == null) {
            return
        }

        when (mConfig.videoRepeatMode) {
            REPEAT_ONE -> {
                mExoPlayer?.seekTo(0L)
                playVideo()
            }
            REPEAT_FOLDER -> {
                activity?.runOnUiThread {
                    listener?.goToNextItem()
                }
            }
            else -> {
                mCurrTime = mExoPlayer!!.duration
                mSeekBar.progress = mSeekBar.max
                mCurrTimeView.text = mDuration.getFormattedDuration()
                pauseVideo()
            }
        }
    }

    private fun cycleRepeatMode() {
        val nextMode = when (mConfig.videoRepeatMode) {
            REPEAT_FOLDER -> REPEAT_ONE
            REPEAT_ONE -> REPEAT_OFF
            else -> REPEAT_FOLDER
        }
        mConfig.videoRepeatMode = nextMode
        updateRepeatModeUI()
        applyRepeatMode()

        val text = when (nextMode) {
            REPEAT_FOLDER -> "リピート: フォルダ巡回"
            REPEAT_ONE -> "リピート: 1曲"
            else -> "リピート: OFF"
        }
        binding.slideInfo.text = text
        binding.slideInfo.alpha = 1f
        binding.slideInfo.animate().alpha(0f).setStartDelay(800L).setDuration(300L).start()
    }

    private fun updateRepeatModeUI() {
        binding.bottomVideoTimeHolder.videoToggleRepeat.apply {
            beVisible()
            when (mConfig.videoRepeatMode) {
                REPEAT_FOLDER -> {
                    text = "🔁 ALL"
                    alpha = 1f
                }
                REPEAT_ONE -> {
                    text = "🔂 1"
                    alpha = 1f
                }
                REPEAT_OFF -> {
                    text = "OFF"
                    alpha = 0.5f
                }
            }
        }
    }

    private fun applyRepeatMode() {
        val player = mExoPlayer ?: return
        if (mConfig.videoRepeatMode == REPEAT_ONE && listener?.isSlideShowActive() == false) {
            if (player.mediaItemCount == 1) {
                player.addMediaSource(buildMediaSource())
            }
            player.repeatMode = Player.REPEAT_MODE_ALL
        } else {
            if (player.mediaItemCount > 1) {
                player.removeMediaItem(1)
            }
            player.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    fun cleanup() {
        pauseVideo()
        releaseExoPlayer()

        if (mWasFragmentInit) {
            mCurrTimeView.text = 0.getFormattedDuration()
            mSeekBar.progress = 0
            mTimerHandler.removeCallbacksAndMessages(null)
            mSlideInfoFadeHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun releaseExoPlayer() {
        mIsPlayerPrepared = false
        mExoPlayer?.apply {
            clearVideoTextureView(mTextureView)
            stop()
            release()
        }
        mExoPlayer = null
    }

    private fun setVideoSize() {
        if (activity == null || mConfig.gestureVideoPlayer) return

        val videoProportion = mVideoSize.x.toFloat() / mVideoSize.y.toFloat()
        val display = requireActivity().windowManager.defaultDisplay
        val screenWidth: Int
        val screenHeight: Int

        val realMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        screenWidth = realMetrics.widthPixels
        screenHeight = realMetrics.heightPixels

        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()

        mTextureView.layoutParams.apply {
            if (videoProportion > screenProportion) {
                width = screenWidth
                height = (screenWidth.toFloat() / videoProportion).toInt()
            } else {
                width = (videoProportion * screenHeight.toFloat()).toInt()
                height = screenHeight
            }
            mTextureView.layoutParams = this
        }
    }

    private fun handleTouchHoldEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mIsPlaying && event.pointerCount == 1) {
                    mInitialX = event.x
                    mInitialY = event.y
                    mTimerHandler.postDelayed(mTouchHoldRunnable, TOUCH_HOLD_DURATION_MS)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.x - mInitialX)
                val deltaY = abs(event.y - mInitialY)
                if (!mIsLongPressActive && (deltaX > mTouchSlop || deltaY > mTouchSlop)) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!mIsLongPressActive) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                stopHoldSpeedMultiplierGesture()
            }
        }
    }

    private fun stopHoldSpeedMultiplierGesture() {
        if (mIsLongPressActive) {
            updatePlaybackSpeed(mOriginalPlaybackSpeed)
            mIsLongPressActive = false
            mPlaybackSpeedPill.fadeOut()
        }
    }
}
