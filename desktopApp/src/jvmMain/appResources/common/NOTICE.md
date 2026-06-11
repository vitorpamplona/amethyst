# Amethyst Desktop — Third-party Notices

Amethyst Desktop is distributed under the MIT License (see `LICENSE-MIT-amethyst.txt`).
The distributed binary includes the following third-party components:

## kdroidFilter ComposeMediaPlayer

- License: MIT
- Version: 0.10.1
- Upstream: https://github.com/kdroidFilter/ComposeMediaPlayer
- License text: `licenses/LICENSE-MIT-kdroidfilter.txt`

## JCodec

- License: BSD 2-Clause
- Version: 0.2.5
- Upstream: https://github.com/jcodec/jcodec
- License text: `licenses/LICENSE-BSD-2-jcodec.txt`

## FFmpeg (LGPL build, bundled per OS for thumbnail extraction)

- License: LGPL-2.1-or-later
- Build: LGPL-only configuration (no `--enable-gpl`, no `--enable-nonfree`)
- Upstream: https://ffmpeg.org/
- License text: `licenses/LICENSE-LGPL-2.1.txt`
- Source availability: https://github.com/vitorpamplona/amethyst (build tag matches
  the binary tag); FFmpeg sources at https://github.com/FFmpeg/FFmpeg
- Per-OS binary source provenance documented in
  `desktopApp/src/jvmMain/appResources/<os>/ffmpeg/README.md`.

## GStreamer (Linux runtime dependency; not bundled)

- License: LGPL-2.1-or-later (core + linked plugins)
- Required at runtime by ComposeMediaPlayer on Linux (linked via `pkg-config`).
- License text: `licenses/LICENSE-LGPL-2.1.txt`
- Users install via their distribution's package manager. We bundle no
  GStreamer binaries.

## Other Apache-2.0 / MIT transitive dependencies

Compose Multiplatform (JetBrains, Apache-2.0), Kotlin stdlib (JetBrains,
Apache-2.0), OkHttp (Square, Apache-2.0), Jackson (FasterXML, Apache-2.0),
Coil (Apache-2.0), kmp-tor (MIT), and others, plus the bundled OpenJDK
runtime (GPLv2 + Classpath Exception). Their license texts are reproduced
under `licenses/` alongside this NOTICE.

## SPDX combined expression

```
MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0
```
