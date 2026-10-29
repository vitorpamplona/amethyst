# Freedoom napplet (proof of concept)

DOOM, as a Nostr event. This builds [doomgeneric](https://github.com/ozkl/doomgeneric) and the
[Freedoom](https://freedoom.github.io/) Phase 1 IWAD into one self-contained `index.html` that
publishes as a NIP-5D napplet, so anyone who sees it in their feed can open it in Amethyst and play.
Nothing in Amethyst changes: the game runs in the existing napplet sandbox, under the existing CSP.

It is also a worked example of packaging a game for that sandbox.

## Build, test, play

```bash
tools/napplet-doom/build.sh                   # -> tools/napplet-doom/build/index.html (~14 MB)
```

```bash
node --test tools/napplet-doom/doom.test.mjs  # headless Chromium, production shell + shim + CSP
```

`build.sh` downloads wasi-sdk 25 and Freedoom 0.13.0 (each checked against a pinned sha256), checks
out doomgeneric at a pinned commit, applies `src/doomgeneric.patch` and compiles. The output is
reproducible: a clean rebuild gives the same artifact sha256, so anyone can rebuild a published
napplet and compare it with its `x` tag. It needs `curl`, `git`, `unzip` and `python3`; Linux
(x86_64, arm64) and macOS (x86_64, arm64) are pinned.

The test plays the build inside Amethyst's real `shell.html` and `shim.js`, with the prelude and CSP
injected the way `NappletWebContract.injectPrelude` does on the device, and a fake native bridge
that keeps storage in memory. It boots to the title loop with music, starts a game from the
keyboard, turns and fires, saves to napplet storage, loads that save on a fresh launch, and keeps
playing when storage is denied or not declared. Screenshots land in `build/screens/`. It uses the
preinstalled `playwright`; set `PLAYWRIGHT_MODULE` and `CHROMIUM` to use others.

Opening `build/index.html` straight in a browser also works (no napplet, so saves last only for
that session).

## Publish

```bash
./gradlew :cli:installDist
cli/build/install/amy/bin/amy napplet publish tools/napplet-doom/build/index.html \
  --server https://blossom.primal.net \
  --optional storage \
  --d freedoom --title "Freedoom" \
  --description "DOOM-engine shooter: doomgeneric + Freedoom Phase 1, all in one napplet" \
  --source https://github.com/vitorpamplona/amethyst/tree/main/tools/napplet-doom \
  --relay wss://relay.damus.io,wss://nos.lol
```

`storage` is optional: the game asks for it in the background (Amethyst prompts once) and plays
either way. The Blossom server must accept a 14 MB blob. On a device, follow
`tools/napplet-test/README.md` to find and open it.

## How it fits the sandbox

| Napplet rule | What the game does |
|---|---|
| One HTML artifact, `connect-src 'none'` | `doom.wasm` and `freedoom1.wad` are embedded as gzip+base64 and unpacked with `DecompressionStream`. The WAD is 28.8 MB raw, 10.2 MB gzipped; the page is 14 MB, under the 20 MiB the host downloads (`NappletBlobHttp.MAX_BLOB_BYTES`). |
| `script-src 'wasm-unsafe-eval'`, no `eval` | `WebAssembly.compile` on the bytes; no generated JS. |
| `worker-src 'none'` | Single-threaded build; no pthreads, no `SharedArrayBuffer`. |
| `media-src 'none'` | No `<audio>`: sound effects are DMX lumps decoded into `AudioBuffer`s, music is the engine's MUS converted to MIDI (`mus2mid`) and played by a small oscillator synth in the page. Audio starts on the Play tap. |
| Opaque origin: no `localStorage`/IndexedDB | The engine's files live in an in-memory WASI file system; `*.dsg` saves and `*.cfg` settings are gzipped into `window.napplet.storage` (512 KiB quota) and restored on the next launch. |
| Main thread only | The page calls `dg_tick()` once per 35 Hz game tic from `requestAnimationFrame`. `src/doomgeneric.patch` turns the blocking screen-melt loop into a per-tic step, and clears the frame buffer so the first melt starts from black. Hidden time is not game time, so returning to the app does not fast-forward. |
| No pointer lock, phones have no keyboard | Keyboard as in vanilla Doom, plus on-screen controls on touch devices and standard-mapping gamepads. Weapon cycling is bound to `[` `]`. |

The engine imports 14 WASI calls (files, `proc_exit`) and 10 page functions (`draw_frame`,
`ticks_ms`, sound and music); see `src/doomgeneric_napplet.c`.

## What it tells us about the hosts

- **It runs on Android's current host unchanged**, in the sense the test covers: production shell,
  shim, CSP and srcdoc bootstrap in Chromium. Not yet tried on a device: Android WebView's memory
  with a 14 MB artifact that the host base64-encodes into the shell (about 19 MB) and decodes again,
  hardware keyboards through `NappletKeyActions`, and whether the Gamepad API reaches the iframe.
- **Desktop cannot open it yet**: the desktop app has no napplet host
  (`desktopApp/plans/2026-06-21-napplet-desktop-host.md`).
- **Size is the real constraint.** A bigger IWAD (Phase 2 is the same size, a PWAD on top is more)
  would not fit in one artifact. Splitting the WAD into a Blossom blob fetched through
  `resource.bytes` hits the 10 MiB `RESOURCE_MAX_BYTES` cap and a base64 hop over IPC.

## Licensing

- doomgeneric: GPL-2.0 (`build/doomgeneric/LICENSE`). The built `index.html` links it, so the
  artifact is GPL-2.0: publish it with `--source` pointing at this directory (the build pins the
  exact doomgeneric commit and patch).
- Freedoom: BSD-3-Clause (`build/freedoom-0.13.0/COPYING.txt`), redistributable. Do not swap in
  the id Software IWADs: those are not redistributable.
- `src/doomgeneric_napplet.c` and `src/doomgeneric.patch` modify or adapt doomgeneric, so they are
  GPL-2.0-or-later. The loader page, scripts and test are MIT, like Amethyst. None of this
  directory ships in Amethyst's own artifacts.
