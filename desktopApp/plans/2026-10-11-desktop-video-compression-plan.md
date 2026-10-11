# Desktop Video Compression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the composer's media-quality slider, H.265 switch and GIF→MP4 switch real on the JVM desktop by transcoding with a system `ffmpeg`, using Android's resolution/bitrate table; hide those controls when no `ffmpeg` is installed.

**Architecture:** The rules table moves from `amethyst` to `commons/commonMain` so both platforms read one source. A new `commons/jvmMain` package `service/upload/video/` runs `ffmpeg`/`ffprobe` as subprocesses (locator, encoder preference, probe, command builder, progress parser, runner). `DesktopMediaUploader` feeds every upload entry point through the compressor; `commonsUI` adapts the shared dialog (video-aware slider stops and label, hidden controls without ffmpeg, "Upload Media").

**Tech Stack:** Kotlin Multiplatform, `java.lang.ProcessBuilder`, Jackson (already on the commons JVM classpath through quartz) for `ffprobe` JSON, kotlinx-coroutines, `kotlin.test` + `runTest` for tests, Compose Multiplatform for the dialog.

**Spec:** `desktopApp/plans/2026-10-11-desktop-video-compression.md`

## Global Constraints

- No new third-party dependency. `ffmpeg` is an external process found on `$PATH` or at `${compose.application.resources.dir}/ffmpeg/ffmpeg(.exe)`; never a working-directory-relative path.
- The LOW/MEDIUM/HIGH table, ×1.3 at ≥ 60 fps, ×0.75 for H.265 and the UNKNOWN fallback (2 Mbps, no resize) are copied **verbatim** from `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt`.
- Encoder preference, exact order — H.264: `libx264, h264_videotoolbox, h264_nvenc, h264_qsv, h264_amf, h264_vaapi, h264_mf, libopenh264`; H.265: `libx265, hevc_videotoolbox, hevc_nvenc, hevc_qsv, hevc_amf, hevc_vaapi, hevc_mf`.
- Output is always `video/mp4`, `-movflags +faststart`, `-pix_fmt yuv420p`, `-c:a aac -b:a 128k`, metadata stripped (`-map_metadata -1 -map_metadata:s -1`), `-tag:v hvc1` for HEVC.
- Compression progress maps to `UploadingState.Compressing` between 0.02 and 0.20. No overall timeout; a stall (no progress line for 60 s) kills the process.
- Output ≥ original size → upload the original. Any failure after `ffmpeg` was found → upload the original and notify.
- Every new user-visible string goes in `commonsUI/src/commonMain/composeResources/values/strings.xml`, positional format args only, no Android-style escaping.
- Use `com.vitorpamplona.quartz.utils.Log` with the lambda form; never fully-qualified names inline; temp files only via `AmethystTempDir.createTempFile("amethyst_…", …)`; delete temps with `File.deleteOrWarn(tag, what)`.
- Run `./gradlew spotlessApply` before every commit. Conventional commit messages. Never `--no-verify`.

## Review Focus

Inputs the spec implies that a user will hit; each has a test in the owning task:

1. A portrait phone video (height > width): the short side must be the width, never upscaled, and the output dimensions even — Task 5 `scaleFilter` tests.
2. A 59.94 fps clip (`r_frame_rate = 60000/1001`): must *not* get the ×1.3 60-fps multiplier (59.94 < 60) — Task 4 parser test + Task 1 multiplier test.
3. `ffmpeg` present but `ffprobe` missing: must fall back to the UNKNOWN rule, not fail — Task 6 test with `ffprobe = null`.
4. A hardware encoder listed by `-encoders` that fails at run time: the next candidate must be tried, and the first temp output deleted — Task 6 candidate-retry test.
5. Adding a video to a selection while the slider sits on VERY_HIGH: the position must snap to HIGH and the H.265 switch must appear — Task 9 `snapForVideo` test.

---

### Task 1: Move the rules table to `commons`

**Files:**
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRules.kt`
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/CompressorQuality.kt:28-37`
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt:46-144` (delete the moved types and the table; import them; keep `getVideoInfo`, `compressVideo`, the toasts)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRulesTest.kt`

**Interfaces:**
- Produces (package `com.vitorpamplona.amethyst.commons.service.uploads.video`):
  - `enum class VideoStandard(val label: String)` — `UHD_4K, QHD_1440P, FHD_1080P, HD_720P, SD_480P, NHD_360P, QVGA_240P, UNKNOWN`, `toString() = label`.
  - `data class VideoResolution(val width: Int, val height: Int) { fun getStandard(): VideoStandard }` — short-side thresholds 2160/1440/1080/720/480/360/240.
  - `data class VideoInfo(val resolution: VideoResolution, val framerate: Float)`.
  - `data class CompressionRule(val shortSide: Int, val bitrateMbps: Float, val description: String) { fun getBitrateBps(framerate: Float, useH265: Boolean): Int }`.
  - `object VideoCompressionRules { fun ruleFor(quality: CompressorQuality, standard: VideoStandard): CompressionRule?; const val FALLBACK_BITRATE_BPS = 2_000_000 }` — null for a level with no video rules.
  - `CompressorQuality.hasVideoRule: Boolean` — third enum constructor parameter: `VERY_LOW false, LOW true, MEDIUM true, HIGH true, VERY_HIGH false, UNCOMPRESSED true`.

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
    }
    @Test fun everyRuledLevelCoversEveryStandard() {
        for (q in listOf(CompressorQuality.LOW, CompressorQuality.MEDIUM, CompressorQuality.HIGH))
            for (s in VideoStandard.entries) assertNotNull(VideoCompressionRules.ruleFor(q, s), "$q/$s")
    }
    @Test fun levelsWithoutRulesReturnNull() {
        for (q in listOf(CompressorQuality.VERY_LOW, CompressorQuality.VERY_HIGH, CompressorQuality.UNCOMPRESSED))
            assertNull(VideoCompressionRules.ruleFor(q, VideoStandard.FHD_1080P))
    }
    @Test fun hasVideoRuleAgreesWithTable() {
        for (q in CompressorQuality.entries)
            assertEquals(q == CompressorQuality.UNCOMPRESSED || VideoCompressionRules.ruleFor(q, VideoStandard.FHD_1080P) != null, q.hasVideoRule, q.name)
    }
    @Test fun bitrateMultipliers() {
        val rule = CompressionRule(1080, 4f, "")
        assertEquals(4_000_000, rule.getBitrateBps(30f, useH265 = false))
        assertEquals(5_200_000, rule.getBitrateBps(60f, useH265 = false))
        assertEquals(3_000_000, rule.getBitrateBps(30f, useH265 = true))
        assertEquals(3_900_000, rule.getBitrateBps(60f, useH265 = true))
        assertEquals(4_000_000, rule.getBitrateBps(59.94f, useH265 = false))   // Review Focus 2
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.uploads.video.*"`
Expected: compilation failure, unresolved `VideoCompressionRules` / `hasVideoRule`.

- [ ] **Step 3: Create `VideoCompressionRules.kt`** with the types in Interfaces. The table body is the three `mapOf(...)` blocks copied from `VideoCompressionHelper.kt:109-144`; `ruleFor` is `rules[quality]?.get(standard)`. Add `hasVideoRule` to `CompressorQuality`.

- [ ] **Step 4: Point Android at it.** In `VideoCompressionHelper.kt` delete `VideoInfo`, `VideoResolution`, `VideoStandard`, `MBPS_TO_BPS_MULTIPLIER`, `CompressionRule` and `compressionRules`; import the commons types; replace `compressionRules.getValue(mediaQuality).getValue(info.resolution.getStandard())` with `VideoCompressionRules.ruleFor(mediaQuality, info.resolution.getStandard())`, and when it is null take the existing `?: run { … 2 Mbps, null resizer }` branch (restructure the `videoInfo?.let` so a null rule reaches that branch).

- [ ] **Step 5: Run tests and the Android compile**

Run: `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.uploads.video.*" :amethyst:compileDebugKotlin`
Expected: tests PASS, Android compiles.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/VideoCompressionHelper.kt
git commit -m "refactor(uploads): share the video compression rules through commons"
```

---

### Task 2: `FfmpegLocator`

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegLocator.kt`
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/VideoThumbnailCache.kt:112-138` (replace the private `ffmpegBinary` lazy with `FfmpegLocator.default.ffmpeg`)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegLocatorTest.kt`

**Interfaces:**
- Produces: `class FfmpegLocator(private val runsOnPath: (binary: String) -> Boolean = ::probeOnPath, private val resourcesDir: () -> File? = { System.getProperty("compose.application.resources.dir")?.let(::File) }) { val ffmpeg: String? by lazy; val ffprobe: String? by lazy; val available: Boolean get() = ffmpeg != null; companion object { val default = FfmpegLocator() } }`. Resolution of a name `b`: `runsOnPath(b)` → `b`; else `resourcesDir()/ffmpeg/b[.exe on Windows]` if it exists and is executable; else null. `probeOnPath` runs `b -version` with stdout/stderr discarded, 2 s wait, `destroyForcibly` on overrun, true iff exit 0 — moved verbatim from `VideoThumbnailCache`.

- [ ] **Step 1: Write the failing tests** (`@TempDir`-style via `Files.createTempDirectory`; clean up in `@AfterTest`)

```kotlin
@Test fun prefersPath() { assertEquals("ffmpeg", FfmpegLocator(runsOnPath = { true }, resourcesDir = { null }).ffmpeg) }
@Test fun fallsBackToBundledExecutable() {
    val bin = File(dir, "ffmpeg/ffmpeg").apply { parentFile.mkdirs(); writeText("#!/bin/sh\n"); setExecutable(true) }
    assertEquals(bin.absolutePath, FfmpegLocator(runsOnPath = { false }, resourcesDir = { dir }).ffmpeg)
}
@Test fun bundledFileMustBeExecutable() {
    File(dir, "ffmpeg/ffprobe").apply { parentFile.mkdirs(); writeText("") ; setExecutable(false) }
    assertNull(FfmpegLocator(runsOnPath = { false }, resourcesDir = { dir }).ffprobe)
}
@Test fun unavailableWhenNeitherExists() { assertFalse(FfmpegLocator(runsOnPath = { false }, resourcesDir = { null }).available) }
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :commons:jvmTest --tests "*FfmpegLocatorTest"` → unresolved `FfmpegLocator`.
- [ ] **Step 3: Implement `FfmpegLocator.kt`** as specified; `ffprobe` resolves by the same two rules.
- [ ] **Step 4: Switch `VideoThumbnailCache`** to `FfmpegLocator.default.ffmpeg` in `runFfmpeg`; delete its `ffmpegBinary` lazy and the now-unused imports.
- [ ] **Step 5: Run** `./gradlew :commons:jvmTest --tests "*FfmpegLocatorTest" :desktopApp:compileKotlinJvm` → PASS, compiles.
- [ ] **Step 6: Commit** — `refactor(desktop): extract FfmpegLocator from VideoThumbnailCache`

---

### Task 3: `FfmpegEncoders` — parse `-encoders`, pick by preference

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegEncoders.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegEncodersTest.kt`

**Interfaces:**
- Produces: `object FfmpegEncoders { val H264: List<String>; val H265: List<String>; fun parseAvailable(encodersOutput: String): Set<String>; fun candidates(available: Set<String>, useH265: Boolean): List<String>; fun isHevc(encoder: String): Boolean = encoder in H265; suspend fun listAvailable(ffmpeg: String): Set<String> }`. `candidates`: `useH265` → `H265.filter { it in available }`, and if that is empty, `H264.filter { it in available }`; otherwise the H.264 list. `listAvailable` runs `ffmpeg -hide_banner -encoders` (stderr discarded, 5 s cap) and parses stdout.

- [ ] **Step 1: Write the failing tests** with this canned output (real format: six flag characters, name, description):

```kotlin
private val GPL = """
Encoders:
 V..... = Video
 ------
 V....D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)
 V....D h264_videotoolbox    VideoToolbox H.264 Encoder (codec h264)
 V....D libx265              libx265 H.265 / HEVC (codec hevc)
 A....D aac                  AAC (Advanced Audio Coding)
""".trimIndent()
private val LGPL = GPL.lines().filterNot { "libx26" in it }.joinToString("\n") + "\n V....D libopenh264          OpenH264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)"

@Test fun parsesNamesOnly() { assertEquals(setOf("libx264", "h264_videotoolbox", "libx265", "aac"), FfmpegEncoders.parseAvailable(GPL)) }
@Test fun gplBuildPrefersX264() { assertEquals(listOf("libx264", "h264_videotoolbox"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(GPL), useH265 = false)) }
@Test fun lgplBuildFallsBackThroughHardwareToOpenH264() { assertEquals(listOf("h264_videotoolbox", "libopenh264"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(LGPL), useH265 = false)) }
@Test fun hevcRequestedWithoutHevcEncoderFallsBackToH264() { assertEquals(listOf("h264_videotoolbox", "libopenh264"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(LGPL), useH265 = true)) }
@Test fun hevcRequestedWithX265() { assertEquals(listOf("libx265"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(GPL), useH265 = true)) }
```

- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement.** Parse lines with `Regex("""^\s*[VAS][.F][.S][.X][.B][.D]\s+(\S+)""")`; group 1 is the name.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegEncodersTest"` → PASS.
- [ ] **Step 5: Commit** — `feat(uploads): encoder preference list for ffmpeg video compression`

---

### Task 4: `FfmpegProbe` — `ffprobe` JSON → `VideoInfo` + duration

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegProbe.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfprobeOutputParserTest.kt`

**Interfaces:**
- Consumes: `VideoInfo`, `VideoResolution` (Task 1).
- Produces: `data class ProbeResult(val info: VideoInfo, val durationSeconds: Double?)`; `object FfprobeOutputParser { fun parse(json: String): ProbeResult? }`; `open class FfmpegProbe(private val ffprobe: String?) { open suspend fun probe(file: File): ProbeResult? }` (open so Task 6's test can fake it) running `ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate -show_entries format=duration -of json <file>` (10 s cap), null when `ffprobe` is null, the process fails, or parsing fails.

- [ ] **Step 1: Write the failing tests**

```kotlin
private const val JSON = """{"streams":[{"width":1920,"height":1080,"r_frame_rate":"60000/1001"}],"format":{"duration":"30.528000"}}"""
@Test fun parsesDimensionsFpsAndDuration() {
    val r = FfprobeOutputParser.parse(JSON)!!
    assertEquals(VideoResolution(1920, 1080), r.info.resolution)
    assertEquals(59.94f, r.info.framerate, 0.01f)
    assertEquals(30.528, r.durationSeconds!!, 0.001)
}
@Test fun missingDurationIsNull() { assertNull(FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"r_frame_rate":"30/1"}],"format":{}}""")!!.durationSeconds) }
@Test fun noVideoStreamIsNull() { assertNull(FfprobeOutputParser.parse("""{"streams":[],"format":{"duration":"1"}}""")) }
@Test fun garbageIsNull() { assertNull(FfprobeOutputParser.parse("not json")) }
@Test fun zeroDenominatorFpsIsZero() { assertEquals(0f, FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"r_frame_rate":"0/0"}],"format":{}}""")!!.info.framerate) }
```

- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement** with Jackson's `ObjectMapper().readTree(json)`; `r_frame_rate` is `num/den` → `num.toFloat() / den` (0 when den is 0).
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
  - `object FfmpegCommandBuilder { fun scaleFilter(shortSide: Int?): String; fun build(ffmpeg: String, input: File, output: File, encoder: String, bitrateBps: Int, shortSide: Int?): List<String> }`.
    `scaleFilter(S)` = `scale='if(gt(iw,ih),-2,trunc(min(S,iw)/2)*2)':'if(gt(iw,ih),trunc(min(S,ih)/2)*2,-2)'`; `scaleFilter(null)` = `scale=trunc(iw/2)*2:trunc(ih/2)*2`.
    `build` = `[ffmpeg, -hide_banner, -loglevel, error, -nostdin, -y, -i, input, -c:v, encoder, -b:v, N, -maxrate, N, -bufsize, 2N, -pix_fmt, yuv420p, -vf, scaleFilter(shortSide), -c:a, aac, -b:a, 128k, -map_metadata, -1, -map_metadata:s, -1, -movflags, +faststart, (-tag:v, hvc1 when FfmpegEncoders.isHevc(encoder)), -progress, pipe:1, output]`.
  - `class FfmpegProgressParser(private val durationSeconds: Double?) { fun onLine(line: String): Double? }` — `out_time_us=<n>` → `(n / 1e6 / duration).coerceIn(0.0, 1.0)` (null when duration is null or ≤ 0); `progress=end` → `1.0`; anything else → null.

- [ ] **Step 1: Write the failing tests**

```kotlin
// FfmpegCommandBuilderTest
@Test fun portraitLimitsWidthAndKeepsEven() {   // Review Focus 1: the expression's width branch is the short side
    assertEquals("scale='if(gt(iw,ih),-2,trunc(min(720,iw)/2)*2)':'if(gt(iw,ih),trunc(min(720,ih)/2)*2,-2)'", FfmpegCommandBuilder.scaleFilter(720))
}
@Test fun unknownRuleOnlyEvensDimensions() { assertEquals("scale=trunc(iw/2)*2:trunc(ih/2)*2", FfmpegCommandBuilder.scaleFilter(null)) }
@Test fun h264CommandHasNoHvc1Tag() {
    val cmd = FfmpegCommandBuilder.build("ffmpeg", File("in.mov"), File("out.mp4"), "libx264", 4_000_000, 1080)
    assertEquals(listOf("-c:v", "libx264", "-b:v", "4000000", "-maxrate", "4000000", "-bufsize", "8000000"), cmd.subList(cmd.indexOf("-c:v"), cmd.indexOf("-c:v") + 8))
    assertFalse("-tag:v" in cmd); assertTrue(cmd.containsAll(listOf("-movflags", "+faststart", "-map_metadata", "-progress", "pipe:1"))); assertEquals("out.mp4", cmd.last())
}
@Test fun hevcCommandTagsHvc1() { val cmd = FfmpegCommandBuilder.build("ffmpeg", File("a"), File("b"), "libx265", 1, null); assertEquals("hvc1", cmd[cmd.indexOf("-tag:v") + 1]) }

// FfmpegProgressParserTest
@Test fun fractionOfDuration() { assertEquals(0.5, FfmpegProgressParser(10.0).onLine("out_time_us=5000000")!!, 1e-9) }
@Test fun clampsOvershoot() { assertEquals(1.0, FfmpegProgressParser(10.0).onLine("out_time_us=99000000")) }
@Test fun endIsOne() { assertEquals(1.0, FfmpegProgressParser(null).onLine("progress=end")) }
@Test fun otherKeysAndUnknownDurationAreNull() { assertNull(FfmpegProgressParser(10.0).onLine("frame=12")); assertNull(FfmpegProgressParser(null).onLine("out_time_us=5")) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement both files.** — [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*Ffmpeg*"` → PASS.
- [ ] **Step 5: Commit** — `feat(uploads): ffmpeg command builder and progress parser`

---

### Task 6: `FfmpegVideoCompressor` — the runner

**Files:**
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/VideoCompressor.kt` (interface + result)
- Create: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegVideoCompressor.kt`
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/notices/UploadNotices.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/upload/video/FfmpegVideoCompressorTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5; `AmethystTempDir.createTempFile`, `File.deleteOrWarn`, `UserNotice`.
- Produces:
  - `sealed interface UploadNotice : UserNotice { data class VideoCompressionFailed(val reason: String?) : UploadNotice; data object HevcUnavailable : UploadNotice }` (commonMain).
  - `sealed interface VideoCompressionResult { data class Compressed(val file: File, val notice: UserNotice? = null) : VideoCompressionResult; data class Original(val notice: UserNotice? = null) : VideoCompressionResult }`.
  - `interface VideoCompressor { val available: Boolean; suspend fun compress(source: File, quality: CompressorQuality, useH265: Boolean, onProgress: (Double) -> Unit): VideoCompressionResult }`.
  - `class FfmpegVideoCompressor(private val locator: FfmpegLocator = FfmpegLocator.default, private val encoders: suspend (ffmpeg: String) -> Set<String> = FfmpegEncoders::listAvailable, private val probe: (ffprobe: String?) -> FfmpegProbe = ::FfmpegProbe, private val stallTimeoutMs: Long = 60_000, private val runner: ProcessRunner = ProcessRunner.Real) : VideoCompressor`.
  - `fun interface ProcessRunner { suspend fun run(command: List<String>, onStdoutLine: (String) -> Unit, stallTimeoutMs: Long): ProcessOutcome }` with `data class ProcessOutcome(val exitCode: Int, val stderrTail: String)`; `ProcessRunner.Real` uses `ProcessBuilder`, reads stdout line by line, keeps the last 2 KB of stderr, kills the process when no stdout line arrives for `stallTimeoutMs` (exit code `-1`), and on coroutine cancellation `destroyForcibly()`s it.

Algorithm of `compress` (the signature and tests do not determine it):
1. `ffmpeg = locator.ffmpeg ?: return Original()`.
2. `probed = probe(locator.ffprobe).probe(source)`; `rule = probed?.let { VideoCompressionRules.ruleFor(quality, it.info.resolution.getStandard()) }`; `shortSide = rule?.shortSide`; `fps = probed?.info?.framerate ?: 30f`.
3. `candidates = FfmpegEncoders.candidates(encoders(ffmpeg), useH265)`; empty → `Original(VideoCompressionFailed("no encoder"))`. `hevc = FfmpegEncoders.isHevc(candidates.first())`; `notice = if (useH265 && !hevc) UploadNotice.HevcUnavailable else null`.
4. `bitrate = rule?.getBitrateBps(fps, hevc) ?: VideoCompressionRules.FALLBACK_BITRATE_BPS`.
5. For each candidate: `out = AmethystTempDir.createTempFile("amethyst_video_", ".mp4")`; run `FfmpegCommandBuilder.build(...)` through `runner` with `FfmpegProgressParser(probed?.durationSeconds)` feeding `onProgress`; exit 0 → stop; else `out.deleteOrWarn(TAG, "failed encode")`, remember `stderrTail`, continue. Wrap in `try/finally` so cancellation deletes `out` under `withContext(NonCancellable)`.
6. No success → `Original(VideoCompressionFailed(lastStderrTail))`.
7. `out.length() >= source.length()` → delete `out`, `Original()`.
8. `Compressed(out, notice)`.

- [ ] **Step 1: Write the failing unit tests** with a fake `ProcessRunner` that records commands, writes `out_time_us=` lines, creates the output file at the path given, and returns a scripted exit code per call:

```kotlin
@Test fun retriesNextEncoderWhenFirstFails() {          // Review Focus 4
    val runner = FakeRunner(exitCodes = listOf(1, 0), outputBytes = 10)
    val result = compressor(runner, encoders = setOf("h264_videotoolbox", "libopenh264")).compress(source(bytes = 1000), CompressorQuality.HIGH, false) {}
    assertIs<VideoCompressionResult.Compressed>(result)
    assertEquals(listOf("h264_videotoolbox", "libopenh264"), runner.commands.map { it[it.indexOf("-c:v") + 1] })
    assertEquals(1, AmethystTempDir.rootDir().listFiles { f -> f.name.startsWith("amethyst_video_") }!!.size)   // the failed temp is gone
}
@Test fun allEncodersFailingUploadsOriginalWithNotice() {
    val r = compressor(FakeRunner(exitCodes = listOf(1), stderr = "boom"), encoders = setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
    assertEquals(VideoCompressionResult.Original(UploadNotice.VideoCompressionFailed("boom")), r)
}
@Test fun largerOutputIsDiscarded() { assertEquals(VideoCompressionResult.Original(), compressor(FakeRunner(listOf(0), outputBytes = 5000), setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}) }
@Test fun hevcUnavailableFallsBackWithNotice() {
    val r = compressor(FakeRunner(listOf(0), outputBytes = 10), setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, useH265 = true) {}
    assertEquals(UploadNotice.HevcUnavailable, (r as VideoCompressionResult.Compressed).notice)
}
@Test fun missingFfprobeUsesFallbackRule() {           // Review Focus 3
    val runner = FakeRunner(listOf(0), outputBytes = 10)
    compressor(runner, setOf("libx264"), ffprobe = null).compress(source(1000), CompressorQuality.HIGH, false) {}
    val cmd = runner.commands.single(); assertEquals("2000000", cmd[cmd.indexOf("-b:v") + 1]); assertEquals("scale=trunc(iw/2)*2:trunc(ih/2)*2", cmd[cmd.indexOf("-vf") + 1])
}
@Test fun progressIsForwarded() { val seen = mutableListOf<Double>(); compressor(FakeRunner(listOf(0), outputBytes = 10, progressLines = listOf("out_time_us=1000000", "progress=end")), setOf("libx264"), duration = 2.0).compress(source(1000), CompressorQuality.HIGH, false) { seen += it }; assertEquals(listOf(0.5, 1.0), seen) }
@Test fun noFfmpegIsOriginal() { assertEquals(VideoCompressionResult.Original(), FfmpegVideoCompressor(locator = FfmpegLocator({ false }, { null })).compress(source(1), CompressorQuality.HIGH, false) {}) }
```

`compressor(runner, encoders, ffprobe = "ffprobe", duration = 30.0)` builds `FfmpegVideoCompressor(locator = FfmpegLocator({ true }, { null }), encoders = { encoders }, probe = { FakeProbe(ffprobe, duration) }, runner = runner)` where `FakeProbe` returns a 1920×1080 @ 30 fps `ProbeResult` unless `ffprobe` is null.

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** `UploadNotices.kt`, `VideoCompressor.kt`, `FfmpegVideoCompressor.kt` (+ `ProcessRunner.Real`).
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*FfmpegVideoCompressorTest"` → PASS.
- [ ] **Step 5: Add the gated integration test** in the same file, each test starting with `assumeTrue(FfmpegLocator.default.available)` (kotlin.test has no `assume`: use `if (!FfmpegLocator.default.available) return`):
  - generate `ffmpeg -f lavfi -i testsrc=size=1920x1080:rate=30 -f lavfi -i sine -t 2 -metadata title=secret -pix_fmt yuv420p src.mp4`;
  - `compress(src, HIGH, false)` → `Compressed`; `FfmpegProbe` on the output reports 1920×1080; `ffprobe -show_entries format_tags` output does not contain `secret`;
  - `compress(src, LOW, false)` → output short side 480;
  - `compress(src, MEDIUM, true)` → `Compressed`, and if `notice == null` the output's codec (`-show_entries stream=codec_name`) is `hevc`.
- [ ] **Step 6: Run** the full file with ffmpeg installed → PASS; `PATH=/usr/bin ./gradlew …` (no ffmpeg) → the gated tests return early, the rest PASS.
- [ ] **Step 7: Commit** — `feat(uploads): FfmpegVideoCompressor with encoder fallback and stall watchdog`

---

### Task 7: Notice plumbing and wording

**Files:**
- Create: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/ForwardingNoticeSink.kt`
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAccountViewModelHost.kt:77`
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAppRoot.kt:70-79`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/notices/ui/UserNoticeText.kt` (add the `UploadNotice` branches beside `NoteActionNotice`)
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml` (after `media_compression_preview_tap_compressed`)
- Test: `desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/app/ForwardingNoticeSinkTest.kt`

**Interfaces:**
- Produces: `class ForwardingNoticeSink : UserNoticeSink { @Volatile var target: UserNoticeSink? ; override fun notify(notice) { target?.notify(notice) ?: Log.w(...) } }`; `DesktopAccountViewModelHost.notices: ForwardingNoticeSink`; `DesktopAppRoot.createAccountViewModel` sets `host.notices.target = viewModel.toastManager` on the `AccountViewModel` it creates (`ToastManager` is a `UserNoticeSink`).
- Strings (exact copy):
  - `video_compression_failed_title` = `Video compression failed`
  - `video_compression_failed_message` = `The original file is being uploaded instead.`
  - `video_compression_hevc_unavailable_title` = `H.265 not available`
  - `video_compression_hevc_unavailable_message` = `ffmpeg has no HEVC encoder on this computer, so the video was encoded as H.264.`
- Wording in `UserNoticeText.kt`: `UploadNotice.VideoCompressionFailed` → title/message above, `reason` as the details line the way `SignerNotice.Failed` passes its cause; `UploadNotice.HevcUnavailable` → title/message.

- [ ] **Step 1: Write the failing test** — `ForwardingNoticeSinkTest`: `notify` before a target is set does not throw; after `target = recording sink`, the notice arrives.
- [ ] **Step 2: Run to verify it fails.** — [ ] **Step 3: Implement** the sink, the host property, the root wiring, the strings, the wording.
- [ ] **Step 4: Run** `./gradlew :desktopApp:jvmTest --tests "*ForwardingNoticeSinkTest" :commonsUI:compileKotlinJvm` → PASS, compiles.
- [ ] **Step 5: Commit** — `feat(desktop): route typed upload notices to the toast manager`

---

### Task 8: `DesktopMediaUploader` compresses video

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/MediaUploader.kt:100-102` (add the property before `displayName`)
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/AndroidMediaUploader.kt:79` (`override val supportsVideoCompression = true`)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopMediaUploader.kt` (constructor, `upload` 129-157, `uploadEncrypted` 175-207, `nip95` 238-268, `nip96` 270-346, `compressIfNeeded` 351-358)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopAccountViewModelHost.kt:77`
- Test: `desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopMediaUploaderVideoTest.kt`

**Interfaces:**
- Consumes: `VideoCompressor`, `VideoCompressionResult`, `FfmpegVideoCompressor` (Task 6), `ForwardingNoticeSink` (Task 7).
- Produces:
  - `MediaUploader.supportsVideoCompression: Boolean get() = false` (KDoc: "Whether [compressIfNeeded] and [upload] transcode `video/*` at a quality other than UNCOMPRESSED on this platform; the composer hides the video controls when false.").
  - `class DesktopMediaUploader(clientFor, httpClients: IRoleBasedHttpClientBuilder, private val notices: UserNoticeSink = UserNoticeSink {}, private val videoCompressor: VideoCompressor = FfmpegVideoCompressor())` — `httpClients` widens from `RoleBasedHttpClientBuilder` to the interface it implements (on the JVM `IRoleBasedHttpClientBuilder` is a typealias of `RoleBasedOkHttpClients`, eight `OkHttpClient`-returning functions) so a test can stub it; the host still passes `modules.roleBasedHttpClientBuilder`. `override val supportsVideoCompression get() = videoCompressor.available`.
  - `internal suspend fun <T> withVideoCompressed(progress: UploadOrchestrator, uri: MediaUri, mimeType: String?, quality: CompressorQuality, useH265: Boolean, convertGifToMp4: Boolean, block: suspend (file: File, compressed: Boolean) -> T): T` — decides `wantsVideo = (mime starts with "video/" && quality != UNCOMPRESSED) || (mime == "image/gif" && convertGifToMp4)` with `mime = mimeType ?: mimeType(uri)`; if `wantsVideo && videoCompressor.available`: `progress.updateState(0.02, Compressing)`, `videoCompressor.compress(fileOf(uri), if (gif) MEDIUM else quality, useH265) { progress.updateState(0.02 + 0.18 * it, Compressing) }`; `Compressed` → `notice?.let(notices::notify)`, `block(file, true)` in `try/finally` that deletes `file` under `NonCancellable`; `Original` → `notice?.let(notices::notify)`, `block(fileOf(uri), false)`. Not wanted/unavailable → `block(fileOf(uri), false)`.
  - The four entry points call their pipeline inside `withVideoCompressed`, passing `file`, `stripExif = stripMetadata && !compressed`, `quality = if (compressed) null else compressionQuality.toTarget()`. `nip95`/`nip96` gain a `mimeType: String?` and `useH265`/`convertGifToMp4` parameters forwarded from the callers; `preparePayload` takes the prepared `File` instead of the `MediaUri`.
  - `compressIfNeeded` override: same decision; `Compressed` → `MediaCompressorResult(StringMediaUri(file.absolutePath), "video/mp4", file.length())` (the caller's `discardTempFile` deletes it — it is under `AmethystTempDir`); otherwise `MediaCompressorResult(uri, mimeType, null)`.
  - `DesktopAccountViewModelHost.mediaUploader = DesktopMediaUploader(modules::blossomClient, modules.roleBasedHttpClientBuilder, notices)`.

- [ ] **Step 1: Write the failing tests** with `FakeVideoCompressor(available: Boolean, result: (File) -> VideoCompressionResult)` and a `RecordingSink`:

```kotlin
@Test fun supportsVideoCompressionMirrorsTheCompressor() { assertTrue(uploader(available = true).supportsVideoCompression); assertFalse(uploader(available = false).supportsVideoCompression) }
@Test fun compressIfNeededReturnsMp4TempForVideo() = runTest {
    val out = AmethystTempDir.createTempFile("amethyst_video_", ".mp4").apply { writeBytes(ByteArray(10)) }
    val r = uploader(result = { VideoCompressionResult.Compressed(out) }).compressIfNeeded(UploadOrchestrator(), StringMediaUri(src.absolutePath), "video/quicktime", CompressorQuality.HIGH, false, false)
    assertEquals(out.absolutePath, r.uri.toString()); assertEquals("video/mp4", r.contentType); assertEquals(10L, r.size)
}
@Test fun uncompressedAndUnavailablePassThrough() = runTest {
    for (u in listOf(uploader(available = false), uploader())) assertEquals(src.absolutePath, u.compressIfNeeded(UploadOrchestrator(), StringMediaUri(src.absolutePath), "video/mp4", CompressorQuality.UNCOMPRESSED, false, false).uri.toString())
    assertEquals(src.absolutePath, uploader(available = false).compressIfNeeded(UploadOrchestrator(), StringMediaUri(src.absolutePath), "video/mp4", CompressorQuality.HIGH, false, false).uri.toString())
}
@Test fun imagesNeverReachTheVideoCompressor() = runTest { var called = false; uploader(result = { called = true; VideoCompressionResult.Original() }).compressIfNeeded(UploadOrchestrator(), StringMediaUri(src.absolutePath), "image/jpeg", CompressorQuality.HIGH, false, false); assertFalse(called) }
@Test fun gifIsConvertedOnlyWhenAsked() = runTest {
    val seen = mutableListOf<CompressorQuality>()
    val u = uploader(result = { VideoCompressionResult.Original() }, onCompress = { _, q, _ -> seen += q })
    u.compressIfNeeded(UploadOrchestrator(), StringMediaUri(src.absolutePath), "image/gif", CompressorQuality.HIGH, false, convertGifToMp4 = false)
    assertTrue(seen.isEmpty())
    u.compressIfNeeded(UploadOrchestrator(), StringMediaUri(src.absolutePath), "image/gif", CompressorQuality.HIGH, false, convertGifToMp4 = true)
    assertEquals(listOf(CompressorQuality.MEDIUM), seen)
}
@Test fun noticesAreForwarded() = runTest { val sink = RecordingSink(); uploader(result = { VideoCompressionResult.Original(UploadNotice.VideoCompressionFailed("x")) }, sink = sink).compressIfNeeded(…HIGH…); assertEquals(listOf(UploadNotice.VideoCompressionFailed("x")), sink.notices) }
@Test fun withVideoCompressedDeletesTempEvenWhenBlockThrows() = runTest {
    val out = AmethystTempDir.createTempFile("amethyst_video_", ".mp4")
    assertFailsWith<IllegalStateException> { uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoCompressed(UploadOrchestrator(), StringMediaUri(src.absolutePath), "video/mp4", CompressorQuality.HIGH, false, false) { _, _ -> error("upload failed") } }
    assertFalse(out.exists())
}
@Test fun withVideoCompressedReportsCompressingProgress() = runTest {
    val progress = UploadOrchestrator()
    val u = uploader(result = { VideoCompressionResult.Compressed(AmethystTempDir.createTempFile("amethyst_video_", ".mp4")) }, progressEmits = listOf(0.5))
    u.withVideoCompressed(progress, StringMediaUri(src.absolutePath), "video/mp4", CompressorQuality.HIGH, false, false) { _, compressed -> assertTrue(compressed) }
    assertEquals(0.11, progress.progress.value, 1e-9)                       // 0.02 + 0.18 * 0.5
    assertEquals(UploadingState.Compressing, progress.progressState.value)
}
```

`uploader(available = true, result = { VideoCompressionResult.Original() }, sink = UserNoticeSink {}, onCompress = { _, _, _ -> }, progressEmits = emptyList())` builds `DesktopMediaUploader(clientFor = { error("unused") }, httpClients = StubHttpClients, notices = sink, videoCompressor = FakeVideoCompressor(available, result, onCompress, progressEmits))`, where `StubHttpClients` is an `object : IRoleBasedHttpClientBuilder` whose eight functions return a plain `OkHttpClient()` (`proxyPortForVideo` returns null), and `FakeVideoCompressor.compress` calls `onCompress(source, quality, useH265)`, emits each `progressEmits` value through `onProgress`, then returns `result(source)`.

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** the interface property, the Android override, the desktop uploader changes, the host wiring.
- [ ] **Step 4: Run** `./gradlew :desktopApp:jvmTest --tests "*DesktopMediaUploaderVideoTest" :amethyst:compileDebugKotlin` → PASS, compiles.
- [ ] **Step 5: Commit** — `feat(desktop): compress videos through ffmpeg in every upload path`

---

### Task 9: Slider steps and label become video-aware (`commons` + `commonsUI`)

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/CompressorQuality.kt` (companion)
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/MultiOrchestrator.kt:54-60`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/creators/uploads/MediaQualitySlider.kt:80-133`
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml:2125` (replace `media_compression_image_max_size`)
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/CompressorQualitySliderTest.kt`

**Interfaces:**
- Consumes: `hasVideoRule` (Task 1), `VideoCompressionRules.ruleFor`.
- Produces, in `CompressorQuality.Companion`:
  - `fun stepsFor(hasVideo: Boolean): List<CompressorQuality>` = `platformSliderSteps.filter { !hasVideo || it.hasVideoRule }` (a function, named apart from the existing `sliderSteps` property).
  - `fun snapForVideo(position: Int): Int` — the platform position itself when its level `hasVideoRule`, else the nearest lower position whose level does.
  - `fun toDisplayedIndex(position: Int, hasVideo: Boolean): Int` = index of `fromSlider(snapForVideo(position))` in `stepsFor(hasVideo)` (no snap when `!hasVideo`).
  - `fun fromDisplayedIndex(index: Int, hasVideo: Boolean): Int` = `platformSliderSteps.indexOf(stepsFor(hasVideo)[index])`.
- `MultiOrchestrator.hasCompressibleImage(): Boolean` = any item with `isImage() == true && isCompressible()`.
- Strings: delete `media_compression_image_max_size`; add `media_compression_images_up_to` = `images up to %1$d px` and `media_compression_video_target` = `1080p video → %1$s`.
- `MediaQualitySlider`: `hasVideo = media?.hasVideo() == true`; `steps = stepsFor(hasVideo)`; `Slider(value = toDisplayedIndex(position, hasVideo), valueRange 0..steps.lastIndex, steps = steps.size - 2, onValueChange = { onPositionChange(fromDisplayedIndex(it.toInt(), hasVideo)) })`; `LaunchedEffect(hasVideo) { if (hasVideo) snapForVideo(position).let { if (it != position) onPositionChange(it) } }`. Label = `listOfNotNull(name, imagePart, videoPart).joinToString(" · ")` where `imagePart` is the images string when `media?.hasCompressibleImage() == true && quality.imageMaxDimension != null`, and `videoPart` is the video string with `"${rule.shortSide}p, ${rule.bitrateMbps} Mbps"` from `ruleFor(quality, FHD_1080P)` when `hasVideo` and the rule exists (`bitrateMbps` printed without a trailing `.0`: `2 Mbps`, `0.75 Mbps`).

- [ ] **Step 1: Write the failing tests** (the JVM platform list is `LOW, MEDIUM, HIGH, VERY_HIGH, UNCOMPRESSED`):

```kotlin
@Test fun videoStepsDropVeryHigh() { assertEquals(listOf(LOW, MEDIUM, HIGH, UNCOMPRESSED), CompressorQuality.stepsFor(hasVideo = true)); assertEquals(CompressorQuality.sliderSteps, CompressorQuality.stepsFor(hasVideo = false)) }
@Test fun veryHighSnapsToHigh() { assertEquals(2, CompressorQuality.snapForVideo(3)); assertEquals(2, CompressorQuality.snapForVideo(2)); assertEquals(4, CompressorQuality.snapForVideo(4)) }   // Review Focus 5
@Test fun displayedIndexRoundTrips() {
    for (p in 0..4) assertEquals(p, CompressorQuality.fromDisplayedIndex(CompressorQuality.toDisplayedIndex(p, false), false))
    assertEquals(3, CompressorQuality.toDisplayedIndex(4, true)); assertEquals(4, CompressorQuality.fromDisplayedIndex(3, true)); assertEquals(2, CompressorQuality.toDisplayedIndex(3, true))
}
@Test fun uncompressedStaysLast() { assertEquals(CompressorQuality.uncompressedSliderPosition, CompressorQuality.fromDisplayedIndex(CompressorQuality.stepsFor(true).lastIndex, true)) }
```

- [ ] **Step 2: Run to verify they fail.** — [ ] **Step 3: Implement** the companion functions, `hasCompressibleImage`, the strings, the slider changes.
- [ ] **Step 4: Run** `./gradlew :commons:jvmTest --tests "*CompressorQualitySliderTest" :commonsUI:compileKotlinJvm` → PASS, compiles. Run `python3 .claude/hooks/compose_escaping_check.py` if present → no findings.
- [ ] **Step 5: Commit** — `feat(uploads): media-quality slider shows video stops and a video-aware label`

---

### Task 10: Dialog honesty — hide dead controls, fix the metadata toggle, "Upload Media"

**Files:**
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/creators/uploads/ImageVideoDescription.kt:356-431`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/actions/NewMediaView.kt:225-277`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/actions/uploads/SelectFromGallery.kt:96,105`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/screen/loggedIn/chats/privateDM/send/NewGroupDMScreen.kt:429`
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml` (after `upload_image`)

**Interfaces:**
- Consumes: `supportsVideoCompression` (Task 8), `hasCompressibleImage` (Task 9).
- Strings: `upload_media` = `Upload Media`; `video_compression_needs_ffmpeg` = `Install ffmpeg to compress videos. Until then videos are uploaded as they are.`
- `ImageVideoDescription`: `val canCompressVideo = accountViewModel.host.mediaUploader.supportsVideoCompression`; `val hasVideo = uris.hasVideo()`;
  `isVideoWithCompression = hasVideo && canCompressVideo && mediaQualitySlider != uncompressedSliderPosition`;
  slider block condition `(uris.hasCompressibleImage() || (hasVideo && canCompressVideo)) && !convertGifToMp4`;
  H.265 switch condition `isVideoWithCompression`; GIF switch condition `uris.hasGif() && canCompressVideo`;
  hint `Text(stringRes(Res.string.video_compression_needs_ffmpeg), style = bodySmall, color = Color.Gray)` shown when `hasVideo && !canCompressVideo`, placed where the H.265 switch would be.
- `NewMediaView`: same four conditions on `postViewModel.multiOrchestrator`.
- `SelectFromGallery.GallerySelectButton` and `NewGroupDMScreen:429`: `Res.string.upload_media`.

- [ ] **Step 1: Apply the changes** (Compose composition is covered by the manual test; the logic they consume is unit-tested in Tasks 8 and 9).
- [ ] **Step 2: Compile both front ends** — `./gradlew :commonsUI:compileKotlinJvm :amethyst:compileDebugKotlin` → success.
- [ ] **Step 3: Run the desktop app** `./gradlew :desktopApp:run` and check, with ffmpeg on `$PATH`: picking a video shows 4 slider stops, the video label, the H.265 switch and the metadata toggle hides below UNCOMPRESSED; picking a photo + video together snaps VERY_HIGH → HIGH. Then `PATH=/usr/bin:/bin ./gradlew :desktopApp:run`: the hint shows, no H.265/GIF switch, the metadata toggle stays, images still get the 5-stop slider. The attach button tooltip reads "Upload Media".
- [ ] **Step 4: Commit** — `fix(composer): hide video compression controls the platform cannot honour`

---

### Task 11: End-to-end verification and plan bookkeeping

**Files:**
- Modify: `desktopApp/plans/README.md` (summary stays "In progress" until merged; add "phase 1 built" to it)
- Create: `desktopApp/plans/2026-10-11-desktop-video-compression-manual-testing.md` (follow the format of `2026-08-20-live-media-manual-testing.md`)

- [ ] **Step 1: Full test run** — `./gradlew test` → BUILD SUCCESSFUL. Also `./gradlew :amethyst:compileDebugKotlin`.
- [ ] **Step 2: The hash check** from the investigation: upload `~/Downloads/file_example_MOV_1920_2_2MB.mov` from the desktop app at HIGH to blossom.band; `curl` the blob; `shasum -a 256` must differ from `ead8b50c…e40c62`; `ffprobe` must report 1920×1080, `h264`, video `bit_rate` ≈ 4 000 000 (±20 %), `format_tags` absent. Repeat at LOW → 480p, and with H.265 → `hevc`.
- [ ] **Step 3: Write the manual-testing sheet** covering: with/without ffmpeg, each stop, H.265 with and without an HEVC encoder, GIF→MP4, a portrait video, cancellation mid-encode (temp dir empty afterwards: `ls $(AmethystTempDir root)` shows no `amethyst_video_*`), a corrupt video (uploads original + toast).
- [ ] **Step 4: Commit** — `docs(desktop): manual test sheet for desktop video compression`

Hand the branch and the sheet to the human; do not open a PR.
