package com.tanu.personal.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import com.tanu.personal.MainActivity
import com.tanu.personal.R
import kotlin.math.abs

class FloatingAssistantService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        showBubble()
    }

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = 36f
            }
            elevation = 14f
            setPadding(12, 12, 12, 12)
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.tanu_app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        box.addView(icon, LinearLayout.LayoutParams(112, 112))

        val params = WindowManager.LayoutParams(
            136,
            136,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 260
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        box.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX - (event.rawX - downX)).toInt()
                    params.y = (startY + (event.rawY - downY)).toInt()
                    windowManager?.updateViewLayout(box, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downX) < 18 && abs(event.rawY - downY) < 18) openApp()
                    true
                }
                else -> false
            }
        }
        bubble = box
        windowManager?.addView(box, params)
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_new_meeting", true)
        )
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
