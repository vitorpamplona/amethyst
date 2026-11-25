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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.convert_gif_to_mp4_label
import com.vitorpamplona.amethyst.commons.resources.file_server
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_original
import com.vitorpamplona.amethyst.commons.resources.media_studio_add
import com.vitorpamplona.amethyst.commons.resources.media_studio_alt_placeholder
import com.vitorpamplona.amethyst.commons.resources.media_studio_alt_suggested
import com.vitorpamplona.amethyst.commons.resources.media_studio_caption_placeholder
import com.vitorpamplona.amethyst.commons.resources.media_studio_compare_original
import com.vitorpamplona.amethyst.commons.resources.media_studio_gif_description
import com.vitorpamplona.amethyst.commons.resources.media_studio_h265
import com.vitorpamplona.amethyst.commons.resources.media_studio_h265_description
import com.vitorpamplona.amethyst.commons.resources.media_studio_long_edge
import com.vitorpamplona.amethyst.commons.resources.media_studio_no_options
import com.vitorpamplona.amethyst.commons.resources.media_studio_position
import com.vitorpamplona.amethyst.commons.resources.media_studio_remove_location
import com.vitorpamplona.amethyst.commons.resources.media_studio_remove_location_audio_description
import com.vitorpamplona.amethyst.commons.resources.media_studio_remove_location_description
import com.vitorpamplona.amethyst.commons.resources.media_studio_retry
import com.vitorpamplona.amethyst.commons.resources.media_studio_sensitive
import com.vitorpamplona.amethyst.commons.resources.media_studio_sensitive_description
import com.vitorpamplona.amethyst.commons.resources.media_studio_tool_alt
import com.vitorpamplona.amethyst.commons.resources.media_studio_tool_crop
import com.vitorpamplona.amethyst.commons.resources.media_studio_tool_privacy
import com.vitorpamplona.amethyst.commons.resources.media_studio_tool_quality
import com.vitorpamplona.amethyst.commons.resources.media_studio_tool_trim
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.resources.video_compression_needs_ffmpeg
import com.vitorpamplona.amethyst.commons.service.ai.AltTextSuggester
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.VideoAwareSliderSteps
import com.vitorpamplona.amethyst.commons.service.uploads.VideoCompressionSupport
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.FilePreviewPlaceholder
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.UploadProgressOverMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberVideoThumbnail
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ComparisonImages
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.longEdge
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.qualityName
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.videoPhrase
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.MouseWheelZoom
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.jetbrains.compose.resources.StringResource
import kotlin.math.min

/**
 * The media studio: what opens right after files are picked. Every choice belongs to one file
 * ([SelectedMediaProcessing.options]) and is made on a picture of that file:
 *
 * - The preview is the upload: a still image is shown compressed exactly as it will be sent, zoomable
 *   (pinch, double-tap, mouse wheel), and pressing and holding the picture flips to the source in place.
 * - Quality lists the platform's levels with the size and long edge each one produces.
 * - Alt text is per file, with the on-device suggestion when the platform has one.
 * - Privacy: the content warning and the metadata strip.
 *
 * Add uploads every file with its own options to the chosen server ([onAdd]); the studio stays up
 * while it runs and, if a file fails, shows why and lets Add try again. Design and the rest of the
 * plan: `commonsUI/plans/2026-11-15-media-composer-redesign.md`.
 */
@Composable
fun MediaStudioDialog(
    media: MultiOrchestrator,
    defaultServer: ServerName,
    isUploading: Boolean,
    onAdd: (ServerName) -> Unit,
    onRemove: (SelectedMediaProcessing) -> Unit,
    onCancel: () -> Unit,
    accountViewModel: AccountViewModel,
    initial: SelectedMediaProcessing? = null,
    confirmLabel: StringResource = Res.string.media_studio_add,
    canCancel: Boolean = true,
    showSensitive: Boolean = true,
    lockedServer: ServerName? = null,
    privacyExtras: (@Composable ColumnScope.() -> Unit)? = null,
    caption: String? = null,
    onCaptionChange: (String) -> Unit = {},
) {
    val accountServers by accountViewModel.account.blossomServers.hostNameFlow
        .collectAsState()
    // A chat that must upload to one server (a Buzz workspace's) offers no other.
    val servers = lockedServer?.let { listOf(it) } ?: accountServers
    val appServices = LocalAppServices.current
    val suggester = remember(appServices) { appServices.createAltTextSuggester() }
    DisposableEffect(suggester) { onDispose { suggester?.close() } }

    StudioDialog(onDismissRequest = onCancel, canDismiss = !isUploading) {
        MediaStudio(
            media = media,
            servers = servers,
            defaultServer = defaultServer,
            isUploading = isUploading,
            uploader = accountViewModel.mediaUploader,
            altSuggester = suggester,
            onAdd = onAdd,
            onRemove = onRemove,
            onCancel = onCancel,
            initial = initial,
            confirmLabel = confirmLabel,
            canCancel = canCancel,
            showSensitive = showSensitive,
            privacyExtras = privacyExtras,
            caption = caption,
            onCaptionChange = onCaptionChange,
        )
    }
}

enum class StudioTool { QUALITY, CROP, TRIM, ALT, PRIVACY }

/** The tools that do something for [media] on a platform with [support]. */
fun studioToolsFor(
    media: SelectedMedia,
    support: VideoCompressionSupport,
    /** The platform can trim a video ([MediaUploader.videoTrim]). */
    trim: Boolean = false,
    /** The platform can crop or trim a GIF, which stays a GIF ([MediaUploader.gifCrop], [MediaUploader.gifTrim]). */
    gifCrop: Boolean = false,
    gifTrim: Boolean = false,
    /** The platform can decode this still to crop it ([MediaUploader.canCrop]). */
    cropStill: Boolean = true,
): List<StudioTool> =
    buildList {
        val hasQuality =
            when {
                media.isVideo() == true -> support != VideoCompressionSupport.Unsupported
                media.isGif() -> support == VideoCompressionSupport.Supported
                else -> media.isCompressible()
            }
        if (hasQuality) add(StudioTool.QUALITY)
        if ((cropStill && media.canCrop() && media.isCompressible()) || (gifCrop && media.isGif())) add(StudioTool.CROP)
        if ((trim && media.isVideo() == true) || (gifTrim && media.isGif())) add(StudioTool.TRIM)
        if (media.isImage() == true || media.isVideo() == true) add(StudioTool.ALT)
        add(StudioTool.PRIVACY)
    }

/** The studio's content, without the dialog: what [MediaStudioDialog] shows, and what tests render. */
@Composable
fun MediaStudio(
    media: MultiOrchestrator,
    servers: List<ServerName>,
    defaultServer: ServerName,
    isUploading: Boolean,
    uploader: MediaUploader,
    altSuggester: AltTextSuggester?,
    onAdd: (ServerName) -> Unit,
    onRemove: (SelectedMediaProcessing) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initial: SelectedMediaProcessing? = null,
    confirmLabel: StringResource = Res.string.media_studio_add,
    canCancel: Boolean = true,
    /** False where nothing carries a content warning (a cordn blob). */
    showSensitive: Boolean = true,
    /** Switches a screen adds to Privacy, e.g. a NIP-17 chat's file encryption. */
    privacyExtras: (@Composable ColumnScope.() -> Unit)? = null,
    /** The post's own text, for a screen where the media is the post (picture and video posts); null shows no field. */
    caption: String? = null,
    onCaptionChange: (String) -> Unit = {},
    initialTool: StudioTool = StudioTool.QUALITY,
    holdingOriginal: Boolean = false,
) {
    val items = media.items()
    if (items.isEmpty()) return

    var selected by remember { mutableStateOf(initial?.takeIf { it in items } ?: items.first()) }
    val current = if (selected in items) selected else items.first()

    var pickedServer by remember { mutableStateOf<ServerName?>(null) }
    val server = pickedServer ?: servers.firstOrNull { it == defaultServer } ?: servers.firstOrNull() ?: DEFAULT_MEDIA_SERVERS[0]

    val previews = rememberCompressionPreviews(uploader)
    val cropped = rememberCroppedSources(uploader, previews)
    // The shape each picture's crop is held to while this studio is open.
    val cropAspects = remember { mutableStateMapOf<SelectedMediaProcessing, CropAspect>() }
    val tools = studioToolsFor(current.media, uploader.videoCompression, uploader.videoTrim, uploader.gifCrop, uploader.gifTrim, uploader.canCrop(current.media))
    val trimSession = remember(current) { TrimSession() }
    var picked by remember(current) { mutableStateOf(initialTool) }
    // The tools can change under the user: a "video" found to be sound loses its video tools.
    val tool = if (picked in tools) picked else tools.first()

    // Labels every file by what it holds before anything is offered for it (a voice memo saved as
    // .mp4 is audio, not a silent video).
    LaunchedEffect(items, uploader) { items.forEach { it.checkType(uploader) } }

    // Opening Crop on a picture cropped earlier holds the shape that crop has, once, so a free drag
    // passing through 1:1 doesn't lock it.
    val imageAspect = current.displayAspectRatio
    LaunchedEffect(current, tool, imageAspect) {
        if (tool == StudioTool.CROP && imageAspect != null && current !in cropAspects) cropAspects[current] = CropAspect.of(current.options.crop, imageAspect)
    }

    SuggestAltText(items, altSuggester)
    PreparePreviews(current, previews, cropped, isUploading || tool == StudioTool.CROP)

    // A trimmed GIF's copy is written once for the whole Studio: held while it is open, so moving
    // between tools (each stage a holder of its own) doesn't let it go and write it again.
    // Per file, so removing one doesn't let go of the others.
    items.forEach { item ->
        key(item) {
            DisposableEffect(item) {
                item.holdTrimmedGif()
                onDispose { item.releaseTrimmedGif() }
            }
        }
    }

    // Frames, lengths and probes read once for every part of the Studio, and let go with it.
    val readScope = rememberCoroutineScope()
    val reads = remember(readScope) { StudioReads(readScope) }

    CompositionLocalProvider(LocalStudioReads provides reads) {
        Column(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            StudioTopBar(
                position = items.indexOf(current) + 1,
                count = items.size,
                isUploading = isUploading,
                confirmLabel = confirmLabel,
                onCancel = onCancel.takeIf { canCancel },
                onAdd = { onAdd(server) },
            )

            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                // A video's first frame to be sent stays under every tool's stage, so switching tools
                // never shows a black frame while the next stage loads its own. Trim opens on a still
                // and needs the length: read them now, so the first visit to Trim doesn't open on
                // black with no times. Trim's stage draws that same frame itself.
                val videoStill = current.media.isVideo() == true && uploader.videoTrim
                val canTrim = StudioTool.TRIM in tools
                if (videoStill || (canTrim && current.media.isGif())) {
                    val still = rememberVideoFrame(current, uploader, current.options.trim?.startMs ?: 0L, STAGE_FRAME_EDGE)
                    if (still != null && videoStill && tool != StudioTool.TRIM) {
                        AsyncImage(model = still, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
                if (canTrim) rememberVideoDuration(current, uploader)
                // One file and a Cancel in the corner: removing it is the same as cancelling, so
                // only a batch (or a reopened file, which has no Cancel) gets its own remove.
                val removable = items.size > 1 || !canCancel
                if (tool == StudioTool.TRIM) {
                    StudioTrimStage(current, uploader, trimSession)
                } else if (tool == StudioTool.CROP) {
                    // The frame's top corners would sit under the remove button: the picture starts below it.
                    StudioCropStage(current, cropAspects[current] ?: CropAspect.FREE, enabled = !isUploading, topClearance = if (removable) REMOVE_BUTTON_BOTTOM else 0.dp)
                } else {
                    StudioPreview(current, previews, cropped, uploader, holdingOriginal, badgeEndInset = if (removable) REMOVE_BUTTON_INSET else 0.dp)
                }
                if (removable) {
                    // The touch target overhangs the circle, so it sits closer to the corner than the badges.
                    RemoveFileButton(enabled = !isUploading, onClick = { onRemove(current) }, modifier = Modifier.align(Alignment.TopEnd).padding(STAGE_OVERLAY_PADDING - REMOVE_BUTTON_OVERHANG))
                }
                UploadOverlay(current)
            }

            UploadErrorBanner(current, confirmLabel)

            if (items.size > 1) {
                // On the stage's black, in either theme: the strip belongs to the pictures, not the panel.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    items.forEachIndexed { index, item ->
                        key(item) {
                            Thumbnail(item, item === current, stringRes(Res.string.media_studio_position, index + 1, items.size)) { selected = item }
                        }
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(top = 12.dp)) {
                    // The post's own text heads the panel, in the same filled style as the tools below it.
                    if (caption != null) {
                        StudioTextField(
                            value = caption,
                            onValueChange = onCaptionChange,
                            placeholder = stringRes(Res.string.media_studio_caption_placeholder),
                            enabled = !isUploading,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                    // Each tool's panel has its own height: easing between them lets the stage above
                    // grow or shrink instead of jumping on every tab.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .heightIn(min = 112.dp)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (tool) {
                            StudioTool.QUALITY -> QualityTool(current, previews, cropped, uploader)
                            StudioTool.TRIM -> TrimTool(current, uploader, trimSession, enabled = !isUploading)
                            StudioTool.CROP ->
                                CropTool(
                                    current,
                                    aspect = cropAspects[current] ?: CropAspect.FREE,
                                    enabled = !isUploading,
                                    onAspect = { cropAspects[current] = it },
                                )
                            StudioTool.ALT -> AltTool(current)
                            StudioTool.PRIVACY -> PrivacyTool(current, uploader.videoCompression, showSensitive, privacyExtras)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The tabs keep their width; the server's name shortens into what is left.
                        Box(Modifier.weight(1f)) {
                            ServerChip(server, servers, enabled = !isUploading, onPick = { pickedServer = it })
                        }
                        tools.forEach { t -> ToolTab(t, t == tool) { picked = t } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    position: Int,
    count: Int,
    isUploading: Boolean,
    confirmLabel: StringResource,
    onCancel: (() -> Unit)?,
    onAdd: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onCancel != null) {
            IconButton(onClick = onCancel, enabled = !isUploading) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.cancel))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Text(
            if (count > 1) stringRes(Res.string.media_studio_position, position, count) else "",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // No spinner here while it uploads: the stage shows each file's progress and what it is doing.
        Button(onClick = onAdd, enabled = !isUploading, modifier = Modifier.padding(end = 8.dp).height(36.dp)) {
            Text(stringRes(confirmLabel))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Background work: alt suggestions and compressed previews
// ---------------------------------------------------------------------------------------------

/** Suggests alt text for each still image that has none, once per image, one at a time. */
@Composable
private fun SuggestAltText(
    items: List<SelectedMediaProcessing>,
    suggester: AltTextSuggester?,
) {
    if (suggester == null) return
    val tried = remember { mutableSetOf<SelectedMediaProcessing>() }
    LaunchedEffect(items, suggester) {
        items.forEach { item ->
            if (item in tried || item.media.isImage() != true || item.media.isGif()) return@forEach
            tried += item
            if (item.options.alt.isNotEmpty()) return@forEach
            val suggestion = suggester.suggestAltText(item.media.uri)
            if (suggestion != null && item.options.alt.isEmpty()) {
                item.options = item.options.copy(alt = suggestion, altSuggested = true)
            }
        }
    }
}

/**
 * Compresses the current image at its chosen level first, then at every other level so each stop
 * can show its real size. Stops while an upload runs: on desktop a preview encode would hold up
 * the upload queued behind it.
 */
@Composable
private fun PreparePreviews(
    item: SelectedMediaProcessing,
    previews: CompressionPreviews,
    cropped: CroppedSources,
    paused: Boolean,
) {
    val quality = item.options.quality
    val crop = item.options.crop
    LaunchedEffect(item, quality, crop, paused) {
        if (paused || item.media.isImage() != true || !item.media.isCompressible()) return@LaunchedEffect
        // Lets a fast tap through the levels settle before encoding.
        delay(250)
        cropped.prepare(item)
        val source = cropped.sourceFor(item) ?: return@LaunchedEffect
        previews.load(source, quality)
        CompressorQuality.sliderSteps.forEach { previews.load(source, it) }
    }
}

// ---------------------------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------------------------

@Composable
private fun StudioPreview(
    item: SelectedMediaProcessing,
    previews: CompressionPreviews,
    cropped: CroppedSources,
    uploader: MediaUploader,
    forceOriginal: Boolean,
    /** Room kept clear at the top right for the stage's remove button. */
    badgeEndInset: Dp,
) {
    val media = item.media
    when {
        media.isImage() == true && media.isCompressible() -> {
            val source = cropped.sourceFor(item)
            if (source != null) {
                CompressedImagePreview(item, source, previews, forceOriginal, badgeEndInset)
            } else {
                // The crop's copy is being written: show the part it will hold meanwhile.
                Box(Modifier.fillMaxSize()) {
                    CroppedImage(media.uri.toString(), item.options.crop, item.displayAspectRatio, ContentScale.Fit, Modifier.fillMaxSize())
                    WorkingBadge(Modifier.align(Alignment.TopEnd).padding(top = STAGE_OVERLAY_PADDING, end = STAGE_OVERLAY_PADDING + badgeEndInset))
                }
            }
        }

        media.isImage() == true -> {
            val zoomState = rememberZoomState()
            // A GIF or an animated WebP plays here as it will in the note, cropped and trimmed as it will be sent.
            CroppedImage(
                rememberTrimmedGif(item, uploader),
                item.options.crop,
                item.displayAspectRatio,
                ContentScale.Fit,
                Modifier.fillMaxSize().zoomable(zoomState, mouseWheelZoom = MouseWheelZoom.Enabled),
                onAspect = { item.displayAspectRatio = it },
                animated = media.isAnimated(),
            )
        }

        media.isVideo() == true -> VideoPreview(item, uploader)

        else ->
            FilePreviewPlaceholder(
                icon = fileSymbol(media),
                label = uploader.displayName(media.uri) ?: media.mimeType ?: "",
            )
    }
}

@Composable
private fun CompressedImagePreview(
    item: SelectedMediaProcessing,
    media: SelectedMedia,
    previews: CompressionPreviews,
    forceOriginal: Boolean,
    badgeEndInset: Dp,
) {
    val quality = item.options.quality
    val compresses = quality.imageMaxDimension != null
    val preview = if (compresses) previews[media, quality] else null
    val loading = compresses && !previews.isLoaded(media, quality)
    val original = (preview ?: previews.anyOf(media))?.original
    var holding by remember(item) { mutableStateOf(false) }
    val showOriginal = holding || forceOriginal || preview == null
    val zoomState = rememberZoomState()
    val compareLabel = stringRes(Res.string.media_studio_compare_original)

    // Pan limits follow the picture, not the letterboxed screen around it.
    LaunchedEffect(original) {
        val width = original?.width
        val height = original?.height
        if (width != null && height != null) zoomState.setContentSize(Size(width.toFloat(), height.toFloat()))
    }

    Box(Modifier.fillMaxSize()) {
        ComparisonImages(
            originalUri = media.uri.toString(),
            compressedUri = preview?.compressedUri?.toString(),
            showOriginal = showOriginal,
            fullResolution = true,
            originalLongEdge = original?.longEdge(),
            // Outside zoomable, so the hold sees the press before zoomable's pan and pinch do.
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (preview != null) {
                            // A screen reader can't hold a finger on the picture: its long-press action flips instead.
                            Modifier.holdToShowOriginal { holding = it }.semantics {
                                onLongClick(compareLabel) {
                                    holding = !holding
                                    true
                                }
                            }
                        } else {
                            Modifier
                        },
                    ).zoomable(zoomState, mouseWheelZoom = MouseWheelZoom.Enabled),
        )

        val stats = if (showOriginal) original else preview.compressed
        if (stats != null) {
            val name = if (showOriginal) stringRes(Res.string.media_compression_preview_original) else qualityName(quality)
            val parts =
                listOfNotNull(
                    name,
                    if (stats.width != null && stats.height != null) "${stats.width}×${stats.height}" else null,
                    stats.bytes?.let { formatFileSize(it) },
                )
            // Vertically centred on the remove button's row, which sits at the same top padding.
            MediaPill(parts.joinToString(" · "), Modifier.align(Alignment.TopStart).padding(start = STAGE_OVERLAY_PADDING, top = STAGE_PILL_TOP))
        }

        if (loading) WorkingBadge(Modifier.align(Alignment.TopEnd).padding(top = STAGE_OVERLAY_PADDING, end = STAGE_OVERLAY_PADDING + badgeEndInset))
    }
}

@Composable
private fun WorkingBadge(modifier: Modifier) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
    }
}

/** Waits until the press at [down] ends, moves past the touch slop or gains a finger; never returns otherwise. */
private suspend fun AwaitPointerEventScope.awaitReleaseOrMove(down: PointerInputChange) {
    while (true) {
        val changes = awaitPointerEvent(PointerEventPass.Initial).changes
        if (changes.count { it.pressed } != 1) return
        val finger = changes.first { it.pressed }
        if ((finger.position - down.position).getDistance() > viewConfiguration.touchSlop) return
    }
}

internal val STAGE_OVERLAY_PADDING = 12.dp
private val REMOVE_BUTTON_SIZE = 32.dp

/** A pill's top on the stage (a 22 dp line), centred on the remove button's circle. */
internal val STAGE_PILL_TOP = STAGE_OVERLAY_PADDING + 5.dp

/** Where the remove button's circle ends, from the stage's top. */
private val REMOVE_BUTTON_BOTTOM = STAGE_OVERLAY_PADDING + REMOVE_BUTTON_SIZE

/** How far the remove button's 48 dp touch target reaches past its circle on each side. */
private val REMOVE_BUTTON_OVERHANG = (48.dp - REMOVE_BUTTON_SIZE) / 2

/** What a badge at the stage's top right moves left by to clear the remove button. */
private val REMOVE_BUTTON_INSET = REMOVE_BUTTON_SIZE + 8.dp

/** Removes the file on the stage from the batch: a round button in the stage's top-right corner. */
@Composable
private fun RemoveFileButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .size(REMOVE_BUTTON_SIZE + REMOVE_BUTTON_OVERHANG * 2)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(REMOVE_BUTTON_SIZE).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.remove), tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Reports true while one finger (or the mouse) is held still on the picture past the long-press
 * timeout, and false when it lets go. A drag or a second finger before then is a pan or a pinch, and
 * is left to zoomable: nothing here consumes an event.
 */
private fun Modifier.holdToShowOriginal(onHold: (Boolean) -> Unit): Modifier =
    pointerInput(onHold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Null when the timeout ran out first: the press is a hold.
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitReleaseOrMove(down) }
            if (endedEarly != null) return@awaitEachGesture
            onHold(true)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                onHold(false)
            }
        }
    }

@Composable
private fun VideoPreview(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
) {
    // Where the platform can decode frames, the Studio draws the first frame to be sent under every
    // tool (see MediaStudio); this only says when it isn't there yet. Elsewhere, the thumbnail.
    val frame = if (uploader.videoTrim) rememberVideoFrame(item, uploader, item.options.trim?.startMs ?: 0L, STAGE_FRAME_EDGE) else null
    val thumbnail = if (!uploader.videoTrim) rememberVideoThumbnail(item.media) else null
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            thumbnail != null -> Image(thumbnail, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            frame == null -> Icon(MaterialSymbols.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        if (uploader.videoCompression == VideoCompressionSupport.Supported) {
            val probe = rememberVideoProbe(item, uploader)
            val quality = item.options.quality
            val label =
                listOfNotNull(
                    levelName(quality.videoLevel),
                    if (quality != CompressorQuality.UNCOMPRESSED && probe.done) videoPhrase(quality.videoLevel, probe.info) else null,
                    item.options.trim?.let { formatLength(it.durationMs) },
                )
            MediaPill(label.joinToString(" · "), Modifier.align(Alignment.TopStart).padding(12.dp))
        }
    }
}

/** A file's upload progress over its preview, while Add runs. */
@Composable
private fun UploadOverlay(item: SelectedMediaProcessing) {
    val status by item.orchestrator.status.collectAsState()
    if (status.state is UploadingState.Ready || status.state is UploadingState.Error) return
    UploadProgressOverMedia(status)
}

@Composable
private fun UploadErrorBanner(
    item: SelectedMediaProcessing,
    confirmLabel: StringResource,
) {
    val state by item.orchestrator.progressState.collectAsState()
    val error = state as? UploadingState.Error ?: return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringRes(error.errorResource, *error.params), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                Text(stringRes(Res.string.media_studio_retry, stringRes(confirmLabel)), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Thumbnail(
    item: SelectedMediaProcessing,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val state by item.orchestrator.progressState.collectAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(52.dp)
            .clip(shape)
            .border(
                2.dp,
                when {
                    state is UploadingState.Error -> MaterialTheme.colorScheme.error
                    selected -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                shape,
            ).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val media = item.media
        when {
            media.isImage() == true ->
                CroppedImage(
                    media.uri.toString(),
                    item.options.crop,
                    item.displayAspectRatio,
                    ContentScale.Crop,
                    Modifier.fillMaxSize(),
                    onAspect = { item.displayAspectRatio = it },
                    animated = media.isAnimated(),
                )
            media.isVideo() == true -> rememberVideoThumbnail(media)?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            else -> Icon(fileSymbol(media), contentDescription = null)
        }
        when (state) {
            is UploadingState.Error -> Icon(MaterialSymbols.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            is UploadingState.Finished -> Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = Color.White, filled = true, modifier = Modifier.size(20.dp))
            is UploadingState.Ready -> Unit
            else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
        }
    }
}

@Composable
private fun MediaPill(
    text: String,
    modifier: Modifier,
) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------------------------

@Composable
private fun ToolTab(
    tool: StudioTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val (symbol, label) =
        when (tool) {
            StudioTool.QUALITY -> MaterialSymbols.Tune to Res.string.media_studio_tool_quality
            StudioTool.CROP -> MaterialSymbols.Crop to Res.string.media_studio_tool_crop
            StudioTool.TRIM -> MaterialSymbols.ContentCut to Res.string.media_studio_tool_trim
            StudioTool.ALT -> MaterialSymbols.EditNote to Res.string.media_studio_tool_alt
            StudioTool.PRIVACY -> MaterialSymbols.PrivacyTip to Res.string.media_studio_tool_privacy
        }
    ToolTabContent(symbol, stringRes(label), color, onClick)
}

@Composable
private fun ToolTabContent(
    symbol: MaterialSymbol,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(symbol, contentDescription = null, tint = color)
        Text(label, color = color, fontSize = 11.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ServerChip(
    server: ServerName,
    servers: List<ServerName>,
    enabled: Boolean,
    onPick: (ServerName) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled && servers.size > 1) { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.CloudUpload, contentDescription = stringRes(Res.string.file_server), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(server.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).widthIn(max = 140.dp))
            if (servers.size > 1) Icon(MaterialSymbols.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            servers.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    leadingIcon = { if (option == server) Icon(MaterialSymbols.Check, contentDescription = null) },
                    onClick = {
                        onPick(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun QualityTool(
    item: SelectedMediaProcessing,
    previews: CompressionPreviews,
    cropped: CroppedSources,
    uploader: MediaUploader,
) {
    val media = item.media
    val support = uploader.videoCompression
    val isVideo = media.isVideo() == true
    when {
        isVideo && support == VideoCompressionSupport.NeedsFfmpeg ->
            Text(stringRes(Res.string.video_compression_needs_ffmpeg), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        media.isGif() ->
            SwitchRow(stringRes(Res.string.convert_gif_to_mp4_label), stringRes(Res.string.media_studio_gif_description), item.options.convertGifToMp4, MaterialSymbols.Movie) {
                item.options = item.options.copy(convertGifToMp4 = it)
            }

        isVideo -> VideoQuality(item, uploader)

        media.isCompressible() -> ImageQuality(item, cropped.sourceFor(item), previews, uploader)

        else -> Text(stringRes(Res.string.media_studio_no_options), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One card per level, each with the size and long edge it produces, measured, not estimated. */
@Composable
private fun ImageQuality(
    item: SelectedMediaProcessing,
    /** What the previews are made from (a crop's copy); null while that copy is being written. */
    media: SelectedMedia?,
    previews: CompressionPreviews,
    uploader: MediaUploader,
) {
    val original = media?.let { previews.anyOf(it) }?.original
    // A content-resolver lookup on Android: off the main thread, and only until the previews measure it.
    // produceState keeps its value across keys, so drop the last media's size before looking up the next.
    val measuredBytes = original?.bytes
    val fallbackBytes by produceState<Long?>(null, media, uploader, measuredBytes) {
        value = null
        if (measuredBytes == null) value = media?.let { withContext(Dispatchers.IO) { uploader.fileSize(it.uri) } }
    }
    val originalBytes = measuredBytes ?: fallbackBytes
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CompressorQuality.sliderSteps.forEach { level ->
            val compressed = media?.let { previews[it, level] }?.compressed
            val bytes = if (level == CompressorQuality.UNCOMPRESSED) originalBytes else compressed?.bytes
            val edge =
                when {
                    level == CompressorQuality.UNCOMPRESSED -> original?.longEdge()
                    compressed != null -> compressed.longEdge()
                    else -> level.imageMaxDimension?.let { cap -> original?.longEdge()?.let { min(cap, it) } ?: cap }
                }
            LevelCard(
                name = levelName(level),
                detail = bytes?.let { formatFileSize(it) } ?: "…",
                subDetail = edge?.let { stringRes(Res.string.media_studio_long_edge, it) },
                selected = item.options.quality == level,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { item.options = item.options.copy(quality = level) },
            )
        }
    }
}

@Composable
private fun VideoQuality(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
) {
    val probe = rememberVideoProbe(item, uploader)
    val steps = VideoAwareSliderSteps.stepsFor(CompressorQuality.sliderSteps, hasVideo = true)
    val chosen = item.options.quality.videoLevel
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Rows, not cards: what a level does to a video ("1080p video → 720p, 2 Mbps") needs the width.
        steps.forEach { level ->
            LevelRow(
                name = levelName(level),
                detail = if (level == CompressorQuality.UNCOMPRESSED || !probe.done) null else videoPhrase(level, probe.info),
                selected = chosen == level,
                onClick = { item.options = item.options.copy(quality = level) },
            )
        }
        if (chosen != CompressorQuality.UNCOMPRESSED) {
            SwitchRow(stringRes(Res.string.media_studio_h265), stringRes(Res.string.media_studio_h265_description), item.options.useH265, MaterialSymbols.Movie) {
                item.options = item.options.copy(useH265 = it)
            }
        }
    }
}

/** What [MediaUploader.probeVideo] found; [done] false while it runs. */
@Immutable
private class VideoProbe(
    val done: Boolean,
    val info: VideoInfo?,
)

/**
 * The picked video's probe, read once for the whole Studio. Until it answers, a level says nothing
 * rather than the 1080p example meant for a file that can't be probed, which read
 * "1080p video → 1080p" and then jumped to the file's real size.
 */
@Composable
private fun rememberVideoProbe(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
): VideoProbe {
    val reads = LocalStudioReads.current?.probes
    val media = item.media
    // Per file: the Studio switches files in place, and produceState would keep the last file's probe.
    val probe =
        remember(media, reads) {
            val known = reads?.now(media)
            mutableStateOf(VideoProbe(known != null, known))
        }
    LaunchedEffect(probe) {
        if (!probe.value.done) probe.value = VideoProbe(true, reads.getOrRead(media) { uploader.probeVideo(media.uri) })
    }
    return probe.value
}

/** In the studio the top level is "Original": the file as picked, the same word the preview uses. */
@Composable
private fun levelName(level: CompressorQuality): String = if (level == CompressorQuality.UNCOMPRESSED) stringRes(Res.string.media_compression_preview_original) else qualityName(level)

@Composable
private fun LevelRow(
    name: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, color = color.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LevelCard(
    name: String,
    detail: String?,
    subDetail: String?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Text(name, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 15.sp)
        if (detail != null) Text(detail, color = color, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
        if (subDetail != null) Text(subDetail, color = color.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun AltTool(item: SelectedMediaProcessing) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StudioTextField(
            value = item.options.alt,
            onValueChange = { item.options = item.options.copy(alt = it, altSuggested = false) },
            placeholder = stringRes(Res.string.media_studio_alt_placeholder),
            minLines = 2,
            maxLines = 4,
        )
        if (item.options.altSuggested) {
            // A note under the field, not a chip: it says where the words came from and offers to clear them.
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringRes(Res.string.media_studio_alt_suggested),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { item.options = item.options.copy(alt = "", altSuggested = false) }) {
                    Text(stringRes(Res.string.clear))
                }
            }
        }
    }
}

@Composable
private fun PrivacyTool(
    item: SelectedMediaProcessing,
    support: VideoCompressionSupport,
    showSensitive: Boolean,
    extras: (@Composable ColumnScope.() -> Unit)?,
) {
    val media = item.media
    // A video that will really be re-encoded loses its metadata anyway; the choice still reaches the
    // uploader (the encode can fall back to the original), so only the switch is hidden.
    val videoReencoded = media.isVideo() == true && support == VideoCompressionSupport.Supported && item.options.quality != CompressorQuality.UNCOMPRESSED
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showSensitive) {
            SwitchRow(stringRes(Res.string.media_studio_sensitive), stringRes(Res.string.media_studio_sensitive_description), item.options.sensitive, MaterialSymbols.VisibilityOff) {
                item.options = item.options.copy(sensitive = it)
            }
        }
        if (!media.isNotMedia() && !videoReencoded) {
            val what = if (media.isAudio() == true) Res.string.media_studio_remove_location_audio_description else Res.string.media_studio_remove_location_description
            SwitchRow(stringRes(Res.string.media_studio_remove_location), stringRes(what), item.options.stripMetadata, MaterialSymbols.LocationOff) {
                item.options = item.options.copy(stripMetadata = it)
            }
        }
        extras?.invoke(this)
    }
}
