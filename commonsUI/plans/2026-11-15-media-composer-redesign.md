# Media in the composer: design review and prototypes

Status: proposal, with prototypes. Nothing in production changes yet.
Prototypes: `commonsUI/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/ui/prototypes/mediacomposer/`.
Run `main` in `PrototypeGallery.kt` from the IDE to click through them (tabs: Post, Reply,
Chat, Profile, Failure; the add buttons open the Studio), or render them all with
`./gradlew :commonsUI:jvmTest --tests "*MediaComposerPrototypesRenderTest*"` (PNGs land in
`commonsUI/build/media-composer-prototypes/`).

## Status

**Implemented in the note composer (`ShortNotePostScreen`):**

1. **Studio after every pick.** Picking files opens `MediaStudioDialog`
   (`commonsUI/.../note/creators/uploads/studio/`) instead of the `ImageVideoDescription` card.
   - Every file carries its own `MediaUploadOptions` (alt, sensitive, quality, strip metadata,
     H.265, GIF→MP4) on `SelectedMediaProcessing.options`.
   - The preview is the real compressed file (`MediaUploader.previewImageCompression`),
     zoomable, with hold-for-original. Each level shows the size and long edge it really produces.
   - Tools: Quality, Alt text (on-device suggestion for every image), Privacy. A server chip.
2. **Markers and upload on Post.** Add uploads nothing:
   - Each file leaves a `[[media:N]]` marker (`MediaMarkers`, commons) at the cursor, on its own
     line, drawn as a chip ("Photo 1") by `UrlUserTagOutputTransformation`. It's one replacement
     list with the mentions, so the spell checker stays aligned.
   - `MentionPreservingInputTransformation` keeps markers whole against IME partial edits.
   - `AttachedMediaStrip` under the text shows the files numbered like their chips, with an ALT
     badge (amber when missing), CW, and remove. Tapping a tile reopens the Studio ("Done").
   - `sendPostSync()` now returns a Boolean. It first uploads every attached file with its own
     options (`MultiOrchestrator.uploadEach`), then swaps markers for URLs and records each
     file's `imeta`, and only then builds and signs the event. If any upload fails it returns
     false: the screen stays, the text and the files are untouched, the strip names the file and
     the server's reason, and Post retries only what failed.
   - Markers never leave the device. Drafts save the text without them (the picked files can't
     travel with a draft), and link previews ignore them. A marker whose file is gone is
     dropped at Post.
   - Tests: `MediaMarkersTest`, `MultiOrchestratorUploadEachTest` (commons);
     `MediaStudioRenderTest` (commonsUI); `MediaStudioUiTest`, `MediaMarkerFieldUiTest`
     (desktopApp, real clicks and keys).

Not yet:
- crop and trim (step 3)
- inline blocks (B)
- the other composers, which still use `ImageVideoDescription`
- keeping attached files in a draft across app restarts

## 1. What we have today

Every screen that attaches a photo or video to text goes through one card,
`ImageVideoDescription` (`commonsUI/.../note/creators/uploads/ImageVideoDescription.kt`):

| Screen | File |
|---|---|
| New note / reply / quote | `screen/loggedIn/home/ShortNotePostScreen.kt` |
| NIP-22 comment | `note/nip22Comments/GenericCommentPostScreen.kt` |
| Public message | `notifications/publicMessages/NewPublicMessageScreen.kt` |
| Long-form | `discover/nip23LongForm/LongFormPostScreen.kt` |
| Git issue | `gitRepo/GitNewIssue.kt` |
| Classified | `discover/nip99Classifieds/NewProductScreen.kt` |
| Group DM (new) | `chats/privateDM/send/NewGroupDMScreen.kt` |
| Edit post | `actions/EditPostView.kt` |

Chats use their own copy of the same fields, `ChatFileUploadDialog` (`chats/utils/`). Picture posts
(kind 20) use `NewMediaView`. Avatars, banners and group icons upload straight away through
`UploadToDefaultServer` with no options at all.

The flow on a note:

1. Pick files. A bordered card appears under the text: a 20 sp title ("Add Image"), a close
   button, the thumbnails, a **File Server** spinner, a **Sensitive Content** switch with a
   one-line explainer, a **Description of the contents** field, an AI-suggestion chip, a **Remove
   private metadata** switch with an explainer, a **Media Quality** title with a 30-word
   explainer, the slider with its own label, a small compression preview with a "tap to zoom"
   line and two stats lines, then (video) an **H.265** switch with an explainer and an HDR hint,
   or (GIF) a **Convert GIF to MP4** switch with an explainer, and an **Add to Message** button.
2. "Add to Message" uploads right away, with the card's choices applied to every file.
3. On success the card goes and the URLs are pasted at the cursor. On failure a toast and the
   card stays.
4. Post is disabled while the card is open (`canPost()` checks `multiOrchestrator == null`) and
   while uploading.

### Problems

- **Two commits for one post.** The user decides twice ("Add to Message", then "Post") and pays
  for the upload before deciding to post. Files abandoned after "Add to Message" stay on the
  server. Post is greyed out with no reason given while the card is open.
- **No sense of place.** The URLs land wherever the cursor was when the upload finished, as raw
  `https://…` text. Before that, nothing in the text says where the media will go.
- **One set of options for all files.** Alt text, sensitivity and quality apply to every picked
  file. Alt text is per image by nature (NIP-92 `imeta alt` is per file).
- **The preview is split three ways.** The thumbnails at the top, the compression preview under
  the slider (fixed 1-box height, its own legend), and a second full-screen dialog
  (`CompressionZoomDialog`) to zoom into it. None of them is the zoomable viewer users already
  know from the feed (`ZoomableImageDialog`).
- **Verbose.** Roughly 120 words of explainer text are on screen for one photo before you scroll
  (counted from `strings.xml`). Seven controls are always visible, even though most users only
  ever touch alt text.
- **No editing.** No crop, rotate or trim anywhere. Users crop in another app and come back,
  and a 40-second clip uploads all 40 seconds.
- **Failure is a toast.** The error says what failed, but not which file, and offers nothing to
  do about it besides finding the card again.

## 2. Proposal

**One flow on every screen that takes media: pick → MediaStudio → result in place → tap to
reopen.** Picking files always opens the Studio, configured for the screen that asked. Its
confirm button hands the edited files back, and the screen shows them where they belong:
inline in a post, as a message in a chat, or in the avatar circle. Tapping a file reopens the
Studio on it. The screens themselves carry no media settings.

| 1. Pick → Studio ("Add") | 2. Added as an inline block | 3. A post with three files |
|---|---|---|
| ![](screenshots/1-post-pick-studio.png) | ![](screenshots/2-post-after-add.png) | ![](screenshots/3-post-inline.png) |

The Studio (prototype `MediaStudio.kt`) knows nothing about its host. It takes a list of drafts,
a `StudioConfig`, and confirm/cancel/remove callbacks:

| Host | Config | Confirm | Then |
|---|---|---|---|
| Note, comment, long-form, issue, classified (after a pick) | `postPick()`: all tools | **Add** | markers go in the text at the cursor, drawn as blocks (B) |
| Same, file tapped in the post | `postEdit()` | **Done** | closes; edits are already on the draft |
| Reply sheets, other small composers | same configs | Add / Done | chips + filmstrip (A): a 210 dp block doesn't fit |
| Chat / DM | `chat(caption)`: all tools + caption | **Send** (beside the caption) | the bubble uploads, then sends |
| Avatar | `avatar()`: crop only, locked 1:1, circle mask | **Save** | uploads right away |
| Banner | `banner()`: crop + quality, locked 3:1 | **Save** | uploads right away |

Cancel (the ✕, shown only when the Studio was opened by a pick) undoes the pick. Nothing reaches
the text or the chat until the user confirms, so backing out leaves no trace. Every option starts
at a default, so a user who changes nothing taps the confirm button once.

The prototypes below show each piece. Each addresses the numbered request in brackets.

### The model: a draft per file, uploaded on Post [1, 4]

`MediaDraft` (prototype `MediaDraft.kt`) holds everything about one picked file: crop, trim,
mute, quality stop, alt, sensitive, strip location, and an upload status
(`Local → Preparing → Uploading → Uploaded | Failed`).

Picking a file inserts a **marker** in the text at the cursor, on its own line:
`[[media:3]]`. The marker is plain text, so undo, cut/paste, drafts and reordering by moving text
all work with no extra code. The field never shows those characters: an `OutputTransformation`
draws it, the same mechanism `UrlUserTagOutputTransformation` uses to draw `nostr:npub…` as
`@Name`. A drawn replacement is one cursor unit, so Backspace after it removes the whole marker.

**Post** uploads every draft that isn't up yet, in parallel. Only when all have succeeded do
markers become URLs, and only then is the event signed. If any fail, the composer stays as it
was. Text, edits and the successful uploads are kept, and the failed file is marked. "Cancel"
during upload returns to editing. A file already uploaded is not re-sent when the user retries
(Blossom deduplicates by hash anyway).

Production mapping: `SelectedMediaProcessing` gains the per-file options; `ShortNotePostViewModel`
keeps `MultiOrchestrator` alive until `sendPostSync()` and runs `upload()` from there;
`canPost()` drops the `multiOrchestrator == null` clause; the `iMetaAttachments.replace()` loop
runs right before signing; and `insertUrlAtCursor` becomes "replace marker *n* with its URL". The
draft store must save markers with the local URIs, so a draft restored after process death can
re-resolve them, or drop them if the file is gone.

### A. Chips + filmstrip (`MarkerStyle.CHIPS`), for small composers [1, 3]

![A](screenshots/4-reply-compact-chips.png)

The marker reads as a chip in the text: `▣ Photo 1`, `▶ Video 2 · 0:15`. A filmstrip under the
text shows the files, numbered to match. Each tile carries only what is uploaded: its size, an
**ALT** badge (amber when missing, the nudge to add one), CW if sensitive, and remove. Tap a tile
to reopen the Studio (C). The server is one chip in the toolbar (`☁ blossom.primal.net ⌄`), not a
form field.

Cheapest to build: a span style on a text replacement, plus a row of tiles.

### B. Inline blocks inside the text field (`MarkerStyle.BLOCKS`), the default [1, 5]

| Dark | Light |
|---|---|
| ![B](screenshots/3-post-inline.png) | ![B light](screenshots/3-post-inline-light.png) |

The media is drawn inside the field, where it will appear in the note. The text reads top to
bottom exactly as the posted note will. How: the transformation turns the marker into a single
transparent character in its own paragraph, styled with a `ParagraphStyle` line height equal to
the card's height. The field's `onTextLayout` reports where that line landed, and the composer
places the card over the hole. No custom text engine, and still one `TextFieldState`.

A cursor next to a block selects the block (highlighted border, caret hidden: the tall line
would otherwise draw a 230 dp caret). Backspace then removes it, the way a block editor behaves.

Costs and risks, to check on device before choosing B over A:

- IME behaviour around the tall line on Android (Gboard's cursor handles, text selection
  handles dragged across a block).
- Accessibility: the card needs its own semantics node ordered between the text paragraphs.
- Spell checking (`SpellCheckedTextField`) has to skip the marker range.
- The card has a fixed height (210 dp) for now. Using the real aspect ratio needs the field
  width, which is known after the first layout.

**Recommendation: B for full composers, A for small ones** (reply sheets) where a 210 dp
block doesn't fit. Both reopen the same Studio.

### C. MediaStudio: the one place per-file choices live [2, 3, 5, 6]

| Quality | Hold for original | Crop | Trim | Alt | Privacy |
|---|---|---|---|---|---|
| ![](screenshots/c-studio-quality.png) | ![](screenshots/c-studio-hold-original.png) | ![](screenshots/c-studio-crop.png) | ![](screenshots/c-studio-trim.png) | ![](screenshots/c-studio-alt.png) | ![](screenshots/c-studio-privacy.png) |

It opens after every pick, and again when a file is tapped. The tools are Crop, Trim (video),
Quality, Alt text and Privacy, filtered by the host's `StudioConfig`. A strip of thumbnails
switches between the files of this pick (or of the post, when reopened).

- **The big picture is the compression preview** [2, 5]. It shows the file as it will be
  uploaded at the chosen stop, with pinch, double-tap and wheel zoom (`net.engawapg` zoomable, as
  `ZoomableImageDialog` and `CompressionZoomDialog` already use). **Hold for original** flips to
  the source in place and keeps the same zoom. This merges the thumbnail, the slider's small
  preview and `CompressionZoomDialog` into one surface. The badge on top says what you get:
  `Small · 1024×768 · 192 KB`.
- **Quality is four stops** that state their cost: Small / Standard / High / Original, each with
  its estimated size and long edge, or 720p / 1080p / 1440p / Original for video. That replaces
  the title, the 30-word explainer and the slider label. `MediaQualitySlider`'s real compression
  preview (`uploader.previewImageCompression`) already produces exact sizes per level, so the
  numbers can be real, not estimated.
- **Crop** [6]: aspect presets (Free, 1:1, 4:5, 16:9), drag corners or move the frame, a
  rule-of-thirds grid, Rotate, Mirror and Reset. It works in normalized coordinates, so it
  applies the same way to the full-resolution file at upload.
- **Trim** [6]: a filmstrip with two handles and the resulting length (`0:03 – 0:18 · 15 s`),
  plus a Sound switch (strip the audio track).
- **Alt text** per file, keeping today's on-device suggestion as a chip that clears it.
- **Privacy**: Sensitive (blurred until tapped) and Remove location (and camera details), one
  line each. H.265 and GIF→MP4 leave the default screen. H.265 becomes an app setting (it is a
  device-compatibility choice, not a per-post one). GIF→MP4 becomes automatic when the server
  limit or the size would benefit, with a "Keep as GIF" option in Quality.
- Edits are live on the draft, and nothing is uploaded here [4]. The confirm button belongs to
  the host: Add, Done, Send or Save.

Implementation notes:

- Image crop and rotate: decode with the region (`BitmapRegionDecoder` on Android, Skia
  `Image.makeSubset` on desktop) inside the existing compressor step, before resizing, so it costs
  no extra pass. Crop must run before `stripMetadata` and before blurhash and thumbhash.
- Video trim: Android's compressor (LightCompressor) has no clip option. Media3 Transformer
  (`ClippingConfiguration`, androidx, Apache-2.0) does trim and mute in the same encode, and
  could later replace LightCompressor. On desktop, ffmpeg `-ss/-to` (and `-an` for mute) in the
  pipeline that already exists in `DesktopMediaUploader`.
- Icons: the bundled Material Symbols subset has no `crop`, `rotate_90_degrees_cw`,
  `content_cut` or `high_quality`. The prototypes draw stand-ins. Production adds the codepoints
  and runs `tools/material-symbols-subset/subset.sh`.

### D. Upload on Post, and recovering from a failure [4]

| Posting | Failed (inline) | Failed (chips) |
|---|---|---|
| ![](screenshots/d-publishing.png) | ![](screenshots/d-failed-inline.png) | ![](screenshots/d-failed-chips.png) |

- Pressing Post locks the field, and the button counts files: `Uploading 1/3`. A 2 dp progress
  line under the top bar shows the overall progress. Every marker shows its own state: a ring on
  the card, a percentage on the chip, a check when done. **Cancel** returns to editing.
- Nothing is published until every file is up. On a failure the screen comes back: one banner
  names the file by its number (`Video 2 didn't upload`), gives the server's reason and
  "Nothing was posted.", and offers **Retry**, **Other server** and **Make smaller** (the obvious
  fix for a 413). The failed card turns red and carries its own Retry. Text, edits and the
  successful uploads are untouched.
- Leaving the screen while uploading should keep going in the background (the scheduled-posts
  service already publishes later), but that is a second step. The prototype keeps the user on
  the screen.

## 3. Words: before and after [3]

| Today | Proposed |
|---|---|
| "Add Image" card title, "Add to Message" button | gone: no card, no second commit |
| "File Server" spinner | `☁ blossom.primal.net ⌄` chip, only shown when there is media |
| "Sensitive Content" + "Adds sensitive content warning before showing this content" | "Sensitive" · "Blurred until tapped", in Privacy |
| "Description of the contents" + "A blue boat in a white sandy beach at sunset" | "Alt text" tab · "Describe it for people who can't see it" |
| "Remove private metadata" + "Attempts to strip private metadata from supported media files before uploading" | "Remove location" · "And camera details" |
| "Media Quality" + 30-word explainer + slider label | four labelled stops with their sizes |
| "Tap the image to zoom in and compare it with the original" + two stats lines | the picture is zoomable; "Hold for original"; one badge |
| "Use H.265/HEVC Codec" + explainer, HDR hint | moved to Settings › Media |
| "Convert GIF to MP4" + explainer | automatic, "Keep as GIF" in Quality |
| Toast "Failed to upload media" | banner naming the file, the reason, and three fixes |

About 120 words of explainer before the first scroll become under 15, and the defaults on the
composer screen are a single chip.

## 4. The same Studio on the other screens

| Chat: caption + Send | Chat: sending | Avatar: 1:1, circle | Banner: 3:1 | Profile after Save |
|---|---|---|---|---|
| ![](screenshots/5-chat-studio.png) | ![](screenshots/6-chat-sending.png) | ![](screenshots/7-avatar-studio.png) | ![](screenshots/8-banner-studio.png) | ![](screenshots/9-profile-saved.png) |

- **Chats** (replaces `ChatFileUploadDialog`): attach opens the Studio with a caption field and
  Send. Text already typed in the input moves into the caption, and back again on Cancel. The
  bubble appears at once and shows its upload, then sends (`HostPrototypes.kt`, `ChatPrototype`).
- **Avatar, banner and group icon** (today's `UploadToDefaultServer`, no options): the Studio
  locked to the shape the profile shows, with a circle mask for avatars. Today a wide photo
  becomes a centre-cut avatar the user never chose.
- **Picture posts** (`NewMediaView`, kind 20): the Studio is the screen, with the caption under
  it; the same as the chat config with "Post" as the label.
- **Long-form**: markers fit Markdown well (`![alt](url)` replaces the marker at publish).

## 5. Suggested order

1. Draft model + upload-on-Post in `ShortNotePostViewModel`, with chips (A) and the failure banner
   (D). This is the behaviour change, and it can ship on its own.
2. MediaStudio as a shared composable with `StudioConfig`, opened after every pick: Quality
   (real compression previews), Alt and Privacy. Retire `ImageVideoDescription`,
   `ChatFileUploadDialog` and `CompressionZoomDialog`.
3. Crop and rotate (both platforms), then Trim and mute (Media3 Transformer on Android, ffmpeg on
   desktop).
4. Inline blocks (B) in the note composer, after an on-device IME check.
5. Move the other hosts (comments, chats, long-form, classifieds, git issues, group DMs, avatar,
   banner, group icons) onto the Studio, each with its config.

## 6. What the prototypes are and aren't

They are Compose code in a test source set (`jvmTest`), so they ship in no artifact and their
English labels are hard-coded on purpose. Production strings go to `commonsUI` compose resources.
They run with a fake uploader (`PrototypeComposer.post()` simulates progress, and
`failNext` forces a failure), procedural stand-in images (`SyntheticMedia.kt`), and a simulated
compression (downscale and upscale) instead of real files. Crop, trim, zoom, hold-to-compare,
remove, retry and server switching all work interactively in the gallery window.

`PrototypeComposerBehaviourTest` checks the claims this doc makes about behaviour:

- Picking puts nothing in the text until Add; Add inserts the markers at the cursor in pick
  order; Cancel leaves the text and the draft list as they were.
- The avatar and banner configs crop to exactly 1:1 and 3:1 in output pixels.
- Post turns every marker into its URL only after all uploads succeed.
- A failure leaves the text and markers byte-for-byte unchanged, and Retry then finishes the post.
- Backspace after a chip, or after an inline block, removes the whole `[[media:n]]` marker
  (tested by sending a real key event to the field).

The screenshots in `screenshots/` are the render test's output, scaled down.
