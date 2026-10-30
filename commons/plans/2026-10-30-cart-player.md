# Playing game cartridges in notes — 2026-10-30

Status: shipped on Android and Desktop. Checked on Desktop with a real note; not yet on an Android
device.

Notes can carry 128x128 fantasy-console cartridges (`.p8.png`, for example as a
`blossom:<sha256>.png` URI). Amethyst detects them and plays them natively. The runtime itself, a
from-scratch Lua interpreter, graphics, sound and cart formats, is the
[kiko-kmp](https://github.com/vitorpamplona/kiko-kmp) library, in three artifacts: `kiko` runs a
cart, `kiko-player` plays it in real time without UI (game thread, frame clock, hang watchdog,
saves, sound) and `kiko-compose` shows it (`KikoGame`: screen, touch controls, keys, pausing with
the app). `commons` uses `libs.kiko.player`, `commonsUI` uses `libs.kiko.compose`; `-PkikoKmpDir`
builds a local checkout. Their design notes live in that repository. This note covers what
Amethyst adds around them.

## In the app


- **Detection** (`commons/.../cartridges/CartDetector.kt`): every image a note shows passes the
  shared `ZoomableContentView` wrapper (`commonsUI/.../note/platform/NotePlatformCalls.kt`). A PNG
  whose size is 160x205 or unknown gets its first 24 bytes read with a Range request through the
  image HTTP client (Tor-aware), and only when the user lets images load. The one cart post found
  on relays is a bare `image.nostr.build/<hash>.png` with no `imeta`, so tags alone would miss it.
  Answers are cached per URL.
- **Player** (`commonsUI/.../ui/cartridges/CartPlayer.kt`, `CartPlayerViewModel` in commons): the
  app holds one `KikoPlayer` per cart in `CartPlayerViewModel` (so a rotation keeps the game) and
  shows it with `KikoGame` in a full-screen dialog, adding the pause and close buttons, its
  strings, Back and the Android window mode. The library gives it the screen scaled by whole
  numbers, a direction pad and O/X buttons (multi-touch, slide between directions), keyboard and
  gamepad keys, and sound through `AudioTrack` (Android) or `javax.sound` (Desktop). A pause
  button opens the game's pause menu on touch screens. The pad and the O/X area each read every
  finger as it slides, so a thumb moves between directions, diagonals and off the pad's edge
  without lifting, and rolls from O to X through a strip that presses both (kiko-compose's
  `ControlsTouchTest`). Each new press gives a haptic tick. Both areas are excluded from Android's
  edge gestures, and Back opens the pause menu (Back again closes the player), so a stray swipe
  never ends a game. Landscape hides the system bars; the screen stays on while the player is
  open. The pointer over the screen and typed keys reach games that read them. Input is handed to
  the game's own thread between frames, so a tap shorter than a frame still counts. The game runs
  on its own thread, paced by the clock; it pauses when the dialog closes and resumes where it
  was. `blossom:` URIs are resolved through the BUD-10 finder, and a declared sha256 is checked
  before the cart loads.
- **Saves** (kiko-player's save buffer over `CartSaveStore`, a `KikoSaveStore`): `cartdata` lands
  in the app's `cart_saves` DataStore as soon as the game saves (at most once a second for a game
  that saves every frame, plus once more when the player stops). Kept per install, by the game's
  own save id. `CartSaveKillTest` SIGKILLs a JVM right after a save and reads it back in the next
  one; on Desktop a high score survived `kill -9` and showed on the title after a restart. The
  game in progress is not saved: the Lua state (coroutines included) lives only in memory, so a
  killed app restarts the game from its title, like the original console.
- **Lifecycle**: the game runs only while the app is started. Home, the app switcher or a
  minimized window pauses it and its sound and writes its saves.
- **Multi-cart games**: there is no convention yet for a post to name its other carts, and
  fetching them from the original's site would add a third-party server, so `loadCart` returns
  null and the player shows "this game continues in another cartridge".
- **Uploads**: Android's `MediaCompressor` uploads a PNG that loads as a cart unchanged instead of
  re-encoding it to JPEG. Desktop never re-encoded PNGs.

## Next steps

1. Try it on an Android phone (touch controls, sound latency, rotation keeping the game, frame
   time of the heaviest carts on a low-end device).
2. Decide how a post names the other carts of a multi-cart game (e.g. more `imeta` entries), then
   answer `ConsoleHost.loadCart` from it.
3. iOS: an HTTP client for `CartBytes`, and an audio sink in kiko-player, both stubs there.
