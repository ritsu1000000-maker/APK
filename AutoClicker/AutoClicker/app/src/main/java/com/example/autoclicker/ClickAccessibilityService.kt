package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView

/**
 * 画面上の指定した1点を、設定した間隔で自動的にタップし続けるアクセシビリティサービス。
 * ・赤い丸(ターゲット)をドラッグしてタップしたい場所に移動
 * ・コントロールパネルの「開始」でタップ開始、もう一度押すと停止
 * ・間隔はテキストをタップすると 100 / 300 / 500 / 1000 / 2000 / 5000 ms を循環
 */
class ClickAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var targetView: View? = null
    private var controlView: View? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var controlParams: WindowManager.LayoutParams? = null

    private var isClicking = false
    private val intervalOptions = listOf(100L, 300L, 500L, 1000L, 2000L, 5000L)
    private var intervalIndex = 3 // デフォルト 1000ms

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addTargetMarker()
        addControlPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun overlayType(): Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    private fun addTargetMarker() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_target, null)
        targetView = view

        val density = resources.displayMetrics.density
        val size = (50 * density).toInt()

        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val metrics = resources.displayMetrics
        params.x = metrics.widthPixels / 2 - size / 2
        params.y = metrics.heightPixels / 3

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    windowManager.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        targetParams = params
    }

    private fun addControlPanel() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_control, null)
        controlView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 100

        val dragHandle = view.findViewById<TextView>(R.id.dragHandle)
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        dragHandle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    windowManager.updateViewLayout(controlView, params)
                    true
                }
                else -> false
            }
        }

        val tvInterval = view.findViewById<TextView>(R.id.tvInterval)
        tvInterval.text = "間隔: ${intervalOptions[intervalIndex]} ms (タップで変更)"
        tvInterval.setOnClickListener {
            intervalIndex = (intervalIndex + 1) % intervalOptions.size
            tvInterval.text = "間隔: ${intervalOptions[intervalIndex]} ms (タップで変更)"
        }

        val btnToggle = view.findViewById<Button>(R.id.btnToggle)
        btnToggle.setOnClickListener {
            isClicking = !isClicking
            btnToggle.text = if (isClicking) "停止" else "開始"
            if (isClicking) startClicking() else stopClicking()
        }

        val btnStopService = view.findViewById<Button>(R.id.btnStopService)
        btnStopService.setOnClickListener {
            stopClicking()
            removeOverlays()
            disableSelf()
        }

        windowManager.addView(view, params)
        controlParams = params
    }

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!isClicking) return
            performTapAtTarget()
            handler.postDelayed(this, intervalOptions[intervalIndex])
        }
    }

    private fun startClicking() {
        handler.post(clickRunnable)
    }

    private fun stopClicking() {
        isClicking = false
        handler.removeCallbacks(clickRunnable)
    }

    private fun performTapAtTarget() {
        val params = targetParams ?: return
        val density = resources.displayMetrics.density
        val size = (50 * density).toInt()
        val x = (params.x + size / 2).toFloat()
        val y = (params.y + size / 2).toFloat()

        val path = Path()
        path.moveTo(x, y)

        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 50)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(strokeDescription)

        dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun removeOverlays() {
        targetView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        controlView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        targetView = null
        controlView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        removeOverlays()
    }
}
