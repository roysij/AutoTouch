package com.autotouch.app

import android.accessibilityservice.AccessibilityService

/** One action in a macro script. */
sealed class Step(val label: String) {
    class Tap(val x: Float, val y: Float) : Step("tap ${x.toInt()},${y.toInt()}")
    class DoubleTap(val x: Float, val y: Float) : Step("doubletap ${x.toInt()},${y.toInt()}")
    class LongPress(val x: Float, val y: Float, val ms: Long) : Step("longpress ${x.toInt()},${y.toInt()}")
    class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val ms: Long) :
        Step("swipe ${x1.toInt()},${y1.toInt()} → ${x2.toInt()},${y2.toInt()}")
    class Wait(val ms: Long) : Step("wait ${ms}ms")
    class Type(val text: String) : Step("type \"${text.take(20)}\"")
    class Append(val text: String) : Step("append \"${text.take(20)}\"")
    class TypeSlow(val charDelay: Long, val text: String) : Step("typeslow \"${text.take(20)}\"")
    class Clear : Step("clear")
    class Click(val query: String) : Step("click \"$query\"")
    class Focus(val query: String) : Step("focus \"$query\"")
    class Enter : Step("enter")
    class Global(val action: Int, name: String) : Step(name)
}

/**
 * Parses a plain-text macro script. One command per line; lines starting with # are comments.
 *
 *   tap X Y                     single tap at screen coordinates
 *   doubletap X Y
 *   longpress X Y [ms]          default 800 ms
 *   swipe X1 Y1 X2 Y2 [ms]      default 300 ms
 *   wait MS
 *   type TEXT                   replace text in the focused input field
 *   append TEXT                 add to the end of the focused field
 *   typeslow MS TEXT            type one character at a time, MS between characters
 *   clear                       empty the focused field
 *   click TEXT                  click the element whose text/description/hint/id matches
 *   focus TEXT                  focus the input field matching TEXT (text, hint or id)
 *   enter                       press the keyboard's Enter/Send/Search action (Android 11+)
 *   back | home | recents | notifications
 */
object MacroParser {
    class ParseError(msg: String) : Exception(msg)

    fun parse(script: String): List<Step> {
        val steps = mutableListOf<Step>()
        script.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val lineNo = index + 1
            val cmd = line.substringBefore(' ').lowercase()
            val rest = line.substringAfter(' ', "").trim()

            fun nums(min: Int): List<Float> {
                val parts = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val values = parts.map {
                    it.toFloatOrNull() ?: throw ParseError("Line $lineNo: '$it' is not a number")
                }
                if (values.size < min) throw ParseError("Line $lineNo: '$cmd' needs at least $min numbers")
                values.forEach { if (it < 0) throw ParseError("Line $lineNo: coordinates can't be negative") }
                return values
            }

            fun text(): String {
                if (rest.isEmpty()) throw ParseError("Line $lineNo: '$cmd' needs some text")
                return unquote(rest)
            }

            steps += when (cmd) {
                "tap" -> nums(2).let { Step.Tap(it[0], it[1]) }
                "doubletap" -> nums(2).let { Step.DoubleTap(it[0], it[1]) }
                "longpress" -> nums(2).let { Step.LongPress(it[0], it[1], it.getOrNull(2)?.toLong() ?: 800L) }
                "swipe" -> nums(4).let { Step.Swipe(it[0], it[1], it[2], it[3], it.getOrNull(4)?.toLong() ?: 300L) }
                "wait", "sleep", "delay" -> nums(1).let { Step.Wait(it[0].toLong()) }
                "type" -> Step.Type(text())
                "append" -> Step.Append(text())
                "typeslow" -> {
                    val delayPart = rest.substringBefore(' ')
                    val ms = delayPart.toLongOrNull()
                        ?: throw ParseError("Line $lineNo: use 'typeslow MS TEXT', e.g. typeslow 80 Hello")
                    val t = rest.substringAfter(' ', "").trim()
                    if (t.isEmpty()) throw ParseError("Line $lineNo: 'typeslow' needs some text")
                    Step.TypeSlow(ms, unquote(t))
                }
                "clear" -> Step.Clear()
                "click" -> Step.Click(text())
                "focus" -> Step.Focus(text())
                "enter" -> Step.Enter()
                "back" -> Step.Global(AccessibilityService.GLOBAL_ACTION_BACK, "back")
                "home" -> Step.Global(AccessibilityService.GLOBAL_ACTION_HOME, "home")
                "recents" -> Step.Global(AccessibilityService.GLOBAL_ACTION_RECENTS, "recents")
                "notifications" -> Step.Global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "notifications")
                else -> throw ParseError("Line $lineNo: unknown command '$cmd'")
            }
        }
        if (steps.isEmpty()) throw ParseError("Script has no commands")
        return steps
    }

    private fun unquote(s: String): String =
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') s.substring(1, s.length - 1) else s
}
