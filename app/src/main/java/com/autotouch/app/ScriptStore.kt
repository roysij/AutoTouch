package com.autotouch.app

import android.content.Context

/** Saves the script and run settings so the app and the floating panel share them. */
object ScriptStore {
    private const val PREFS = "autotouch"

    val DEFAULT_SCRIPT = """
        # AutoTouch script – one command per line
        # Use the 📍 Pick button on the floating panel to grab tap coordinates.
        wait 1000
        tap 540 1200
        type Hello from AutoTouch!
        enter
    """.trimIndent()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun script(ctx: Context): String = prefs(ctx).getString("script", null) ?: DEFAULT_SCRIPT
    fun loops(ctx: Context): Int = prefs(ctx).getInt("loops", 1)
    fun stepDelay(ctx: Context): Long = prefs(ctx).getLong("stepDelay", 300L)

    fun save(ctx: Context, script: String, loops: Int, stepDelay: Long) {
        prefs(ctx).edit()
            .putString("script", script)
            .putInt("loops", loops)
            .putLong("stepDelay", stepDelay)
            .apply()
    }

    fun appendLine(ctx: Context, line: String) {
        val current = script(ctx).trimEnd()
        prefs(ctx).edit().putString("script", "$current\n$line").apply()
    }
}
