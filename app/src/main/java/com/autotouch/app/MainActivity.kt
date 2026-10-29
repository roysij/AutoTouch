package com.autotouch.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var scriptInput: EditText
    private lateinit var loopsInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var serviceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scriptInput = findViewById(R.id.scriptInput)
        loopsInput = findViewById(R.id.loopsInput)
        delayInput = findViewById(R.id.delayInput)
        serviceStatus = findViewById(R.id.serviceStatus)
        findViewById<TextView>(R.id.helpText).text = HELP

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (save()) Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPanel).setOnClickListener {
            if (!save()) return@setOnClickListener
            val service = AutoService.instance
            if (service == null) {
                Toast.makeText(this, "Turn on the AutoTouch accessibility service first", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                service.showPanel()
                Toast.makeText(this, "Panel shown – open the app you want to automate", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload in case the 📍 Pick button appended new lines while we were away.
        scriptInput.setText(ScriptStore.script(this))
        loopsInput.setText(ScriptStore.loops(this).toString())
        delayInput.setText(ScriptStore.stepDelay(this).toString())
        updateServiceStatus()
    }

    override fun onPause() {
        super.onPause()
        save(quiet = true)
    }

    private fun updateServiceStatus() {
        val on = AutoService.instance != null
        serviceStatus.text = if (on) "● Accessibility service is ON" else "○ Accessibility service is OFF – tap the button below and enable \"AutoTouch automation\""
        serviceStatus.setBackgroundColor(if (on) 0x331E8E3E else 0x33D93025)
        serviceStatus.setTextColor(if (on) Color.rgb(30, 142, 62) else Color.rgb(217, 48, 37))
    }

    /** Validates and saves. Returns false (and shows the error) if the script is invalid. */
    private fun save(quiet: Boolean = false): Boolean {
        val script = scriptInput.text.toString()
        val loops = loopsInput.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 1
        val delay = delayInput.text.toString().toLongOrNull()?.coerceAtLeast(0) ?: 300L
        if (!quiet) {
            try {
                MacroParser.parse(script)
            } catch (e: MacroParser.ParseError) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                return false
            }
        }
        ScriptStore.save(this, script, loops, delay)
        return true
    }

    private companion object {
        val HELP = """
            COMMANDS (one per line, # = comment)

            tap X Y               tap a point
            doubletap X Y
            longpress X Y [MS]    default 800
            swipe X1 Y1 X2 Y2 [MS] default 300
            wait MS               pause

            type TEXT             replace text in focused field
            append TEXT           add to focused field
            typeslow MS TEXT      type char by char
            clear                 empty focused field
            enter                 keyboard Enter/Send (Android 11+)

            click TEXT            click a button/element by its
                                  text, description, hint or id
            focus TEXT            focus an input field by its
                                  text, hint or id

            back | home | recents | notifications

            EXAMPLE – send a chat message
            click Search
            focus Search
            type Juan
            wait 1000
            click Juan dela Cruz
            focus Message
            type Good morning!
            click Send

            Tips: use 📍 Pick on the floating panel to grab
            coordinates. Or turn on Developer options ▸
            Pointer location to see X/Y while you touch.
        """.trimIndent()
    }
}
