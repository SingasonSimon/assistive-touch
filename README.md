# Assistive Touch Kotlin

A modern Android Assistive Touch app built with Kotlin and Material components.

It provides a draggable floating button and a quick action panel for accessibility/system actions such as Home, Back, Recents, lock screen, screenshot, notifications, brightness, volume, flashlight, and screen recording.

---

## Highlights

- Floating overlay button (drag, snap-to-edge, tap-to-open panel, long-press action)
- Accessibility-powered global actions:
  - Home
  - Back
  - Recents
  - Lock screen
  - Screenshot
  - Open notifications shade
- Quick panel system actions:
  - Wi-Fi settings panel
  - Bluetooth settings
  - Flashlight toggle
  - Volume slider
  - Brightness slider (with `WRITE_SETTINGS` capability)
- Native screen recording flow using MediaProjection
  - Runtime consent screen
  - Foreground recording service
  - Persistent notification stop action
  - Video saved to MediaStore (`Movies/AssistiveTouch` on Android 10+)
- App favorites manager to pin launchable apps
- Light/Dark panel theming and button customization (size/alpha/color)

---

## Tech Stack

- **Language:** Kotlin
- **Build system:** Gradle (Android application plugin)
- **UI:** Android Views + Material Components
- **Min SDK:** 29
- **Target SDK / Compile SDK:** 35
- **Java / Kotlin target:** 17

---

## Project Structure

```text
app/src/main/java/com/example/assistivetouch/
  prefs/
    FavoritesManager.kt
  service/
    FloatingButtonService.kt
    MyAccessibilityService.kt
    ScreenRecordingService.kt
  ui/
    WelcomeActivity.kt
    MainActivity.kt
    SettingsActivity.kt
    FavoritesActivity.kt
    ScreenRecordPermissionActivity.kt
```

---

## Permissions & Why They Are Needed

### Core permissions

- `SYSTEM_ALERT_WINDOW`
  - Required to draw the floating button on top of other apps.
- `BIND_ACCESSIBILITY_SERVICE` (service-level permission)
  - Required to perform global actions (Home/Back/Recents/Lock/Screenshot/Notifications).
- `FOREGROUND_SERVICE`
  - Required for long-running user-visible services.
- `POST_NOTIFICATIONS`
  - Used for persistent notifications (service/recording status).

### Feature-specific permissions

- `WRITE_SETTINGS`
  - Needed to change screen brightness from the panel.
- `CAMERA`
  - Used for flashlight control via camera torch API.
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`
  - Required for screen recording foreground service type on modern Android.

### Package visibility

This app does **not** request broad package visibility (`QUERY_ALL_PACKAGES`).
It uses a targeted `<queries>` launcher-intent declaration to list launchable apps for favorites.

---

## How It Works

1. User grants overlay + accessibility permissions from `MainActivity`.
2. `FloatingButtonService` starts as foreground service and renders draggable floating UI.
3. Tapping the bubble opens panel actions.
4. Accessibility actions are dispatched through `MyAccessibilityService`.
5. Screen recording flow:
   - Panel record button opens `ScreenRecordPermissionActivity`.
   - User grants MediaProjection consent.
   - `ScreenRecordingService` starts recording via `MediaRecorder` + `VirtualDisplay`.
   - User stops from panel (toggle) or notification action.

---

## Local Development

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK for API 35
- JDK 17
- Gradle wrapper (included)

### Clone

```bash
git clone https://github.com/x-dash-io/assistive-touch.git
cd assistive-touch
```

### Build debug APK

```bash
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Run unit tests

```bash
./gradlew testDebugUnitTest
```

---

## Release Notes (Important)

Current `app/build.gradle` release signing block uses the debug keystore for convenience.
Before publishing to Play Store, replace this with your production keystore and secure credentials.

Recommended pre-release checklist:

- Configure production signing
- Verify privacy policy URL
- Complete Data safety declaration
- Validate accessibility disclosure language and in-app messaging
- Test on physical devices across Android 10/12/14/15

Additional checklist: see [`PLAYSTORE_READINESS.md`](PLAYSTORE_READINESS.md).

---

## Testing Guidance

Automated tests exist under:

- `app/src/test/`
- `app/src/androidTest/`

Manual regression scenarios to run before release:

- Overlay and accessibility permission onboarding
- Panel actions (Home/Back/Recents/Lock/Screenshot/Notifications)
- Brightness write settings behavior with/without permission
- Flashlight behavior on devices with/without torch
- Screen recording start/stop and file output
- Favorites search + selection persistence

---

## Known Environment Caveat (for CI/sandbox users)

If your environment blocks outbound artifact resolution (e.g. HTTP 403 from Maven/Google), Gradle tasks may fail before compilation.
In that case, run the same commands from a machine/network with normal Maven repository access.

---

## License

No license file is currently defined in this repository.
If you plan to open-source publicly, add a license (for example: MIT, Apache-2.0, GPL-3.0).

