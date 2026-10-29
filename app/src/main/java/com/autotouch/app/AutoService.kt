package com.autotouch.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The engine. Android only lets an app touch/type into OTHER apps through an
 * Accessibility Service, which the user must switch on in Settings.
 */
class AutoService : AccessibilityService() {

    companion object {
        /** Non-null while the service is enabled and connected. */
        var instance: AutoService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private lateinit var overlay: OverlayController

    val isRunning: Boolean get() = job?.isActive == true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = OverlayController(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not needed */ }

    override fun onInterrupt() = stopMacro()

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        stopMacro()
        if (::overlay.isInitialized) overlay.hide()
        instance = null
    }

    // ---------------------------------------------------------------- panel

    fun showPanel() = overlay.show()
    fun hidePanel() = overlay.hide()

    // ---------------------------------------------------------------- running

    /** loops <= 0 means repeat until Stop is pressed. */
    fun runMacro(script: String, loops: Int, stepDelay: Long) {
        if (isRunning) return
        val steps = try {
            MacroParser.parse(script)
        } catch (e: MacroParser.ParseError) {
            overlay.setStatus("⚠ ${e.message}")
            return
        }
        overlay.setRunning(true)
        job = scope.launch {
            try {
                val total = if (loops <= 0) Int.MAX_VALUE else loops
                var failures = 0
                for (loop in 1..total) {
                    for (step in steps) {
                        ensureActive()
                        val loopLabel = if (loops <= 0) "Loop $loop" else "Loop $loop/$loops"
                        overlay.setStatus("$loopLabel · ${step.label}")
                        if (!execute(step)) {
                            failures++
                            overlay.setStatus("✗ Failed: ${step.label}")
                            delay(600)
                        }
                        delay(stepDelay)
                    }
                }
                overlay.setStatus(if (failures == 0) "✓ Done" else "Done with $failures failed step(s)")
            } catch (e: CancellationException) {
                overlay.setStatus("■ Stopped")
                throw e
            } finally {
                overlay.setRunning(false)
            }
        }
    }

    fun stopMacro() {
        job?.cancel()
        job = null
    }

    private suspend fun execute(step: Step): Boolean = when (step) {
        is Step.Tap -> tap(step.x, step.y)
        is Step.DoubleTap -> tap(step.x, step.y).also { delay(90) } && tap(step.x, step.y)
        is Step.LongPress -> gesture(pointPath(step.x, step.y), step.ms)
        is Step.Swipe -> gesture(Path().apply {
            moveTo(step.x1, step.y1); lineTo(step.x2, step.y2)
        }, step.ms)
        is Step.Wait -> { delay(step.ms); true }
        is Step.Type -> setText(step.text, append = false)
        is Step.Append -> setText(step.text, append = true)
        is Step.TypeSlow -> typeSlow(step.text, step.charDelay)
        is Step.Clear -> setText("", append = false)
        is Step.Click -> clickElement(step.query)
        is Step.Focus -> focusField(step.query)
        is Step.Enter -> pressEnter()
        is Step.Global -> performGlobalAction(step.action)
    }

    // ---------------------------------------------------------------- touch

    private fun pointPath(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    suspend fun tap(x: Float, y: Float) = gesture(pointPath(x, y), 50)

    private suspend fun gesture(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val duration = durationMs.coerceIn(1, GestureDescription.getMaxGestureDuration())
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!accepted && cont.isActive) cont.resume(false)
        }

    // ---------------------------------------------------------------- typing

    private fun focusedInput(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun setText(text: String, append: Boolean, node: AccessibilityNodeInfo? = focusedInput()): Boolean {
        val target = node ?: return false
        val existing = if (append && !target.isShowingHintText) target.text?.toString().orEmpty() else ""
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Types character by character so apps that react to each keystroke still work. */
    private suspend fun typeSlow(text: String, charDelay: Long): Boolean {
        val node = focusedInput() ?: return false
        val start = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val sb = StringBuilder(start)
        for (c in text) {
            sb.append(c)
            node.refresh()
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, sb.toString())
            }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
            delay(charDelay)
        }
        return true
    }

    private fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val node = focusedInput() ?: return false
        return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    // ---------------------------------------------------------------- finding elements

    /** Waits up to [timeoutMs] for an element to appear on screen. */
    private suspend fun waitForNode(query: String, editableOnly: Boolean, timeoutMs: Long = 3000): AccessibilityNodeInfo? {
        val until = System.currentTimeMillis() + timeoutMs
        while (true) {
            findNode(query, editableOnly)?.let { return it }
            if (System.currentTimeMillis() > until) return null
            delay(250)
        }
    }

    private fun findNode(query: String, editableOnly: Boolean): AccessibilityNodeInfo? {
        val q = query.trim().lowercase()
        val roots = windows.mapNotNull { it.root }.ifEmpty { listOfNotNull(rootInActiveWindow) }
            .filter { it.packageName != packageName } // never match our own panel

        var partial: AccessibilityNodeInfo? = null
        for (root in roots) {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
                if (editableOnly && !n.isEditable) continue
                if (!n.isVisibleToUser) continue

                val candidates = listOfNotNull(
                    n.text?.toString(),
                    n.contentDescription?.toString(),
                    n.hintText?.toString(),
                    n.viewIdResourceName?.substringAfter(":id/"),
                ).map { it.trim().lowercase() }

                if (candidates.any { it == q }) return n          // exact match wins
                if (partial == null && candidates.any { it.contains(q) }) partial = n
            }
        }
        return partial
    }

    private suspend fun clickElement(query: String): Boolean {
        val node = waitForNode(query, editableOnly = false) ?: return false
        // Text labels are often inside a clickable parent (e.g. a button row).
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // Fallback: physically tap the centre of the element.
        val r = Rect().also(node::getBoundsInScreen)
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    private suspend fun focusField(query: String): Boolean {
        val node = waitForNode(query, editableOnly = true) ?: return false
        if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK) // brings up the keyboard in most apps
            return true
        }
        val r = Rect().also(node::getBoundsInScreen)
        return tap(r.exactCenterX(), r.exactCenterY())
    }
}
