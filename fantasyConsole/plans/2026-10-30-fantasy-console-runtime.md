# Fantasy-console cart runtime — 2026-10-30

Status: runtime written and tested; carts in notes get a Play button and a full-screen player
(Android and Desktop). Checked on Desktop with a real note; not yet on an Android device.

## Goal

Play 128x128 fantasy-console cartridges (`.p8`, `.p8.png`) posted on Nostr, for example as a
`blossom:<sha256>.png` URI, natively in Amethyst instead of in a WebView. The runtime is written
from scratch in Kotlin Multiplatform so Android, Desktop and iOS share it, and so it can move to
its own repository and Maven Central like the libraries in `AGENTS.md`.

## Why from scratch

- The original runtime is proprietary; it cannot ship inside an MIT app.
- LuaJ (MIT, Lua 5.2) is JVM-only and counts in doubles. Carts depend on 16:16 fixed-point
  overflow and rounding, and on the console's syntax, so a Lua engine needed changes anyway.
- Nothing here is copied: the API, syntax and memory map come from the public manual, and the
  file formats from community documentation. The 3x5 font is our own drawing (the original font's
  license is unconfirmed). The module never uses the console's name in its API.

## Layout (`com.vitorpamplona.fantasyconsole`, stdlib only)

| Package | What |
|---|---|
| `lua` | `Fix` (16:16 math), `Lexer`, `Parser` → executable AST (`Ast.kt`), `Interpreter` (metatables, coroutines), `LuaTable` |
| `cart` | `CartLoader` (`.p8.png`, `.p8`, `.p8.rom`, detected by content), `CodeCompression` (`:c:` and `pxa`), `P8scii` |
| `png` | `PngDecoder` + `Inflate`, so `commonMain` needs no `java.util.zip` |
| `runtime` | `FantasyConsole` (frame loop, host API), `Ram`, `Graphics`, `Printer` (P8SCII text), `Font`, `Audio`, `Input`, `PauseMenu`, `Clock`, `Api` (the globals) |

Design points:

- **Numbers** are the raw 32-bit pattern in an `Int`; `+`/`-` wrap for free.
- **Locals** resolve to frame slots at parse time; only captured locals are boxed in a `Cell`.
- **Coroutines** are Kotlin coroutines. `coresume`/`yield`/`flip` post to a one-slot
  `Scheduler` and suspend, so the native stack never grows with resumes (20,000 resumes in one
  frame is a test).
- **Draw state lives in RAM** at its documented address, so carts that `poke` it work.
- **The cart's frame loop is Lua.** Like the original, the console copies 60 built-ins into
  top-level locals on the cart's first line (so a function whose `_ENV` is a plain table still
  reaches `circfill`) and appends the `_init`/`_update`/`_draw` loop after the cart's code (so it
  sees a cart's `local function _draw()`). Line numbers are unchanged.
- **Busy waits end the frame.** Loop back-edges call a hook every 1024 iterations; outside
  `_draw`, a cart past its frame time yields there, so `while time() < 3 do end` advances time.
- The host calls `step()` once per frame, then reads `render()` and `audioBuffer`
  (22,050 Hz mono). Run it on a thread with `RECOMMENDED_STACK_BYTES` (64 MiB of reserved
  stack): some carts run an interpreter of their own that recurses over 10,000 calls deep. A
  deeper chain ends the cart with "stack overflow" (`Interpreter.MAX_DEPTH`, 50,000), not a crash.
- **Games in several carts** ask the host for the next one (`ConsoleHost.loadCart`) on
  `load(name, breadcrumb, param)` and `reload(…, name)`; `extcmd("breadcrumb")` goes back.

## Verified

- 46 unit tests: fixed-point edge cases, every syntax shorthand, closures per iteration, varargs,
  metatables, goto, coroutines, `all()` with deletion, `_ENV`, frame loop, `flip()` loops, busy
  waits, errors with line numbers, cart data, graphics primitives, palettes, clip, camera, fill
  patterns with colors and inversion, out-of-bounds values, map and screen remapping, print modes,
  control codes and custom fonts, the pause menu and `menuitem`, mouse and keyboard, `run(param)`,
  multi-cart `load`/`reload`, both code compressions, P8SCII round trip, and a `.p8.png` written by
  the JDK's PNG encoder.
- 164 carts from the public cart forum (kept out of the repo, they are CC BY-NC-SA): its featured
  list plus recent posts, 300 frames each with buttons pressed at random after frame 15. 151 run
  and render recognizably (platformers, raycasters, a music tracker, card games, a fighting game,
  minified and `_ENV`-heavy code, custom fonts). 11 are the first cart of a multi-cart game and
  stop with a clear message, since the app cannot fetch the next cart yet. 2 hit their own bug
  (a nil read) when a button is held through a screen change, which the original console does
  too. Reproduce with `./gradlew :fantasyConsole:jvmTest --tests '*CartSmokeTest*'
  -DfantasyConsole.carts=<dir> -DfantasyConsole.out=<dir>`.
- Semantics were cross-checked against the public manual and two open-source tools' readings of
  it, without running or copying their code.
- Speed on the desktop JVM, steady state: 1.3-5.3 ms per frame for the heaviest of those carts
  (budget: 33 ms at 30 fps). Profile: table lookups ~15%, continuation allocation ~13%.
- Compiles for JVM, Android and iosArm64.

## Known gaps

- Sound: waveforms and effects are approximations, and the per-sfx filters (noiz, buzz, detune,
  reverb, dampen) are not applied. Custom instruments, fades, music masks and `\a` in `print`
  work.
- The app answers no `loadCart`, so multi-cart games stop at the first cart (see "In the app").
- Kana glyphs (154-253) draw blank. `serial`, `export`, `cstore` and `printh` to a file do
  nothing.
- No CPU budget: a heavy cart runs as fast as the host thread allows instead of slowing down.
  `stat(1)` is wall-clock time over the frame, capped at 2.
- Exactness of `rnd` sequences, `atan2(0, 0)` and number printing is from community docs, not
  checked against the original.

## In the app

- **Detection** (`commons/.../cartridges/CartDetector.kt`): every image a note shows passes the
  shared `ZoomableContentView` wrapper (`commonsUI/.../note/platform/NotePlatformCalls.kt`). A PNG
  whose size is 160x205 or unknown gets its first 24 bytes read with a Range request through the
  image HTTP client (Tor-aware), and only when the user lets images load. The one cart post found
  on relays is a bare `image.nostr.build/<hash>.png` with no `imeta`, so tags alone would miss it.
  Answers are cached per URL.
- **Player** (`commonsUI/.../ui/cartridges/CartPlayer.kt`, `CartPlayerViewModel` in commons): a
  full-screen dialog with the screen scaled by whole numbers, a direction pad and O/X buttons
  (multi-touch, slide between directions), keyboard and gamepad keys, and sound through
  `AudioTrack` (Android) or `javax.sound` (Desktop). A pause button opens the game's pause menu
  on touch screens. The pad and the O/X area each read every finger as it slides, so a thumb
  moves between directions, diagonals and off the pad's edge without lifting, and rolls from O to
  X through a strip that presses both (`CartControlsTouchTest`). Each new press gives a haptic
  tick. Both areas are excluded from Android's edge gestures, and Back opens the pause menu
  (Back again closes the player), so a stray swipe never ends a game. Landscape hides the system
  bars; the screen stays on while the player is open.
  physical keyboard. The pointer over the screen and typed keys reach games that read them. Input
  is handed to the game's own thread between frames, so a tap shorter than a frame still counts.
  The game runs on its own thread, paced by the clock; it pauses when the dialog closes and
  resumes where it was. `blossom:` URIs are resolved through the BUD-10 finder, and a declared
  sha256 is checked before the cart loads.
- **Saves** (`CartSaves` over `CartSaveStore`): `cartdata` lands in the app's `cart_saves`
  DataStore as soon as the game saves (at most once a second for a game that saves every frame,
  plus once more when the player stops). Kept per install, by the game's own save id.
  `CartSaveKillTest` SIGKILLs a JVM right after a save and reads it back in the next one; on
  Desktop a high score survived `kill -9` and showed on the title after a restart. The game in
  progress is not saved: the Lua state (coroutines included) lives only in memory, so a killed
  app restarts the game from its title, like the original console.
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
2. Decide how a post names the other carts of a multi-cart game (e.g. more `imeta` entries).
3. Sound filters.
4. iOS: an audio sink and an HTTP client for the two `expect`s that are stubs there.
5. Move the module to its own repository once the API settles.
