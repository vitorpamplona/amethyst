# Media Size Hint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** With auto-download off, every tap-to-load media placeholder shows "Video · 12.4 MB" without downloading the file.

**Architecture:** `MediaUrlContent` carries the declared size. A `commons` probe (`probeMediaSize`, an `expect`/`actual` `HEAD` over the role-based OkHttp clients) fills a composition-readable `MediaSizeCache`, which the existing MP4 track probe also feeds from its `Content-Range`. A `commonsUI` hook (`rememberMediaSize`) and label (`MediaSizeLabel`) are used by every placeholder on Android, in shared UI and on Desktop.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, OkHttp, kotlin.test, JDK `com.sun.net.httpserver.HttpServer` in tests.

**Spec:** `commons/plans/2026-11-08-media-size-hint.md`

## Global Constraints

- No new dependencies. Tests use the JDK `HttpServer`, as `EncryptedCartDownloadTest` does; `commons` has no MockWebServer.
- New strings go only in `commonsUI/src/commonMain/composeResources/values/strings.xml`, with positional arguments (`%1$s`), and are read with `stringRes(Res.string.x)`.
- Log through Quartz's `Log` with the lambda form; never `android.util.Log`.
- No fully-qualified class names inline; import them.
- Only a 2xx answer with a positive length counts as a size; anything else is a miss, never a guess.
- Requests go through `okHttpClientForImage` / `okHttpClientForVideo` / `okHttpClientForPreview`, never a new client, so Tor routing applies.
- Number format: decimal units, no-break space (` `) before the unit; under 1 KB → "1 KB"; KB whole; MB/GB one decimal below 100, none from 100 up.
- Run `./gradlew spotlessApply` before each commit that touches Kotlin.

## Review Focus

1. **A `HEAD` that redirects to a CDN on another host** should report the final file's length. Pinned in Task 3 (`redirectIsFollowed`).
2. **`Content-Length: 0`**, which some servers send for `HEAD`, should be a miss, not "0 KB". Pinned in Task 3 (`zeroLengthIsAMiss`).
3. **Files over 2 GB** (long videos) must not overflow: parsing and formatting use `Long`. Pinned in Task 2 (`gigabytes`) and Task 3 (`lengthAboveIntRange`).
4. **Scrolling away mid-request** must not cache a miss, or the size never appears when the user scrolls back. Pinned in Task 3 (`cancelledProbeCachesNothing`).
5. **`Content-Range: bytes 0-1/*`** (unknown total) must record nothing rather than crash or cache a bogus size. Pinned in Task 3 (`contentRangeWithUnknownTotal`).

---

### Task 1: Declared size on every media model

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/richtext/MediaContentModels.kt`
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/richtext/RichTextParser.kt:56-124`
- Modify (encrypted call sites): `commonsUI/.../chats/feed/types/RenderEncryptedFile.kt:93,107`, `commonsUI/.../chats/feed/types/RenderMarmotEncryptedMedia.kt:167,180,292,306`, `commonsUI/.../chats/privateDM/send/NewGroupDMScreen.kt:643,661`, `commonsUI/src/jvmAndroid/.../chats/cordnGroup/CordnGroupChatScreen.kt:1206,1218`
- Test: `commons/src/commonTest/kotlin/com/vitorpamplona/amethyst/commons/richtext/RichTextParserSizeImetaTest.kt`

**Interfaces:**
- Produces: `MediaUrlContent.sizeBytes: Long?` (constructor parameter `sizeBytes: Long? = null`, last). `MediaUrlVideo` no longer declares its own `sizeBytes`; it passes its parameter to the base. `EncryptedMediaUrlImage` and `EncryptedMediaUrlVideo` take `sizeBytes: Long? = null` and pass it on.

- [ ] **Step 1: Write the failing test**

Same `parse(...)` helper shape as `RichTextParserAudioImetaTest`, with a `.jpg`, an `.mp4` and a `.pdf` URL.

```kotlin
@Test fun imageSizeFromImeta()      // "m image/jpeg", "size 245000" → assertIs<MediaUrlImage>; sizeBytes == 245_000L
@Test fun pdfSizeFromImeta()        // "m application/pdf", "size 2100000" → assertIs<MediaUrlPdf>; sizeBytes == 2_100_000L
@Test fun videoSizeStillParsed()    // "m video/mp4", "size 12400000" → assertIs<MediaUrlVideo>; sizeBytes == 12_400_000L
@Test fun sizeFromUrlFragment()     // url "…/a.jpg#size=300000", no imeta → sizeBytes == 300_000L (NIP-54 fragment wins)
@Test fun garbageSizeIsNull()       // "size abc" → sizeBytes == null
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.richtext.RichTextParserSizeImetaTest"`
Expected: compile failure (`sizeBytes` unresolved on `MediaUrlImage`/`MediaUrlPdf`).

- [ ] **Step 3: Move `sizeBytes` to `MediaUrlContent`; read `size` on the image and PDF branches**

Use the same expression as the video branch (`(frags[SizeTag.TAG_NAME] ?: tags[SizeTag.TAG_NAME]?.firstOrNull())?.toLongOrNull()`), hoisted to one `val declaredSize` above the branches. Every existing `MediaUrl*` constructor call uses named arguments, so the new trailing parameter breaks none of them; `EncryptedMediaUrlVideo` passes `sizeBytes = sizeBytes` by name.

- [ ] **Step 4: Feed the encrypted call sites**

- `RenderEncryptedFile.kt`: `sizeBytes = noteEvent.size()?.toLongOrNull()`. Check what `size()` returns in the quartz version in `gradle/libs.versions.toml`; drop the `toLongOrNull` if it is already a number.
- `NewGroupDMScreen.kt`: `sizeBytes = data.result.size` (`MediaUploadResult.size: Long?`).
- `RenderMarmotEncryptedMedia.kt`, `CordnGroupChatScreen.kt`: pass a size if `reference` / `attachment` has a size field; otherwise leave the default.

- [ ] **Step 5: Run the new test, the audio-imeta test and compile commonsUI**

Run: `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.richtext.*" :commonsUI:compileKotlinJvm`
Expected: BUILD SUCCESSFUL; `RichTextParserAudioImetaTest` still passes.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A commons/src commonsUI/src
git commit -m "feat: carry the declared file size on every media model"
```

---

### Task 2: Shared, decimal `formatFileSize`

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/util/NumberFormatters.kt`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/audio/player/PlayableMediaCards.kt:672-675` (delete the old function, import the new one)
- Test: `commons/src/commonTest/kotlin/com/vitorpamplona/amethyst/commons/util/FormatFileSizeTest.kt`

**Interfaces:**
- Produces: `fun formatFileSize(bytes: Long): String` in package `com.vitorpamplona.amethyst.commons.util`.

- [ ] **Step 1: Write the failing test** (`NBSP = " "`)

```kotlin
@Test fun underOneKb()   { assertEquals("1${NBSP}KB", formatFileSize(0)); assertEquals("1${NBSP}KB", formatFileSize(999)) }
@Test fun kilobytes()    { assertEquals("850${NBSP}KB", formatFileSize(850_000)); assertEquals("999${NBSP}KB", formatFileSize(999_499)) }
@Test fun megabytes()    { assertEquals("1.0${NBSP}MB", formatFileSize(1_000_000)); assertEquals("12.4${NBSP}MB", formatFileSize(12_400_000)) }
@Test fun hundredCutoff(){ assertEquals("99.9${NBSP}MB", formatFileSize(99_940_000)); assertEquals("140${NBSP}MB", formatFileSize(140_000_000)) }
@Test fun gigabytes()    { assertEquals("1.2${NBSP}GB", formatFileSize(1_200_000_000)); assertEquals("5.0${NBSP}GB", formatFileSize(5_000_000_000)) }
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.util.FormatFileSizeTest"`
Expected: FAIL, unresolved `formatFileSize`.

- [ ] **Step 3: Implement `formatFileSize(bytes: Long): String`**

Common code has no `String.format`: build the one decimal from integer tenths, as the old function did. At a unit boundary that rounds up (999,950 KB → "1000 KB"), move to the next unit.

- [ ] **Step 4: Point the audio card at it**

Delete `formatFileSize` from `PlayableMediaCards.kt`. Import the `commons.util` one in `PlayableMediaCards.kt` and in `commonsUI/.../audio/player/PlayableMediaCardPreviews.kt` if it calls the function.

- [ ] **Step 5: Run the test and compile**

Run: `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.util.FormatFileSizeTest" :commonsUI:compileKotlinJvm`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A commons/src commonsUI/src
git commit -m "refactor: one decimal file-size formatter, shared from commons"
```

---

### Task 3: Size probe, cache, and track-probe feed

**Files:**
- Create: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/MediaSizeProbe.kt`
- Create: `commons/src/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/service/MediaSizeProbe.jvmAndroid.kt`
- Create: `commons/src/iosMain/kotlin/com/vitorpamplona/amethyst/commons/service/MediaSizeProbe.ios.kt`
- Modify: `commons/src/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/audio/MediaTrackProbe.jvmAndroid.kt` (206 branch)
- Test: `commons/src/commonTest/kotlin/com/vitorpamplona/amethyst/commons/service/ContentRangeTest.kt`
- Test: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/service/MediaSizeProbeTest.kt`

**Interfaces:**
- Produces, in package `com.vitorpamplona.amethyst.commons.service`:
  - `enum class MediaSizeRole { IMAGE, VIDEO, PREVIEW }`
  - `object MediaSizeCache { val entries: StateFlow<Map<String, Long>>; fun get(url: String): Long?; fun isAnswered(url: String): Boolean; fun put(url: String, size: Long?); fun clear() }`. A miss is stored internally as `-1L`; `get` returns null for a miss or an unknown URL, and `isAnswered` tells the two apart. 500 entries, copy-on-write like `MediaTrackProbeCache`; `clear()` exists for tests.
  - `suspend fun IRoleBasedHttpClientBuilder.probeMediaSize(url: String, mimeType: String?, role: MediaSizeRole): Long?`
  - `internal expect suspend fun IRoleBasedHttpClientBuilder.headContentLength(url: String, role: MediaSizeRole): Long?`
  - `fun parseContentRangeTotal(header: String?): Long?`

- [ ] **Step 1: Write the failing common test (`ContentRangeTest`)**

```kotlin
@Test fun total()                       { assertEquals(12_400_000L, parseContentRangeTotal("bytes 0-1023/12400000")) }
@Test fun contentRangeWithUnknownTotal(){ assertNull(parseContentRangeTotal("bytes 0-1/*")) }
@Test fun missingOrGarbage()            { assertNull(parseContentRangeTotal(null)); assertNull(parseContentRangeTotal("nonsense")) }
@Test fun zeroTotal()                   { assertNull(parseContentRangeTotal("bytes */0")) }
```

- [ ] **Step 2: Write the failing JVM test (`MediaSizeProbeTest`)**

A JDK `HttpServer` on `127.0.0.1:0` with one context per scenario; each records the request method. Clients come from `SingleRoleBasedHttpClientBuilder(OkHttpClient())`. Call `MediaSizeCache.clear()` in `@BeforeTest`.

For `HEAD`, the JDK server may rewrite or warn about `Content-Length`. Set the header yourself in `responseHeaders` and call `sendResponseHeaders(code, -1)`. If the header is still dropped, answer with raw HTTP over a `ServerSocket`, as `LocalBlossomCacheTorRoutingTest` does.

```kotlin
@Test fun lengthFromHead()             // 200 + Content-Length 245000 → 245_000L; recorded method == "HEAD"
@Test fun errorPageIsAMiss()           // 403 with a 5615-byte body → null; MediaSizeCache.isAnswered(url) == true
@Test fun noLengthIsAMiss()            // 200, chunked (sendResponseHeaders(200, 0)) → null
@Test fun zeroLengthIsAMiss()          // 200 + Content-Length 0 → null
@Test fun encodedIsAMiss()             // 200 + Content-Length 900 + Content-Encoding gzip → null
@Test fun lengthAboveIntRange()        // 200 + Content-Length 5000000000 (set via header; HEAD has no body) → 5_000_000_000L
@Test fun redirectIsFollowed()         // 302 Location → second context returning 200 + Content-Length 777 → 777L
@Test fun cachedUrlMakesNoRequest()    // probe twice → server saw exactly 1 request
@Test fun nonHttpMakesNoRequest()      // probeMediaSize("blossom:abc…", …) → null, 0 requests, not cached
@Test fun hlsMakesNoRequest()          // "…/live.m3u8" → null, 0 requests
@Test fun cancelledProbeCachesNothing()// server sleeps 2 s; launch probe, cancel after 100 ms → isAnswered(url) == false
```

- [ ] **Step 3: Run both and watch them fail**

Run: `./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.service.ContentRangeTest" --tests "com.vitorpamplona.amethyst.commons.service.MediaSizeProbeTest"`
Expected: compile failure (symbols missing).

- [ ] **Step 4: Implement `MediaSizeProbe.kt` (common)**

`probeMediaSize`:
1. If `MediaSizeCache.isAnswered(url)`, return `get(url)`.
2. If the URL doesn't start with `http://` or `https://`, or `isHlsMedia(url, mimeType)`, return null without caching.
3. Otherwise call `headContentLength`, `put` the result (a null result is a miss) and return it.

Cancellation propagates out of step 3 before `put` runs, so nothing is cached. `parseContentRangeTotal` takes the text after the last `/` and returns it as a positive `Long`; `*`, garbage and 0 give null.

- [ ] **Step 5: Implement the `jvmAndroid` actual and the iOS stub**

`jvmAndroid`, in `withContext(Dispatchers.IO)`:
- pick the client by role (`IMAGE` → `okHttpClientForImage`, `VIDEO` → `okHttpClientForVideo`, `PREVIEW` → `okHttpClientForPreview`);
- send `Request.Builder().url(url).head().header("Accept-Encoding", "identity")` with `executeAsync()`;
- return the length only when `response.isSuccessful`, `Content-Encoding` is absent and `Content-Length` parses to > 0;
- rethrow `CancellationException`; any other exception → `Log.d(TAG, "size probe failed", e)` (a plain string: the throwable overload has no lambda form) and null.

iOS: `= null`.

- [ ] **Step 6: Feed the cache from the track probe**

In `MediaTrackProbe.jvmAndroid.kt`, on the `response.code == 206` branch, before reading the body:
`parseContentRangeTotal(response.header("Content-Range"))?.let { MediaSizeCache.put(url, it) }`.
Never put a miss here: the `HEAD` fallback in Task 4 handles a probe that found no size.

- [ ] **Step 7: Run the tests**

Same command as Step 3. Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply
git add -A commons/src
git commit -m "feat: probe media size with HEAD and record the track probe's Content-Range total"
```

---

### Task 4: Hook, label and placeholder cards (commonsUI)

**Files:**
- Create: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/components/MediaSizeHint.kt`
- Modify: `commonsUI/src/commonMain/composeResources/values/strings.xml` (append before `</resources>`)
- Test: `commonsUI/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/ui/components/MediaSizeKindTest.kt`

**Interfaces:**
- Consumes: Task 1 `MediaUrlContent.sizeBytes`, Task 2 `formatFileSize`, Task 3 `MediaSizeRole`, `MediaSizeCache`, `probeMediaSize`; `mayHoldVideo(mimeType, url)` and `MediaTrackProbeCache` from `commons.audio`; `OmniboxInput.hostOf(url)` from `commons.browser`.
- Produces:
  - `enum class MediaKind(val role: MediaSizeRole) { IMAGE(MediaSizeRole.IMAGE), VIDEO(MediaSizeRole.VIDEO), AUDIO(MediaSizeRole.VIDEO), PDF(MediaSizeRole.PREVIEW) }`
  - `fun MediaUrlContent.mediaKind(): MediaKind`: Image, PDF, or `MediaUrlVideo` → AUDIO when `RichTextParser.isAudioContent(mimeType, url)`, else VIDEO.
  - `@Composable fun rememberMediaSize(url: String, mimeType: String?, declaredSize: Long?, kind: MediaKind, accountViewModel: AccountViewModel): Long?`
  - `@Composable fun rememberMediaSize(content: MediaUrlContent, accountViewModel: AccountViewModel): Long?`
  - `@Composable fun MediaSizeLabel(kind: MediaKind, sizeBytes: Long?, modifier: Modifier = Modifier, showKind: Boolean = true, pill: Boolean = false)`
  - `@Composable fun MediaDownloadCard(url: String, kind: MediaKind, sizeBytes: Long?, ratio: Float, modifier: Modifier, onLoad: () -> Unit)` (mockup C)
  - `@Composable fun MediaDownloadRow(url: String, kind: MediaKind, sizeBytes: Long?, modifier: Modifier, onLoad: () -> Unit)` (mockup D)

- [ ] **Step 1: Add the strings**

```xml
<string name="media_kind_image">Image</string>
<string name="media_kind_video">Video</string>
<string name="media_kind_audio">Audio</string>
<string name="media_kind_pdf">PDF</string>
<string name="media_type_and_size">%1$s · %2$s</string>
```

- [ ] **Step 2: Write the failing test (`MediaSizeKindTest`)**

```kotlin
@Test fun imageKind()  { assertEquals(MediaKind.IMAGE, MediaUrlImage("https://h/a.jpg").mediaKind()) }
@Test fun pdfKind()    { assertEquals(MediaKind.PDF, MediaUrlPdf("https://h/a.pdf").mediaKind()); assertEquals(MediaSizeRole.PREVIEW, MediaKind.PDF.role) }
@Test fun audioKind()  { assertEquals(MediaKind.AUDIO, MediaUrlVideo("https://h/a.mp3", mimeType = "audio/mpeg").mediaKind()) }
@Test fun videoKind()  { assertEquals(MediaKind.VIDEO, MediaUrlVideo("https://h/a.mp4").mediaKind()) }
```

- [ ] **Step 3: Run it and watch it fail**

Run: `./gradlew :commonsUI:jvmTest --tests "com.vitorpamplona.amethyst.commons.ui.components.MediaSizeKindTest"`
Expected: compile failure.

- [ ] **Step 4: Implement `MediaSizeHint.kt`**

`rememberMediaSize(url, mimeType, declaredSize, kind, accountViewModel)`:
- If `declaredSize != null`, return it.
- Otherwise collect `MediaSizeCache.entries` (`collectAsState`) and read this URL's entry through `MediaSizeCache.get(url)`, so a size recorded later appears.
- In `LaunchedEffect(url)`: if the URL is unanswered, and either `!mayHoldVideo(mimeType, url)` or `MediaTrackProbeCache.get(url) != null`, call `accountViewModel.httpClientBuilder.probeMediaSize(url, mimeType, kind.role)`. If `mayHoldVideo` is true and the track probe hasn't answered, wait on `snapshotFlow { MediaTrackProbeCache.get(url) }.first { it != null }`, then probe if still unanswered.

`MediaSizeLabel`:
- shows `stringRes(Res.string.media_type_and_size, kindWord, formatFileSize(size))` when the size is known and `showKind`;
- shows the size alone when `!showKind`, and the kind word alone while the size is null;
- puts the size part inside `AnimatedVisibility(fadeIn())`;
- with `pill = true`, draws it on a translucent black rounded background with white text (mockup A).

`MediaDownloadCard` / `MediaDownloadRow` follow mockups C and D: `MaterialTheme.colorScheme.surfaceVariant` surface, `DownloadForOfflineIcon`, the label, and the host (`OmniboxInput.hostOf(url)`) in `onSurfaceVariant`. The whole surface is clickable → `onLoad`.

- [ ] **Step 5: Run the test and compile**

Run: `./gradlew :commonsUI:jvmTest --tests "com.vitorpamplona.amethyst.commons.ui.components.MediaSizeKindTest" :commonsUI:compileKotlinJvm`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A commonsUI/src
git commit -m "feat: media size hook, label and download placeholders"
```

---

### Task 5: Android placeholders

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/components/ZoomableContentView.kt` (`UrlImageView` 575-708; `VideoView` call 310)
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/playback/composable/VideoView.kt` (both overloads; branches 169-245)
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/components/PlayableAudioView.kt:117-130`

**Interfaces:**
- Consumes: Task 4 `rememberMediaSize`, `MediaSizeLabel`, `MediaDownloadCard`, `MediaDownloadRow`, `MediaKind`.
- Produces: `VideoView(…, sizeBytes: Long? = null)` on both overloads.

- [ ] **Step 1: `UrlImageView`**

Call `rememberMediaSize(content, accountViewModel)` inside the not-shown branch only, so a shown image never probes.
- Blur + ratio branch: put `MediaSizeLabel(MediaKind.IMAGE, size, pill = true)` under the existing `IconButton`, in a `Column` centred in the box.
- Otherwise, if `ratio != null`: `MediaDownloadCard(content.url, MediaKind.IMAGE, size, ratio, loadedImageModifier, onLoad = { showImage.value = true })`.
- Else: `MediaDownloadRow(…)` in place of `ImageUrlWithDownloadButton`.

- [ ] **Step 2: `VideoView`**

Add `sizeBytes: Long? = null` to both overloads, and pass `sizeBytes = content.sizeBytes` from `ZoomableContentView.kt:310`. Kind is `MediaKind.VIDEO`, or `AUDIO` when `RichTextParser.isAudioContent(mimeType, videoUri)`. Call `rememberMediaSize(videoUri, mimeType, sizeBytes, kind, accountViewModel)` only while `!automaticallyStartPlayback.value`.
- Blur branch (224-245): pill label under the icon.
- No-blur branch (169-181): when `ratio != null`, a `MediaDownloadCard` at the ratio replaces the URL row. This fixes the empty black box. Otherwise a `MediaDownloadRow`. `onLoad` sets `automaticallyStartPlayback.value = true`.

- [ ] **Step 3: Audio card**

In `PlayableAudioView`, `val size = rememberMediaSize(content, accountViewModel)`, use `sizeBytes = size` in `AudioCardInfo`, and add `size` to the `remember` keys.

- [ ] **Step 4: Compile and lint**

Run: `./gradlew :amethyst:compilePlayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Install and check on the device with auto-download off**

Run: `./gradlew :amethyst:installPlayDebug`. Turn off image and video auto-download in Settings, then look at:
- a Primal video note (no blur, `dim` known): a card fills the box with "Video · N MB" and the host;
- an Amethyst image note: a pill under the icon;
- a bare image URL: a compact row;
- an audio note: "MP3 · N MB".
Tap each one and confirm it loads.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A amethyst/src
git commit -m "feat: size hint on Android image, video and audio placeholders"
```

---

### Task 6: Shared placeholders: cover images, PDF card, gallery tiles

**Files:**
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/components/MyAsyncImage.kt:142-165`
- Modify: `commonsUI/src/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/ui/components/pdf/PdfPreviewCard.kt:233-246`
- Modify: `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/screen/loggedIn/profile/gallery/GalleryThumb.kt:212-300`

**Interfaces:**
- Consumes: Task 4 hook, label and `MediaKind`.

- [ ] **Step 1: `MyAsyncImage`**

In both not-shown boxes, add `rememberMediaSize(imageUrl, null, null, MediaKind.IMAGE, accountViewModel)` and a `MediaSizeLabel(MediaKind.IMAGE, size)` under the icon, in a centred `Column`.

- [ ] **Step 2: `PlaceholderPdfCard`**

Pass `accountViewModel` in from `PdfPreviewCard`. The subtitle becomes `"${stringRes(Res.string.pdf_tap_to_load)} · ${formatFileSize(size)}"` once `rememberMediaSize(content, accountViewModel)` returns a size (kind PDF → role `PREVIEW`).

- [ ] **Step 3: Gallery tile**

In the not-shown branches of `UrlImageView`, add `MediaSizeLabel(content.mediaKind(), size, showKind = false, pill = true)` under the icon.

- [ ] **Step 4: Compile**

Run: `./gradlew :commonsUI:compileKotlinJvm :amethyst:compilePlayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A commonsUI/src
git commit -m "feat: size hint on cover images, PDF cards and gallery tiles"
```

---

### Task 7: Desktop video and audio

**Files:**
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/media/DesktopVideoPlayer.kt` (signature 126-149; native button 397-414; Chromium button 616-634)
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/media/AudioPlayer.kt:57-95`
- Modify: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/app/DesktopNotePlatform.kt:225,241`

**Interfaces:**
- Consumes: Task 4 hook, label and `MediaKind`.
- Produces: `DesktopVideoPlayer(…, sizeBytes: Long? = null)`, `ChromiumRoutedVideo(…, sizeBytes: Long?)`, `AudioPlayer(…, sizeBytes: Long? = null)`.

- [ ] **Step 1: Thread `sizeBytes` through**

- `DesktopNotePlatform.kt:241`: `sizeBytes = if (!accountViewModel.settings.startVideoPlayback()) video?.let { rememberMediaSize(it, accountViewModel) } else null`. `video` is null for a `MediaLocalVideo`, which needs no hint.
- `DesktopNotePlatform.kt:225`: `sizeBytes = video?.let { rememberMediaSize(it, accountViewModel) }`.
- Both download buttons in `DesktopVideoPlayer` get `MediaSizeLabel(MediaKind.VIDEO, sizeBytes, pill = true)` beneath them.
- `AudioPlayer` uses `sizeBytes` in `AudioCardInfo`, and in its `remember` keys.

- [ ] **Step 2: Compile**

Run: `./gradlew :desktopApp:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run it headless**

Run `tools/desktop-run/run-headless.sh start`. Log in with an `npub` that follows media posters, turn off video loading in Settings, and screenshot a feed with a video and an audio note; the label must show under the load button. Then run `tools/desktop-run/run-headless.sh logs` (no new exceptions) and `stop`.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply
git add -A desktopApp/src
git commit -m "feat: size hint on Desktop video and audio"
```

---

### Task 8: Whole-branch check and plan status

**Files:**
- Modify: `commons/plans/2026-11-08-media-size-hint.md` (Status line), `commons/plans/README.md` (row text)

- [ ] **Step 1: Run every JVM test and the pre-push checks**

Run: `./gradlew test` (≥5 min: background it or use a 600000 ms timeout), then `PREPUSH_KMP_METADATA=1 bash .git-hooks/pre-push`.
Expected: all tests pass; lint clean.

- [ ] **Step 2: Mark the spec implemented**

Set Status to "implemented 2026-11-xx; Android checked on device, Desktop headless", or say what was not checked, and update the README row to match.

- [ ] **Step 3: Commit**

```bash
git add commons/plans
git commit -m "docs: media size hint implemented"
```
