# Screensaver Notification Helper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Toast-based feedback messages during screensaver operation with a custom view-based notification that works on all platforms including FireOS DreamService.

**Architecture:** Create a `NotificationHelper` object that adds a styled `TextView` directly to the screensaver's root `FrameLayout`. Since the view is part of the screensaver's own view hierarchy, it's visible on all platforms regardless of DreamService window context. Auto-dismisses after a configurable duration with fade animation.

**Tech Stack:** Android XML drawables, Kotlin coroutines, FrameLayout view manipulation

## Global Constraints

- Target SDK: Android TV / Fire OS (API 21+)
- Minimum API: 21 (Android 5.0)
- Follow existing code patterns in `ui/helpers/` package
- Use `sans-serif-thin` font family matching existing `OverlayText` style
- Notification appears at bottom center with 48dp margin from bottom edge

---

## File Structure

| Action | File | Purpose |
|--------|------|---------|
| Create | `app/src/main/res/drawable/bg_notification.xml` | Rounded-corner background drawable |
| Create | `app/src/main/java/com/neilturner/aerialviews/ui/helpers/NotificationHelper.kt` | Helper object for showing notifications |
| Modify | `app/src/main/java/com/neilturner/aerialviews/ui/core/VideoPlayerView.kt:139-144` | Replace ToastHelper with NotificationHelper for looping message |
| Modify | `app/src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt:799-802` | Replace ToastHelper with NotificationHelper for brightness message |
| Modify | `app/src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt:864-867` | Replace Toast with NotificationHelper for playback speed message |

---

### Task 1: Create notification background drawable

**Covers:** Visual styling requirement

**Files:**
- Create: `app/src/main/res/drawable/bg_notification.xml`

**Interfaces:**
- Consumes: None (standalone drawable)
- Produces: Drawable resource `R.drawable.bg_notification`

- [ ] **Step 1: Create the drawable**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC000000" />
    <corners android:radius="8dp" />
    <padding
        android:bottom="12dp"
        android:left="16dp"
        android:right="16dp"
        android:top="12dp" />
</shape>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/drawable/bg_notification.xml
git commit -m "feat: add notification background drawable with rounded corners"
```

---

### Task 2: Create NotificationHelper object

**Covers:** Core notification functionality

**Files:**
- Create: `app/src/main/java/com/neilturner/aerialviews/ui/helpers/NotificationHelper.kt`

**Interfaces:**
- Consumes: `R.drawable.bg_notification`, `R.style.OverlayText`
- Produces: `NotificationHelper.show(view: ViewGroup, message: String, duration: Long)` suspend function

- [ ] **Step 1: Create the NotificationHelper**

```kotlin
package com.neilturner.aerialviews.ui.helpers

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.neilturner.aerialviews.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

object NotificationHelper {
    private const val DEFAULT_DURATION_MS = 3000L
    private const val FADE_DURATION_MS = 200L
    private const val BOTTOM_MARGIN_DP = 48

    fun show(
        view: ViewGroup,
        message: String,
        duration: Long = DEFAULT_DURATION_MS,
    ) {
        val context = view.context
        val textView =
            TextView(context).apply {
                setText(R.style.OverlayText)
                text = message
                setBackgroundResource(R.drawable.bg_notification)
                gravity = Gravity.CENTER
            }

        val params =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (BOTTOM_MARGIN_DP * context.resources.displayMetrics.density).toInt()
            }

        view.addView(textView, params)

        // Fade in
        textView.alpha = 0f
        textView.animate()
            .alpha(1f)
            .setDuration(FADE_DURATION_MS)
            .start()

        // Auto-dismiss after duration
        Handler(Looper.getMainLooper()).postDelayed({
            textView.animate()
                .alpha(0f)
                .setDuration(FADE_DURATION_MS)
                .withEndAction {
                    view.removeView(textView)
                }
                .start()
        }, duration)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/neilturner/aerialviews/ui/helpers/NotificationHelper.kt
git commit -m "feat: add NotificationHelper for screensaver feedback messages"
```

---

### Task 3: Update VideoPlayerView to use NotificationHelper

**Covers:** Replace Toast for looping message

**Files:**
- Modify: `app/src/main/java/com/neilturner/aerialviews/ui/core/VideoPlayerView.kt:139-144`

**Interfaces:**
- Consumes: `NotificationHelper.show()`, `parent` ViewGroup from VideoPlayerView
- Produces: Updated looping toggle feedback

- [ ] **Step 1: Update the toggleLooping method**

Replace lines 139-144 in `VideoPlayerView.kt`:

```kotlin
// Old code:
if (mainScope.coroutineContext[Job]?.isActive == true) {
    mainScope.launch {
        val message = if (GeneralPrefs.loopUntilSkipped) "Looping enabled" else "Looping disabled"
        ToastHelper.show(context, message)
    }
}

// New code:
val parent = parent as? ViewGroup
if (parent != null) {
    val message = if (GeneralPrefs.loopUntilSkipped) "Looping enabled" else "Looping disabled"
    NotificationHelper.show(parent, message)
}
```

- [ ] **Step 2: Update imports**

Add import at top of `VideoPlayerView.kt`:

```kotlin
import com.neilturner.aerialviews.ui.helpers.NotificationHelper
import android.view.ViewGroup
```

Remove unused import if present:

```kotlin
import com.neilturner.aerialviews.ui.helpers.ToastHelper
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/neilturner/aerialviews/ui/core/VideoPlayerView.kt
git commit -m "feat: use NotificationHelper for looping toggle message"
```

---

### Task 4: Update ScreenController to use NotificationHelper

**Covers:** Replace Toast for brightness and playback speed messages

**Files:**
- Modify: `app/src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt:799-802`
- Modify: `app/src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt:864-867`

**Interfaces:**
- Consumes: `NotificationHelper.show()`, `view` property from ScreenController
- Produces: Updated brightness and playback speed feedback

- [ ] **Step 1: Update changeBrightness method**

Replace lines 799-802 in `ScreenController.kt`:

```kotlin
// Old code:
// Show toast
mainScope.launch {
    ToastHelper.show(context, "Brightness: $newBrightness%")
}

// New code:
// Show notification
NotificationHelper.show(view, "Brightness: $newBrightness%")
```

- [ ] **Step 2: Update handlePlaybackSpeedChanged method**

Replace lines 864-867 in `ScreenController.kt`:

```kotlin
// Old code:
private fun handlePlaybackSpeedChanged() {
    val message = resources.getString(R.string.playlist_playback_speed_changed, GeneralPrefs.playbackSpeed + "x")
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

// New code:
private fun handlePlaybackSpeedChanged() {
    val message = resources.getString(R.string.playlist_playback_speed_changed, GeneralPrefs.playbackSpeed + "x")
    NotificationHelper.show(view, message)
}
```

- [ ] **Step 3: Update imports**

Add import at top of `ScreenController.kt`:

```kotlin
import com.neilturner.aerialviews.ui.helpers.NotificationHelper
```

Remove unused imports if present:

```kotlin
import android.widget.Toast
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt
git commit -m "feat: use NotificationHelper for brightness and speed messages"
```

---

### Task 5: Verify build and test

**Covers:** Final verification

**Files:**
- None (verification only)

**Interfaces:**
- Consumes: All previous tasks
- Produces: Passing build, manual verification

- [ ] **Step 1: Run build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run lint checks**

```bash
./gradlew lintDebug
```

Expected: No new errors

- [ ] **Step 3: Manual verification checklist**

Test on device/emulator:
- [ ] Long-press OK button with TOGGLE_LOOPING assigned → notification shows "Looping enabled"
- [ ] Long-press OK button again → notification shows "Looping disabled"
- [ ] Adjust brightness → notification shows "Brightness: X%"
- [ ] Change playback speed → notification shows speed change message
- [ ] Notification appears at bottom center
- [ ] Notification fades in and auto-dismisses after ~3 seconds
- [ ] Notification works in Test Screensaver mode
- [ ] Notification works when screensaver starts naturally (if FireOS device available)

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete notification helper for screensaver feedback"
```
