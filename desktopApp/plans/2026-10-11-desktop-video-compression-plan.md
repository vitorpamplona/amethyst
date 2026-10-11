# Desktop Video Compression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the composer's media-quality slider, H.265 switch and GIF→MP4 switch real on the JVM desktop by transcoding with a system `ffmpeg`, using Android's resolution/bitrate table; hide those controls when no `ffmpeg` is installed; make the "compression strips metadata" promise true even when the original is uploaded.

**Architecture:** The rules table moves from `amethyst` to `commons/commonMain` so both platforms read one source. A new `commons/jvmMain` package `service/upload/video/` runs `ffmpeg`/`ffprobe` as subprocesses (locator, encoder preference, probe, command builder, progress parser, process registry, runner). `DesktopMediaUploader` feeds every upload entry point through the compressor; `commonsUI` adapts the shared dialogs (video-aware slider stops and label, hidden controls without ffmpeg, "Upload Media").

**Tech Stack:** Kotlin Multiplatform, `java.lang.ProcessBuilder`, Jackson (already on the commons JVM classpath through quartz) for `ffprobe` JSON, kotlinx-coroutines, `kotlin.test` + `runTest`, Compose Multiplatform for the dialogs.

**Spec:** `desktopApp/plans/2026-10-11-desktop-video-compression.md`

## Global Constraints

- No new third-party dependency. `ffmpeg`/`ffprobe` are external processes found on `$PATH`, in the package-manager directories listed in spec §2, or at `${compose.application.resources.dir}/ffmpeg/<name>(.exe)`; never a working-directory-relative path. The environment variable `AMETHYST_FFMPEG_DISABLED` (any value) makes the locator report nothing — the test gate and the manual "no ffmpeg" run.
- The LOW/MEDIUM/HIGH table **including its UNKNOWN rows**, ×1.3 at ≥ 59 fps (the one deliberate change from Android, which used ≥ 60 and so missed broadcast 59.94 fps; the shared rule now moves Android too), ×0.75 for H.265, are copied **verbatim** from `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt:112-147`. The probe-failure fallback (`:177-179`) is a separate constant: 2 000 000 bps, no resize.
- Encoder preference, exact order — H.264: `libx264, h264_videotoolbox, h264_nvenc, h264_qsv, h264_amf, h264_mf, libopenh264`; H.265: `libx265, hevc_videotoolbox, hevc_nvenc, hevc_qsv, hevc_amf, hevc_mf`. No `*_vaapi`. Candidates = available HEVC (if asked) **then** available H.264.
- Encode command (spec §2): `-map 0:v:0 -map 0:a:0? -sn -dn -map_chapters -1`, `-pix_fmt yuv420p`, `-maxrate`=`-b:v`, `-bufsize`=2×, `-c:a aac -b:a 128k`, `-map_metadata -1 -map_metadata:s -1`, `-movflags +faststart`, `-tag:v hvc1` for HEVC, `-progress pipe:1`. Remux command: `-map 0:v -map 0:a? -dn -sn -c copy -map_metadata -1 -map_metadata:s -1 -map_chapters -1 -movflags +faststart -progress pipe:1` (never `-map 0`: a MOV with a timecode data track then fails with exit 234).
- Compression progress maps to `UploadingState.Compressing` from 0.02 up to a per-path ceiling (0.20 Blossom/NIP-96, 0.40 NIP-95). No overall timeout; a stall (no progress line for 60 s) kills the process — the remux prints progress too, so it is covered the same way. A process-wide `Semaphore(1)` serialises encodes. Every live process is registered and killed by a JVM shutdown hook. Only `FfmpegProcesses` and `FfmpegProbe` switch to `Dispatchers.IO`; `FfmpegVideoCompressor` and `DesktopMediaUploader` never change dispatcher (their cancellation tests rely on `runTest` virtual time).
- Output ≥ original → the original, remuxed. Every candidate fails → the original, remuxed, plus a notice. Remux failure → the untouched original. A failed GIF conversion → the untouched GIF (never remuxed), plus the notice.
- Tests never list, create or delete files in the real `AmethystTempDir`: the compressor takes a temp-file factory and tests point it at a per-test directory.
- Every new user-visible string goes in `commonsUI/src/commonMain/composeResources/values/strings.xml`, positional format args only, no Android-style escaping.
- `com.vitorpamplona.quartz.utils.Log` lambda form; no inline fully-qualified names; production temp files only via `AmethystTempDir.createTempFile("amethyst_…", …)` (tests use their own directory, see above); delete with `File.deleteOrWarn(tag, what)`.
- `./gradlew spotlessApply` before every commit. Conventional commits. Never `--no-verify`.

## Review Focus

Inputs the spec implies that a user will hit; each has a test in the owning task:

1. A portrait phone video (height > width): the short side is the width, never upscaled, dimensions even — Task 5 `scaleFilter` test, Task 6 gated rotated-source test.
2. A 59.94 fps clip (`avg_frame_rate = 60000/1001`): gets the ×1.3 multiplier, 50 fps does not — Task 4 parser test + Task 1 multiplier test.
3. `ffprobe` missing while `ffmpeg` is present: the probe-failure constant, not a crash and not the UNKNOWN row — Task 6 test.
4. A hardware HEVC encoder listed by `-encoders` but failing at run time: the H.264 tail is reached, the bitrate recomputed without the ×0.75, and the first temp deleted — Task 3 candidate test + Task 6 retry test.
5. A packaged app launched from Finder (empty launchd PATH) with Homebrew ffmpeg: found through `/opt/homebrew/bin` — Task 2 locator test + manual sheet.

---

### Task 1: Move the rules table to `commons`

**Files:**
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRules.kt`
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/CompressorQuality.kt:28-37`
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt:47-107,112-147,159-180` (delete the moved types and the table; import them; keep `getVideoInfo`, `compressVideo`, the toasts)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRulesTest.kt`

**Interfaces:**
- Produces (package `com.vitorpamplona.amethyst.commons.service.uploads.video`):
  - `enum class VideoStandard(val label: String)` — `UHD_4K, QHD_1440P, FHD_1080P, HD_720P, SD_480P, NHD_360P, QVGA_240P, UNKNOWN`, `toString() = label`.
  - `data class VideoResolution(val width: Int, val height: Int) { fun getStandard(): VideoStandard }` — short-side thresholds 2160/1440/1080/720/480/360/240.
  - `data class VideoInfo(val resolution: VideoResolution, val framerate: Float)`.
  - `data class CompressionRule(val shortSide: Int, val bitrateMbps: Float, val description: String) { fun getBitrateBps(framerate: Float, useH265: Boolean): Int }`.
  - `object VideoCompressionRules { fun ruleFor(quality: CompressorQuality, standard: VideoStandard): CompressionRule?; const val PROBE_FAILED_BITRATE_BPS = 2_000_000 }` — `ruleFor` looks the table up with `quality.videoLevel`, so it is null only for UNCOMPRESSED.
  - `CompressorQuality.hasVideoRule: Boolean` (constructor parameter: `VERY_LOW false, LOW true, MEDIUM true, HIGH true, VERY_HIGH false, UNCOMPRESSED true`) and `CompressorQuality.videoLevel: CompressorQuality` — a **getter** (`get() = when (this) { VERY_HIGH -> HIGH; VERY_LOW -> LOW; else -> this }`), not a constructor parameter: `VERY_LOW` is declared before `LOW`, so a constructor argument would be an illegal forward reference.

- [ ] **Step 1: Write the failing tests**

```kotlin
class VideoCompressionRulesTest {
    @Test fun standardIsKeyedOnShortSide() {
        assertEquals(VideoStandard.FHD_1080P, VideoResolution(1080, 1920).getStandard())   // portrait
        assertEquals(VideoStandard.HD_720P, VideoResolution(1280, 720).getStandard())
        assertEquals(VideoStandard.UNKNOWN, VideoResolution(200, 100).getStandard())
    }
    @Test fun tableMatchesAndroid() {
        assertEquals(CompressionRule(720, 2f, "1080p→720p, 2Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.MEDIUM, VideoStandard.FHD_1080P))
        assertEquals(CompressionRule(2160, 8f, "4K→4K, 8Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.HIGH, VideoStandard.UHD_4K))
        assertEquals(CompressionRule(480, 0.75f, "1080p→480p, 0.75Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.LOW, VideoStandard.FHD_1080P))
        assertEquals(CompressionRule(720, 2f, "Medium quality fallback, 2Mbps"), VideoCompressionRules.ruleFor(CompressorQuality.MEDIUM, VideoStandard.UNKNOWN))
    }
    @Test fun everyRuledLevelCoversEveryStandard() {
        for (q in listOf(CompressorQuality.LOW, CompressorQuality.MEDIUM, CompressorQuality.HIGH))
            for (s in VideoStandard.entries) assertNotNull(VideoCompressionRules.ruleFor(q, s), "$q/$s")
    }
    @Test fun videoLevelMapsImageOnlyLevels() {
        assertEquals(CompressorQuality.HIGH, CompressorQuality.VERY_HIGH.videoLevel)
        assertEquals(CompressorQuality.LOW, CompressorQuality.VERY_LOW.videoLevel)
        assertEquals(CompressorQuality.MEDIUM, CompressorQuality.MEDIUM.videoLevel)
        assertEquals(VideoCompressionRules.ruleFor(CompressorQuality.HIGH, VideoStandard.UHD_4K), VideoCompressionRules.ruleFor(CompressorQuality.VERY_HIGH, VideoStandard.UHD_4K))
        assertNull(VideoCompressionRules.ruleFor(CompressorQuality.UNCOMPRESSED, VideoStandard.FHD_1080P))
    }
    @Test fun hasVideoRule() { assertEquals(listOf(false, true, true, true, false, true), CompressorQuality.entries.map { it.hasVideoRule }) }
    @Test fun bitrateMultipliers() {
        val rule = CompressionRule(1080, 4f, "")
        assertEquals(4_000_000, rule.getBitrateBps(30f, useH265 = false))
        assertEquals(5_200_000, rule.getBitrateBps(60f, useH265 = false))
        assertEquals(3_000_000, rule.getBitrateBps(30f, useH265 = true))
        assertEquals(3_900_000, rule.getBitrateBps(60f, useH265 = true))
        assertEquals(5_200_000, rule.getBitrateBps(59.94f, useH265 = false))   // Review Focus 2
        assertEquals(4_000_000, rule.getBitrateBps(50f, useH265 = false))
    }
    @Test fun probeFailureConstant() { assertEquals(2_000_000, VideoCompressionRules.PROBE_FAILED_BITRATE_BPS) }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.uploads.video.*"` → unresolved symbols.
- [ ] **Step 3: Create `VideoCompressionRules.kt`** with the types in Interfaces; the table body is the three `mapOf(...)` blocks copied from `VideoCompressionHelper.kt:112-147`. Add `hasVideoRule` and `videoLevel` to `CompressorQuality`.
- [ ] **Step 4: Point Android at it.** Delete the moved declarations and `MBPS_TO_BPS_MULTIPLIER` from `VideoCompressionHelper.kt`; import the commons types; replace `compressionRules.getValue(mediaQuality).getValue(…)` with `VideoCompressionRules.ruleFor(mediaQuality, info.resolution.getStandard()) ?: return MediaCompressorResult(uri, contentType, null)` (Android never calls it at UNCOMPRESSED — `MediaCompressor.kt:67` returns before — so the branch is unreachable, but a `!!` must not be the first thing a future caller hits; the same shape `MediaCompressor.kt:94` uses); replace `2 * MBPS_TO_BPS_MULTIPLIER` with `VideoCompressionRules.PROBE_FAILED_BITRATE_BPS`.
- [ ] **Step 5: Run** `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.uploads.video.*" :amethyst:compilePlayDebugKotlin` → PASS, compiles.
- [ ] **Step 6: Commit** — `refactor(uploads): share the video compression rules through commons`

---

### Task 2: `FfmpegLocator` with package-manager paths and warm-up

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegLocator.kt`
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/VideoThumbnailCache.kt:112-138` (replace the private `ffmpegBinary` lazy with `FfmpegLocator.default.ffmpeg`)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAppModules.kt:284` (inside the existing `init { … }` block, which already starts other background work on `applicationIOScope` declared at `:151`: add `applicationIOScope.launch { FfmpegLocator.default.warmUp() }`)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegLocatorTest.kt`

**Interfaces:**
- Produces: `class FfmpegLocator(private val runsOnPath: (binary: String) -> Boolean = ::probeOnPath, private val packageDirs: () -> List<File> = ::defaultPackageDirs, private val resourcesDir: () -> File? = { System.getProperty("compose.application.resources.dir")?.let(::File) }, private val disabled: () -> Boolean = { System.getenv("AMETHYST_FFMPEG_DISABLED") != null }) { val ffmpeg: String? by lazy; val ffprobe: String? by lazy; val available: Boolean get() = ffmpeg != null; fun warmUp() { ffmpeg; ffprobe }; companion object { val default = FfmpegLocator() } }`.
  Resolution of a name `b` (with `.exe` appended on Windows): `disabled()` → null; else `runsOnPath(b)` → `b`; else the first `dir/b` in `packageDirs()` that exists and is executable; else `resourcesDir()/ffmpeg/b` if it exists and is executable; else null. An environment variable rather than a system property because Gradle's forked test JVM and the `run` task's app JVM inherit the environment, while `-D` on the Gradle command line stays in the Gradle JVM.
  `defaultPackageDirs()`: on macOS/Linux `/opt/homebrew/bin, /usr/local/bin, /opt/local/bin`; on Windows `%ProgramFiles%\ffmpeg\bin, %LOCALAPPDATA%\Microsoft\WinGet\Links, %USERPROFILE%\scoop\shims, %ChocolateyInstall%\bin` (skipping unset variables).
  `probeOnPath` is the `-version` probe moved verbatim from `VideoThumbnailCache` (stdout/stderr discarded, 2 s wait, `destroyForcibly` on overrun, true iff exit 0).

- [ ] **Step 1: Write the failing tests** (temp dirs created in `@BeforeTest`, deleted in `@AfterTest`; executables are empty files with `setExecutable(true)`). Every `FfmpegLocator(...)` below is built through a local helper that passes `disabled = { false }` — otherwise the suite would fail when run under `AMETHYST_FFMPEG_DISABLED=1` (Task 6 Step 7, Task 11 Step 1); only `disabledIgnoresEverything` passes `{ true }`.

```kotlin
@Test fun prefersPath() { assertEquals("ffmpeg", FfmpegLocator({ true }, { emptyList() }, { null }).ffmpeg) }
@Test fun findsHomebrewStyleDirWhenPathIsBare() {        // Review Focus 5
    val bin = executable(File(pkgDir, "ffmpeg"))
    assertEquals(bin.absolutePath, FfmpegLocator({ false }, { listOf(File(dir, "missing"), pkgDir) }, { null }).ffmpeg)
}
@Test fun fallsBackToBundledExecutable() {
    val bin = executable(File(resDir, "ffmpeg/ffmpeg"))
    assertEquals(bin.absolutePath, FfmpegLocator({ false }, { emptyList() }, { resDir }).ffmpeg)
}
@Test fun nonExecutableFilesAreIgnored() {
    File(resDir, "ffmpeg/ffprobe").apply { parentFile.mkdirs(); writeText("") ; setExecutable(false) }
    assertNull(FfmpegLocator({ false }, { emptyList() }, { resDir }).ffprobe)
}
@Test fun unavailableWhenNothingExists() { assertFalse(FfmpegLocator({ false }, { emptyList() }, { null }).available) }
@Test fun disabledIgnoresEverything() { var probes = 0; val l = FfmpegLocator({ probes++; true }, { emptyList() }, { null }, disabled = { true }); assertNull(l.ffmpeg); assertNull(l.ffprobe); assertEquals(0, probes) }
@Test fun warmUpResolvesBoth() { var probes = 0; FfmpegLocator({ probes++; true }, { emptyList() }, { null }).warmUp(); assertEquals(2, probes) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** `FfmpegLocator.kt`; switch `VideoThumbnailCache.runFfmpeg` to `FfmpegLocator.default.ffmpeg` and delete its lazy; add the warm-up launch in `DesktopAppModules`' `init`.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegLocatorTest" :desktopApp:compileKotlin` → PASS, compiles.
- [ ] **Step 5: Commit** — `refactor(desktop): FfmpegLocator finds package-manager installs and warms up at start`

---

### Task 3: `FfmpegEncoders` — parse `-encoders`, pick by preference

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegEncoders.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegEncodersTest.kt`

**Interfaces:**
- Produces: `object FfmpegEncoders { val H264: List<String>; val H265: List<String>; fun parseAvailable(encodersOutput: String): Set<String>; fun candidates(available: Set<String>, useH265: Boolean): List<String>; fun isHevc(encoder: String): Boolean = encoder in H265; suspend fun listAvailable(ffmpeg: String): Set<String> }`. `candidates` = `(if (useH265) H265.filter { it in available } else emptyList()) + H264.filter { it in available }`. `listAvailable` runs `ffmpeg -hide_banner -encoders` on `Dispatchers.IO` (stderr discarded, 5 s cap) and parses stdout; the result is memoized per `ffmpeg` path in a `ConcurrentHashMap` so each upload does not spawn a process (a failed run is not cached — an empty set is returned and the next call retries).

- [ ] **Step 1: Write the failing tests** (real `-encoders` line format: six flag characters, name, description)

```kotlin
private val GPL = """
Encoders:
 V..... = Video
 ------
 V....D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)
 V....D h264_videotoolbox    VideoToolbox H.264 Encoder (codec h264)
 V....D libx265              libx265 H.265 / HEVC (codec hevc)
 V....D hevc_nvenc           NVIDIA NVENC hevc encoder (codec hevc)
 V....D h264_vaapi           H.264/AVC (VAAPI) (codec h264)
 A....D aac                  AAC (Advanced Audio Coding)
""".trimIndent()
private val LGPL = GPL.lines().filterNot { "libx26" in it }.joinToString("\n") + "\n V....D libopenh264          OpenH264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)"

@Test fun parsesNamesOnly() { assertEquals(setOf("libx264", "h264_videotoolbox", "libx265", "hevc_nvenc", "h264_vaapi", "aac"), FfmpegEncoders.parseAvailable(GPL)) }
@Test fun vaapiIsNeverACandidate() { assertFalse("h264_vaapi" in FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(GPL), false)) }
@Test fun gplBuildPrefersX264() { assertEquals(listOf("libx264", "h264_videotoolbox"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(GPL), useH265 = false)) }
@Test fun lgplBuildFallsBackThroughHardwareToOpenH264() { assertEquals(listOf("h264_videotoolbox", "libopenh264"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(LGPL), useH265 = false)) }
@Test fun hevcCandidatesComeFirstThenH264() {              // Review Focus 4
    assertEquals(listOf("libx265", "hevc_nvenc", "libx264", "h264_videotoolbox"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(GPL), useH265 = true))
    assertEquals(listOf("hevc_nvenc", "h264_videotoolbox", "libopenh264"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(LGPL), useH265 = true))
}
@Test fun isHevc() { assertTrue(FfmpegEncoders.isHevc("hevc_videotoolbox")); assertFalse(FfmpegEncoders.isHevc("libx264")) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement.** Parse only the lines **after** the ` ------` separator (the legend above it — ` V..... = Video`, ` .F.... = Frame-level multithreading` — has the same six-character shape and a naive regex captures `=` three times on a real ffmpeg 9 listing), each with `Regex("""^\s*[VAS][.F][.S][.X][.B][.D]\s+([A-Za-z0-9_]+)""")`. The `GPL` fixture above keeps its legend line and separator on purpose so `parsesNamesOnly` proves this.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegEncodersTest"` → PASS.
- [ ] **Step 5: Commit** — `feat(uploads): encoder preference list for ffmpeg video compression`

---

### Task 4: `FfmpegProbe` — `ffprobe` JSON → `VideoInfo`, duration, HDR

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegProbe.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfprobeOutputParserTest.kt`

**Interfaces:**
- Consumes: `VideoInfo`, `VideoResolution` (Task 1).
- Produces: `data class ProbeResult(val info: VideoInfo, val durationSeconds: Double?, val isHdr: Boolean)`; `object FfprobeOutputParser { fun parse(json: String): ProbeResult? }`; `open class FfmpegProbe(private val ffprobe: String?) { open suspend fun probe(file: File): ProbeResult? }` (open so Task 6's test can fake it) running, on `Dispatchers.IO`, `ffprobe -v error -select_streams V:0 -show_entries stream=width,height,avg_frame_rate,color_transfer -show_entries format=duration -of json <file>` (10 s cap); null when `ffprobe` is null, the process fails, or parsing fails. `isHdr` = `color_transfer` is `smpte2084` or `arib-std-b67`.

- [ ] **Step 1: Write the failing tests**

```kotlin
private const val JSON = """{"streams":[{"width":1920,"height":1080,"avg_frame_rate":"60000/1001","color_transfer":"bt709"}],"format":{"duration":"30.528000"}}"""
@Test fun parsesDimensionsFpsAndDuration() {
    val r = FfprobeOutputParser.parse(JSON)!!
    assertEquals(VideoResolution(1920, 1080), r.info.resolution)
    assertEquals(59.94f, r.info.framerate, 0.01f)                               // Review Focus 2
    assertEquals(30.528, r.durationSeconds!!, 0.001); assertFalse(r.isHdr)
}
@Test fun hdrTransfers() {
    for (t in listOf("smpte2084", "arib-std-b67")) assertTrue(FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"avg_frame_rate":"30/1","color_transfer":"$t"}],"format":{}}""")!!.isHdr, t)
}
@Test fun missingDurationAndTransferAreTolerated() { val r = FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"avg_frame_rate":"30/1"}],"format":{}}""")!!; assertNull(r.durationSeconds); assertFalse(r.isHdr) }
@Test fun noVideoStreamIsNull() { assertNull(FfprobeOutputParser.parse("""{"streams":[],"format":{"duration":"1"}}""")) }
@Test fun garbageIsNull() { assertNull(FfprobeOutputParser.parse("not json")) }
@Test fun zeroDenominatorFpsIsZero() { assertEquals(0f, FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"avg_frame_rate":"0/0"}],"format":{}}""")!!.info.framerate) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** with Jackson's `ObjectMapper().readTree(json)`; `avg_frame_rate` is `num/den` → `num.toFloat() / den` (0 when den is 0).
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfprobeOutputParserTest"` → PASS.
- [ ] **Step 5: Commit** — `feat(uploads): ffprobe-based video info for desktop compression`

---

### Task 5: `FfmpegCommandBuilder` and `FfmpegProgressParser`

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegCommandBuilder.kt`
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegProgressParser.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegCommandBuilderTest.kt`, `…/FfmpegProgressParserTest.kt`

**Interfaces:**
- Produces:
  - `object FfmpegCommandBuilder { fun scaleFilter(shortSide: Int?): String; fun encode(ffmpeg: String, input: File, output: File, encoder: String, bitrateBps: Int, shortSide: Int?): List<String>; fun remux(ffmpeg: String, input: File, output: File): List<String> }`.
    `scaleFilter(S)` = `scale='if(gt(iw,ih),-2,trunc(min(S,iw)/2)*2)':'if(gt(iw,ih),trunc(min(S,ih)/2)*2,-2)'`; `scaleFilter(null)` = `scale=trunc(iw/2)*2:trunc(ih/2)*2`.
    `encode` = `[ffmpeg, -hide_banner, -loglevel, error, -nostdin, -y, -i, input, -map, 0:v:0, -map, 0:a:0?, -sn, -dn, -map_chapters, -1, -c:v, encoder, -b:v, N, -maxrate, N, -bufsize, 2N, -pix_fmt, yuv420p, -vf, scaleFilter(shortSide), -c:a, aac, -b:a, 128k, -map_metadata, -1, -map_metadata:s, -1, -movflags, +faststart, (-tag:v, hvc1 when FfmpegEncoders.isHevc(encoder)), -progress, pipe:1, output]`.
    `remux` = `[ffmpeg, -hide_banner, -loglevel, error, -nostdin, -y, -i, input, -map, 0:v, -map, 0:a?, -dn, -sn, -c, copy, -map_metadata, -1, -map_metadata:s, -1, -map_chapters, -1, -movflags, +faststart, -progress, pipe:1, output]` — video and audio only (a MOV timecode/data track makes `-map 0` fail with exit 234), and `-progress` so the stall watchdog sees output.
  - `class FfmpegProgressParser(private val durationSeconds: Double?) { fun onLine(line: String): Double? }` — `out_time_us=<n>` → `(n / 1e6 / duration).coerceIn(0.0, 1.0)` (null when duration is null or ≤ 0, or the value is not a number such as `N/A`); `progress=end` → `1.0`; anything else → null.

- [ ] **Step 1: Write the failing tests**

```kotlin
// FfmpegCommandBuilderTest
@Test fun portraitLimitsWidthAndKeepsEven() {             // Review Focus 1
    assertEquals("scale='if(gt(iw,ih),-2,trunc(min(720,iw)/2)*2)':'if(gt(iw,ih),trunc(min(720,ih)/2)*2,-2)'", FfmpegCommandBuilder.scaleFilter(720))
}
@Test fun noRuleOnlyEvensDimensions() { assertEquals("scale=trunc(iw/2)*2:trunc(ih/2)*2", FfmpegCommandBuilder.scaleFilter(null)) }
@Test fun h264EncodeCommand() {
    val cmd = FfmpegCommandBuilder.encode("ffmpeg", File("in.mov"), File("out.mp4"), "libx264", 4_000_000, 1080)
    assertEquals(listOf("-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn", "-map_chapters", "-1"), cmd.subList(cmd.indexOf("-map"), cmd.indexOf("-map") + 8))
    assertEquals(listOf("-c:v", "libx264", "-b:v", "4000000", "-maxrate", "4000000", "-bufsize", "8000000"), cmd.subList(cmd.indexOf("-c:v"), cmd.indexOf("-c:v") + 8))
    assertFalse("-tag:v" in cmd); assertTrue(cmd.containsAll(listOf("-movflags", "+faststart", "-map_metadata:s", "-progress", "pipe:1"))); assertEquals("out.mp4", cmd.last())
}
@Test fun hevcEncodeTagsHvc1() { val cmd = FfmpegCommandBuilder.encode("ffmpeg", File("a"), File("b"), "hevc_videotoolbox", 1, null); assertEquals("hvc1", cmd[cmd.indexOf("-tag:v") + 1]) }
@Test fun remuxCopiesVideoAndAudioOnlyAndStrips() {
    val cmd = FfmpegCommandBuilder.remux("ffmpeg", File("a"), File("b"))
    assertEquals(listOf("-map", "0:v", "-map", "0:a?", "-dn", "-sn", "-c", "copy", "-map_metadata", "-1", "-map_metadata:s", "-1", "-map_chapters", "-1", "-movflags", "+faststart", "-progress", "pipe:1", "b"), cmd.subList(cmd.indexOf("-map"), cmd.size))
    assertFalse("-c:v" in cmd); assertFalse("0" in cmd)   // never `-map 0`
}

// FfmpegProgressParserTest
@Test fun fractionOfDuration() { assertEquals(0.5, FfmpegProgressParser(10.0).onLine("out_time_us=5000000")!!, 1e-9) }
@Test fun clampsOvershoot() { assertEquals(1.0, FfmpegProgressParser(10.0).onLine("out_time_us=99000000")) }
@Test fun endIsOne() { assertEquals(1.0, FfmpegProgressParser(null).onLine("progress=end")) }
@Test fun notAvailableAndOtherKeysAreNull() { val p = FfmpegProgressParser(10.0); assertNull(p.onLine("out_time_us=N/A")); assertNull(p.onLine("frame=12")); assertNull(FfmpegProgressParser(null).onLine("out_time_us=5")) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement both files.** — [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*Ffmpeg*"` → PASS.
- [ ] **Step 5: Commit** — `feat(uploads): ffmpeg command builder and progress parser`

---

### Task 6: `FfmpegVideoCompressor` — the runner

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/VideoCompressor.kt` (interface + result)
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegProcesses.kt` (runner + registry + shutdown hook)
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegVideoCompressor.kt`
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/notices/UploadNotices.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegVideoCompressorTest.kt`, `…/FfmpegProcessesTest.kt` (real child process, no ffmpeg), `…/FfmpegVideoCompressorFfmpegTest.kt` (gated)

**Interfaces:**
- Consumes: Tasks 1–5; `AmethystTempDir.createTempFile`, `File.deleteOrWarn`, `UserNotice`.
- Produces:
  - `sealed interface UploadNotice : UserNotice { data class VideoCompressionFailed(val reason: String?) : UploadNotice; data object HevcUnavailable : UploadNotice; data object HdrSource : UploadNotice }` (commonMain).
  - `sealed interface VideoCompressionResult { data class Compressed(val file: File, val notices: List<UserNotice> = emptyList()) : VideoCompressionResult; data class Original(val stripped: File?, val notices: List<UserNotice> = emptyList()) : VideoCompressionResult; data object TooBig : VideoCompressionResult }` — `stripped` is a remuxed temp (caller deletes) or null when remux was impossible.
  - `interface VideoCompressor { val available: Boolean; suspend fun probe(source: File): VideoInfo?; suspend fun compress(source: File, quality: CompressorQuality, useH265: Boolean, maxBytes: Long? = null, onProgress: (Double) -> Unit): VideoCompressionResult; suspend fun convertGif(source: File, onProgress: (Double) -> Unit): VideoCompressionResult; suspend fun strip(source: File): File? }` — `probe` is what the slider label uses (null when `ffprobe` is missing or the file cannot be read); `strip` is the remux alone (null when unavailable or failed).
  - `fun interface ProcessRunner { suspend fun run(command: List<String>, onStdoutLine: (String) -> Unit, stallTimeoutMs: Long): ProcessOutcome }`, `data class ProcessOutcome(val exitCode: Int, val stderrTail: String)`.
  - `object FfmpegProcesses : ProcessRunner` — the real runner, the only place besides `FfmpegProbe` that uses `Dispatchers.IO`: `ProcessBuilder`, stdout read line by line, stderr drained on its own thread with the last 2 KB kept, no stdout line for `stallTimeoutMs` → `destroyForcibly()` and exit code `-1`, coroutine cancellation → `destroyForcibly()`; every started `Process` is added to `val live = ConcurrentHashMap.newKeySet<Process>()` (internal, so the test can assert it is empty) and removed on exit; `init` registers `Runtime.getRuntime().addShutdownHook` that `destroyForcibly()`s every live one. Exposes `val encodeGate = Semaphore(1)` (kotlinx) for the compressor.
  - `class FfmpegVideoCompressor(private val locator: FfmpegLocator = FfmpegLocator.default, private val encoders: suspend (ffmpeg: String) -> Set<String> = FfmpegEncoders::listAvailable, private val probe: (ffprobe: String?) -> FfmpegProbe = ::FfmpegProbe, private val stallTimeoutMs: Long = 60_000, private val runner: ProcessRunner = FfmpegProcesses, private val gate: Semaphore = FfmpegProcesses.encodeGate, private val tempFile: () -> File = { AmethystTempDir.createTempFile("amethyst_video_", ".mp4") }) : VideoCompressor`. The class never calls `withContext`; it runs on the caller's dispatcher.

Algorithm of `compress` (not determined by the signature and tests):
1. `ffmpeg = locator.ffmpeg ?: return Original(null)`.
2. `probed = probe(locator.ffprobe).probe(source)`; `rule = probed?.let { VideoCompressionRules.ruleFor(quality, it.info.resolution.getStandard()) }`; `shortSide = rule?.shortSide`; `fps = probed?.info?.framerate ?: 30f`; `notices` starts with `HdrSource` when `probed?.isHdr == true`.
3. `maxBytes` given and `probed?.durationSeconds` known: `estimate = ((bitrateFor(hevc = useH265) + 128_000) / 8.0) * duration`; `estimate > maxBytes` → `TooBig`.
4. `candidates = FfmpegEncoders.candidates(encoders(ffmpeg), useH265)`; empty → `fail("no encoder")`.
5. Under `gate.withPermit`, for each candidate: `hevc = isHevc(candidate)`, `bitrate = rule?.getBitrateBps(fps, hevc) ?: PROBE_FAILED_BITRATE_BPS`; then `var out: File? = null; try { out = tempFile(); run encode(...) feeding FfmpegProgressParser(probed?.durationSeconds) into onProgress; exit 0 → stop; else delete out, keep stderrTail, continue } catch (e: CancellationException) { withContext(NonCancellable) { out?.deleteOrWarn(...) }; throw e }` — `out` is created **inside** the `try` so a `tempFile()` that throws leaves nothing behind.
6. No success → `fail(lastStderrTail)` where `fail(reason)` = `Original(strip(source), notices + VideoCompressionFailed(reason))`.
7. `out.length() >= source.length()` → delete `out`, `Original(strip(source), notices)`.
8. `useH265 && !hevcOfTheWinner` → `notices += HevcUnavailable`. `Compressed(out, notices)`.

`convertGif` = steps 1, 2 (rule = MEDIUM row for the GIF's standard, `shortSide = null` so the source size is kept), 4–8 with `useH265 = false`, no size-vs-original rule (an MP4 is routinely bigger than a GIF; the point is playability), and failure = `Original(null, notices + VideoCompressionFailed(reason))` — a GIF is never remuxed. `probe(source)` = `probe(locator.ffprobe).probe(source)?.info`. `strip(source)` runs `remux` into `tempFile()`, returning the file on exit 0, else deleting it and returning null.

- [ ] **Step 1: Write the failing unit tests** with a fake `ProcessRunner` that records commands, writes the scripted progress lines, creates the output file (last argument) with the scripted byte count, and returns a scripted exit code per call:

Every test is `= runTest { … }` — `compress`, `convertGif` and `strip` are `suspend`.

```kotlin
@Test fun retriesNextEncoderWhenFirstFails() = runTest {            // Review Focus 4
    val runner = FakeRunner(exitCodes = listOf(1, 0), outputBytes = 10)
    val r = compressor(runner, encoders = setOf("hevc_nvenc", "libx264")).compress(source(1000), CompressorQuality.HIGH, useH265 = true) {}
    assertIs<VideoCompressionResult.Compressed>(r)
    assertEquals(listOf("hevc_nvenc", "libx264"), runner.commands.map { it[it.indexOf("-c:v") + 1] })
    assertEquals(listOf("3000000", "4000000"), runner.commands.map { it[it.indexOf("-b:v") + 1] })   // ×0.75 only for the HEVC attempt
    assertEquals(listOf(UploadNotice.HevcUnavailable), r.notices)
    assertEquals(1, tempVideos().size)                                            // the failed temp is gone
}
@Test fun allEncodersFailingRemuxesOriginalWithNotice() = runTest {
    val runner = FakeRunner(exitCodes = listOf(1, 0), stderr = "boom", outputBytes = 10)                  // 2nd call is the remux
    val r = compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
    assertEquals(listOf(UploadNotice.VideoCompressionFailed("boom")), (r as VideoCompressionResult.Original).notices)
    assertNotNull(r.stripped); assertTrue("copy" in runner.commands[1])
}
@Test fun largerOutputIsDiscardedAndOriginalRemuxed() = runTest {
    val runner = FakeRunner(exitCodes = listOf(0, 0), outputBytes = 5000)
    val r = compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
    assertIs<VideoCompressionResult.Original>(r); assertNotNull(r.stripped); assertEquals(1, tempVideos().size)
}
@Test fun remuxFailureYieldsNullStripped() = runTest { val r = compressor(FakeRunner(listOf(0, 1), outputBytes = 5000), setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}; assertNull((r as VideoCompressionResult.Original).stripped); assertTrue(tempVideos().isEmpty()) }
@Test fun hdrSourceIsNoticed() = runTest { val r = compressor(FakeRunner(listOf(0), outputBytes = 10), setOf("libx264"), hdr = true).compress(source(1000), CompressorQuality.HIGH, false) {}; assertEquals(listOf(UploadNotice.HdrSource), (r as VideoCompressionResult.Compressed).notices) }
@Test fun missingFfprobeUsesProbeFailureFallback() = runTest {     // Review Focus 3
    val runner = FakeRunner(listOf(0), outputBytes = 10)
    compressor(runner, setOf("libx264"), ffprobe = null).compress(source(1000), CompressorQuality.HIGH, false) {}
    val cmd = runner.commands.single(); assertEquals("2000000", cmd[cmd.indexOf("-b:v") + 1]); assertEquals("scale=trunc(iw/2)*2:trunc(ih/2)*2", cmd[cmd.indexOf("-vf") + 1])
}
@Test fun probeExposesInfo() = runTest { assertEquals(VideoResolution(1920, 1080), compressor(FakeRunner(listOf()), setOf("libx264")).probe(source(1))!!.resolution); assertNull(compressor(FakeRunner(listOf()), setOf("libx264"), ffprobe = null).probe(source(1))) }
@Test fun veryHighEncodesAsHigh() = runTest { val runner = FakeRunner(listOf(0), outputBytes = 10); compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.VERY_HIGH, false) {}; assertEquals("4000000", runner.commands.single().let { it[it.indexOf("-b:v") + 1] }) }
@Test fun nip95EstimateRefusesEarly() = runTest { val runner = FakeRunner(listOf(0), outputBytes = 10); assertEquals(VideoCompressionResult.TooBig, compressor(runner, setOf("libx264"), duration = 30.0).compress(source(1000), CompressorQuality.HIGH, false, maxBytes = 80_000) {}); assertTrue(runner.commands.isEmpty()) }
@Test fun progressIsForwarded() = runTest { val seen = mutableListOf<Double>(); compressor(FakeRunner(listOf(0), outputBytes = 10, progressLines = listOf("out_time_us=1000000", "out_time_us=N/A", "progress=end")), setOf("libx264"), duration = 2.0).compress(source(1000), CompressorQuality.HIGH, false) { seen += it }; assertEquals(listOf(0.5, 1.0), seen) }
@Test fun gifKeepsSizeAndIgnoresSizeRule() = runTest {
    val runner = FakeRunner(listOf(0), outputBytes = 5000)
    val r = compressor(runner, setOf("libx264")).convertGif(source(1000)) {}
    assertIs<VideoCompressionResult.Compressed>(r); val cmd = runner.commands.single(); assertEquals("scale=trunc(iw/2)*2:trunc(ih/2)*2", cmd[cmd.indexOf("-vf") + 1]); assertEquals("2000000", cmd[cmd.indexOf("-b:v") + 1])   // MEDIUM row for 1080p
}
@Test fun gifFailureIsNeverRemuxed() = runTest {
    val runner = FakeRunner(listOf(1), stderr = "bad gif", outputBytes = 0)
    val r = compressor(runner, setOf("libx264")).convertGif(source(1000)) {}
    assertEquals(VideoCompressionResult.Original(null, listOf(UploadNotice.VideoCompressionFailed("bad gif"))), r)
    assertEquals(1, runner.commands.size); assertTrue(tempVideos().isEmpty())   // no remux call, no temp left
}
@Test fun encodesRunOneAtATime() = runTest {
    val runner = FakeRunner(listOf(0, 0), outputBytes = 10, delayMs = 50, recordConcurrency = true)
    val c = compressor(runner, setOf("libx264"))
    listOf(async { c.compress(source(1000), CompressorQuality.HIGH, false) {} }, async { c.compress(source(1000), CompressorQuality.HIGH, false) {} }).awaitAll()
    assertEquals(1, runner.maxConcurrent)
}
@Test fun cancellationDeletesTemp() = runTest { val runner = FakeRunner(listOf(0), outputBytes = 10, delayMs = 10_000); val job = launch { compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {} }; advanceTimeBy(100); job.cancelAndJoin(); assertTrue(tempVideos().isEmpty()) }
@Test fun noFfmpegIsOriginalUnstripped() = runTest { assertEquals(VideoCompressionResult.Original(null), FfmpegVideoCompressor(locator = FfmpegLocator({ false }, { emptyList() }, { null })).compress(source(1), CompressorQuality.HIGH, false) {}) }
```

`compressor(runner, encoders, ffprobe = "ffprobe", duration = 30.0, hdr = false)` builds `FfmpegVideoCompressor(locator = FfmpegLocator({ true }, { emptyList() }, { null }, disabled = { false }), encoders = { encoders }, probe = { FakeProbe(ffprobe, duration, hdr) }, runner = runner, gate = Semaphore(1), tempFile = { File.createTempFile("amethyst_video_", ".mp4", tempDir) })`, where `FakeProbe` returns a 1920×1080 @ 30 fps `ProbeResult` unless `ffprobe` is null; `tempDir` is a fresh directory per test (`@BeforeTest` creates it, `@AfterTest` deletes it recursively) and `tempVideos()` lists its `amethyst_video_*` entries; `source(bytes)` writes a file of that size into `tempDir` under a different prefix (`src_`). The real `AmethystTempDir` is never read or written by this test. `FakeRunner.delayMs` is a `kotlinx.coroutines.delay`, which is why `cancellationDeletesTemp` and `encodesRunOneAtATime` work under `runTest` virtual time — and why the compressor must not switch dispatcher.

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** the four source files.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegVideoCompressorTest"` → PASS.
- [ ] **Step 5: Write `FfmpegProcessesTest.kt`** against the real runner with a real child process, no ffmpeg involved (`sleep` on macOS/Linux, `timeout /t` on Windows — pick by `os.name`):
  - `stalledChildIsKilledAndUnregistered` = `runTest`-free (`runBlocking`, real time): `FfmpegProcesses.run(listOf("sleep", "30"), {}, stallTimeoutMs = 200)` returns within a few seconds with `exitCode == -1`, and `FfmpegProcesses.live.isEmpty()` afterwards.
  - `exitAndStderrAreForwarded`: `run(listOf("sh", "-c", "echo out; echo err 1>&2; exit 3"), …, 5_000)` → `exitCode == 3`, `stderrTail.trim() == "err"`, the stdout callback saw `out` (on Windows use `cmd /c`).
  - `cancellationKillsTheChild` (`runBlocking`): launch `run(listOf("sleep", "30"), {}, 60_000)`, cancel after 100 ms, join → `live.isEmpty()`.
- [ ] **Step 6: Write the gated tests** in `FfmpegVideoCompressorFfmpegTest.kt`; every test starts with `if (!ffmpegForTests()) return`, where `ffmpegForTests()` = `FfmpegLocator.default.ffmpeg != null && FfmpegLocator.default.ffprobe != null && "libx264" in runBlocking { FfmpegEncoders.listAvailable(ffmpeg) }` (the size and bitrate assertions below were measured with x264; an LGPL build encodes differently). Fixtures made with the real binary into a temp dir:
  - `noisy.mp4`: `ffmpeg -f lavfi -i "color=c=gray:s=1920x1080:r=30,noise=alls=100:allf=t+u" -f lavfi -i sine -t 2 -shortest -pix_fmt yuv420p -c:v libx264 -crf 18 -preset ultrafast -metadata title=secret noisy.mp4` (high-entropy, ~90 MB, so every encode is smaller — spec §5; `noise` is a filter and needs the `color` source in front).
  - `easy.mp4`: `ffmpeg -f lavfi -i testsrc=size=1920x1080:rate=30 -t 2 -pix_fmt yuv420p -metadata title=secret easy.mp4` (55 KB; every encode is larger — HIGH measured 181 KB — so the result is the remuxed original, which is what the `title` assertion checks).
  - `rotated.mp4`: `ffmpeg -display_rotation 90 -i noisy.mp4 -c copy rotated.mp4` — from **`noisy`**, so the encode is kept (`easy`'s would be discarded for the remux, which still carries the rotation side data). ffprobe shows `side_data_list: [{rotation: 90}]`; `-metadata:s:v:0 rotate=90` writes nothing on ffmpeg 9.
  - `tc.mov`: `ffmpeg -f lavfi -i testsrc=size=1920x1080:rate=30 -t 2 -pix_fmt yuv420p -timecode 00:00:00:00 -metadata location=+48.2082+016.3738/ -metadata title=secret tc.mov` (one video stream plus a `data` timecode track — the shape iPhones write; `-map 0 -c copy` to MP4 exits 234 on it).
  - Asserting with a small JSON read of `ffprobe -v error -select_streams V:0 -show_entries stream=width,height,codec_name,bit_rate:stream_side_data=rotation:format_tags -of json`:
    - `compress(noisy, HIGH, false)` → `Compressed`, 1920×1080, `bit_rate ≤ 4_000_000 * (1 + 2 / 2.0)` (the VBV bound with `bufsize = 2b` on a 2 s clip — a short clip legitimately bursts above the target), `format_tags` has no `title`.
    - `compress(noisy, LOW, false)` → short side 480 (854×480), and its `bit_rate` < the HIGH output's.
    - `compress(noisy, MEDIUM, true)` → `Compressed`; when `HevcUnavailable` is not in `notices`, `codec_name == hevc`.
    - `compress(easy, HIGH, false)` → `Original` with non-null `stripped`, `stripped.length() < easy.length() + 4096`, no `title` tag (the source has one).
    - `compress(rotated, HIGH, false)` → `Compressed`, width 1080, height 1920 and no `side_data_list` on the output stream (measured 215 KB).
    - `strip(tc)` → non-null; `ffprobe -show_entries stream=codec_type:format_tags` on it shows exactly one stream (`video`) and neither `title` nor `location`.
- [ ] **Step 7: Run** with ffmpeg installed → PASS; `AMETHYST_FFMPEG_DISABLED=1 ./gradlew :commons:jvmTest --tests "*Ffmpeg*"` → gated tests return early, the unit tests and `FfmpegProcessesTest` PASS (a bare `PATH` is not a valid gate: the locator finds Homebrew by design).
- [ ] **Step 8: Commit** — `feat(uploads): FfmpegVideoCompressor with encoder fallback, remux strip and stall watchdog`

---

### Task 7: Notice plumbing and wording

**Files:**
- Create: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/ForwardingNoticeSink.kt`
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAccountViewModelHost.kt:77` (add `val notices = ForwardingNoticeSink()` beside it)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAppRoot.kt:70-79`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/notices/ui/UserNoticeText.kt` (add the `UploadNotice` branches beside `NoteActionNotice`)
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml` (after `media_compression_preview_tap_compressed`)
- Test: `desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/app/ForwardingNoticeSinkTest.kt`

**Interfaces:**
- Produces: `class ForwardingNoticeSink : UserNoticeSink { @Volatile var target: UserNoticeSink? = null; override fun notify(notice: UserNotice) { target?.notify(notice) ?: Log.w(TAG) { "No sink for $notice" } } }`; `DesktopAppRoot.createAccountViewModel` does `.also { host.notices.target = it.toastManager }` on the `AccountViewModel` it builds.
- Strings (exact copy):
  - `video_compression_failed_title` = `Video compression failed`
  - `video_compression_failed_message` = `The original file is being uploaded instead.`
  - `video_compression_hevc_unavailable_title` = `H.265 not available`
  - `video_compression_hevc_unavailable_message` = `ffmpeg has no working HEVC encoder on this computer, so the video was encoded as H.264.`
  - `video_compression_hdr_title` = `HDR video`
  - `video_compression_hdr_message` = `Compressed HDR video looks flat. Choose Uncompressed to keep the original colours.`
- Wording in `UserNoticeText.kt`: `VideoCompressionFailed` → title/message above with `reason` as the details line the way `SignerNotice.Failed` passes its cause; `HevcUnavailable` and `HdrSource` → their title/message.

- [ ] **Step 1: Write the failing test** — `notify` before a target is set does not throw; after `target = recording sink`, the notice arrives.
- [ ] **Step 2: Run to verify it fails.** — [ ] **Step 3: Implement** the sink, the host property, the root wiring, the strings, the wording.
- [ ] **Step 4: Run** `./gradlew :desktopApp:test --tests "*ForwardingNoticeSinkTest" :commonsUI:compileKotlinJvm` → PASS, compiles.
- [ ] **Step 5: Commit** — `feat(desktop): route typed upload notices to the toast manager`

---

### Task 8: `DesktopMediaUploader` compresses, strips and probes video

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/MediaUploader.kt:100-102` (add the two members before `displayName`) and `:126` (`UnavailableMediaUploader`: `videoCompression = Unsupported`)
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/VideoCompressionSupport.kt`
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/AndroidMediaUploader.kt:79` (`override val videoCompression = VideoCompressionSupport.Supported`)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopMediaUploader.kt` (constructor `:78-81`, `upload` `:115-158`, `uploadEncrypted` `:160-206`, `preparePayload` `:220-236`, `nip95` `:238-269`, `nip96` `:271-313`, `compressIfNeeded` `:351-358`, `stripMetadata` `:413-420`)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAccountViewModelHost.kt:77`
- Test: `desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopMediaUploaderVideoTest.kt`

**Interfaces:**
- Consumes: `VideoCompressor` (with `probe`), `VideoCompressionResult`, `FfmpegVideoCompressor` (Task 6), `ForwardingNoticeSink` (Task 7).
- Produces:
  - `enum class VideoCompressionSupport { Supported, NeedsFfmpeg, Unsupported }`.
  - `MediaUploader.videoCompression: VideoCompressionSupport get() = Unsupported` (KDoc: whether `upload`/`compressIfNeeded` transcode `video/*` below UNCOMPRESSED; the composer hides the video controls unless `Supported` and shows the install hint for `NeedsFfmpeg`) and `suspend fun probeVideo(uri: MediaUri): VideoInfo? = null`.
  - `class DesktopMediaUploader(clientFor, httpClients: IRoleBasedHttpClientBuilder, private val notices: UserNoticeSink = UserNoticeSink {}, private val videoCompressor: VideoCompressor = FfmpegVideoCompressor())` — one ffmpeg dependency, no separate probe (a default `FfmpegProbe(FfmpegLocator.default.ffprobe)` parameter would resolve `ffprobe` at construction, possibly from composition). `httpClients` widens from `RoleBasedHttpClientBuilder` to the interface it implements (on the JVM `IRoleBasedHttpClientBuilder` is a typealias of `RoleBasedOkHttpClients`: seven `OkHttpClient`-returning functions plus `proxyPortForVideo(url): Int?`) so a test can stub it; the host passes `modules.roleBasedHttpClientBuilder` and `notices`.
  - `override val videoCompression get() = if (videoCompressor.available) Supported else NeedsFfmpeg`; `override suspend fun probeVideo(uri) = videoCompressor.probe(fileOf(uri))`.
  - `internal suspend fun <T> withVideoPrepared(progress: UploadOrchestrator, uri: MediaUri, mimeType: String?, quality: CompressorQuality, useH265: Boolean, stripMetadata: Boolean, convertGifToMp4: Boolean, progressCeiling: Double, maxBytes: Long? = null, block: suspend (file: File, prepared: Boolean) -> T): T?` — `mime = mimeType ?: mimeType(uri)`; `isVideo = mime starts with "video/"`, `isGif = mime == "image/gif"`. Decision, when `videoCompressor.available`:
    - `isGif && convertGifToMp4` → `convertGif`;
    - `isVideo && quality != UNCOMPRESSED` → `compress(source, quality, useH265, maxBytes)`;
    - `isVideo && quality == UNCOMPRESSED && stripMetadata` → `Original(strip(source))`;
    - otherwise → `block(fileOf(uri), false)`.
    Outcomes: `TooBig` → `null` (the caller reports `MEDIA_TOO_BIG_FOR_NIP95`); `Compressed(file)` → notices forwarded, `block(file, true)` with `file` deleted in `finally` under `NonCancellable`; `Original(stripped)` → notices forwarded, `block(stripped ?: fileOf(uri), stripped != null)` with `stripped` deleted likewise. Progress during the encode: `progress.updateState(0.02 + (progressCeiling - 0.02) * fraction, Compressing)`. Not available → `block(fileOf(uri), false)`.
  - Entry points: `upload`/`uploadEncrypted` wrap their Blossom call with `progressCeiling = 0.2`; `nip95` with `0.4` and `maxBytes = NIP95_MAX_BYTES`; `nip96` with `0.2`. Inside the block the pipeline receives `file`, `stripExif = stripMetadata && !prepared`, `quality = if (prepared) null else compressionQuality.toTarget()`. `nip95`/`nip96` gain `mimeType`, `useH265`, `convertGifToMp4` parameters forwarded from the callers; `preparePayload` takes the prepared `File` and a `prepared: Boolean` instead of the `MediaUri`.
  - `compressIfNeeded` override: same decision table but without the block; `Compressed(file)`/`Original(stripped != null)` → `MediaCompressorResult(StringMediaUri(file.absolutePath), "video/mp4", file.length())` (the caller's `discardTempFile` deletes it — it is under `AmethystTempDir`); `Original(null)`/not wanted → `MediaCompressorResult(uri, mimeType, null)`; `TooBig` cannot occur (no `maxBytes`).
  - `stripMetadata(uri, mimeType)` override: for `video/*` with ffmpeg available → `videoCompressor.strip(file)?.let { StringMediaUri(it.absolutePath) } ?: uri`; otherwise the existing JPEG path.

- [ ] **Step 1: Write the failing tests** with `FakeVideoCompressor(available, result: (File) -> VideoCompressionResult, onCompress: (File, CompressorQuality, Boolean) -> Unit, onGif: (File) -> Unit, progressEmits: List<Double>, stripResult: () -> File?, probeResult: VideoInfo? = VideoInfo(VideoResolution(1920, 1080), 30f), compressDelayMs: Long = 0)` (`compress` calls `onCompress`, `delay(compressDelayMs)`, emits each `progressEmits` value, returns `result(source)`; `convertGif` calls `onGif` then returns `result(source)`; `strip` returns `stripResult()`; `probe` returns `probeResult`), a `RecordingSink`, and `StubHttpClients` (an `object : IRoleBasedHttpClientBuilder` whose seven `OkHttpClient` functions return `OkHttpClient()`; `proxyPortForVideo` returns null). All tests `= runTest`; `src` and every `tempVideo(n)` live in a per-test directory, never in `AmethystTempDir`:

```kotlin
@Test fun supportMirrorsTheCompressor() { assertEquals(VideoCompressionSupport.Supported, uploader(available = true).videoCompression); assertEquals(VideoCompressionSupport.NeedsFfmpeg, uploader(available = false).videoCompression) }
@Test fun compressIfNeededReturnsMp4TempForVideo() = runTest {
    val out = tempVideo(10)
    val r = uploader(result = { VideoCompressionResult.Compressed(out) }).compressIfNeeded(UploadOrchestrator(), uri(src), "video/quicktime", CompressorQuality.HIGH, false, false)
    assertEquals(out.absolutePath, r.uri.toString()); assertEquals("video/mp4", r.contentType); assertEquals(10L, r.size)
}
@Test fun compressIfNeededReturnsStrippedOriginal() = runTest { val s = tempVideo(10); val r = uploader(result = { VideoCompressionResult.Original(s) }).compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, false); assertEquals(s.absolutePath, r.uri.toString()) }
@Test fun uncompressedAndUnavailablePassThrough() = runTest {
    assertEquals(src.absolutePath, uploader().compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.UNCOMPRESSED, false, false).uri.toString())
    assertEquals(src.absolutePath, uploader(available = false).compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, false).uri.toString())
}
@Test fun imagesNeverReachTheVideoCompressor() = runTest { var called = false; uploader(result = { called = true; VideoCompressionResult.Original(null) }).compressIfNeeded(UploadOrchestrator(), uri(src), "image/jpeg", CompressorQuality.HIGH, false, false); assertFalse(called) }
@Test fun gifIsConvertedOnlyWhenAsked() = runTest {
    var gifCalls = 0
    val u = uploader(result = { VideoCompressionResult.Original(null) }, onGif = { gifCalls++ })
    u.compressIfNeeded(UploadOrchestrator(), uri(src), "image/gif", CompressorQuality.UNCOMPRESSED, false, convertGifToMp4 = false); assertEquals(0, gifCalls)
    u.compressIfNeeded(UploadOrchestrator(), uri(src), "image/gif", CompressorQuality.UNCOMPRESSED, false, convertGifToMp4 = true); assertEquals(1, gifCalls)   // even at UNCOMPRESSED, as Android
}
@Test fun noticesAreForwarded() = runTest { val sink = RecordingSink(); uploader(result = { VideoCompressionResult.Original(null, listOf(UploadNotice.HdrSource)) }, sink = sink).compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, false); assertEquals(listOf(UploadNotice.HdrSource), sink.notices) }
@Test fun withVideoPreparedDeletesTempOnSuccess() = runTest {
    val out = tempVideo(10)
    assertEquals("ok", uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { f, prepared -> assertEquals(out, f); assertTrue(prepared); "ok" })
    assertFalse(out.exists()); assertTrue(src.exists())
}
@Test fun withVideoPreparedDeletesTempEvenWhenBlockThrows() = runTest {
    val out = tempVideo(10)
    assertFailsWith<IllegalStateException> { uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { _, _ -> error("upload failed") } }
    assertFalse(out.exists())
}
@Test fun withVideoPreparedDeletesTempWhenCancelledInsideTheBlock() = runTest {
    val out = tempVideo(10)
    val job = launch { uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { _, _ -> delay(10_000) } }
    advanceTimeBy(100); job.cancelAndJoin()
    assertFalse(out.exists())                                                       // deleted under NonCancellable
}
@Test fun cancellationDuringCompressionLeavesNothingToDelete() = runTest {
    var blockRan = false
    val job = launch { uploader(result = { VideoCompressionResult.Compressed(tempVideo(10)) }, compressDelayMs = 10_000).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { _, _ -> blockRan = true } }
    advanceTimeBy(100); job.cancelAndJoin()
    assertFalse(blockRan); assertTrue(src.exists())
}
@Test fun withVideoPreparedReportsCompressingUpToTheCeiling() = runTest {
    val progress = UploadOrchestrator()
    uploader(result = { VideoCompressionResult.Compressed(tempVideo(10)) }, progressEmits = listOf(0.5)).withVideoPrepared(progress, uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, progressCeiling = 0.4) { _, prepared -> assertTrue(prepared) }
    assertEquals(0.21, progress.progress.value, 1e-9)                              // 0.02 + 0.38 * 0.5
    assertEquals(UploadingState.Compressing, progress.progressState.value)
}
@Test fun uncompressedVideoWithStripOnIsRemuxed() = runTest {
    val s = tempVideo(10)
    uploader(stripResult = { s }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.UNCOMPRESSED, false, stripMetadata = true, false, 0.2) { f, prepared -> assertEquals(s, f); assertTrue(prepared) }
}
@Test fun tooBigReturnsNull() = runTest { assertNull(uploader(result = { VideoCompressionResult.TooBig }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.4, maxBytes = 80_000) { _, _ -> Unit }) }
@Test fun stripMetadataRemuxesVideo() = runTest { val s = tempVideo(10); assertEquals(s.absolutePath, uploader(stripResult = { s }).stripMetadata(uri(src), "video/mp4").toString()); assertEquals(uri(src), uploader(stripResult = { null }).stripMetadata(uri(src), "video/mp4")) }
@Test fun probeVideoReturnsInfo() = runTest { assertEquals(VideoResolution(1920, 1080), uploader().probeVideo(uri(src))!!.resolution) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** the enum, the interface members, the Android override, `UnavailableMediaUploader`, the desktop uploader, the host wiring.
- [ ] **Step 4: Run** `./gradlew :desktopApp:test --tests "*DesktopMediaUploaderVideoTest" :amethyst:compilePlayDebugKotlin :commons:compileKotlinIosArm64` (the iOS task only if that target is configured on this machine; otherwise drop it) → PASS, compiles.
- [ ] **Step 5: Commit** — `feat(desktop): compress, strip and probe videos through ffmpeg in every upload path`

---

### Task 9: Slider steps and label become video-aware (`commons` + `commonsUI`)

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/CompressorQuality.kt` (companion)
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/MultiOrchestrator.kt:54-60`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/creators/uploads/MediaQualitySlider.kt:80-133`
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml:2125` (replace `media_compression_image_max_size`) and delete the same key from the three locales that translated it: `values-pl-rPL/strings.xml`, `values-hi-rIN/strings.xml`, `values-hu-rHU/strings.xml` (its value embeds the level name, so it cannot be composed with a video phrase; an orphaned translation would be reported by Crowdin)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/VideoAwareSliderStepsTest.kt`

**Interfaces:**
- Consumes: `hasVideoRule`, `videoLevel`, `VideoCompressionRules.ruleFor` (Task 1), `probeVideo` (Task 8).
- Produces, as pure functions in a new `object VideoAwareSliderSteps` (commonMain, same file as `CompressorQuality`), each taking the step list explicitly so the test can pass both platform lists:
  - `fun stepsFor(steps: List<CompressorQuality>, hasVideo: Boolean): List<CompressorQuality>` = `steps.filter { !hasVideo || it.hasVideoRule }`.
  - `fun snapForVideo(steps: List<CompressorQuality>, position: Int): Int` = `steps.indexOf(steps[position].videoLevel)`.
  - `fun toDisplayedIndex(steps: List<CompressorQuality>, position: Int, hasVideo: Boolean): Int` = index of `steps[if (hasVideo) snapForVideo(steps, position) else position]` in `stepsFor(steps, hasVideo)`.
  - `fun fromDisplayedIndex(steps: List<CompressorQuality>, index: Int, hasVideo: Boolean): Int` = `steps.indexOf(stepsFor(steps, hasVideo)[index])`.
  - `CompressorQuality.Companion` wrappers with the same names minus the `steps` parameter, bound to `platformSliderSteps`.
- `MultiOrchestrator.hasCompressibleImage(): Boolean` = any item with `isImage() == true && isCompressible()`; `fun firstVideo(): SelectedMedia?`.
- Strings: delete `media_compression_image_max_size` (base + the three locales); add `media_compression_images_up_to` = `images up to %1$d px`, `media_compression_video_target` = `%1$s video → %2$dp, %3$s Mbps` (e.g. `4K video → 2160p, 8 Mbps`; the units live in the resource so translators can move them), `media_compression_video_target_example` = `1080p video → %1$dp, %2$s Mbps` (used when the file could not be probed).
- `MediaQualitySlider`: `videoMode = media?.hasVideo() == true && uploader.videoCompression == VideoCompressionSupport.Supported` — **not** `hasVideo()` alone: on a platform that will not compress the video the slider governs only the images, so it must keep the 5-stop list and say nothing about video (otherwise a photo+video post on a desktop without ffmpeg would lose VERY_HIGH and promise a video target it cannot deliver). `steps = CompressorQuality.stepsFor(videoMode)`; `Slider(value = toDisplayedIndex(position, videoMode), valueRange 0..steps.lastIndex, steps = steps.size - 2, onValueChange = { onPositionChange(fromDisplayedIndex(it.toInt(), videoMode)) })`; `LaunchedEffect(videoMode) { if (videoMode) snapForVideo(position).let { if (it != position) onPositionChange(it) } }`. The video phrase: `val info by produceState<VideoInfo?>(null, media?.firstVideo()) { value = media?.firstVideo()?.let { uploader.probeVideo(it.uri) } }`; with `info` → `rule = ruleFor(quality, info.resolution.getStandard())` worded as `stringRes(Res.string.media_compression_video_target, standard.label, rule.shortSide, mbps(rule.bitrateMbps))` where `mbps()` drops a trailing `.0` (`2`, `0.75`, `8`); without → `media_compression_video_target_example` with the 1080p row's `shortSide` and bitrate. Label = `listOfNotNull(name, imagePart, videoPart).joinToString(" · ")`, `imagePart` only when `media?.hasCompressibleImage() == true && quality.imageMaxDimension != null`, `videoPart` only when `videoMode && quality != UNCOMPRESSED`.

- [ ] **Step 1: Write the failing tests**

```kotlin
private val DESKTOP = listOf(LOW, MEDIUM, HIGH, VERY_HIGH, UNCOMPRESSED)
private val PHONE = listOf(LOW, MEDIUM, HIGH, UNCOMPRESSED)
@Test fun videoStepsDropVeryHigh() { assertEquals(PHONE, VideoAwareSliderSteps.stepsFor(DESKTOP, true)); assertEquals(DESKTOP, VideoAwareSliderSteps.stepsFor(DESKTOP, false)); assertEquals(PHONE, VideoAwareSliderSteps.stepsFor(PHONE, true)) }
@Test fun veryHighSnapsToHigh() { assertEquals(2, VideoAwareSliderSteps.snapForVideo(DESKTOP, 3)); assertEquals(2, VideoAwareSliderSteps.snapForVideo(DESKTOP, 2)); assertEquals(4, VideoAwareSliderSteps.snapForVideo(DESKTOP, 4)); assertEquals(3, VideoAwareSliderSteps.snapForVideo(PHONE, 3)) }
@Test fun displayedIndexRoundTrips() {
    for (p in DESKTOP.indices) assertEquals(p, VideoAwareSliderSteps.fromDisplayedIndex(DESKTOP, VideoAwareSliderSteps.toDisplayedIndex(DESKTOP, p, false), false))
    assertEquals(3, VideoAwareSliderSteps.toDisplayedIndex(DESKTOP, 4, true)); assertEquals(4, VideoAwareSliderSteps.fromDisplayedIndex(DESKTOP, 3, true)); assertEquals(2, VideoAwareSliderSteps.toDisplayedIndex(DESKTOP, 3, true))
    for (p in PHONE.indices) assertEquals(p, VideoAwareSliderSteps.fromDisplayedIndex(PHONE, VideoAwareSliderSteps.toDisplayedIndex(PHONE, p, true), true))
}
@Test fun uncompressedStaysLast() { assertEquals(DESKTOP.lastIndex, VideoAwareSliderSteps.fromDisplayedIndex(DESKTOP, VideoAwareSliderSteps.stepsFor(DESKTOP, true).lastIndex, true)) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** the object and wrappers, `hasCompressibleImage`/`firstVideo`, the strings, the slider changes.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*VideoAwareSliderStepsTest" :commonsUI:compileKotlinJvm` → PASS, compiles. Run `python3 .claude/hooks/compose_escaping_check.py` if present → no findings.
- [ ] **Step 5: Commit** — `feat(uploads): media-quality slider shows video stops and describes the picked video`

---

### Task 10: Dialog honesty — hide dead controls, fix the metadata toggle, "Upload Media"

**Files:**
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/creators/uploads/ImageVideoDescription.kt:356-431`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/actions/NewMediaView.kt:225-277`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/screen/loggedIn/chats/utils/ChatFileUploadDialog.kt:238-270`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/actions/uploads/SelectFromGallery.kt:63,85,89-105` (`GallerySelectButton` gains a `label: StringResource` parameter; `SelectFromGallery` passes `upload_media`, `SelectSingleFromGallery` passes `upload_image`), `…/chats/privateDM/send/NewGroupDMScreen.kt:429`, `…/longs/NewLongVideoButton.kt:162`, `…/shorts/NewShortVideoButton.kt:176`, `…/video/NewVideoFeedButton.kt:208`
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml` (after `upload_image`)

**Interfaces:**
- Consumes: `videoCompression` (Task 8), `hasCompressibleImage` (Task 9).
- Strings: `upload_media` = `Upload Media`; `video_compression_needs_ffmpeg` = `Install ffmpeg to compress videos. Until then videos are uploaded as they are.`
- Shared in each dialog: `val support = accountViewModel.host.mediaUploader.videoCompression`; `val canCompressVideo = support == VideoCompressionSupport.Supported`; `val hasVideo = …hasVideo() == true`; `val isVideoWithCompression = hasVideo && canCompressVideo && slider != uncompressedSliderPosition`; the hint `Text(stringRes(Res.string.video_compression_needs_ffmpeg), style = bodySmall, color = Color.Gray)` when `hasVideo && support == VideoCompressionSupport.NeedsFfmpeg`, placed where the H.265 switch would be (in the chat dialog: under the slider block).
- The three dialogs differ and keep their shapes; only conditions change:

  | Dialog | Slider block shown when | H.265 switch | GIF→MP4 switch | Strip-metadata toggle |
  | --- | --- | --- | --- | --- |
  | `ImageVideoDescription.kt:372-431` | `(hasCompressibleImage() \|\| (hasVideo && canCompressVideo)) && !convertGifToMp4` | `isVideoWithCompression` | `hasGif() && canCompressVideo` | hidden when `isVideoWithCompression` (today `:357` hides it on `hasVideo && slider != UNCOMPRESSED` — the bug) |
  | `NewMediaView.kt:225-277` | always (unchanged — it has no `hasCompressible` guard today) | `isVideoWithCompression` (today `hasVideo()`) | none — `NewMediaModel` has no `convertGifToMp4` state; do not add one | always (unchanged) |
  | `ChatFileUploadDialog.kt:238-270` | `hasCompressibleImage() \|\| (hasVideo && canCompressVideo)` (today `hasCompressible()`) | none — `ChatFileUploadState` has no `useH265`; do not add one | none | always (unchanged) |

- The five attach buttons: `Res.string.upload_media`. `SelectSingleFromGallery` (profile picture, banner, group and channel pictures), `NewPictureButton` and `ProfileHeader` keep `upload_image`.

- [ ] **Step 1: Apply the changes** (composition is covered by the manual test; the logic they consume is unit-tested in Tasks 8 and 9).
- [ ] **Step 2: Compile both front ends** — `./gradlew :commonsUI:compileKotlinJvm :amethyst:compilePlayDebugKotlin` → success.
- [ ] **Step 3: Run the desktop app** `./gradlew :desktopApp:run` with ffmpeg installed: picking a video shows 4 slider stops, a label naming the file's real resolution, the H.265 switch, and the metadata toggle hides below UNCOMPRESSED; photo + video together snaps VERY_HIGH → HIGH. Then `AMETHYST_FFMPEG_DISABLED=1 ./gradlew :desktopApp:run` (the `run` task's JVM inherits the environment; a bare `PATH` would not do, the locator finds Homebrew): the hint shows, no H.265/GIF switch, the metadata toggle stays, images still get the 5-stop slider and the photo+video label has no video phrase; the chat file dialog behaves the same. The post-composer attach tooltip reads "Upload Media"; the profile-picture picker still reads "Upload Image".
- [ ] **Step 4: Commit** — `fix(composer): hide video compression controls the platform cannot honour`

---

### Task 11: End-to-end verification and plan bookkeeping

**Files:**
- Modify: `desktopApp/plans/README.md` (add "phase 1 built" to the summary; keep "In progress" until merged)
- Create: `desktopApp/plans/2026-10-11-desktop-video-compression-manual-testing.md` (follow the format of `2026-08-20-live-media-manual-testing.md`)

- [ ] **Step 1: Full test run** — `./gradlew test` → BUILD SUCCESSFUL (with ffmpeg installed, so the gated tests run); then `AMETHYST_FFMPEG_DISABLED=1 ./gradlew :commons:jvmTest :desktopApp:test` → still green; `./gradlew :amethyst:compilePlayDebugKotlin`.
- [ ] **Step 2: The hash check** from the investigation: upload a **high-bitrate** 1080p clip (the manual sheet's `big.mp4`; the investigation's `file_example_MOV_1920_2_2MB.mov` is ~450 kbps, so every level above LOW encodes larger than it and the remuxed original goes up — it is the sheet's size-rule case, not the encoder case) from the desktop app at HIGH to blossom.band; `curl` the blob; `shasum -a 256` must differ from `ead8b50c…e40c62`; `ffprobe` must report 1920×1080, `h264`, video `bit_rate ≤ 4 400 000`, no `format_tags`. Repeat at LOW → 480p, and with H.265 → `hevc` (or the H.264 toast). Upload once at UNCOMPRESSED with strip on → hash differs from the source (remuxed), `ffprobe` shows the source's codec and no tags.
- [ ] **Step 3: Package and launch from Finder** — `./gradlew :desktopApp:packageDmg`, install, launch from Finder with Homebrew ffmpeg present → the video controls appear (Review Focus 5).
- [ ] **Step 4: Write the manual-testing sheet** covering: with ffmpeg and with `AMETHYST_FFMPEG_DISABLED=1`; the Finder launch; each stop; H.265 with and without an HEVC encoder; GIF→MP4; a portrait phone video; an HDR iPhone clip (toast shown, upload proceeds); three videos in one post (`pgrep ffmpeg` shows one at a time); quit mid-encode (`pgrep ffmpeg` empty afterwards, `AmethystTempDir.rootDir()` has no `amethyst_video_*`); a corrupt video (uploads original + toast); NIP-95 with a long clip refused before encoding; the chat file dialog with and without ffmpeg.
- [ ] **Step 5: Commit** — `docs(desktop): manual test sheet for desktop video compression`

Hand the branch and the sheet to the human; do not open a PR.
