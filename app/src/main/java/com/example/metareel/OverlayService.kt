package com.example.metareel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * OverlayService displays a floating overlay window showing real-time Instagram Reel statistics.
 * It subscribes to data from CounterStore and updates the overlay with username, likes, and scroll counts.
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var overlayText: TextView
    private var isOverlayShown = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 OverlayService created")
        
        createNotificationChannel()
        
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        showOverlay()
        observeStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 OverlayService destroyed")
        
        if (isOverlayShown && ::overlayText.isInitialized) {
            try {
                windowManager.removeView(overlayText)
                isOverlayShown = false
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay: ${e.message}")
            }
        }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Creates and displays the floating overlay window
     */
    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        overlayText = TextView(this).apply {
            text = "MetaReel\nWaiting for data..."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000")) // Semi-transparent black
            textSize = 13f
            setPadding(32, 24, 32, 24)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 200
        }

        try {
            windowManager.addView(overlayText, params)
            isOverlayShown = true
            Log.d(TAG, "✅ Overlay displayed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show overlay: ${e.message}")
        }
    }

    /**
     * Observes CounterStore stats and updates the overlay in real-time
     */
    private fun observeStats() {
        scope.launch {
            CounterStore.stats.collectLatest { stats ->
                val displayText = buildString {
                    append("📱 MetaReel Monitor\n")
                    append("━━━━━━━━━━━━━━━━\n")
                    
                    // Current section
                    append("📍 Section: ${stats.currentSection.uppercase()}\n")
                    
                    // Current reel info
                    if (stats.currentUsername.isNotEmpty() && stats.currentUsername != "Unknown") {
                        append("👤 User: @${stats.currentUsername}\n")
                    } else {
                        append("👤 User: Waiting...\n")
                    }
                    
                    if (stats.currentLikes.isNotEmpty() && stats.currentLikes != "0") {
                        append("❤️ Likes: ${stats.currentLikes}\n")
                    } else {
                        append("❤️ Likes: --\n")
                    }
                    
                    if (stats.isAd) {
                        append("📢 AD/Sponsored\n")
                    }
                    
                    append("━━━━━━━━━━━━━━━━\n")
                    
                    // Scroll statistics
                    append("📊 Unique Reels: ${stats.totalScrolls}\n")
                    append("🔄 Raw Scrolls: ${stats.rawScrolls}\n")
                    
                    // Section breakdown
                    if (stats.sectionScrolls.isNotEmpty()) {
                        append("\n📈 By Section:\n")
                        stats.sectionScrolls.forEach { (section, count) ->
                            append("  • ${section}: $count\n")
                        }
                    }
                }
                
                overlayText.text = displayText
            }
        }
    }

    /**
     * Creates notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MetaReel Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows MetaReel overlay status"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the foreground service notification
     */
    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("MetaReel Active")
            .setContentText("Monitoring Instagram Reels activity")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "metareel_overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }
}

// Made with Bob
