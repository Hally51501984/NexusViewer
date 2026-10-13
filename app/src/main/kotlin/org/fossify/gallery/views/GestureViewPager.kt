package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.fossify.commons.views.MyViewPager
import kotlin.math.abs

class GestureViewPager : MyViewPager {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var isCurrentItemVideo: () -> Boolean = { false }
    var onTwoFingerSwipe: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var isTwoFingerTriggered = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var loopDownX = 0f
    private var loopDownY = 0f
    private var isLoopSwipeCandidate = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // フォルダ両端でのループスワイプ処理（先頭で右スワイプ -> 末尾へ、末尾で左スワイプ -> 先頭へ）
        val totalCount = adapter?.count ?: 0
        if (totalCount > 1) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    loopDownX = ev.rawX
                    loopDownY = ev.rawY
                    val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                    val isEdge = ev.rawX < screenWidth * 0.25f || ev.rawX > screenWidth * 0.75f
                    val isAllowedZone = !isCurrentItemVideo() || isEdge
                    isLoopSwipeCandidate = isAllowedZone && (currentItem == 0 || currentItem == totalCount - 1)
                }
                MotionEvent.ACTION_UP -> {
                    if (isLoopSwipeCandidate) {
                        val dx = ev.rawX - loopDownX
                        val dy = ev.rawY - loopDownY
                        val loopThreshold = 50f * resources.displayMetrics.density
                        if (abs(dx) > loopThreshold && abs(dx) > abs(dy) * 1.3f) {
                            if (currentItem == 0 && dx > 0) {
                                post {
                                    setCurrentItem(totalCount - 1, false)
                                }
                            } else if (currentItem == totalCount - 1 && dx < 0) {
                                post {
                                    setCurrentItem(0, false)
                                }
                            }
                        }
                    }
                    isLoopSwipeCandidate = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isLoopSwipeCandidate = false
                }
            }
        }

        // 2本指ジェスチャーの検出（フォルダブックマーク一覧呼び出し）
        if (ev.pointerCount >= 2) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    isTwoFingerTriggered = false
                    downY = ev.getY(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isTwoFingerTriggered) {
                        val dy = ev.getY(0) - downY
                        if (abs(dy) > touchSlop * 3) {
                            isTwoFingerTriggered = true
                            onTwoFingerSwipe?.invoke()
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    isTwoFingerTriggered = false
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
            }
        }

        // 動画表示中の場合: 左右端25%はPrev/Next用ViewPagerインターセプト、中央50%はシーク用で子Viewへスルー
        if (isCurrentItemVideo()) {
            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            if (screenWidth > 0) {
                val isLeftEdge = downX < screenWidth * 0.25f
                val isRightEdge = downX > screenWidth * 0.75f
                if (!isLeftEdge && !isRightEdge) {
                    // 中央50%の領域はシーク専用とするため、ViewPagerの横取りをブロック
                    return false
                }
            }
        }

        // 静止画表示中は画面全域で通常通りViewPagerがページめくりをハンドリング
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
