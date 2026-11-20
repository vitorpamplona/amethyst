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

- Tray icon from ComposeNativeTray 2.1.7 (`dev.nucleusframework`, MIT; the `io.github.kdroidfilter`
  coordinates stopped at 1.3.3): Win32, an NSStatusItem, or a StatusNotifierItem over
  D-Bus on Linux. Shown only while a signer runs. Menu: Open Amethyst, Quit Amethyst; a left
  click opens.
- Windows and Linux draw the icon from files (`IconResources.trayIconFiles`: `.ico` on Windows,
  PNG on Linux), written to the data folder's `tray/` only when the tray is about to show. macOS
  draws `TrayGlyph`, the one-colour gem of Android's themed icon cropped to its bounds, which the
  library renders black or white to match the menu bar's appearance.
- The first close that would go to the tray, with the window still on screen, asks once
  (`TrayExplainDialog`): where Amethyst went and why, Keep running or Quit. The answer is kept in
  the data folder's `tray-explained`; dismissing it keeps the window and asks again next time.
  A Painter is drawn off-screen first and that can fail (no DirectX 12 on Windows), leaving no
  icon while the window hides. 1.3.3's Painter path also crashed on Compose 1.12's Skiko
  (`Image.encodeToData`); 2.1.1 fixed that.
- Linux shows the icon only while a tray host (`org.kde.StatusNotifierWatcher`) runs. Stock GNOME
  has none without the AppIndicator extension. Without a tray closing quits, as with no signer:
  the app never runs on with nothing on screen.
- The library reports no failure, so `SystemTrayHost` checks for itself: `available()` (its native
  library loads, and on Linux a tray host runs) before showing the icon, asked again every 30 s on
  Linux while there is none; `showing()` again on every close, off the UI thread, before hiding
  (on Linux the item also owns `org.kde.StatusNotifierItem-<pid>-1`, which it needs to register).
  A failed check quits instead of hiding.
- Closing the last screen window with the main one already hidden takes the same route
  (`onAllClosed`'s `keepRunning`).
- macOS: Cmd+Q and the Dock's Quit go to a quit handler (`installAppMenuHandlers`), since the
  `CLOSE_ALL_WINDOWS` strategy alone would only send the window to the tray. Its `QuitResponse` is
  held (`MacQuitResponse`) and cancelled when the app declines (Keep printing, dismissed), so a
  later Cmd+Q or a logout doesn't stall behind it. A Dock click (`AppReopenedListener`) brings the
  window back.
- A relaunch while in the tray reaches the running copy (`HiddenWindowMarker.TRAY_FILE`,
  `amethyst:open` over the instance bus) instead of opening a second one.
- Flatpak: `--talk-name=org.kde.StatusNotifierWatcher` and
  `--own-name=org.kde.StatusNotifierItem-2-1`. The item claims that name before registering and
  shows no icon without it (checked with a bus that refuses it); in the sandbox the app is pid 2.

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
signer off closing still quits; with no tray host closing quits; a tray host started after the
app gets the icon within 30 s; a tray host gone before the close, or an item that could not claim
its name (a bus that refuses it, as Flatpak's would), makes closing quit instead of hiding.

## Open

- Real trays on Windows, macOS, KDE and GNOME+AppIndicator were not tried, only the D-Bus
  protocol on Linux. Nor a real Flatpak sandbox: the item's pid 2 there is reasoned, not seen.
- `flathub/gradle-sources.json` must be regenerated for the new dependency at the next release
  (`flathub/tools/regenerate-gradle-sources.sh <tag>`).
- The release build's ProGuard keep rules for `dev.nucleusframework.**` were not tried in a
  packaged build.
