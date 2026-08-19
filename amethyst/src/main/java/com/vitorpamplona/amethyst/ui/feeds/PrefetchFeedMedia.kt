/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.feeds

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip71Video.image
import com.vitorpamplona.quartz.nip73ExternalIds.scope
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.UrlTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** How many notes on each side of the viewport to warm. */
private const val PREFETCH_RADIUS = 3

private const val MIME_TYPE_KEY = "m"

private const val DIM_KEY = "dim"

/**
 * How many levels of secret (variation-selector-encoded) emoji to unwrap while
 * warming. 2 covers the realistic case — a note's secret, plus a secret nested in
 * the revealed body — without letting a hand-crafted chain fan out unbounded.
 */
private const val SECRET_EMOJI_DEPTH = 2

/**
 * Warms the feed notes just outside the viewport — on **both** sides, so scrolling
 * either direction is covered — off the main thread, so they are ready by the time
 * the user reaches them. For each note [warm] does three things:
 *
 *  1. **Pre-parses** the rich-text body into [CachedRichTextParser]'s shared cache
 *     using the exact same key the renderer will use, so the scroll-time
 *     composition becomes a cache hit instead of parsing on the UI thread. This is
 *     only possible for kinds whose render key we can reproduce (text notes and
 *     comments); other kinds still get their media/links prefetched (below), just
 *     without the render-cache warm.
 *  2. **Prefetches images** — inline content images and video poster frames, from
 *     both NIP-92 imeta tags (every kind) and the body — into Coil's cache (gated
 *     on [AccountViewModel] `showImages()`), recording each decoded aspect ratio so
 *     the composable reserves the right space on first layout (no jump).
 *  3. **Warms OpenGraph/link previews** for non-media URLs via [UrlCachedPreviewer]
 *     (gated on `showUrlPreview()`), so link cards render instantly.
 *  4. **Unwraps secret emoji** and does 1-3 for the body each one hides, so the
 *     message revealed on tap is already cached (see [WarmTargets.harvest]). The
 *     posts *quoted* inside a secret are handled by [PrefetchSecretEmojiMedia],
 *     which needs a composition to hold the relay subscription.
 *
 * A `warmed` set keeps back-and-forth scrolling cheap: a note already processed is
 * skipped entirely. `collectLatest` also cancels a stale pass as soon as the
 * visible range changes. The only main-thread cost is reading the visible item
 * keys in the snapshot flow.
 *
 * Video *bytes* are deliberately not prefetched — they are large, often HLS
 * (whose prefix can't be cleanly pre-cached), and the player pool already starts
 * fast once a video is on screen. Warming the poster covers the visible card.
 */
@Composable
fun PrefetchFeedMedia(
    listState: LazyListState,
    notes: ImmutableList<Note>,
    accountViewModel: AccountViewModel,
    radius: Int = PREFETCH_RADIUS,
) {
    val visibleEnds = remember(listState) { listState.visibleEnds() }
    PrefetchVisibleMedia(listState, visibleEnds, notes, accountViewModel, radius)
}

/** Grid variant: identical behavior, driven by a [LazyGridState] (galleries, discover, products). */
@Composable
fun PrefetchFeedMedia(
    gridState: LazyGridState,
    notes: ImmutableList<Note>,
    accountViewModel: AccountViewModel,
    radius: Int = PREFETCH_RADIUS,
) {
    val visibleEnds = remember(gridState) { gridState.visibleEnds() }
    PrefetchVisibleMedia(gridState, visibleEnds, notes, accountViewModel, radius)
}

@Composable
private fun PrefetchVisibleMedia(
    stateKey: Any,
    visibleEnds: Flow<VisibleEnds>,
    notes: ImmutableList<Note>,
    accountViewModel: AccountViewModel,
    radius: Int,
) {
    val context = LocalContext.current
    val currentNotes by rememberUpdatedState(notes)
    val warmed = remember(stateKey) { ConcurrentHashMap.newKeySet<String>() }

    LaunchedEffect(stateKey, accountViewModel) {
        visibleEnds.collectLatest { ends ->
            withContext(Dispatchers.Default) {
                currentNotes.notesAround(ends, radius).forEach { note ->
                    if (note.idHex !in warmed) {
                        note.warm(context, accountViewModel)
                        warmed.add(note.idHex)
                    }
                }
            }
        }
    }
}

/**
 * Prefetches media for a loaded feed. The shared entry point used by the feed
 * dispatchers (`RenderFeedContentState` and `RenderFeedState`), so every list feed
 * that routes through them gets prefetching without per-screen wiring.
 */
@Composable
fun PrefetchLoadedFeedMedia(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    PrefetchFeedMedia(listState, items.list, accountViewModel)
}

/** Grid variant of [PrefetchLoadedFeedMedia] for [LazyGridState]-backed feeds. */
@Composable
fun PrefetchLoadedFeedMedia(
    loaded: FeedState.Loaded,
    gridState: LazyGridState,
    accountViewModel: AccountViewModel,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    PrefetchFeedMedia(gridState, items.list, accountViewModel)
}

/** The keys (note `idHex`) of the first and last visible feed items. */
private data class VisibleEnds(
    val firstKey: String?,
    val lastKey: String?,
)

/** Emits the visible range ends, once per change. */
private fun LazyListState.visibleEnds(): Flow<VisibleEnds> =
    snapshotFlow {
        val visible = layoutInfo.visibleItemsInfo
        VisibleEnds(visible.firstOrNull()?.key as? String, visible.lastOrNull()?.key as? String)
    }.distinctUntilChanged()

/** Grid equivalent of [visibleEnds]. */
private fun LazyGridState.visibleEnds(): Flow<VisibleEnds> =
    snapshotFlow {
        val visible = layoutInfo.visibleItemsInfo
        VisibleEnds(visible.firstOrNull()?.key as? String, visible.lastOrNull()?.key as? String)
    }.distinctUntilChanged()

/** Up to [radius] notes immediately before the first visible and after the last visible. */
private fun List<Note>.notesAround(
    ends: VisibleEnds,
    radius: Int,
): List<Note> = notesBefore(ends.firstKey, radius) + notesAfter(ends.lastKey, radius)

private fun List<Note>.notesBefore(
    key: String?,
    count: Int,
): List<Note> {
    val idx = key?.let { k -> indexOfFirst { it.idHex == k } } ?: -1
    if (idx <= 0) return emptyList()
    return subList(maxOf(0, idx - count), idx)
}

private fun List<Note>.notesAfter(
    key: String?,
    count: Int,
): List<Note> {
    val idx = key?.let { k -> indexOfFirst { it.idHex == k } } ?: -1
    if (idx < 0) return emptyList()
    return subList(minOf(idx + 1, size), minOf(idx + 1 + count, size))
}

/** Prefetches this note's media into Coil and warms its link previews, honoring the data-saver gates. */
internal fun Note.warm(
    context: Context,
    accountViewModel: AccountViewModel,
) {
    collectWarmTargets()?.warm(context, accountViewModel)
}

/** The set of URLs worth warming ahead of a note scrolling into view. */
internal class WarmTargets {
    // image URL -> a video URL whose layout box should also be seeded from this
    // image's (poster's) decoded aspect ratio, or null for a plain image.
    private val images = LinkedHashMap<String, String?>()
    val links = LinkedHashSet<String>()

    fun image(url: String) {
        images.putIfAbsent(url, null)
    }

    /** A video poster: warmed as an image, and its decoded ratio also seeds [videoUrl]'s box (if non-null). */
    fun videoPoster(
        posterUrl: String,
        videoUrl: String?,
    ) {
        // Never downgrade an existing video association back to null.
        images[posterUrl] = videoUrl ?: images[posterUrl]
    }

    fun link(url: String) {
        links.add(url)
    }

    fun forEachImage(action: (url: String, videoUrlForDims: String?) -> Unit) {
        images.forEach { (url, videoUrl) -> action(url, videoUrl) }
    }

    /** Sends everything harvested so far to Coil / the link previewer, honoring the data-saver gates. */
    fun warm(
        context: Context,
        accountViewModel: AccountViewModel,
    ) {
        if (accountViewModel.settings.showImages()) {
            forEachImage(context::prefetchImage)
        }
        if (accountViewModel.settings.showUrlPreview()) {
            links.forEach { url ->
                if (UrlCachedPreviewer.cache.get(url) == null) {
                    accountViewModel.urlPreview(url) {}
                }
            }
        }
    }

    /**
     * Harvest media and links from a fully parsed rich-text body, including the
     * bodies hidden inside its secret (variation-selector-encoded) emoji — see
     * [harvestSecrets].
     */
    fun harvest(
        state: RichTextViewerState,
        secretDepth: Int = SECRET_EMOJI_DEPTH,
    ) {
        state.mediaList.forEach { media ->
            when (media) {
                is MediaUrlImage -> image(media.url)
                is MediaUrlVideo ->
                    media.artworkUri?.let { poster ->
                        videoPoster(poster, media.url.takeIf { media.dim == null })
                    }
            }
        }
        state.urlSet.withScheme.forEach { url ->
            if (!RichTextParser.isImageOrVideoUrl(url)) link(url)
        }
        harvestSecrets(state, secretDepth)
    }

    /**
     * Walks the parsed body for [SecretEmoji] words — an emoji carrying a message
     * hidden in trailing variation selectors — and harvests the media of the
     * message each one reveals on tap. Nothing about the hidden body is shown until
     * the tap, so without this the reveal starts every image download from scratch.
     *
     * The hidden body is parsed through [CachedRichTextParser] with the very key
     * `DisplaySecretEmoji` uses — the host note's tags, no callbackUri, no author —
     * so the reveal is a parse cache hit as well as an image cache hit. A hidden
     * body may itself contain a secret emoji, hence [secretDepth].
     */
    private fun harvestSecrets(
        state: RichTextViewerState,
        secretDepth: Int,
    ) {
        if (secretDepth <= 0) return
        state.paragraphs.forEach { paragraph ->
            paragraph.words.forEach { word ->
                if (word is SecretEmoji) harvestSecret(word.segmentText, state.tags, secretDepth)
            }
        }
    }

    private fun harvestSecret(
        codedEmoji: String,
        tags: ImmutableListOfLists<String>,
        secretDepth: Int,
    ) {
        val decoded = EmojiCoder.decode(codedEmoji)
        if (decoded.isBlank()) return
        harvest(CachedRichTextParser.parseText(decoded, tags), secretDepth - 1)
    }

    /**
     * Lightweight discovery for kinds we don't render-warm: pull http(s) URLs from
     * the body and classify them by extension. A bare content video has no poster
     * we can cheaply warm, so it is skipped.
     */
    fun harvestContentUrls(
        content: String,
        secretDepth: Int = SECRET_EMOJI_DEPTH,
    ) {
        UrlParser().parseValidUrls(content).withScheme.forEach { url ->
            when {
                RichTextParser.isImageUrl(url) -> image(url)
                RichTextParser.isVideoUrl(url) -> Unit
                else -> link(url)
            }
        }
        harvestSecretContentUrls(content, secretDepth)
    }

    /**
     * The [harvestContentUrls] counterpart of [harvestSecrets]: decodes each secret
     * emoji in a body we can't render-warm and scans the revealed text for URLs.
     * Deliberately does *not* go through [CachedRichTextParser] — we can't reproduce
     * these kinds' render keys, so parsing here would only add a dead entry that
     * evicts a live one.
     */
    private fun harvestSecretContentUrls(
        content: String,
        secretDepth: Int,
    ) {
        if (secretDepth <= 0) return
        // Cheap gate: no variation selector in the body means no secret emoji, and
        // splitting it into words would be wasted work.
        if (!content.hasVariationSelector()) return

        content.splitToSequence(' ', '\n', '\t').forEach { word ->
            if (EmojiCoder.isCoded(word)) {
                val decoded = EmojiCoder.decode(word)
                if (decoded.isNotBlank()) harvestContentUrls(decoded, secretDepth - 1)
            }
        }
    }

    /**
     * Adds NIP-94 file-header media: blobs declared as top-level `url` tags (not
     * imeta), as used by FileHeaderEvent and the gallery/file kinds. Only image
     * files are prefetched — detected by the URL extension or the event's top-level
     * `m` mime type (a hashed URL may carry no extension). The decode listener
     * records their dims like any other image.
     */
    fun addFileHeaderMedia(event: Event) {
        val urls = event.tags.mapNotNull(UrlTag::parse)
        if (urls.isEmpty()) return
        val imageMime = event.tags.firstNotNullOfOrNull(MimeTypeTag::parse)?.startsWith("image/") == true
        urls.forEach { url ->
            if (imageMime || RichTextParser.isImageUrl(url)) image(url)
        }
    }
}

/**
 * True when [this] contains any character a secret emoji's payload is made of: a
 * VS1..VS16 selector (BMP) or the single high surrogate (`\uDB40`) every selector
 * in the VS17..VS256 supplement block starts with. A pure `Char` scan, so it stays
 * allocation-free on the overwhelmingly common no-secret body.
 */
private fun String.hasVariationSelector(): Boolean =
    any { c ->
        c in '\uFE00'..'\uFE0F' || c == '\uDB40'
    }

/**
 * Collects everything worth warming for this note. Reads NIP-92 imeta tags (every
 * kind), the NIP-73 external-content scope (kind 1111 only), then the free-text
 * body: text notes and comments go through the shared render cache — which both
 * warms the renderer and yields a fully classified body — while every other kind
 * gets a cheap URL scan. Returns null only when the note has no event.
 */
private fun Note.collectWarmTargets(): WarmTargets? {
    val ev = event ?: return null
    val targets = WarmTargets()

    // Structured media declared in NIP-92 imeta tags (text-note attachments, and the
    // kinds whose typed accessors wrap imetas() — Picture, Video). Video imeta carries
    // its poster in the `image` field.
    ev.tags.imetas().forEach { meta ->
        if (meta.isImage()) targets.image(meta.url)
        meta.image()?.forEach { poster -> targets.videoPoster(poster, meta.videoUrlForPosterDims()) }
    }

    // NIP-94 file-header convention: the blob is a top-level `url` tag with sibling
    // `m`/`dim`, not imeta (FileHeaderEvent and the gallery/file kinds). The generic
    // imetas() above misses these entirely.
    targets.addFileHeaderMedia(ev)

    // NIP-73 external-content scope. Only kind 1111 carries one, so the type check
    // keeps every other kind out of the tag walk. Reuses scope() — the same accessor
    // the renderer uses — so we only warm a URL that will actually be shown.
    if (ev is CommentEvent) {
        (ev.scope() as? UrlId)?.let { targets.link(it.url) }
    }

    // Free-text body. The reproducible-key kinds parse through the shared cache
    // (which also warms the render); everything else is scanned for URLs only.
    val rendered = warmRenderCache(ev)
    if (rendered != null) {
        targets.harvest(rendered)
    } else {
        targets.harvestContentUrls(ev.content)
    }

    return targets
}

/**
 * Parses the body through [CachedRichTextParser] so the result lands in the exact
 * entry the composition later looks up — turning the scroll-time parse into a cache
 * hit. Returns the parsed state, or null when we can't reproduce the renderer's key.
 *
 * Deliberately limited to text notes and comments. The cache key is
 * `(content, tags, callbackUri, authorPubKey)`, and each renderer passes its own
 * shape: Picture uses `EmptyTagList`, Highlight uses `EmptyTagList` + `null`
 * callbackUri, Video/Poll/Git pass derived content vars, etc. We can only reproduce
 * the key for these two kinds (verified to hit at runtime). Parsing the others with
 * a guessed key would miss at render time *and* pollute this shared cache with dead
 * entries — evicting the text-note entries that do hit — so it would be a net loss.
 * Their media and links are still prefetched via [WarmTargets.harvestContentUrls].
 */
private fun Note.warmRenderCache(ev: Event): RichTextViewerState? {
    val body =
        when (ev) {
            is TextNoteEvent -> {
                // Matches RenderTextEvent: a subject is prepended when not already in the body.
                val subject = ev.subject()?.ifBlank { null }
                if (subject != null && !ev.content.contains(subject, ignoreCase = true)) {
                    "$subject\n\n${ev.content}"
                } else {
                    ev.content
                }
            }
            is CommentEvent -> ev.content
            else -> return null
        }

    val tags = ev.tags.toImmutableListOfLists()
    // callbackUri = note.toNostrUri(), authorPubKey = null — the values the feed passes.
    return CachedRichTextParser.parseText(body, tags, toNostrUri(), null)
}

/** Enqueues an image prefetch that also records its decoded aspect ratio. */
private fun Context.prefetchImage(
    url: String,
    videoUrlForDims: String?,
) {
    runCatching {
        imageLoader.enqueue(
            ImageRequest
                .Builder(this)
                .data(url)
                .listener(CaptureAspectRatio(url, videoUrlForDims))
                .build(),
        )
    }
}

/**
 * Records the decoded aspect ratio of a prefetched image so the composable can
 * reserve the right space on first layout (no jump) even when the note carries no
 * imeta `dim` — the renderer reads `MediaAspectRatioCache.get(url)` as its
 * fallback. We decode for the cache warm anyway, so this is essentially free.
 *
 * For a video poster, [videoUrlForDims] is the video URL; its box is seeded from
 * the poster's ratio (a poster is a frame of the video), but only when nothing is
 * cached yet, so we never stomp a real ratio. ExoPlayer's onVideoSizeChanged
 * corrects it to the exact size once the video plays.
 */
private class CaptureAspectRatio(
    private val imageUrl: String,
    private val videoUrlForDims: String?,
) : ImageRequest.Listener {
    override fun onSuccess(
        request: ImageRequest,
        result: SuccessResult,
    ) {
        val w = result.image.width
        val h = result.image.height
        MediaAspectRatioCache.add(imageUrl, w, h)
        if (videoUrlForDims != null && MediaAspectRatioCache.get(videoUrlForDims) == null) {
            MediaAspectRatioCache.add(videoUrlForDims, w, h)
        }
    }
}

/** The video URL this imeta's poster should seed dims for, or null when the video declares its own dim. */
private fun IMetaTag.videoUrlForPosterDims(): String? = url.takeIf { properties[DIM_KEY].isNullOrEmpty() }

/**
 * True only for image blobs. A missing mime type falls back to the URL extension;
 * we never treat video imeta as an image (its poster is warmed separately).
 */
private fun IMetaTag.isImage(): Boolean {
    val mimeType = properties[MIME_TYPE_KEY]?.firstOrNull()
    return mimeType?.startsWith("image/") ?: RichTextParser.isImageUrl(url)
}
