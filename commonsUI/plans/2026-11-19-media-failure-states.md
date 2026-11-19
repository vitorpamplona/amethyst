# What to show when note media fails to load: design review

Status: steps 0–3 are implemented for images and GIFs (Blossom fallback, `MediaLoadFailure`,
`MediaFailureCard`, wired inline and fullscreen on both platforms). Video, audio, NIP-92
`fallback` and auto-retry are not.

## The case that started this

A Ditto kind 1 note whose image lives at `https://24242.io/4e91…4fee.jpg`, with a full `imeta`
(`dim 1466x1920`, blurhash, `x`). 24242.io answers **404** for the blob (with or without the
extension). The same sha256 is on `blossom.ditto.pub` and `blossom.primal.net`, and
`blossom.primal.net` is first in the author's BUD-03 server list (kind 10063).

What the viewer saw: the blurhash, at the full portrait ratio, forever. Nothing says the image
failed, so the card reads as "still loading" and the user waits.

## What the app shows today

Survey of every media surface (file references are as of this review):

| Surface | Failure today | Problem |
|---|---|---|
| Android inline image (`UrlImageView`) | The blurhash box is replaced by the raw URL as link text | The layout collapses from a 1466x1920 box to one line, and a URL isn't an explanation |
| Desktop inline image (`DesktopNotePlatform.MediaImage`) | Known ratio: **nothing changes**, the blurhash stays. Unknown ratio: the raw URL | Looks exactly like loading, which is the reported bug |
| Android GIF (`GifVideoView`) | Raw URL | As above |
| Desktop GIF (`AnimatedGifImage`) | No handling: blurhash or an empty box | Looks like loading |
| Galleries, picture feed, shorts | Each tile behaves like its inline view (raw URL inside a cropped tile on Android; blurhash on desktop) | A URL in a 100dp tile is unreadable |
| Android fullscreen (`ZoomableContentDialog`) | Raw URL on the dark pager | |
| Desktop fullscreen (`ZoomableImage`) | Blank: the Error state only sets `loaded = true` | Blank screen with no way to tell why |
| Android local (uploading) image | `BlankNote`: "Event is loading or can't be found in your relay list" | Wrong copy |
| Android video (`RenderPlaybackError`) | Dimmed overlay, `VideocamOff`, "Can't play this video", "Open in browser" | Good shape, but the copy always blames the codec, even on a 404 or a timeout |
| Desktop video (`PlaybackErrorMessage`) | "Can't play this video" plus the **raw engine error string** | Engine strings aren't for users |
| Audio cards (both) | No handling: the card stays idle | Play does nothing, silently |
| Encrypted media (NIP-17, Marmot) | Missing key: "Could not decrypt the message". A failed download looks like any other failure. `EncryptedBlobInterceptor` returns an already-consumed body on decrypt failure | Decrypt failure and 404 look the same |
| Hash mismatch (Android only) | Red `Report` badge, explained by a dialog | Fine; desktop has no equivalent |
| Avatars, banners | NIP-92 `fallback` URLs, then robohash or the default banner | Fine: this is the model to follow |
| URL preview image | No handling: an empty box up to 200dp tall | Looks like loading |

Nothing in note media distinguishes a 404 from a timeout from a decode failure. The only
throwable checks are the URL-preview favicon (`HttpException`) and the 401 read-auth retry.

## Principles

1. **Recover before telling.** Blossom blobs are content-addressed and usually mirrored, and NIP-92
   `imeta` can list `fallback` URLs. Try those first, silently, while the loading state stays up.
   Show a failure only when nothing else is left to try.
2. **A failure must never look like loading.** A blurhash means "on its way". When nothing is on
   its way, the blurhash has to be dimmed and the card has to say so.
3. **Keep the space.** Don't collapse a 1466x1920 slot to one line under the reader's thumb. The
   failure card takes the slot the loading state had (declared `dim`, or the cached ratio), capped
   at the inline max height. Without a known shape, use a compact row, not a raw URL.
4. **Say what happened, in words.** Different causes need different next steps, so they get
   different copy. The URL is one tap away (Copy link, Open in browser), never the message itself.
5. **One component, every surface.** Image, GIF, gallery tile, fullscreen, video and audio all use
   the same shared card and the same cause classification, on Android and desktop. Today there are
   six different behaviours.
6. **Name the host.** "24242.io doesn't have this image anymore" tells the user that it's the
   server, not the app or the note.

## Recovery order (before any failure UI)

0. **Blossom fallback (implemented with this review).** When a URL whose last segment is
   `<sha256>[.ext]` fails with 404, 410, 5xx or a network error, ask the BUD-10 resolver for the
   same hash on the author's kind 10063 servers and the viewer's own servers, then load it from
   the first server that answers its HEAD. The bytes are filed under the blob's own disk-cache key
   (`blossom:<sha256>`, as `blossom:` URIs are). Answers about access (401, 402, 403) are not
   overridden.
   - `BlossomFallbackFetcher` (commonsUI `jvmAndroid`) wraps Coil's network fetcher in
     `OkHttpFactory`, so it covers inline images, gallery tiles, fullscreen and the URL preview's
     image on both platforms. It is `suspend`, so no OkHttp dispatcher thread is held.
   - `BlossomAuthorHints` (commons) maps blob sha256 to the authors of the notes that showed it.
     `RichTextParser.createMediaContent` fills it, and text notes now pass their author to the
     parser (`RenderTextEvent`, and the matching feed prefetch key).
   - Not yet covered: video and audio (ExoPlayer and the desktop players don't go through Coil),
     GIFs on desktop (they skip Coil), and Android profile pictures (they have their own fetcher,
     and robohash already covers them).
1. **NIP-92 `fallback` URLs.** `FallbackUrlState` already walks them for avatars and banners. Note
   media should do the same: parse `fallback` from the `imeta` into `MediaUrlContent`, and walk them
   before step 0 (they are the author's own, explicit mirrors).
2. **Automatic retry on reconnect.** When the failure was "offline", retry once the connectivity
   flow says the device is back online, with no tap needed.

## The failure card

### Causes and copy

Classify once, in a shared `MediaLoadFailure` (commons, headless), from Coil's throwable or
ExoPlayer's error code:

| Cause | From | Title | Detail | Primary action |
|---|---|---|---|---|
| `Gone` | 404, 410, after every fallback | Image no longer available | `{host}` doesn't have this file anymore | Open in browser |
| `Unreachable` | DNS, connect, TLS, timeout, 5xx | Couldn't reach `{host}` | The server didn't answer | **Retry** |
| `Refused` | 401, 403 (after read-auth) | `{host}` won't share this file | It may be private | Open in browser |
| `PaymentRequired` | 402 | `{host}` asks for payment to show this | — | Open in browser |
| `NotMedia` | Decode failure, an HTML page answering 200 | This isn't an image the app can show | — | Open in browser |
| `Offline` | Device has no network | You're offline | It will load when you're back online | (auto) |
| `CantDecrypt` | Missing key, decrypt failure | Can't decrypt this file | — | — |

Implemented so far: `Gone`, `Unreachable`, `Refused`, `PaymentRequired`, `NotMedia`, plus an
`Unknown` fallback ("Couldn't load this image", Retry). `Offline` needs the connectivity state, so an
offline device reads as `Unreachable` for now; `CantDecrypt` waits on the interceptor change below.

"Image" becomes "Video", "GIF", "Audio" or "File" by media kind (separate keys, not
concatenation, for translators). Video keeps its codec copy only for a real codec error.

### Layout, by available space

Built on the held-back placeholder (`MediaDownloadCard` / `MediaDownloadRow` in
`MediaSizeHint.kt`), so "not loaded yet" and "couldn't load" are the same family of card and
differ in icon and words:

- **Full (≥ 190dp tall):** the blurhash dimmed with a 60% scrim (or `surfaceVariant` when there is
  no hash), and centred over it a 40dp icon, the title, the detail line, and one tonal button.
  This is the shape `RenderPlaybackError` already uses for video, now shared.
- **Medium (120–190dp):** icon and title; the action is a tap on the card.
- **Tile (< 120dp, gallery cells):** the icon only, on the dimmed blurhash. Tap opens the fullscreen
  viewer, which shows the full card.
- **No known shape:** a `MediaDownloadRow`-style row: icon, title, host. No raw URL.

Long-press (desktop: right-click) opens the same menu as a loaded image: Copy link, Open in
browser, Share. Tapping a `Retry` card retries. Tapping a `Gone` card opens the fullscreen viewer,
whose top bar has Open in browser and Copy link.

### Icons

`MaterialSymbols` has `ErrorOutline`, `Refresh`, `OpenInBrowser` and `VideocamOff`, but nothing
for a missing image. Add `HideImage` (images, GIFs), `CloudOff` (`Unreachable`, `Offline`) and
`MusicOff` (audio), then run `tools/material-symbols-subset/subset.sh`.

## Per-surface changes

- **Android `UrlImageView`, `GifVideoView`, `ZoomableContentDialog`:** replace
  `ClickableUrlOrBlossom` on Error with the card. Fix the local-image `BlankNote` copy.
- **Desktop `MediaImage`, `AnimatedGifImage`, `ZoomableImage`:** handle Error at all; today the
  blurhash and the blank viewer are the bug.
- **Video, both:** classify the error. Keep the overlay; drop the raw engine string on desktop.
- **Audio, both:** add an error field to `AudioPlaybackUi` and show the row-sized card in place of
  the play button.
- **URL preview image:** collapse the image area on failure; the text part of the card still works.
- **Encrypted media:** make `EncryptedBlobInterceptor` fail with a typed exception rather than
  return a consumed body, so `CantDecrypt` can be told apart from `Gone`.

## Plan

0. Blossom fallback. **Done** (`BlossomFallbackFetcher`, `BlossomAuthorHints`).
1. `MediaLoadFailure` (commons) and `mediaLoadFailureOf` (commonsUI, from Coil's throwable).
   **Done**; ExoPlayer's error codes are still to map.
2. `MediaFailureCard` in `commonsUI` (the three sizes and the row), strings, icons
   (`HideImage`, `CloudOff`, `BrokenImage`). **Done**; `MediaFailureCardRenderTest` renders every
   cause at every size, light and dark, to `commonsUI/build/media-failure-card/`.
3. Images and GIFs on both platforms, inline and fullscreen. **Done**: Android `UrlImageView`
   (which also serves its fullscreen viewer) and `GifVideoView`; desktop `MediaImage` (images
   and GIFs) and `ZoomableImage`. Not yet: the Android local (uploading) image's `BlankNote`.
4. Video and audio, using the same classifier.
5. NIP-92 `fallback` for note media; Blossom fallback for the video data source.
6. Auto-retry on reconnect.

## Open questions

- Should a `Gone` image in a feed keep its full declared height, or shrink to a medium card after a
  few seconds? Keeping the height avoids a jump but spends a screen of space on nothing for tall
  portraits. Proposal: keep it while the note is on screen, and use the medium height when the note
  is composed again.
- Should the viewer be told the image was loaded from another server? Proposal: no badge, but say
  "Loaded from blossom.primal.net" in the fullscreen viewer's info sheet. On Android the `x` hash
  check already flags a mirror that serves different bytes; desktop has no such check yet.
