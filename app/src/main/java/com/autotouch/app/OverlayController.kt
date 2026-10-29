package com.autotouch.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Small draggable control panel that floats over every app.
 * Uses TYPE_ACCESSIBILITY_OVERLAY, so no extra "draw over other apps" permission is needed.
 */
class OverlayController(private val service: AutoService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private var panel: View? = null
    private var pickView: View? = null
    private var statusView: TextView? = null
    private var runButton: TextView? = null

    private val panelParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(16)
        y = dp(120)
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show() {
        if (panel != null) return
        val ctx: Context = service

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xEE202124.toInt())
                cornerRadius = dp(14).toFloat()
            }
        }

        val title = TextView(ctx).apply {
            text = "⠿  AutoTouch"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }

        val status = TextView(ctx).apply {
            text = "Ready"
            setTextColor(0xFFBDC1C6.toInt())
            textSize = 12f
            maxWidth = dp(230)
            maxLines = 2
        }

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        fun button(label: String, color: Int, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6); topMargin = dp(8) }
        }

        val run = button("▶ Run", GREEN) { toggleRun() }
        row.addView(run)
        row.addView(button("📍 Pick", BLUE) { startPick() })
        row.addView(button("✕", GREY) { hide() })

        root.addView(title)
        root.addView(status)
        root.addView(row)

        // Drag the panel by its title bar.
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = panelParams.x; startY = panelParams.y
                    touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = startX + (e.rawX - touchX).toInt()
                    panelParams.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(root, panelParams)
                    true
                }
                else -> false
            }
        }

        wm.addView(root, panelParams)
        panel = root
        statusView = status
        runButton = run
        setRunning(service.isRunning)
    }

    fun hide() {
        service.stopMacro()
        removePick()
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        statusView = null
        runButton = null
    }

    fun setStatus(text: String) {
        statusView?.text = text
    }

    fun setRunning(running: Boolean) {
        runButton?.apply {
            text = if (running) "■ Stop" else "▶ Run"
            (background as? GradientDrawable)?.setColor(if (running) RED else GREEN)
        }
    }

    private fun toggleRun() {
        if (service.isRunning) {
            service.stopMacro()
        } else {
            removePick()
            service.runMacro(
                ScriptStore.script(service),
                ScriptStore.loops(service),
                ScriptStore.stepDelay(service)
            )
        }
    }

    /** Full-screen catcher: the next touch anywhere becomes a "tap X Y" line in the script. */
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun startPick() {
        if (pickView != null || service.isRunning) return
        val catcher = TextView(service).apply {
            text = "Tap anywhere to capture its coordinates"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(0x441A73E8)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val x = e.rawX.toInt()
                    val y = e.rawY.toInt()
                    ScriptStore.appendLine(service, "tap $x $y")
                    setStatus("Added: tap $x $y")
                    Toast.makeText(service, "Added: tap $x $y", Toast.LENGTH_SHORT).show()
                    removePick()
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(catcher, params)
        pickView = catcher
        setStatus("Pick mode: tap a spot…")
    }

    private fun removePick() {
        pickView?.let { runCatching { wm.removeView(it) } }
        pickView = null
    }

    private companion object {
        const val GREEN = 0xFF1E8E3E.toInt()
        const val BLUE = 0xFF1A73E8.toInt()
        const val RED = 0xFFD93025.toInt()
        const val GREY = 0xFF5F6368.toInt()
    }
}
