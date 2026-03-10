package com.example.metareel

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val stateText = findViewById<TextView>(R.id.stateText)
        val countersText = findViewById<TextView>(R.id.countersText)

        findViewById<Button>(R.id.overlayPermissionButton).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.startOverlayButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                stateText.text = "Overlay permission not granted."
                return@setOnClickListener
            }
            val serviceIntent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            CounterStore.reset()
        }

        lifecycleScope.launch {
            CounterStore.stats.collectLatest { stats ->
                stateText.text = "Current section: ${stats.currentSection} | Playing: ${stats.isPlaying}"
                countersText.text = buildString {
                    append("Total reel scrolls: ${stats.totalScrolls}\n")
                    if (stats.sectionScrolls.isEmpty()) {
                        append("No section counts yet")
                    } else {
                        stats.sectionScrolls.forEach { (section, count) ->
                            append("$section: $count\n")
                        }
                    }
                }
            }
        }
    }
}
