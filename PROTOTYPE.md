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

## Notification launch compatibility fix

User-triggered notification card/action launches now pass Android sender-side activity-launch options on API 34+. API 36 uses ALLOW_IF_VISIBLE; API 34–35 uses the older supported mode, guarded by the resumed, non-destroyed activity lifecycle. Older Android retains the existing PendingIntent path. No background automatic launch, fallback launch that loses the notification target, or permission changes were added. Phone testing is still needed.

Column preferences now apply separately to wide/square versus narrow window layouts, identified from window width/height ratio (at least 0.75 is wide). Auto chooses two on wide layouts and one on narrow layouts. Bounds are independent of keyboard height and display scaling. Old global column overrides are intentionally not migrated because forcing two globally caused the cover-screen regression. This is window geometry, not hardware hinge detection.

## Calendar and notification action polish

Upcoming events have a narrow rounded color bar at the left using event display color, calendar color fallback, then launcher accent only if neither is provided. Text remains readable without depending on color. Notification actions lay out horizontally, aligned right, wrapping only when actual measured labels overflow. Media notifications are recognized by Android category/media-session metadata and show their provided action icons centered in supplied order with accessible labels and 56dp targets. Missing icons fall back to action text. No extra permission or media-session access is requested.

The A–Z index has no persistent background or shadow. Its full 56dp touch region, sliding/jump behavior, accessibility actions and temporary selected-letter feedback remain.

## Media icon and swipe animation fixes

Media icon lookup now uses the source application context, recognizes MediaStyle/compact-actions metadata, and remains icon-only if an icon is missing. Resource names are preferred for fallback transport symbols; unknown controls use a generic more icon with the original accessible label/intent. Long-press reveals the supplied label. Clearable cards track horizontal finger movement, fade and animate out on dismissal, or snap back on short/canceled drags. Non-clearable cards remain stationary.

## Unified Home feed

Home contains notifications, tasks, upcoming calendar, then all apps alphabetically in one continuous scroll. Its fixed right rail has section jumps, A–Z sliding navigation and a bottom magnifier that opens typed KISS search. Search no longer shows recent apps or an alphabet rail. The favorites/recent-app dock is unchanged. Home no longer intercepts pull-down to open search, leaving normal system shade gestures alone. Adaptive app columns and full accessible app labels remain.

## Continuous rail gesture

A single pointer stream now crosses notifications, tasks, calendar and A–Z in either direction without lifting. Existing labeled section buttons and alphabet accessibility actions remain for assistive navigation. The bottom Search control remains a separate tap; sliding into it does not launch search. Ordinary home refresh waits until rail release so jump targets remain stable.

## Integrated feed position indicator

The native Home scrollbar is hidden. A contrasting circle moves on the right navigation rail according to actual section/letter heading positions, interpolating between anchors rather than treating the whole feed as a uniform percentage. Grabbing the circle scrubs the feed smoothly; other section/alphabet gestures, selected-letter popup, accessibility controls and separate Search remain. Notification app icons now use KISS IconsHandler masking and existing icon-shape preferences; media and navigation glyphs are not masked.

Call notifications (platform call category or CallStyle) center offered controls. Exact recognized English hang-up/mute/unmute labels use circular controls; unknown/localized labels retain text and real action intent. No controls are manufactured; microphone glyph reflects offered mute/unmute action, not an independently queried audio state. Material call/microphone vectors: Google Material Design Icons, Apache-2.0.

Recognized exposed call speaker controls now use matching circular speaker icons. Explicit off actions use a slashed speaker; generic Speaker retains its original accessible label without claiming current routing state. Unknown audio-routing labels remain text.
