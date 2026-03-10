package com.example.metareel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity provides the control interface for MetaReel.
 * Users can grant permissions, start/stop the overlay service, and view statistics.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var overlayButton: Button
    private lateinit var startOverlayButton: Button
    private lateinit var stopOverlayButton: Button
    
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupClickListeners()
        observeStats()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
        overlayButton = findViewById(R.id.overlayPermissionButton)
        startOverlayButton = findViewById(R.id.startOverlayButton)
        stopOverlayButton = findViewById(R.id.stopOverlayButton)
        findViewById<Button>(R.id.accessibilityButton)
        findViewById<Button>(R.id.resetButton)
    }

    private fun setupClickListeners() {
        // Overlay permission button
        overlayButton.setOnClickListener {
            requestOverlayPermission()
        }

        // Accessibility settings button
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            openAccessibilitySettings()
        }

        // Start overlay service button
        startOverlayButton.setOnClickListener {
            startOverlayService()
        }

        // Stop overlay service button
        stopOverlayButton.setOnClickListener {
            stopOverlayService()
        }

        // Reset counters button
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            resetCounters()
        }
    }

    /**
     * Observes statistics from CounterStore and updates the UI
     */
    private fun observeStats() {
        lifecycleScope.launch {
            CounterStore.stats.collectLatest { stats ->
                updateStatsDisplay(stats)
            }
        }
    }

    /**
     * Updates the statistics display with current data
     */
    private fun updateStatsDisplay(stats: ReelStats) {
        statsText.text = buildString {
            append("📊 Statistics\n")
            append("━━━━━━━━━━━━━━━━━━━━━━\n\n")
            
            // Current section and reel info
            append("📍 Current Section: ${stats.currentSection.uppercase()}\n\n")
            
            if (stats.currentUsername.isNotEmpty() && stats.currentUsername != "Unknown") {
                append("👤 Current User: @${stats.currentUsername}\n")
                append("❤️ Likes: ${stats.currentLikes}\n")
                if (stats.isAd) {
                    append("📢 Type: AD/Sponsored\n")
                }
                append("\n")
            }
            
            // Scroll statistics
            append("📈 Unique Reels Viewed: ${stats.totalScrolls}\n")
            append("🔄 Total Scroll Events: ${stats.rawScrolls}\n\n")
            
            // Section breakdown
            if (stats.sectionScrolls.isNotEmpty()) {
                append("📊 Breakdown by Section:\n")
                stats.sectionScrolls.forEach { (section, count) ->
                    val rawCount = stats.sectionRawScrolls[section] ?: 0
                    append("  • ${section.uppercase()}: $count unique ($rawCount raw)\n")
                }
            } else {
                append("No reels tracked yet.\n")
                append("Open Instagram and scroll through Reels!")
            }
        }
    }

    /**
     * Checks and displays current permission status
     */
    private fun checkPermissions() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        statusText.text = buildString {
            append("🔐 Permissions Status\n")
            append("━━━━━━━━━━━━━━━━━━━━━━\n\n")
            append("Overlay Permission: ${if (hasOverlay) "✅ Granted" else "❌ Not Granted"}\n")
            append("Notification Permission: ${if (hasNotification) "✅ Granted" else "❌ Not Granted"}\n")
            append("Accessibility Service: ${if (isAccessibilityServiceEnabled()) "✅ Enabled" else "❌ Disabled"}\n\n")
            
            if (isServiceRunning) {
                append("🟢 Overlay Service: RUNNING\n")
            } else {
                append("🔴 Overlay Service: STOPPED\n")
            }
            
            if (!hasOverlay || !hasNotification || !isAccessibilityServiceEnabled()) {
                append("\n⚠️ Please grant all permissions to use MetaReel")
            }
        }

        // Update button states
        overlayButton.isEnabled = !hasOverlay
        startOverlayButton.isEnabled = hasOverlay && !isServiceRunning
        stopOverlayButton.isEnabled = isServiceRunning

        // Request notification permission if needed
        if (!hasNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    /**
     * Checks if the accessibility service is enabled
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${ReelAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        return enabledServices.contains(expectedComponentName)
    }

    /**
     * Requests overlay permission
     */
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens accessibility settings
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            this,
            "Enable 'MetaReel Accessibility' service",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Starts the overlay service
     */
    private fun startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(
                this,
                "Please enable Accessibility Service first",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        isServiceRunning = true
        checkPermissions()
        Toast.makeText(this, "Overlay service started", Toast.LENGTH_SHORT).show()
    }

    /**
     * Stops the overlay service
     */
    private fun stopOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        stopService(serviceIntent)
        
        isServiceRunning = false
        checkPermissions()
        Toast.makeText(this, "Overlay service stopped", Toast.LENGTH_SHORT).show()
    }

    /**
     * Resets all counters
     */
    private fun resetCounters() {
        CounterStore.reset()
        Toast.makeText(this, "Counters reset", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            checkPermissions()
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}

// Made with Bob
