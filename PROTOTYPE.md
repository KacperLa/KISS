# Accessible KISS prototype

This is a GPL-3.0-or-later KISS fork, based on upstream commit 242682663ac581e8ef62c887691833faabfce617. Upstream copyright, LICENSE and search/provider code are retained.

The approved visual references are retained separately in the owner's Space and local working copy. They are design references, not screenshots of the running app.

## Implemented first pass
- Full-width notification dashboard with app icons, Android ranking order, no persisted notification content, Open and Dismiss actions.
- Bottom icon dock: up to five KISS favorite apps on wide screens, four on narrow screens, and two recently launched apps excluding favorites. The dock scrolls horizontally instead of shrinking targets.
- Local editable tasks and optional read-only device calendar events for the next week.
- Home is feed-only, with a fixed quick-controls button at the far left of the bottom dock. Pull down from the top of the feed to search.
- Existing KISS provider/query/history pipeline drives typed search. Empty search shows up to six recent apps followed by all apps in one continuous alphabetical list; A–Z jumps within that list. Search has only a back arrow and wide input. Long-press a result to choose favorites.
- App rows are transparent over system wallpaper, with temporary press/focus feedback. Two columns are used when the app-list area is at least 600dp wide; otherwise one column. Long app names fade at the trailing edge without ellipses, and accessibility retains the full name.
- High contrast lavender/black styling, wrapping text, 20–60sp base text slider (Android font scaling still applies).
- Quick-controls dialog with Wi-Fi, Bluetooth, display and general settings shortcuts.
- Separate application ID `app.dot.accessiblekiss` (`.debug` for test builds), so this does not replace upstream KISS.

## Not finished / verification limits
- No device or emulator validation yet. Fold continuity, keyboard/Back behavior, TalkBack, large font clipping, notification access/revocation, work profiles and system bars require testing.
- Inline notification quick reply and per-notification action buttons are not included yet. Open takes you to the originating app. Android may redact content, including detected OTPs.
- Calendar uses an asynchronous read-only query, refreshed on resume. Calendar source permissions are only requested after an explicit tap.
- Task integration is local only; there is no external task-service sync.
- Quick controls currently open system settings; direct brightness/flashlight controls are not implemented yet.
- A–Z navigates Latin initials (including decomposed accented initials). Other names remain in the full list; non-Latin indexing needs iteration.
- No release signing key is included or configured. CI debug APK uses an ephemeral debug signer; it is for testing, not a stable update channel.

## Build and test
Use JDK 21 and Android SDK 36 with upstream Gradle wrapper:
```
./gradlew lint testDebugUnitTest assembleDebug
```
See upstream `qa-suite.md` plus the checks above. CI builds on the prototype branch. This work does not install the APK, choose a default launcher, or grant any permissions.

## Third prototype adjustments

Quick controls now offers App columns: Auto, 1 column, or 2 columns. Auto follows available list width; explicit choices persist across folding and rotation without shrinking font size. App names retain their full accessibility labels and visual trailing fade. Notification action buttons align to the trailing edge.

The search input uses the entire header width with no back icon. Android Home returns to the dashboard when this app is the selected default launcher. Standard Back first dismisses the keyboard where Android consumes it, then returns from search. No default launcher setting is changed by the build.

Notification body previews default to four rendered lines. Show more/Show less appears only when the rendered text overflows. Expansion is kept in memory by notification key across refreshes, not persisted on disk. Expanding does not launch the source app.

Tap a notification card to open its content intent. Swipe horizontally at least 72dp to dismiss clearable notifications; an accessible Dismiss notification action is also provided. Vertical gestures remain feed scrolling. Expand and app-action controls handle their own taps. Generic Open/Dismiss buttons are removed. Non-reply app-provided actions are shown right-aligned; inline reply requiring RemoteInput remains unsupported.
