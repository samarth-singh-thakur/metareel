# MetaReel Dev Overlay (Android / Kotlin)

Development-only Android app that:

- Draws a floating overlay on top of apps (including Instagram)
- Uses an AccessibilityService to observe Instagram UI events
- Detects a best-effort "reel playing" state from accessibility labels
- Counts reel scroll events overall and per inferred Instagram section

## Important

This is **for local dev/testing only**. Accessibility + overlay behavior can break with Instagram UI changes and should not be treated as production-safe logic.

## Setup

1. Build and install the app.
2. Open app and tap:
   - **Grant Overlay Permission**
   - **Open Accessibility Settings** and enable **MetaReel Accessibility**
   - **Start Floating Counter**
3. Open Instagram and scroll reels.
4. Watch counters update in both app UI and floating overlay.

## Permissions used

- `SYSTEM_ALERT_WINDOW` (draw overlay)
- `FOREGROUND_SERVICE` (keep overlay service alive)
- `BIND_ACCESSIBILITY_SERVICE` (service-level permission for accessibility binding)
