# Accessible KISS prototype

This is a GPL-3.0-or-later KISS fork, based on upstream commit 242682663ac581e8ef62c887691833faabfce617. Upstream copyright, LICENSE and search/provider code are retained.

The approved visual references are retained separately in the owner's Space and local working copy. They are design references, not screenshots of the running app.

## Implemented first pass
- Full-width notification dashboard, Android ranking order, no persisted notification content, Open and Dismiss actions.
- Bottom icon dock: up to five KISS favorite apps on wide screens, four on narrow screens, and two recently launched apps excluding favorites. The dock scrolls horizontally instead of shrinking targets.
- Local editable tasks and optional read-only device calendar events for the next week.
- Pull down from the top of the home feed to search; an explicit accessible Search button is also available.
- Existing KISS provider/query/history pipeline drives search results. Alphabet browsing is a separate view over KISS's loaded app provider. Long-press a result to choose favorites.
- High contrast lavender/black styling, wrapping text, 20–60sp base text slider (Android font scaling still applies).
- Quick-controls dialog with Wi-Fi, Bluetooth, display and general settings shortcuts.
- Separate application ID `app.dot.accessiblekiss` (`.debug` for test builds), so this does not replace upstream KISS.

## Not finished / verification limits
- No device or emulator validation yet. Fold continuity, keyboard/Back behavior, TalkBack, large font clipping, notification access/revocation, work profiles and system bars require testing.
- Inline notification quick reply and per-notification action buttons are not included yet. Open takes you to the originating app. Android may redact content, including detected OTPs.
- Calendar uses an asynchronous read-only query, refreshed on resume. Calendar source permissions are only requested after an explicit tap.
- Task integration is local only; there is no external task-service sync.
- Quick controls currently open system settings; direct brightness/flashlight controls are not implemented yet.
- The initial A–Z view filters from the selected letter onward; final polished sliding index and non-Latin indexing need iteration.
- No release signing key is included or configured. CI debug APK uses an ephemeral debug signer; it is for testing, not a stable update channel.

## Build and test
Use JDK 21 and Android SDK 36 with upstream Gradle wrapper:
```
./gradlew lint testDebugUnitTest assembleDebug
```
See upstream `qa-suite.md` plus the checks above. CI builds on the prototype branch. This work does not install the APK, choose a default launcher, or grant any permissions.
