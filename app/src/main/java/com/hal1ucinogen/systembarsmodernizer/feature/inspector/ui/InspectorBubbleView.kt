package com.hal1ucinogen.systembarsmodernizer.feature.inspector.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.google.android.material.color.MaterialColors
import com.hal1ucinogen.systembarsmodernizer.R
import com.hal1ucinogen.systembarsmodernizer.databinding.ViewInspectorBubbleBinding
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class InspectorBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    val layoutParams: WindowManager.LayoutParams,
    private val onInspectClick: () -> Unit,
    private val onCloseClick: () -> Unit
) : FrameLayout(
    android.view.ContextThemeWrapper(
        context,
        R.style.Theme_SystemBarsModernizer
    )
) {

    private val binding: ViewInspectorBubbleBinding =
        ViewInspectorBubbleBinding.inflate(LayoutInflater.from(this.context), this, true)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var errorShakeAnimator: ValueAnimator? = null

    init {
        isClickable = true

        binding.btnBubbleInspect.setOnClickListener {
            onInspectClick()
        }

        binding.btnBubbleClose.setOnClickListener {
            onCloseClick()
        }

        setIdleState()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                    return true
                }
                return isDragging
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) {
                    updateDragPosition(dx, dy)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateDragPosition(dx: Float, dy: Float) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val bubbleWidth = if (width > 0) width else layoutParams.width
        val bubbleHeight = if (height > 0) height else layoutParams.height
        val margin = (8 * resources.displayMetrics.density).toInt()
        val minX = margin
        val maxX = (screenWidth - bubbleWidth - margin).coerceAtLeast(minX)
        val minY = (24 * resources.displayMetrics.density).toInt()
        val maxY = (screenHeight - bubbleHeight - (24 * resources.displayMetrics.density).toInt()).coerceAtLeast(minY)

        layoutParams.x = (initialX + dx.toInt()).coerceIn(minX, maxX)
        layoutParams.y = (initialY + dy.toInt()).coerceIn(minY, maxY)
        if (isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(this, layoutParams) }
        }
    }

    fun setIdleState(message: String? = null) {
        binding.btnBubbleInspect.visibility = View.VISIBLE
        binding.btnBubbleInspect.isEnabled = true
        binding.progressIndicator.visibility = View.GONE
        binding.tvBubbleStatus.text = message ?: context.getString(R.string.inspector_status_idle)
        val normalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.GRAY
        )
        binding.tvBubbleStatus.setTextColor(normalColor)
    }

    fun setLoadingState() {
        binding.btnBubbleInspect.visibility = View.INVISIBLE
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvBubbleStatus.text = context.getString(R.string.inspector_status_checking)
        val primaryColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimary,
            Color.BLUE
        )
        binding.tvBubbleStatus.setTextColor(primaryColor)
    }

    fun setErrorState(errorMessage: String) {
        binding.btnBubbleInspect.visibility = View.VISIBLE
        binding.btnBubbleInspect.isEnabled = true
        binding.progressIndicator.visibility = View.GONE
        binding.tvBubbleStatus.text = errorMessage
        val errorColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorError,
            Color.RED
        )
        binding.tvBubbleStatus.setTextColor(errorColor)
        playErrorAnimation()
    }

    fun playErrorAnimation() {
        errorShakeAnimator?.cancel()
        errorShakeAnimator = ValueAnimator.ofFloat(0f, -14f, 14f, -10f, 10f, -5f, 5f, 0f).apply {
            duration = 320
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                translationX = anim.animatedValue as Float
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        errorShakeAnimator?.cancel()
        errorShakeAnimator = null
        super.onDetachedFromWindow()
    }
}
