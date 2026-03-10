# 📱 MetaReel - Instagram Reels Monitor

MetaReel is a development and monitoring tool designed to track and analyze user activity within the Instagram app, specifically focusing on Reels. It provides real-time statistics and insights into how Instagram serves content.

## 🎯 Features

- **Real-time Monitoring**: Track Instagram Reels as you scroll through them
- **Floating Overlay**: Display live statistics on top of Instagram
- **Detailed Analytics**: 
  - Username of reel creators
  - Like counts
  - Ad/Sponsored content detection
  - Scroll counts (both unique reels and raw scroll events)
  - Section tracking (Home, Reels, Search, Profile)
- **Non-intrusive**: Runs in the background without interfering with Instagram

## 🏗️ Architecture

MetaReel consists of three main components:

### 1. ReelAccessibilityService
An `AccessibilityService` that monitors the Instagram UI in real-time:
- Detects which section the user is viewing (Home, Reels, Search, Profile)
- Extracts reel metadata (username, likes, caption snippets)
- Identifies sponsored/ad content
- Tracks scroll events with deduplication logic
- Uses stability checks to avoid counting the same reel multiple times

### 2. OverlayService
A foreground service that displays a floating overlay window:
- Shows real-time statistics on top of other apps
- Displays current reel information (username, likes)
- Shows scroll counts and section breakdowns
- Updates reactively as data changes

### 3. CounterStore
A centralized state management system using Kotlin Coroutines and StateFlow:
- Single source of truth for all statistics
- Reactive updates to all subscribers
- Thread-safe state management
- Provides both unique reel counts and raw scroll events

## 📋 Requirements

- Android 8.0 (API 26) or higher
- Instagram app installed
- Accessibility service permission
- Overlay permission
- Notification permission (Android 13+)

## 🚀 Setup Instructions

### 1. Build and Install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Grant Permissions

#### a. Overlay Permission
1. Open MetaReel app
2. Tap "Grant Overlay Permission"
3. Enable "Display over other apps"

#### b. Accessibility Service
1. Tap "Open Accessibility Settings"
2. Find "MetaReel Accessibility" in the list
3. Enable the service
4. Confirm the permission dialog

#### c. Notification Permission (Android 13+)
- The app will automatically request this permission
- Grant it when prompted

### 3. Start Monitoring
1. In MetaReel app, tap "Start Overlay Service"
2. Open Instagram
3. Navigate to Reels section
4. Start scrolling through reels
5. Watch the overlay update in real-time!

## 📊 Understanding the Statistics

### Unique Reels
Counts each distinct reel viewed. Uses deduplication logic based on:
- Username + likes combination
- Caption snippets for ads
- Stability checks (reel must be visible for 250ms)

### Raw Scrolls
Counts all scroll events, including:
- Repeated scrolls on the same reel
- Quick swipes
- Partial scrolls

### Section Breakdown
Shows statistics per Instagram section:
- **Reels**: Main Reels feed
- **Home**: Home feed
- **Search**: Search/Explore section
- **Profile**: User profiles

## 🔧 Technical Details

### Key Technologies
- **Kotlin**: Primary programming language
- **Coroutines & Flow**: Reactive state management
- **AccessibilityService**: UI monitoring
- **WindowManager**: Overlay display
- **Foreground Service**: Background processing

### Detection Algorithm
1. **Event Capture**: Monitors accessibility events (scroll, content change)
2. **Debouncing**: 150ms delay to batch rapid events
3. **Feature Extraction**: Analyzes UI tree for username, likes, captions
4. **Likelihood Scoring**: Assigns confidence score to detected reels
5. **Deduplication**: Compares with previous reel to avoid duplicates
6. **Stability Check**: Requires 250ms stability before counting
7. **Gap Check**: Enforces 600ms minimum between counted reels

### State Management Flow
```
AccessibilityService → CounterStore → OverlayService
                              ↓
                         MainActivity
```

## 🐛 Troubleshooting

### Overlay Not Showing
- Ensure overlay permission is granted
- Check that the service is running (green indicator in app)
- Try stopping and restarting the service

### No Statistics Updating
- Verify accessibility service is enabled
- Check that you're in the Instagram app
- Ensure you're scrolling through Reels (not Home feed)
- Look for logs in Logcat with tag `ReelA11yService`

### Duplicate Counts
- The app uses sophisticated deduplication logic
- If you see duplicates, they may be legitimate (same user posted multiple reels)
- Check the logs to see what's being detected

### Service Stops Unexpectedly
- Android may kill the service to save battery
- This is normal behavior for background services
- Simply restart the service from the app

## 📱 Logcat Monitoring

To see detailed logs:
```bash
adb logcat -s ReelA11yService:* OverlayService:* CounterStore:*
```

Key log messages:
- `✅ REEL COUNTED`: A unique reel was successfully counted
- `📍 Section changed to`: User navigated to a different section
- `🚀 OverlayService created`: Overlay service started
- `✅ Overlay displayed successfully`: Overlay is visible

## ⚠️ Important Notes

### Privacy & Ethics
- This tool is for **development and research purposes only**
- It monitors only what's visible on your own device
- No data is transmitted or stored externally
- Respects Instagram's terms of service for personal use

### Performance
- Minimal battery impact (uses efficient event processing)
- Low memory footprint
- Optimized UI tree traversal
- Debounced event handling

### Limitations
- Only works when Instagram is in the foreground
- Requires accessibility service (may affect other accessibility features)
- Instagram UI changes may require updates to detection logic
- Some reels may not be detected if UI structure differs

## 🔄 Updates & Maintenance

Instagram frequently updates its UI, which may affect detection accuracy. If you notice issues:

1. Check the logs to see what's being detected
2. Update the detection logic in `ReelAccessibilityService.kt`
3. Adjust the likelihood scoring thresholds
4. Test with different types of content (regular reels, ads, etc.)

## 📄 License

This project is for educational and development purposes. Use responsibly and in accordance with Instagram's terms of service.

## 🤝 Contributing

This is a development tool. Feel free to:
- Report issues with detection accuracy
- Suggest improvements to the algorithm
- Share insights about Instagram's UI patterns
- Optimize performance

## 📞 Support

For issues or questions:
1. Check the troubleshooting section
2. Review the logs in Logcat
3. Verify all permissions are granted
4. Ensure you're using a compatible Android version

---

**Built with ❤️ for Instagram Reels analysis and development**
