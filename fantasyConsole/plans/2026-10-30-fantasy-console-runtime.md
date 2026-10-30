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
| `runtime` | `FantasyConsole` (frame loop, host API), `Ram`, `Graphics`, `Font`, `Audio`, `Input`, `Api` (the globals) |

Design points:

- **Numbers** are the raw 32-bit pattern in an `Int`; `+`/`-` wrap for free.
- **Locals** resolve to frame slots at parse time; only captured locals are boxed in a `Cell`.
- **Coroutines** are Kotlin coroutines. `coresume`/`yield`/`flip` post to a one-slot
  `Scheduler` and suspend, so the native stack never grows with resumes (20,000 resumes in one
  frame is a test).
- **Draw state lives in RAM** at its documented address, so carts that `poke` it work.
- The host calls `step()` once per frame, then reads `render()` and `audioBuffer`
  (22,050 Hz mono). Run it on a thread with `RECOMMENDED_STACK_BYTES` (16 MiB): a 1 MiB stack
  overflows between 500 and 1000 nested Lua calls.

## Verified

- 34 unit tests: fixed-point edge cases, every syntax shorthand, closures per iteration, varargs,
  metatables, goto, coroutines, `all()` with deletion, `_ENV`, frame loop, `flip()` loops, errors
  with line numbers, cart data, graphics primitives, palettes, clip, camera, fill patterns, both
  code compressions, P8SCII round trip, and a `.p8.png` written by the JDK's PNG encoder.
- 15 recent carts from the public cart forum (kept out of the repo, they are CC BY-NC-SA) run 300
  frames each without errors and render recognizably: platformers, a raycaster, a music tracker,
  a strategy map, minified code with P8SCII control codes. Reproduce with
  `./gradlew :fantasyConsole:jvmTest --tests '*CartSmokeTest*' -DfantasyConsole.carts=<dir>`.
- Speed on the desktop JVM, steady state: 1.3-5.3 ms per frame for the heaviest of those carts
  (budget: 33 ms at 30 fps). Profile: table lookups ~15%, continuation allocation ~13%.
- Compiles for JVM, Android and iosArm64.

## Known gaps

- Text: no wide/tall/inverted modes, custom fonts (`\^.`, 0x5600) or the small-caps font for
  uppercase; extended glyphs beyond the button symbols draw blank.
- Sound: waveforms and effects are approximations; custom instruments use only their
  waveform; no fades, filters (0x5f40) or `\a` sounds in `print`.
- `load()` of another cart, `menuitem`, mouse/keyboard (`stat(30-34)`), `stat()` clock values,
  `serial`, `extcmd` are stubs. Map/sprite address remapping (0x5f54-0x5f57) is not read.
- No CPU budget: a heavy cart runs as fast as the host thread allows instead of slowing down.
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
  `AudioTrack` (Android) or `javax.sound` (Desktop). The game runs on its own 16 MiB thread, paced
  by the clock; it pauses when the dialog closes and resumes where it was. `blossom:` URIs are
  resolved through the BUD-10 finder, and a declared sha256 is checked before the cart loads.
- **Uploads**: Android's `MediaCompressor` uploads a PNG that loads as a cart unchanged instead of
  re-encoding it to JPEG. Desktop never re-encoded PNGs.

## Next steps

1. Try it on an Android phone (touch controls, sound latency, rotation keeping the game).
2. Close the runtime gaps above, starting with wide text and sound fidelity, driven by a larger
   cart corpus.
3. iOS: an audio sink and an HTTP client for the two `expect`s that are stubs there.
4. Move the module to its own repository once the API settles.
