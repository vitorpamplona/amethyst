# Media size hint when auto-download is off

Status: designed 2026-11-08, not started.

## Goal

A user asked to see how big a file is before tapping to download it, when the auto-download
setting holds media back. The hint goes on every placeholder that waits for a tap: images,
videos and audio, on Android and Desktop. Website link previews are out of scope.

Success: a placeholder shows its type and size ("Video · 12.4 MB") without downloading the
file. When the size cannot be learned it shows the type alone, never a guess.

## Where the size comes from

The file itself cannot be fetched; that is what the setting exists to prevent. Two sources
remain, measured on 2026-11-08:

| Source | Coverage | Cost |
|---|---|---|
| imeta `size` (NIP-92/94) | 7 of 41 notes with media in a 500-note sample from nos.lol. Most of those notes had an imeta tag (36/41), but few carried `size`: Amethyst and Nostrich did, Primal and Damus did not. One relay, rough numbers. | None, already in the event |
| HTTP `HEAD` → `Content-Length` | 10 of 12 media hosts sampled, including nostr.build, blossom.primal.net, blossom.band and nostrmedia. Blossom servers must answer `HEAD` (BUD-01). Flickr sent no length; Midjourney answered 403 with the length of its error page. | One request, no body |

**Decision:** imeta first; `HEAD` when the event does not say. Only a 2xx answer with a
positive `Content-Length` counts. A `HEAD` still tells the host the user's IP and which file
they looked at. It goes through the same Tor routing as the media itself, and no setting is
added (decided with the user).

## What exists today

- `MediaUrlVideo.sizeBytes` holds the imeta `size` for video and audio. `RichTextParser`
  reads `size` only on the video branch, so images and PDFs drop it.
- The audio card already shows "MP3 · 4.8 MB" from `sizeBytes` (`PlayableAudioView`,
  `AudioCardInfo.fileFacts`).
- Shared code reaches HTTP through `accountViewModel.httpClientBuilder`
  (`IRoleBasedHttpClientBuilder`). Probes are `expect` extension functions on it with a
  `jvmAndroid` actual and an iOS stub. `isVideoOnline` (`VideoOnlineProbe.kt` +
  `OnlineChecker` + `OnlineStatusCache`) is the model to copy.
- Placeholders that wait for a tap:
  - Android `ZoomableContentView.kt`: `UrlImageView` (blur + icon, or
    `ImageUrlWithDownloadButton` when there is no blur). `LocalImageView` has the same
    placeholder but draws a file already on the device, so it costs no data and gets no hint.
  - Android `VideoView.kt`: blur + icon, or `ImageUrlWithDownloadButton` when there is no
    blur. In that second case the surrounding box is sized to the video's aspect ratio while
    only a one-line URL row is drawn, leaving an empty black area (seen on Primal posts, which
    carry `dim` but no blurhash).
  - `commonsUI` `MyAsyncImage.kt`: its own icon placeholder. It draws cover and banner images
    from a bare URL (long-form, wiki, calendar, goal and other cards; 43 call sites), so there
    is no imeta to read.
  - Desktop `DesktopVideoPlayer.kt`: the `loadOnDemand` download button, in both the
    native and the Chromium-routed player.
- Three byte formatters exist: `countToHumanReadableBytes` (`commons`, rounds to whole
  units), `formatBytes` (`SoftwareApp.kt`) and `formatFileSize` (`PlayableMediaCards.kt`,
  one decimal place for MB). None are reused across features.

## Design

### 1. Model

Move `sizeBytes: Long?` from `MediaUrlVideo` up to `MediaUrlContent`, so images, videos,
audio and PDFs all carry it. `RichTextParser.createMediaContent` reads `size` (URL fragment
first, then imeta, as for every other field) on every branch. Encrypted variants pass the
value through; for an encrypted file it is the ciphertext size, which is within a few bytes of
the real one.

### 2. Size probe

Following the `isVideoOnline` pattern:

- `commons/commonMain/.../service/MediaSizeProbe.kt`:
  `expect suspend fun IRoleBasedHttpClientBuilder.mediaSize(url: String, isVideo: Boolean): Long?`
- `commons/commonMain/.../service/MediaSizeCache.kt`: an `LruCache<String, Long>` (500
  entries) that also remembers misses (a sentinel such as `-1`), so a URL that answered
  without a length is not asked again in this process.
- `commons/jvmAndroid/.../service/MediaSizeProbe.jvmAndroid.kt`: a `HEAD` request through
  `okHttpClientForVideo(url)` or `okHttpClientForImage(url)`, which applies the user's Tor
  routing. Redirects are followed (OkHttp's default). The probe:
  - returns the length only for a 2xx response with `Content-Length` > 0, and null otherwise;
  - treats an exception other than cancellation as a miss and logs it at debug level;
  - lets cancellation propagate;
  - joins concurrent callers asking about the same URL to one request, through a map of
    in-flight `Deferred`s;
  - uses a short call timeout (5 s): a size that arrives late is useless.
- `commons/iosMain`: returns null.

### 3. Composable hook

`commonsUI`:
`@Composable fun rememberMediaSize(url: String, declaredSize: Long?, isVideo: Boolean, accountViewModel): State<Long?>`,
plus a `MediaUrlContent` overload that passes `url`, `sizeBytes` and the content's type.
`MyAsyncImage` calls the URL form with `declaredSize = null`. If `declaredSize` is set the hook
returns it at once and sends nothing. Otherwise it reads
the cache, and on a miss starts the probe in a `LaunchedEffect(content.url)`. It is called
only inside placeholders, which exist only while auto-download is off and, in a lazy feed,
only while on screen. That is the "visible placeholders only" rule, with no extra machinery.
A note that auto-downloads never sends a `HEAD`.

### 4. UI (mockup option A, with the type)

One shared label in `commonsUI` `MediaPlaceholders.kt`: `MediaSizeLabel(kind, sizeBytes)`,
which renders "Video · 12.4 MB", or "Video" while the size is unknown. The size fades in when
it arrives, with no spinner.

| Case | Today | New |
|---|---|---|
| Blurhash or thumbhash present (Android `UrlImageView`, `VideoView`) | Blur + 75 dp icon | Same, plus the label in a translucent pill under the icon |
| No blur, `dim` known | Empty box at the aspect ratio with a URL row (video) or URL row (image) | `MediaDownloadCard`: neutral surface at the aspect ratio, icon, label, host below |
| No blur, no `dim` | `ImageUrlWithDownloadButton` (URL text + small icon) | Compact `MediaDownloadRow`: icon, label, host. Tap loads the media. |
| `MyAsyncImage` placeholder | Icon only | Icon + label |
| Desktop video `loadOnDemand` | Download button | Download button + label beneath it |
| Audio card | "MP3 · 4.8 MB" from imeta only | Same, falling back to the probe |

The label's type word comes from the content's class and MIME type: Image, Video, Audio, PDF.
`MyAsyncImage` always says Image. New keys in `commonsUI` `composeResources/values/strings.xml`:
`media_kind_image`, `media_kind_video`, `media_kind_audio`, `media_kind_pdf`, and the format
string `media_type_and_size` = `%1$s · %2$s`. Existing "Video"/"Audio" keys such as `kind_video`
are not reused: they belong to other screens, and sharing a key ties their translations together.

**Behaviour change, deliberate:** in the no-blur, no-`dim` row, tapping used to open the URL in
a browser. It now loads the media in place, like every other placeholder. The URL stays
reachable: the host is shown, and if loading fails the error state already shows the full
clickable URL (`ClickableUrlOrBlossom`).

### 5. Formatting

Move `formatFileSize` from `PlayableMediaCards.kt` to `commons/.../util/NumberFormatters.kt`
and add a GB step ("1.2 GB"). It shows "850 KB", "12.4 MB" and "1.2 GB". The audio card and the
new label both use it. `formatBytes` and `countToHumanReadableBytes` are left as they are;
merging them is out of scope.

## Error handling

| Situation | Result |
|---|---|
| imeta `size` present | Shown on the first frame, no request |
| `HEAD` in flight | Type only; size fades in |
| Non-2xx (403 error page, 404, 405 `HEAD` not allowed) | Type only, cached as a miss |
| 2xx without `Content-Length` (chunked, Flickr) | Type only, cached as a miss |
| Network error or timeout | Type only, cached as a miss for this process |
| Placeholder scrolled away mid-request | Request cancelled with the composition |
| HLS / live stream | No probe (a playlist's length means nothing); type only |

## Testing

- `commons` `commonTest`: `RichTextParser` fills `sizeBytes` for an image, a video and a PDF
  from imeta and from a URL fragment.
- `commons` `jvmTest`, with MockWebServer as in `OnlineCheckerTest`:
  - a 200 with a length returns it;
  - a 403 with a body returns null;
  - a 200 with no length (chunked) returns null;
  - a second call to the same URL makes no request;
  - two concurrent calls make one request;
  - the request method is `HEAD`.
- `formatFileSize`: KB, MB and GB boundaries.
- Manual, Android device with auto-download off:
  - a Primal video note (no blur, `dim` known);
  - an Amethyst image note (imeta `size`);
  - a bare image URL without imeta;
  - an audio note.
- Manual, Desktop: `tools/desktop-run/run-headless.sh` with video loading off.

## Out of scope

- Website link previews.
- Desktop note images appear to load whatever the auto-download setting says
  (`DesktopNotePlatform.MediaImage` does not check `showImages()`). That is a separate bug, to
  confirm and fix on its own.
- Merging the three byte formatters.
- A setting to turn the `HEAD` probe off.
