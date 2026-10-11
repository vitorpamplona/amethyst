# Desktop Video Compression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the composer's media-quality slider, H.265 switch and GIF→MP4 switch real on the JVM desktop by transcoding with a system `ffmpeg`, using Android's resolution/bitrate table; hide those controls when no `ffmpeg` is installed; make the "compression strips metadata" promise true even when the original is uploaded.

**Architecture:** The rules table moves from `amethyst` to `commons/commonMain` so both platforms read one source. A new `commons/jvmMain` package `service/upload/video/` runs `ffmpeg`/`ffprobe` as subprocesses (locator, encoder preference, probe, command builder, progress parser, process registry, runner). `DesktopMediaUploader` feeds every upload entry point through the compressor; `commonsUI` adapts the shared dialogs (video-aware slider stops and label, hidden controls without ffmpeg, "Upload Media").

**Tech Stack:** Kotlin Multiplatform, `java.lang.ProcessBuilder`, Jackson (already on the commons JVM classpath through quartz) for `ffprobe` JSON, kotlinx-coroutines, `kotlin.test` + `runTest`, Compose Multiplatform for the dialogs.

**Spec:** `desktopApp/plans/2026-10-11-desktop-video-compression.md`

## Global Constraints

- No new third-party dependency. `ffmpeg`/`ffprobe` are external processes found on `$PATH`, in the package-manager directories listed in spec §2, or at `${compose.application.resources.dir}/ffmpeg/<name>(.exe)`; never a working-directory-relative path.
- The LOW/MEDIUM/HIGH table **including its UNKNOWN rows**, ×1.3 at ≥ 60 fps, ×0.75 for H.265, are copied **verbatim** from `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt:109-144`. The probe-failure fallback (`:177-179`) is a separate constant: 2 000 000 bps, no resize.
- Encoder preference, exact order — H.264: `libx264, h264_videotoolbox, h264_nvenc, h264_qsv, h264_amf, h264_mf, libopenh264`; H.265: `libx265, hevc_videotoolbox, hevc_nvenc, hevc_qsv, hevc_amf, hevc_mf`. No `*_vaapi`. Candidates = available HEVC (if asked) **then** available H.264.
- Encode command (spec §2): `-map 0:v:0 -map 0:a:0? -sn -dn -map_chapters -1`, `-pix_fmt yuv420p`, `-maxrate`=`-b:v`, `-bufsize`=2×, `-c:a aac -b:a 128k`, `-map_metadata -1 -map_metadata:s -1`, `-movflags +faststart`, `-tag:v hvc1` for HEVC, `-progress pipe:1`. Remux command: `-map 0 -c copy -map_metadata -1 -map_metadata:s -1 -map_chapters -1 -movflags +faststart`.
- Compression progress maps to `UploadingState.Compressing` from 0.02 up to a per-path ceiling (0.20 Blossom/NIP-96, 0.40 NIP-95). No overall timeout; a stall (no progress line for 60 s) kills the process. A process-wide `Semaphore(1)` serialises encodes. Every live process is registered and killed by a JVM shutdown hook.
- Output ≥ original → the original, remuxed. Every candidate fails → the original, remuxed, plus a notice. Remux failure → the untouched original.
- Every new user-visible string goes in `commonsUI/src/commonMain/composeResources/values/strings.xml`, positional format args only, no Android-style escaping.
- `com.vitorpamplona.quartz.utils.Log` lambda form; no inline fully-qualified names; temp files only via `AmethystTempDir.createTempFile("amethyst_…", …)`; delete with `File.deleteOrWarn(tag, what)`.
- `./gradlew spotlessApply` before every commit. Conventional commits. Never `--no-verify`.

## Review Focus

Inputs the spec implies that a user will hit; each has a test in the owning task:

1. A portrait phone video (height > width): the short side is the width, never upscaled, dimensions even — Task 5 `scaleFilter` test, Task 6 gated rotated-source test.
2. A 59.94 fps clip (`avg_frame_rate = 60000/1001`): no ×1.3 multiplier — Task 4 parser test + Task 1 multiplier test.
3. `ffprobe` missing while `ffmpeg` is present: the probe-failure constant, not a crash and not the UNKNOWN row — Task 6 test.
4. A hardware HEVC encoder listed by `-encoders` but failing at run time: the H.264 tail is reached, the bitrate recomputed without the ×0.75, and the first temp deleted — Task 3 candidate test + Task 6 retry test.
5. A packaged app launched from Finder (empty launchd PATH) with Homebrew ffmpeg: found through `/opt/homebrew/bin` — Task 2 locator test + manual sheet.

---

### Task 1: Move the rules table to `commons`

**Files:**
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRules.kt`
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/CompressorQuality.kt:28-37`
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt:46-144,159-179` (delete the moved types and the table; import them; keep `getVideoInfo`, `compressVideo`, the toasts)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRulesTest.kt`

**Interfaces:**
- Produces (package `com.vitorpamplona.amethyst.commons.service.uploads.video`):
  - `enum class VideoStandard(val label: String)` — `UHD_4K, QHD_1440P, FHD_1080P, HD_720P, SD_480P, NHD_360P, QVGA_240P, UNKNOWN`, `toString() = label`.
  - `data class VideoResolution(val width: Int, val height: Int) { fun getStandard(): VideoStandard }` — short-side thresholds 2160/1440/1080/720/480/360/240.
  - `data class VideoInfo(val resolution: VideoResolution, val framerate: Float)`.
  - `data class CompressionRule(val shortSide: Int, val bitrateMbps: Float, val description: String) { fun getBitrateBps(framerate: Float, useH265: Boolean): Int }`.
  - `object VideoCompressionRules { fun ruleFor(quality: CompressorQuality, standard: VideoStandard): CompressionRule?; const val PROBE_FAILED_BITRATE_BPS = 2_000_000 }` — `ruleFor` looks the table up with `quality.videoLevel`, so it is null only for UNCOMPRESSED.
  - `CompressorQuality.hasVideoRule: Boolean` (constructor parameter: `VERY_LOW false, LOW true, MEDIUM true, HIGH true, VERY_HIGH false, UNCOMPRESSED true`) and `CompressorQuality.videoLevel: CompressorQuality` (`VERY_HIGH → HIGH`, `VERY_LOW → LOW`, else `this`).

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
        assertEquals(4_000_000, rule.getBitrateBps(59.94f, useH265 = false))   // Review Focus 2
    }
    @Test fun probeFailureConstant() { assertEquals(2_000_000, VideoCompressionRules.PROBE_FAILED_BITRATE_BPS) }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.uploads.video.*"` → unresolved symbols.
- [ ] **Step 3: Create `VideoCompressionRules.kt`** with the types in Interfaces; the table body is the three `mapOf(...)` blocks copied from `VideoCompressionHelper.kt:109-144`. Add `hasVideoRule` and `videoLevel` to `CompressorQuality`.
- [ ] **Step 4: Point Android at it.** Delete the moved declarations and `MBPS_TO_BPS_MULTIPLIER` from `VideoCompressionHelper.kt`; import the commons types; replace `compressionRules.getValue(mediaQuality).getValue(…)` with `VideoCompressionRules.ruleFor(mediaQuality, info.resolution.getStandard())!!` (non-null: Android never calls it at UNCOMPRESSED — `MediaCompressor.kt:67` returns before); replace `2 * MBPS_TO_BPS_MULTIPLIER` with `VideoCompressionRules.PROBE_FAILED_BITRATE_BPS`.
- [ ] **Step 5: Run** `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.uploads.video.*" :amethyst:compileDebugKotlin` → PASS, compiles.
- [ ] **Step 6: Commit** — `refactor(uploads): share the video compression rules through commons`

---

### Task 2: `FfmpegLocator` with package-manager paths and warm-up

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegLocator.kt`
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/VideoThumbnailCache.kt:112-138` (replace the private `ffmpegBinary` lazy with `FfmpegLocator.default.ffmpeg`)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAppModules.kt` (after `applicationIOScope` at `:151`: `applicationIOScope.launch { FfmpegLocator.default.warmUp() }`)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegLocatorTest.kt`

**Interfaces:**
- Produces: `class FfmpegLocator(private val runsOnPath: (binary: String) -> Boolean = ::probeOnPath, private val packageDirs: () -> List<File> = ::defaultPackageDirs, private val resourcesDir: () -> File? = { System.getProperty("compose.application.resources.dir")?.let(::File) }) { val ffmpeg: String? by lazy; val ffprobe: String? by lazy; val available: Boolean get() = ffmpeg != null; fun warmUp() { ffmpeg; ffprobe }; companion object { val default = FfmpegLocator() } }`.
  Resolution of a name `b` (with `.exe` appended on Windows): `runsOnPath(b)` → `b`; else the first `dir/b` in `packageDirs()` that exists and is executable; else `resourcesDir()/ffmpeg/b` if it exists and is executable; else null.
  `defaultPackageDirs()`: on macOS/Linux `/opt/homebrew/bin, /usr/local/bin, /opt/local/bin`; on Windows `%ProgramFiles%\ffmpeg\bin, %LOCALAPPDATA%\Microsoft\WinGet\Links, %USERPROFILE%\scoop\shims, %ChocolateyInstall%\bin` (skipping unset variables).
  `probeOnPath` is the `-version` probe moved verbatim from `VideoThumbnailCache` (stdout/stderr discarded, 2 s wait, `destroyForcibly` on overrun, true iff exit 0).

- [ ] **Step 1: Write the failing tests** (temp dirs created in `@BeforeTest`, deleted in `@AfterTest`; executables are empty files with `setExecutable(true)`)

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
@Test fun warmUpResolvesBoth() { var probes = 0; FfmpegLocator({ probes++; true }, { emptyList() }, { null }).warmUp(); assertEquals(2, probes) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** `FfmpegLocator.kt`; switch `VideoThumbnailCache.runFfmpeg` to `FfmpegLocator.default.ffmpeg` and delete its lazy; add the warm-up launch in `DesktopAppModules`.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegLocatorTest" :desktopApp:compileKotlinJvm` → PASS, compiles.
- [ ] **Step 5: Commit** — `refactor(desktop): FfmpegLocator finds package-manager installs and warms up at start`

---

### Task 3: `FfmpegEncoders` — parse `-encoders`, pick by preference

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegEncoders.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegEncodersTest.kt`

**Interfaces:**
- Produces: `object FfmpegEncoders { val H264: List<String>; val H265: List<String>; fun parseAvailable(encodersOutput: String): Set<String>; fun candidates(available: Set<String>, useH265: Boolean): List<String>; fun isHevc(encoder: String): Boolean = encoder in H265; suspend fun listAvailable(ffmpeg: String): Set<String> }`. `candidates` = `(if (useH265) H265.filter { it in available } else emptyList()) + H264.filter { it in available }`. `listAvailable` runs `ffmpeg -hide_banner -encoders` (stderr discarded, 5 s cap) and parses stdout.

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

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement.** Parse with `Regex("""^\s*[VAS][.F][.S][.X][.B][.D]\s+(\S+)""")`.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegEncodersTest"` → PASS.
- [ ] **Step 5: Commit** — `feat(uploads): encoder preference list for ffmpeg video compression`

---

### Task 4: `FfmpegProbe` — `ffprobe` JSON → `VideoInfo`, duration, HDR

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegProbe.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfprobeOutputParserTest.kt`

**Interfaces:**
- Consumes: `VideoInfo`, `VideoResolution` (Task 1).
- Produces: `data class ProbeResult(val info: VideoInfo, val durationSeconds: Double?, val isHdr: Boolean)`; `object FfprobeOutputParser { fun parse(json: String): ProbeResult? }`; `open class FfmpegProbe(private val ffprobe: String?) { open suspend fun probe(file: File): ProbeResult? }` (open so Task 6's test can fake it) running `ffprobe -v error -select_streams V:0 -show_entries stream=width,height,avg_frame_rate,color_transfer -show_entries format=duration -of json <file>` (10 s cap); null when `ffprobe` is null, the process fails, or parsing fails. `isHdr` = `color_transfer` is `smpte2084` or `arib-std-b67`.

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
    `remux` = `[ffmpeg, -hide_banner, -loglevel, error, -nostdin, -y, -i, input, -map, 0, -c, copy, -map_metadata, -1, -map_metadata:s, -1, -map_chapters, -1, -movflags, +faststart, output]`.
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
@Test fun remuxCopiesAndStrips() {
    val cmd = FfmpegCommandBuilder.remux("ffmpeg", File("a"), File("b"))
    assertEquals(listOf("-map", "0", "-c", "copy", "-map_metadata", "-1", "-map_metadata:s", "-1", "-map_chapters", "-1", "-movflags", "+faststart", "b"), cmd.subList(cmd.indexOf("-map"), cmd.size))
    assertFalse("-c:v" in cmd); assertFalse("-progress" in cmd)
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
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegVideoCompressorTest.kt`, `…/FfmpegVideoCompressorFfmpegTest.kt` (gated)

**Interfaces:**
- Consumes: Tasks 1–5; `AmethystTempDir.createTempFile`, `File.deleteOrWarn`, `UserNotice`.
- Produces:
  - `sealed interface UploadNotice : UserNotice { data class VideoCompressionFailed(val reason: String?) : UploadNotice; data object HevcUnavailable : UploadNotice; data object HdrSource : UploadNotice }` (commonMain).
  - `sealed interface VideoCompressionResult { data class Compressed(val file: File, val notices: List<UserNotice> = emptyList()) : VideoCompressionResult; data class Original(val stripped: File?, val notices: List<UserNotice> = emptyList()) : VideoCompressionResult; data object TooBig : VideoCompressionResult }` — `stripped` is a remuxed temp (caller deletes) or null when remux was impossible.
  - `interface VideoCompressor { val available: Boolean; suspend fun compress(source: File, quality: CompressorQuality, useH265: Boolean, maxBytes: Long? = null, onProgress: (Double) -> Unit): VideoCompressionResult; suspend fun convertGif(source: File, onProgress: (Double) -> Unit): VideoCompressionResult; suspend fun strip(source: File): File? }` — `strip` is the remux alone (null when unavailable or failed).
  - `fun interface ProcessRunner { suspend fun run(command: List<String>, onStdoutLine: (String) -> Unit, stallTimeoutMs: Long): ProcessOutcome }`, `data class ProcessOutcome(val exitCode: Int, val stderrTail: String)`.
  - `object FfmpegProcesses : ProcessRunner` — the real runner: `ProcessBuilder`, stdout read line by line, last 2 KB of stderr kept, no stdout line for `stallTimeoutMs` → `destroyForcibly()` and exit code `-1`, coroutine cancellation → `destroyForcibly()`; every started `Process` is added to a `ConcurrentHashMap.newKeySet<Process>()` and removed on exit; `init` registers `Runtime.getRuntime().addShutdownHook` that `destroyForcibly()`s every live one. Exposes `val encodeGate = Semaphore(1)` (kotlinx) for the compressor.
  - `class FfmpegVideoCompressor(private val locator: FfmpegLocator = FfmpegLocator.default, private val encoders: suspend (ffmpeg: String) -> Set<String> = FfmpegEncoders::listAvailable, private val probe: (ffprobe: String?) -> FfmpegProbe = ::FfmpegProbe, private val stallTimeoutMs: Long = 60_000, private val runner: ProcessRunner = FfmpegProcesses, private val gate: Semaphore = FfmpegProcesses.encodeGate) : VideoCompressor`.

Algorithm of `compress` (not determined by the signature and tests):
1. `ffmpeg = locator.ffmpeg ?: return Original(null)`.
2. `probed = probe(locator.ffprobe).probe(source)`; `rule = probed?.let { VideoCompressionRules.ruleFor(quality, it.info.resolution.getStandard()) }`; `shortSide = rule?.shortSide`; `fps = probed?.info?.framerate ?: 30f`; `notices` starts with `HdrSource` when `probed?.isHdr == true`.
3. `maxBytes` given and `probed?.durationSeconds` known: `estimate = ((bitrateFor(hevc = useH265) + 128_000) / 8.0) * duration`; `estimate > maxBytes` → `TooBig`.
4. `candidates = FfmpegEncoders.candidates(encoders(ffmpeg), useH265)`; empty → `fail("no encoder")`.
5. Under `gate.withPermit`, for each candidate: `hevc = isHevc(candidate)`, `bitrate = rule?.getBitrateBps(fps, hevc) ?: PROBE_FAILED_BITRATE_BPS`, `out = createTempFile("amethyst_video_", ".mp4")`, run `encode(...)` feeding `FfmpegProgressParser(probed?.durationSeconds)` into `onProgress`; exit 0 → stop; else delete `out`, keep `stderrTail`, continue. A `try/finally` deletes `out` under `NonCancellable` when cancelled.
6. No success → `fail(lastStderrTail)` where `fail(reason)` = `Original(strip(source), notices + VideoCompressionFailed(reason))`.
7. `out.length() >= source.length()` → delete `out`, `Original(strip(source), notices)`.
8. `useH265 && !hevcOfTheWinner` → `notices += HevcUnavailable`. `Compressed(out, notices)`.

`convertGif` = steps 1, 2 (rule = MEDIUM row for the GIF's standard, `shortSide = null` so the source size is kept), 4–8 with `useH265 = false` and no size-vs-original rule (an MP4 is routinely bigger than a GIF; the point is playability). `strip(source)` runs `remux` into `createTempFile("amethyst_video_", ".mp4")`, returning the file on exit 0, else deleting it and returning null.

- [ ] **Step 1: Write the failing unit tests** with a fake `ProcessRunner` that records commands, writes the scripted progress lines, creates the output file (last argument) with the scripted byte count, and returns a scripted exit code per call:

```kotlin
@Test fun retriesNextEncoderWhenFirstFails() {            // Review Focus 4
    val runner = FakeRunner(exitCodes = listOf(1, 0), outputBytes = 10)
    val r = compressor(runner, encoders = setOf("hevc_nvenc", "libx264")).compress(source(1000), CompressorQuality.HIGH, useH265 = true) {}
    assertIs<VideoCompressionResult.Compressed>(r)
    assertEquals(listOf("hevc_nvenc", "libx264"), runner.commands.map { it[it.indexOf("-c:v") + 1] })
    assertEquals(listOf("3000000", "4000000"), runner.commands.map { it[it.indexOf("-b:v") + 1] })   // ×0.75 only for the HEVC attempt
    assertEquals(listOf(UploadNotice.HevcUnavailable), r.notices)
    assertEquals(1, tempVideos().size)                                            // the failed temp is gone
}
@Test fun allEncodersFailingRemuxesOriginalWithNotice() {
    val runner = FakeRunner(exitCodes = listOf(1, 0), stderr = "boom", outputBytes = 10)                  // 2nd call is the remux
    val r = compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
    assertEquals(listOf(UploadNotice.VideoCompressionFailed("boom")), (r as VideoCompressionResult.Original).notices)
    assertNotNull(r.stripped); assertTrue("copy" in runner.commands[1])
}
@Test fun largerOutputIsDiscardedAndOriginalRemuxed() {
    val runner = FakeRunner(exitCodes = listOf(0, 0), outputBytes = 5000)
    val r = compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
    assertIs<VideoCompressionResult.Original>(r); assertNotNull(r.stripped); assertEquals(1, tempVideos().size)
}
@Test fun remuxFailureYieldsNullStripped() { val r = compressor(FakeRunner(listOf(0, 1), outputBytes = 5000), setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}; assertNull((r as VideoCompressionResult.Original).stripped) }
@Test fun hdrSourceIsNoticed() { val r = compressor(FakeRunner(listOf(0), outputBytes = 10), setOf("libx264"), hdr = true).compress(source(1000), CompressorQuality.HIGH, false) {}; assertEquals(listOf(UploadNotice.HdrSource), (r as VideoCompressionResult.Compressed).notices) }
@Test fun missingFfprobeUsesProbeFailureFallback() {     // Review Focus 3
    val runner = FakeRunner(listOf(0), outputBytes = 10)
    compressor(runner, setOf("libx264"), ffprobe = null).compress(source(1000), CompressorQuality.HIGH, false) {}
    val cmd = runner.commands.single(); assertEquals("2000000", cmd[cmd.indexOf("-b:v") + 1]); assertEquals("scale=trunc(iw/2)*2:trunc(ih/2)*2", cmd[cmd.indexOf("-vf") + 1])
}
@Test fun veryHighEncodesAsHigh() { val runner = FakeRunner(listOf(0), outputBytes = 10); compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.VERY_HIGH, false) {}; assertEquals("4000000", runner.commands.single().let { it[it.indexOf("-b:v") + 1] }) }
@Test fun nip95EstimateRefusesEarly() { val runner = FakeRunner(listOf(0), outputBytes = 10); assertEquals(VideoCompressionResult.TooBig, compressor(runner, setOf("libx264"), duration = 30.0).compress(source(1000), CompressorQuality.HIGH, false, maxBytes = 80_000) {}); assertTrue(runner.commands.isEmpty()) }
@Test fun progressIsForwarded() { val seen = mutableListOf<Double>(); compressor(FakeRunner(listOf(0), outputBytes = 10, progressLines = listOf("out_time_us=1000000", "out_time_us=N/A", "progress=end")), setOf("libx264"), duration = 2.0).compress(source(1000), CompressorQuality.HIGH, false) { seen += it }; assertEquals(listOf(0.5, 1.0), seen) }
@Test fun gifKeepsSizeAndIgnoresSizeRule() {
    val runner = FakeRunner(listOf(0), outputBytes = 5000)
    val r = compressor(runner, setOf("libx264")).convertGif(source(1000)) {}
    assertIs<VideoCompressionResult.Compressed>(r); val cmd = runner.commands.single(); assertEquals("scale=trunc(iw/2)*2:trunc(ih/2)*2", cmd[cmd.indexOf("-vf") + 1]); assertEquals("2000000", cmd[cmd.indexOf("-b:v") + 1])   // MEDIUM row for 1080p
}
@Test fun encodesRunOneAtATime() = runTest {
    val runner = FakeRunner(listOf(0, 0), outputBytes = 10, delayMs = 50, recordConcurrency = true)
    val c = compressor(runner, setOf("libx264"))
    listOf(async { c.compress(source(1000), CompressorQuality.HIGH, false) {} }, async { c.compress(source(1000), CompressorQuality.HIGH, false) {} }).awaitAll()
    assertEquals(1, runner.maxConcurrent)
}
@Test fun cancellationDeletesTemp() = runTest { val runner = FakeRunner(listOf(0), outputBytes = 10, delayMs = 10_000); val job = launch { compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {} }; advanceTimeBy(100); job.cancelAndJoin(); assertTrue(tempVideos().isEmpty()) }
@Test fun noFfmpegIsOriginalUnstripped() { assertEquals(VideoCompressionResult.Original(null), FfmpegVideoCompressor(locator = FfmpegLocator({ false }, { emptyList() }, { null })).compress(source(1), CompressorQuality.HIGH, false) {}) }
```

`compressor(runner, encoders, ffprobe = "ffprobe", duration = 30.0, hdr = false)` builds `FfmpegVideoCompressor(locator = FfmpegLocator({ true }, { emptyList() }, { null }), encoders = { encoders }, probe = { FakeProbe(ffprobe, duration, hdr) }, runner = runner, gate = Semaphore(1))`, where `FakeProbe` returns a 1920×1080 @ 30 fps `ProbeResult` unless `ffprobe` is null; `source(bytes)` writes a temp file of that size; `tempVideos()` lists `AmethystTempDir.rootDir()` entries starting with `amethyst_video_` (cleared in `@BeforeTest`/`@AfterTest`).

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** the four source files.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegVideoCompressorTest"` → PASS.
- [ ] **Step 5: Write the gated tests** in `FfmpegVideoCompressorFfmpegTest.kt`; every test starts with `if (!FfmpegLocator.default.available) return`. Fixtures made with the real binary into a temp dir:
  - `noisy.mp4`: `ffmpeg -f lavfi -i "color=c=gray:s=1920x1080:r=30,noise=alls=100:allf=t+u" -f lavfi -i sine -t 2 -shortest -pix_fmt yuv420p -c:v libx264 -crf 18 -preset ultrafast -metadata title=secret noisy.mp4` (high-entropy, tens of MB, so every encode is smaller — spec §5; `noise` is a filter and needs the `color` source in front).
  - `easy.mp4`: `ffmpeg -f lavfi -i testsrc=size=1920x1080:rate=30 -t 2 -pix_fmt yuv420p easy.mp4` (55 KB; every encode is larger — HIGH measured 181 KB).
  - `rotated.mp4`: `ffmpeg -display_rotation 90 -i easy.mp4 -c copy rotated.mp4` (ffprobe shows `side_data_list: [{rotation: 90}]`; `-metadata:s:v:0 rotate=90` writes nothing on ffmpeg 9).
  - Asserting with a small JSON read of `ffprobe -v error -select_streams V:0 -show_entries stream=width,height,codec_name,bit_rate:stream_side_data=rotation:format_tags -of json`:
    - `compress(noisy, HIGH, false)` → `Compressed`, 1920×1080, `bit_rate ≤ 4_000_000 * (1 + 2 / 2.0)` (the VBV bound with `bufsize = 2b` on a 2 s clip — a short clip legitimately bursts above the target), `format_tags` has no `title`.
    - `compress(noisy, LOW, false)` → short side 480 (854×480), and its `bit_rate` < the HIGH output's.
    - `compress(noisy, MEDIUM, true)` → `Compressed`; when `HevcUnavailable` is not in `notices`, `codec_name == hevc`.
    - `compress(easy, HIGH, false)` → `Original` with non-null `stripped`, `stripped.length() < easy.length() + 4096`, no `title` tag.
    - `compress(rotated, HIGH, false)` → width 1080, height 1920 and no `side_data_list` on the output stream.
- [ ] **Step 6: Run** with ffmpeg installed → PASS; `PATH=/usr/bin:/bin ./gradlew :commons:jvmTest --tests "*FfmpegVideoCompressor*"` → gated tests return early, unit tests PASS.
- [ ] **Step 7: Commit** — `feat(uploads): FfmpegVideoCompressor with encoder fallback, remux strip and stall watchdog`

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
- [ ] **Step 4: Run** `./gradlew :desktopApp:jvmTest --tests "*ForwardingNoticeSinkTest" :commonsUI:compileKotlinJvm` → PASS, compiles.
- [ ] **Step 5: Commit** — `feat(desktop): route typed upload notices to the toast manager`

---

### Task 8: `DesktopMediaUploader` compresses, strips and probes video

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/MediaUploader.kt:100-102` (add the two members before `displayName`) and `:126` (`UnavailableMediaUploader`: `videoCompression = Unsupported`)
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/VideoCompressionSupport.kt`
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/AndroidMediaUploader.kt:79` (`override val videoCompression = VideoCompressionSupport.Supported`)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopMediaUploader.kt` (constructor `:78-81`, `upload` `:129-157`, `uploadEncrypted` `:175-207`, `preparePayload` `:218-234`, `nip95` `:238-268`, `nip96` `:270-346`, `compressIfNeeded` `:351-358`, `stripMetadata` `:413-420`)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAccountViewModelHost.kt:77`
- Test: `desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopMediaUploaderVideoTest.kt`

**Interfaces:**
- Consumes: `VideoCompressor`, `VideoCompressionResult`, `FfmpegVideoCompressor`, `FfmpegProbe` (Tasks 4, 6), `ForwardingNoticeSink` (Task 7).
- Produces:
  - `enum class VideoCompressionSupport { Supported, NeedsFfmpeg, Unsupported }`.
  - `MediaUploader.videoCompression: VideoCompressionSupport get() = Unsupported` (KDoc: whether `upload`/`compressIfNeeded` transcode `video/*` below UNCOMPRESSED; the composer hides the video controls unless `Supported` and shows the install hint for `NeedsFfmpeg`) and `suspend fun probeVideo(uri: MediaUri): VideoInfo? = null`.
  - `class DesktopMediaUploader(clientFor, httpClients: IRoleBasedHttpClientBuilder, private val notices: UserNoticeSink = UserNoticeSink {}, private val videoCompressor: VideoCompressor = FfmpegVideoCompressor(), private val probe: FfmpegProbe = FfmpegProbe(FfmpegLocator.default.ffprobe))` — `httpClients` widens from `RoleBasedHttpClientBuilder` to the interface it implements (on the JVM `IRoleBasedHttpClientBuilder` is a typealias of `RoleBasedOkHttpClients`, eight `OkHttpClient`-returning functions) so a test can stub it; the host passes `modules.roleBasedHttpClientBuilder` and `notices`.
  - `override val videoCompression get() = if (videoCompressor.available) Supported else NeedsFfmpeg`; `override suspend fun probeVideo(uri) = probe.probe(fileOf(uri))?.info`.
  - `internal suspend fun <T> withVideoPrepared(progress: UploadOrchestrator, uri: MediaUri, mimeType: String?, quality: CompressorQuality, useH265: Boolean, stripMetadata: Boolean, convertGifToMp4: Boolean, progressCeiling: Double, maxBytes: Long? = null, block: suspend (file: File, prepared: Boolean) -> T): T?` — `mime = mimeType ?: mimeType(uri)`; `isVideo = mime starts with "video/"`, `isGif = mime == "image/gif"`. Decision, when `videoCompressor.available`:
    - `isGif && convertGifToMp4` → `convertGif`;
    - `isVideo && quality != UNCOMPRESSED` → `compress(source, quality, useH265, maxBytes)`;
    - `isVideo && quality == UNCOMPRESSED && stripMetadata` → `Original(strip(source))`;
    - otherwise → `block(fileOf(uri), false)`.
    Outcomes: `TooBig` → `null` (the caller reports `MEDIA_TOO_BIG_FOR_NIP95`); `Compressed(file)` → notices forwarded, `block(file, true)` with `file` deleted in `finally` under `NonCancellable`; `Original(stripped)` → notices forwarded, `block(stripped ?: fileOf(uri), stripped != null)` with `stripped` deleted likewise. Progress during the encode: `progress.updateState(0.02 + (progressCeiling - 0.02) * fraction, Compressing)`. Not available → `block(fileOf(uri), false)`.
  - Entry points: `upload`/`uploadEncrypted` wrap their Blossom call with `progressCeiling = 0.2`; `nip95` with `0.4` and `maxBytes = NIP95_MAX_BYTES`; `nip96` with `0.2`. Inside the block the pipeline receives `file`, `stripExif = stripMetadata && !prepared`, `quality = if (prepared) null else compressionQuality.toTarget()`. `nip95`/`nip96` gain `mimeType`, `useH265`, `convertGifToMp4` parameters forwarded from the callers; `preparePayload` takes the prepared `File` and a `prepared: Boolean` instead of the `MediaUri`.
  - `compressIfNeeded` override: same decision table but without the block; `Compressed(file)`/`Original(stripped != null)` → `MediaCompressorResult(StringMediaUri(file.absolutePath), "video/mp4", file.length())` (the caller's `discardTempFile` deletes it — it is under `AmethystTempDir`); `Original(null)`/not wanted → `MediaCompressorResult(uri, mimeType, null)`; `TooBig` cannot occur (no `maxBytes`).
  - `stripMetadata(uri, mimeType)` override: for `video/*` with ffmpeg available → `videoCompressor.strip(file)?.let { StringMediaUri(it.absolutePath) } ?: uri`; otherwise the existing JPEG path.

- [ ] **Step 1: Write the failing tests** with `FakeVideoCompressor(available, result: (File) -> VideoCompressionResult, onCompress: (File, CompressorQuality, Boolean) -> Unit, onGif: (File) -> Unit, progressEmits: List<Double>, stripResult: () -> File?)` (`compress` calls `onCompress`, emits each `progressEmits` value, returns `result(source)`; `convertGif` calls `onGif` then returns `result(source)`; `strip` returns `stripResult()`), `FakeProbe` (1920×1080 @ 30 fps), a `RecordingSink`, and `StubHttpClients` (an `object : IRoleBasedHttpClientBuilder` whose eight functions return `OkHttpClient()`; `proxyPortForVideo` returns null):

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
@Test fun withVideoPreparedDeletesTempEvenWhenBlockThrows() = runTest {
    val out = tempVideo(10)
    assertFailsWith<IllegalStateException> { uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { _, _ -> error("upload failed") } }
    assertFalse(out.exists())
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
- [ ] **Step 4: Run** `./gradlew :desktopApp:jvmTest --tests "*DesktopMediaUploaderVideoTest" :amethyst:compileDebugKotlin :commons:compileKotlinIosArm64` (if the iOS target is configured on this machine; otherwise skip) → PASS, compiles.
- [ ] **Step 5: Commit** — `feat(desktop): compress, strip and probe videos through ffmpeg in every upload path`

---

### Task 9: Slider steps and label become video-aware (`commons` + `commonsUI`)

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/CompressorQuality.kt` (companion)
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/MultiOrchestrator.kt:54-60`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/creators/uploads/MediaQualitySlider.kt:80-133`
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml:2125` (replace `media_compression_image_max_size`)
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
- Strings: delete `media_compression_image_max_size`; add `media_compression_images_up_to` = `images up to %1$d px`, `media_compression_video_target` = `%1$s video → %2$s` (e.g. `4K video → 4K, 8 Mbps`), `media_compression_video_target_example` = `1080p video → %1$s` (used when the file could not be probed).
- `MediaQualitySlider`: `hasVideo = media?.hasVideo() == true`; `steps = CompressorQuality.stepsFor(hasVideo)`; `Slider(value = toDisplayedIndex(position, hasVideo), valueRange 0..steps.lastIndex, steps = steps.size - 2, onValueChange = { onPositionChange(fromDisplayedIndex(it.toInt(), hasVideo)) })`; `LaunchedEffect(hasVideo) { if (hasVideo) snapForVideo(position).let { if (it != position) onPositionChange(it) } }`. The video phrase: `val info by produceState<VideoInfo?>(null, media?.firstVideo()) { value = media?.firstVideo()?.let { uploader.probeVideo(it.uri) } }`; with `info` → `ruleFor(quality, info.resolution.getStandard())` worded as `media_compression_video_target(standard.label, "<shortSide>p, <bitrate> Mbps")` (no trailing `.0`: `2 Mbps`, `0.75 Mbps`; `shortSide ≥ 2160` is shown as `4K`); without → `media_compression_video_target_example` on the 1080p row. Label = `listOfNotNull(name, imagePart, videoPart).joinToString(" · ")`, `imagePart` only when `media?.hasCompressibleImage() == true && quality.imageMaxDimension != null`, `videoPart` only when `hasVideo && quality != UNCOMPRESSED`.

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
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/actions/uploads/SelectFromGallery.kt:96,105`, `…/chats/privateDM/send/NewGroupDMScreen.kt:429`, `…/longs/NewLongVideoButton.kt:162`, `…/shorts/NewShortVideoButton.kt:176`, `…/video/NewVideoFeedButton.kt:208`
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml` (after `upload_image`)

**Interfaces:**
- Consumes: `videoCompression` (Task 8), `hasCompressibleImage` (Task 9).
- Strings: `upload_media` = `Upload Media`; `video_compression_needs_ffmpeg` = `Install ffmpeg to compress videos. Until then videos are uploaded as they are.`
- In each dialog: `val support = accountViewModel.host.mediaUploader.videoCompression`; `val canCompressVideo = support == VideoCompressionSupport.Supported`; `val hasVideo = …hasVideo()`;
  `isVideoWithCompression = hasVideo && canCompressVideo && slider != uncompressedSliderPosition`;
  slider block condition `(hasCompressibleImage() || (hasVideo && canCompressVideo)) && !convertGifToMp4` (the chat dialog has no GIF switch: drop that term there);
  H.265 switch condition `isVideoWithCompression` (add the switch to the chat dialog if its state holder already carries `useH265`; otherwise leave it out there — `ChatFileUploadState` has no such field today, so leave it out);
  GIF switch condition `hasGif() && canCompressVideo`;
  hint `Text(stringRes(Res.string.video_compression_needs_ffmpeg), style = bodySmall, color = Color.Gray)` when `hasVideo && support == VideoCompressionSupport.NeedsFfmpeg`, where the H.265 switch would be.
- The five attach buttons: `Res.string.upload_media`. `NewPictureButton` and `ProfileHeader` keep `upload_image`.

- [ ] **Step 1: Apply the changes** (composition is covered by the manual test; the logic they consume is unit-tested in Tasks 8 and 9).
- [ ] **Step 2: Compile both front ends** — `./gradlew :commonsUI:compileKotlinJvm :amethyst:compileDebugKotlin` → success.
- [ ] **Step 3: Run the desktop app** `./gradlew :desktopApp:run` with ffmpeg on `$PATH`: picking a video shows 4 slider stops, a label naming the file's real resolution, the H.265 switch, and the metadata toggle hides below UNCOMPRESSED; photo + video together snaps VERY_HIGH → HIGH. Then `PATH=/usr/bin:/bin ./gradlew :desktopApp:run` **with Homebrew's directory also hidden** (`mv /opt/homebrew/bin/ffmpeg{,.off}` for the test, restore after): the hint shows, no H.265/GIF switch, the metadata toggle stays, images still get the 5-stop slider; the chat file dialog behaves the same. The attach-button tooltip reads "Upload Media".
- [ ] **Step 4: Commit** — `fix(composer): hide video compression controls the platform cannot honour`

---

### Task 11: End-to-end verification and plan bookkeeping

**Files:**
- Modify: `desktopApp/plans/README.md` (add "phase 1 built" to the summary; keep "In progress" until merged)
- Create: `desktopApp/plans/2026-10-11-desktop-video-compression-manual-testing.md` (follow the format of `2026-08-20-live-media-manual-testing.md`)

- [ ] **Step 1: Full test run** — `./gradlew test` → BUILD SUCCESSFUL; `./gradlew :amethyst:compileDebugKotlin`.
- [ ] **Step 2: The hash check** from the investigation: upload `~/Downloads/file_example_MOV_1920_2_2MB.mov` from the desktop app at HIGH to blossom.band; `curl` the blob; `shasum -a 256` must differ from `ead8b50c…e40c62`; `ffprobe` must report 1920×1080, `h264`, video `bit_rate ≤ 4 400 000`, no `format_tags`. Repeat at LOW → 480p, and with H.265 → `hevc` (or the H.264 toast). Upload once at UNCOMPRESSED with strip on → hash differs from the source (remuxed), `ffprobe` shows the source's codec and no tags.
- [ ] **Step 3: Package and launch from Finder** — `./gradlew :desktopApp:packageDmg`, install, launch from Finder with Homebrew ffmpeg present → the video controls appear (Review Focus 5).
- [ ] **Step 4: Write the manual-testing sheet** covering: with/without ffmpeg; the Finder launch; each stop; H.265 with and without an HEVC encoder; GIF→MP4; a portrait phone video; an HDR iPhone clip (toast shown, upload proceeds); three videos in one post (`pgrep ffmpeg` shows one at a time); quit mid-encode (`pgrep ffmpeg` empty afterwards, `AmethystTempDir.rootDir()` has no `amethyst_video_*`); a corrupt video (uploads original + toast); NIP-95 with a long clip refused before encoding; the chat file dialog with and without ffmpeg.
- [ ] **Step 5: Commit** — `docs(desktop): manual test sheet for desktop video compression`

Hand the branch and the sheet to the human; do not open a PR.
