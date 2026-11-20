# NIP-46 signer in the background: tray, background mode, nostrconnect links

Status: shipped 2026-11-20.

Android keeps its NIP-46 signer answering apps from a background service and opens
`nostrconnect://` offers from any app. Desktop now does the same.

## nostrconnect:// links

- Registered beside `nostr:`/`amethyst:` on every package: macOS `CFBundleURLSchemes`, the
  Linux desktop entries (jpackage, `scripts/fix-deb-desktop-entry.sh`, AppImage, Flatpak),
  and Windows' per-user registry (`DesktopDeepLinks.WINDOWS_SCHEMES`).
- `uriToRoute` already sends an offer to the Remote Signer screen, which asks before pairing.
- The login screen hands its own `nostrconnect://` code to a signer app. With Amethyst now a
  handler, the OS may hand it back: `DesktopDeepLinks.markOwnHandoff` remembers the code and
  `offer` drops it. On Linux and Windows the handoff also skips Amethyst up front
  (`hasNostrConnectHandler`); macOS can't be asked, so only the drop guards it there.

## Close to the tray

`BackgroundCloseRules`: closing the main window when it would quit keeps the app running while
any loaded account has its signer on (`AccountCacheState.anySignerRunning`).

- Tray icon from ComposeNativeTray (MIT): Win32, an NSStatusItem, or a StatusNotifierItem over
  D-Bus on Linux. Shown only while a signer runs. Menu: Open Amethyst, Quit Amethyst; a left
  click opens.
- The icon comes from files (`IconResources.trayIconFiles`: `.ico` on Windows, PNG elsewhere).
  The library's Painter overloads render through `Image.encodeToData`, which Compose 1.12's
  Skiko no longer has: that crashed the app as the tray appeared.
- Linux shows the icon only while a tray host (`org.kde.StatusNotifierWatcher`) runs. Stock GNOME
  has none without the AppIndicator extension. `SystemTrayHost` asks the session bus; without a
  host closing quits, as with no signer: the app never runs on with nothing on screen.
- macOS: Cmd+Q and the Dock's Quit go to a quit handler (`installAppMenuHandlers`), since the
  `CLOSE_ALL_WINDOWS` strategy alone would only send the window to the tray. A Dock click
  (`AppReopenedListener`) brings the window back.
- A relaunch while in the tray reaches the running copy (`HiddenWindowMarker.TRAY_FILE`,
  `amethyst:open` over the instance bus) instead of opening a second one.
- Flatpak: `--talk-name=org.kde.StatusNotifierWatcher` and `--own-name=org.kde.*` (the item's
  name carries the pid).

## Background mode

Compose Desktop reports a minimized window as CREATED but a hidden one as STARTED, so a window
in the tray kept every feed subscribed. `HiddenWindowLifecycle` caps the screens' lifecycle at
CREATED while the main window is hidden: lifecycle-aware feed subscriptions close after their
30 s grace, as on Android. What stays: each account's always-on subscriptions (metadata and lists,
inbox notifications, DMs and gift wraps, drafts, wallet) and the signer.

Unlike Android, every signing account keeps pulling its notifications in the background: Desktop
has no "keep this account active in the background" setting, and it always notified every account.

## Checked

Headless (Xvfb), with a fake tray host on a private session bus and a REQ-logging proxy in front
of a local geode: the icon registers when the signer turns on; closing hides the window; the
nApplets feed's REQ closes 30 s later while 7 account subscriptions stay; the tray click and a
relaunch link bring the window back and the feed resubscribes; the tray's Quit exits; with the
signer off closing still quits; with no tray host the app keeps running and the window stays (since changed: it now quits).

## Open

- Real trays on Windows, macOS, KDE and GNOME+AppIndicator were not tried, only the D-Bus
  protocol on Linux.
- `flathub/gradle-sources.json` must be regenerated for the new dependency at the next release
  (`flathub/tools/regenerate-gradle-sources.sh <tag>`).
- The release build's ProGuard keep rule for `com.kdroid.composetray.**` was not tried in a
  packaged build.
