# Accessible KISS prototype

An accessible presentation over KISS's existing search/provider/history pipeline, maintained on `prototype/accessible-dashboard`. Upstream license and notices are preserved.

## Current behavior

- Home is one scrollable feed: notifications, local tasks, upcoming calendar events, then alphabetically grouped apps. Empty letter groups are omitted. Numbers, symbols and other alphabets appear under #.
- A transparent right rail provides one continuous gesture through notification/task/calendar shortcuts and all 26 letters. It draws large A/Z and dots with an enlarged selected-letter popup. Letter jumps align each heading to the same viewport top; measured trailing space lets the final group align too. Section icons are original scalable vectors. Search at the bottom remains a separate tap.
- Search uses the existing KISS pipeline. An empty query displays up to six unique Recent apps from KISS launch history. Home's full app list and the favorites/recent-app dock are separate and unchanged.
- App rows preserve full accessibility names and use a trailing visual fade, not ellipsis. Wide/square windows default to two columns; narrow windows to one. Separate per-layout overrides survive folding and display scaling.
- The removed Quick controls pane and dock gear have no remaining code/listeners. Text size and column overrides live in normal KISS Settings. Long-press the Search magnifier or use its Launcher settings accessibility action to reach them. Existing preference values are retained.
- Notification cards include app icons, four-line previews with expansion only on actual overflow, tap-to-open and animated horizontal dismissal for clearable notifications. Short/canceled drags snap back; non-dismissible cards stay still. Refreshes defer during gestures to preserve stability.
- Non-reply notification actions align right and wrap when needed. Media actions use source-app icons and metadata, centered in their supplied order; fallback symbols preserve the original intent/accessibility label. Unknown actions use a generic icon and a long-press label.
- Upcoming events have a narrow rounded bar using event display color, then calendar color, then the launcher accent. Event information is still conveyed in text.
- Local tasks stay on device. Calendar access is optional and read-only. Notification content is not persisted and is hidden while locked. No install, default-launcher switch or permission grant is performed by the build.

## Build and signing

GitHub Actions runs lint, unit tests, and APK builds. Test APKs use `app.dot.accessiblekiss.debug`. The downloaded artifact is signed outside GitHub with the durable private signer backed up in the user's private Space. No private key or password belongs in this repository or workflow artifacts. APK signature and package/version are verified before delivery.

## Limitations

Physical-device rendering and accessibility testing remain necessary. Inline notification replies are not implemented. Android or source apps can redact notification content or omit launch/actions. Calendar currently shows up to five events over the next seven days. The layout uses window proportions rather than hardware hinge detection. The alphabet index groups non-Latin initials under #. Existing KISS settings/features remain available; no shared permission was removed merely because the dashboard's Quick controls pane was removed.
