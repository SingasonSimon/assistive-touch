# Play Store & Security Readiness Review

This checklist summarizes code-level compliance work completed in this branch and what still must be done before publication.

## ✅ Completed in code
- Removed `QUERY_ALL_PACKAGES` from manifest to reduce Play policy risk for broad package visibility.
- Added a `<queries>` launcher intent declaration for app-launcher discovery used by favorites picker.
- Added first-party screen recording flow using `MediaProjection` + foreground service + persistent notification stop action.
- Kept accessibility service non-exported and bound only with `BIND_ACCESSIBILITY_SERVICE`.
- Added `foregroundServiceType="mediaProjection"` for recording service.
- Fixed user-facing copy issue: "Allow modifying system settings ...".
- Fixed floating button snap-to-edge animation bug.
- Improved unit tests to use JUnit assertions instead of JVM `assert(...)`.

## ⚠️ Required before Store submission (outside pure code)
1. **Privacy policy URL** in Play Console (required due to accessibility + camera use).
2. **Data safety form** declaration (what is/is not collected/shared).
3. **Prominent disclosure** in listing/in-app for accessibility usage and user benefit.
4. **App content questionnaire** (permissions justification: camera for flashlight, accessibility actions, media projection screen recording).
5. **Release signing** with your production keystore (do not ship debug keystore).

## Recommended manual QA before release
- Start/stop floating service after granting overlay and accessibility permissions.
- Confirm Home/Back/Recents/Lock/Screenshot/Notifications actions work on physical devices.
- Verify screen recording permission flow, file creation in `Movies/AssistiveTouch`, and stop action from notification.
- Test flashlight behavior on devices with and without camera flash hardware.
- Run on Android 10, 12, 14, and 15 where possible.
