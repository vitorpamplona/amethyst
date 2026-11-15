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
package com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import kotlin.math.abs
import kotlin.math.max

/*
 * PROTOTYPE ONLY. MediaStudio: the step after every pick, on every screen that takes media, and the
 * screen a picked file reopens when tapped. It knows nothing about the screen that opened it: it
 * edits a list of drafts under a [StudioConfig] and hands them back. The host decides what happens
 * next (insert markers in a post, send a chat message, save an avatar).
 *
 * - The big picture IS the compression preview: the file as it will be uploaded at the chosen stop,
 *   pinch / double-tap / wheel zoomable like ZoomableImageDialog, and press-and-hold "Original"
 *   flips to the source in place.
 * - Crop (aspect presets, free drag, rotate, mirror) and Trim (filmstrip with two handles, sound)
 *   edit the same picture. A config can lock the aspect and mask a circle (avatars, banners).
 * - Quality is four stops, each labelled with the size it produces.
 * - Every edit is live on the draft; nothing is uploaded here. The confirm button ("Add", "Done",
 *   "Save", or Send beside a caption) hands control back to the host.
 */

enum class StudioTool(
    val label: String,
) {
    CROP("Crop"),
    TRIM("Trim"),
    QUALITY("Quality"),
    ALT("Alt text"),
    PRIVACY("Privacy"),
}

/** What one screen lets the Studio do. The presets below are the hosts in the prototypes. */
data class StudioConfig(
    val confirmLabel: String,
    val tools: List<StudioTool> = StudioTool.entries,
    val aspects: List<AspectPreset> = listOf(AspectPreset.FREE, AspectPreset.SQUARE, AspectPreset.PORTRAIT, AspectPreset.LANDSCAPE),
    /** Forces this crop on open and hides the other presets: avatars are square, banners 3:1. */
    val lockedAspect: AspectPreset? = null,
    val circleMask: Boolean = false,
    /** A caption field with a Send button replaces the confirm button (chats). */
    val caption: TextFieldState? = null,
    val initialTool: StudioTool = StudioTool.QUALITY,
    val canRemove: Boolean = true,
) {
    companion object {
        /** Right after a pick in a post-like composer: Add inserts the markers. */
        fun postPick() = StudioConfig(confirmLabel = "Add")

        /** Reopened from a block already in the post. */
        fun postEdit() = StudioConfig(confirmLabel = "Done")

        fun chat(caption: TextFieldState) = StudioConfig(confirmLabel = "Send", caption = caption)

        fun avatar() =
            StudioConfig(
                confirmLabel = "Save",
                tools = listOf(StudioTool.CROP),
                lockedAspect = AspectPreset.SQUARE,
                circleMask = true,
                initialTool = StudioTool.CROP,
                canRemove = false,
            )

        fun banner() =
            StudioConfig(
                confirmLabel = "Save",
                tools = listOf(StudioTool.CROP, StudioTool.QUALITY),
                lockedAspect = AspectPreset.BANNER,
                initialTool = StudioTool.CROP,
                canRemove = false,
            )
    }
}

/**
 * Applies a locked aspect before the Studio opens, so the first frame already shows the crop that
 * will be saved. Hosts call it when they hand files to the Studio.
 */
fun StudioConfig.prepare(drafts: List<MediaDraft>) {
    val locked = lockedAspect ?: return
    drafts.forEach { d ->
        if (d.crop.aspect != locked) d.crop = CropSpec(rect = centeredCrop(d, locked), aspect = locked)
    }
}

private val StudioBackground = Color(0xFF0E0E11)
private val StudioPanel = Color(0xFF1A1A1F)

/**
 * @param drafts the files this session edits, in order; the thumbnail strip switches between them.
 * @param onCancel shown as a close button when the Studio was opened by a pick, which it undoes.
 * @param onRemove drops one file; the Studio moves to another, or cancels when none are left.
 */
@Composable
fun MediaStudio(
    drafts: List<MediaDraft>,
    initial: MediaDraft,
    config: StudioConfig,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onRemove: (MediaDraft) -> Unit = {},
    initialTool: StudioTool? = null,
    holdingOriginal: Boolean = false,
) {
    var current by remember { mutableStateOf(initial) }
    if (current !in drafts) drafts.firstOrNull()?.let { current = it }

    val tools = remember(current, config) { config.tools.filter { it != StudioTool.TRIM || current.kind == DraftKind.VIDEO } }
    var tool by remember(current) { mutableStateOf((initialTool ?: config.initialTool).takeIf { it in tools } ?: tools.first()) }

    Column(Modifier.fillMaxSize().background(StudioBackground)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onCancel != null) {
                IconButton(onClick = onCancel) { Icon(MaterialSymbols.Close, contentDescription = "Cancel", tint = Color.White) }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            val name = if (current.kind == DraftKind.VIDEO) "Video" else "Photo"
            Text(
                if (drafts.size > 1) "$name ${drafts.indexOf(current) + 1} of ${drafts.size}" else name,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.weight(1f))
            if (config.canRemove) {
                IconButton(onClick = { onRemove(current) }) {
                    Icon(MaterialSymbols.Delete, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.8f))
                }
            }
            if (config.caption == null) {
                Button(onClick = onConfirm, modifier = Modifier.padding(end = 8.dp).height(36.dp)) {
                    Text(config.confirmLabel)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tool == StudioTool.CROP) {
                CropEditor(current, Modifier.fillMaxSize(), circleMask = config.circleMask)
            } else {
                StudioPreview(current, holdingOriginal, Modifier.fillMaxSize())
            }
        }

        if (drafts.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                drafts.forEach { d ->
                    DraftImage(
                        d,
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, if (d === current) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { current = d },
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(StudioPanel).padding(top = 12.dp)) {
            Box(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                when (tool) {
                    StudioTool.CROP -> CropTools(current, config)
                    StudioTool.TRIM -> TrimTools(current)
                    StudioTool.QUALITY -> QualityTools(current)
                    StudioTool.ALT -> AltTools(current)
                    StudioTool.PRIVACY -> PrivacyTools(current)
                }
            }
            // One tool needs no tabs (the avatar crop).
            if (tools.size > 1) ToolTabs(tools, tool, onPick = { tool = it })
            config.caption?.let { CaptionRow(it, config.confirmLabel, onConfirm) }
        }
    }
}

/** Chat: the message goes with the media, so the caption and Send live in the Studio. */
@Composable
private fun CaptionRow(
    caption: TextFieldState,
    sendLabel: String,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(StudioBackground).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = caption,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorator = { inner ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (caption.text.isEmpty()) Text("Add a caption…", color = Color.White.copy(alpha = 0.5f))
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(MaterialSymbols.AutoMirrored.Send, contentDescription = sendLabel, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ToolTabs(
    tools: List<StudioTool>,
    selected: StudioTool,
    onPick: (StudioTool) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        tools.forEach { t ->
            val color = if (t == selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPick(t) }.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (t) {
                    StudioTool.CROP -> CropGlyph(color)
                    StudioTool.TRIM -> TrimGlyph(color)
                    StudioTool.QUALITY -> Icon(MaterialSymbols.Tune, contentDescription = null, tint = color)
                    StudioTool.ALT -> Icon(MaterialSymbols.EditNote, contentDescription = null, tint = color)
                    StudioTool.PRIVACY -> Icon(MaterialSymbols.PrivacyTip, contentDescription = null, tint = color)
                }
                Text(t.label, color = color, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Preview: the upload, zoomable, hold to compare
// ---------------------------------------------------------------------------------------------

@Composable
private fun StudioPreview(
    draft: MediaDraft,
    forceOriginal: Boolean,
    modifier: Modifier,
) {
    val quality = draft.quality
    val compressed = remember(draft, quality) { simulateQuality(draft.bitmap, max(draft.widthPx, draft.heightPx), quality) }
    var holding by remember { mutableStateOf(false) }
    val showOriginal = holding || forceOriginal
    val zoomState = rememberZoomState()

    Box(modifier) {
        DraftImage(
            draft,
            bitmap = if (showOriginal) draft.bitmap else compressed,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp).zoomable(zoomState),
        )
        if (draft.kind == DraftKind.VIDEO) {
            Icon(
                MaterialSymbols.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                filled = true,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .overMediaCircle()
                        .padding(10.dp),
            )
        }

        val (w, h) = if (showOriginal) draft.widthPx to draft.heightPx else draft.outputSize()
        val bytes = if (showOriginal) draft.originalBytes else draft.estimatedBytes()
        val label =
            if (showOriginal) {
                "Original"
            } else if (draft.kind == DraftKind.VIDEO) {
                draft.quality.videoLabel
            } else {
                draft.quality.label
            }
        MediaPill("$label · $w×$h · ${formatBytes(bytes)}", Modifier.align(Alignment.TopStart).padding(14.dp))

        if (draft.quality != QualityStop.ORIGINAL || !draft.crop.isIdentity) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (showOriginal) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        })
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hold for original", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Quality: four stops that say what they cost
// ---------------------------------------------------------------------------------------------

@Composable
private fun QualityTools(draft: MediaDraft) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QualityStop.entries.forEach { stop ->
            val selected = draft.quality == stop
            val (w, h) = draft.outputSize(stop)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { draft.quality = stop }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (draft.kind == DraftKind.VIDEO) stop.videoLabel else stop.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(formatBytes(draft.estimatedBytes(stop)), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Text("${max(w, h)} px", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Crop
// ---------------------------------------------------------------------------------------------

private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

private const val MIN_CROP = 0.12f

@Composable
fun CropEditor(
    draft: MediaDraft,
    modifier: Modifier,
    circleMask: Boolean = false,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.padding(28.dp), contentAlignment = Alignment.Center) {
        val crop = draft.crop
        val odd = crop.quarterTurns % 2 == 1
        val imageAspect = draft.widthPx.toFloat() / draft.heightPx
        val shownAspect = if (odd) 1f / imageAspect else imageAspect
        val boxAspect = maxWidth / maxHeight
        val (shownW, shownH) = if (shownAspect > boxAspect) maxWidth to maxWidth / shownAspect else maxHeight * shownAspect to maxHeight
        // The unrotated image, rotated as a whole: pointer input arrives in its own coordinates.
        val (innerW, innerH) = if (odd) shownH to shownW else shownW to shownH

        Box(
            Modifier
                .requiredSize(innerW, innerH)
                .graphicsLayer {
                    rotationZ = 90f * crop.quarterTurns
                    scaleX = if (crop.mirrored) -1f else 1f
                },
        ) {
            DraftImage(draft, Modifier.fillMaxSize(), applyCrop = false, contentScale = ContentScale.FillBounds)
            var handle by remember { mutableStateOf<CropHandle?>(null) }
            Canvas(
                Modifier.fillMaxSize().pointerInput(draft) {
                    val touch = with(density) { 36.dp.toPx() }
                    detectDragGestures(
                        onDragStart = { start ->
                            val r = draft.crop.rect
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val corners =
                                mapOf(
                                    CropHandle.TOP_LEFT to Offset(r.left * w, r.top * h),
                                    CropHandle.TOP_RIGHT to Offset(r.right * w, r.top * h),
                                    CropHandle.BOTTOM_LEFT to Offset(r.left * w, r.bottom * h),
                                    CropHandle.BOTTOM_RIGHT to Offset(r.right * w, r.bottom * h),
                                )
                            handle = corners.entries
                                .minByOrNull { (it.value - start).getDistance() }
                                ?.takeIf { (it.value - start).getDistance() < touch }
                                ?.key
                                ?: if (Rect(r.left * w, r.top * h, r.right * w, r.bottom * h).contains(start)) CropHandle.MOVE else null
                        },
                        onDragEnd = { handle = null },
                        onDrag = { change, drag ->
                            val which = handle ?: return@detectDragGestures
                            change.consume()
                            draft.crop = draft.crop.copy(rect = dragCrop(draft, which, drag.x / size.width, drag.y / size.height))
                        },
                    )
                },
            ) {
                val r = draft.crop.rect
                val rect = Rect(r.left * size.width, r.top * size.height, r.right * size.width, r.bottom * size.height)
                val dim = Color.Black.copy(alpha = 0.6f)
                if (circleMask) {
                    // An avatar is shown in a circle: dim everything the circle will hide.
                    val hole =
                        Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, size))
                            addOval(rect)
                        }
                    drawPath(hole, dim)
                    drawOval(Color.White, rect.topLeft, rect.size, style = Stroke(2.dp.toPx()))
                } else {
                    drawRect(dim, Offset.Zero, Size(size.width, rect.top))
                    drawRect(dim, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                    drawRect(dim, Offset(0f, rect.top), Size(rect.left, rect.height))
                    drawRect(dim, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
                }
                val grid = Color.White.copy(alpha = 0.35f)
                for (i in 1..2) {
                    drawLine(grid, Offset(rect.left + rect.width * i / 3, rect.top), Offset(rect.left + rect.width * i / 3, rect.bottom), 1.dp.toPx())
                    drawLine(grid, Offset(rect.left, rect.top + rect.height * i / 3), Offset(rect.right, rect.top + rect.height * i / 3), 1.dp.toPx())
                }
                drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx()))
                val len = 22.dp.toPx()
                val thick = 4.dp.toPx()
                listOf(
                    rect.topLeft to Offset(1f, 1f),
                    rect.topRight to Offset(-1f, 1f),
                    rect.bottomLeft to Offset(1f, -1f),
                    rect.bottomRight to Offset(-1f, -1f),
                ).forEach { (corner, dir) ->
                    drawLine(Color.White, corner, corner + Offset(len * dir.x, 0f), thick)
                    drawLine(Color.White, corner, corner + Offset(0f, len * dir.y), thick)
                }
            }
        }
    }
}

/** Moves one corner (or the whole rect) by a normalized delta, keeping the aspect lock and bounds. */
private fun dragCrop(
    draft: MediaDraft,
    handle: CropHandle,
    dx: Float,
    dy: Float,
): Rect {
    val r = draft.crop.rect
    if (handle == CropHandle.MOVE) {
        val nx = (r.left + dx).coerceIn(0f, 1f - r.width)
        val ny = (r.top + dy).coerceIn(0f, 1f - r.height)
        return Rect(nx, ny, nx + r.width, ny + r.height)
    }
    var left = r.left
    var top = r.top
    var right = r.right
    var bottom = r.bottom
    when (handle) {
        CropHandle.TOP_LEFT -> {
            left += dx
            top += dy
        }
        CropHandle.TOP_RIGHT -> {
            right += dx
            top += dy
        }
        CropHandle.BOTTOM_LEFT -> {
            left += dx
            bottom += dy
        }
        else -> {
            right += dx
            bottom += dy
        }
    }
    left = left.coerceIn(0f, right - MIN_CROP)
    right = right.coerceIn(left + MIN_CROP, 1f)
    top = top.coerceIn(0f, bottom - MIN_CROP)
    bottom = bottom.coerceIn(top + MIN_CROP, 1f)

    val ratio = draft.crop.aspect.ratio ?: return Rect(left, top, right, bottom)
    // Width leads; the height follows the locked ratio (in pixels, after the turns), anchored on
    // the edge opposite the dragged corner, and both shrink if that runs off the picture.
    val effective = if (draft.crop.quarterTurns % 2 == 1) 1f / ratio else ratio
    var w = right - left
    var h = w * draft.widthPx / (draft.heightPx * effective)
    val anchorTop = handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.BOTTOM_RIGHT
    val room = if (anchorTop) 1f - top else bottom
    if (h > room) {
        h = room
        w = h * draft.heightPx * effective / draft.widthPx
    }
    val anchorLeft = handle == CropHandle.TOP_RIGHT || handle == CropHandle.BOTTOM_RIGHT
    if (anchorLeft) right = left + w else left = right - w
    if (anchorTop) bottom = top + h else top = bottom - h
    return Rect(left, top, right, bottom)
}

/** The largest centered rect of [preset]'s ratio inside the picture. */
fun centeredCrop(
    draft: MediaDraft,
    preset: AspectPreset,
): Rect {
    val ratio = preset.ratio ?: return Rect(0f, 0f, 1f, 1f)
    val effective = if (draft.crop.quarterTurns % 2 == 1) 1f / ratio else ratio
    var w = 1f
    var h = draft.widthPx / (draft.heightPx * effective)
    if (h > 1f) {
        w = draft.heightPx * effective / draft.widthPx
        h = 1f
    }
    return Rect((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
}

@Composable
private fun CropTools(
    draft: MediaDraft,
    config: StudioConfig,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (config.lockedAspect?.let { listOf(it) } ?: config.aspects).forEach { preset ->
                FilterChip(
                    selected = draft.crop.aspect == preset,
                    onClick = { draft.crop = draft.crop.copy(aspect = preset, rect = centeredCrop(draft, preset)) },
                    label = { Text(preset.label) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            labelColor = Color.White,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StudioAction("Rotate", { Icon(MaterialSymbols.AutoMirrored.Redo, contentDescription = null, tint = Color.White) }) {
                draft.crop = draft.crop.copy(quarterTurns = (draft.crop.quarterTurns + 1) % 4).let { it.copy(rect = if (it.aspect.ratio == null) it.rect else centeredCropFor(draft, it)) }
            }
            StudioAction("Mirror", { Icon(MaterialSymbols.Flip, contentDescription = null, tint = Color.White) }) {
                draft.crop = draft.crop.copy(mirrored = !draft.crop.mirrored)
            }
            StudioAction("Reset", { Icon(MaterialSymbols.AutoMirrored.Undo, contentDescription = null, tint = Color.White) }) {
                draft.crop = config.lockedAspect?.let { CropSpec(rect = centeredCropFor(draft, CropSpec(aspect = it)), aspect = it) } ?: CropSpec()
            }
        }
    }
}

private fun centeredCropFor(
    draft: MediaDraft,
    spec: CropSpec,
): Rect {
    val saved = draft.crop
    draft.crop = spec
    return centeredCrop(draft, spec.aspect).also { draft.crop = saved }
}

@Composable
private fun StudioAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Trim
// ---------------------------------------------------------------------------------------------

@Composable
private fun TrimTools(draft: MediaDraft) {
    val duration = draft.durationMs ?: return
    val trim = draft.trim ?: TrimSpec(0, duration)
    val frames = remember { (0 until 8).map { cityFrame(192, 108, it / 8f) } }
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp))) {
            Row(Modifier.fillMaxSize()) {
                frames.forEach { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize()) }
            }
            var dragging by remember { mutableStateOf<Boolean?>(null) } // true = start handle
            Canvas(
                Modifier.fillMaxSize().pointerInput(draft) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val t = draft.trim ?: TrimSpec(0, duration)
                            val sx = t.startMs.toFloat() / duration * size.width
                            val ex = t.endMs.toFloat() / duration * size.width
                            dragging = abs(p.x - sx) < abs(p.x - ex)
                        },
                        onDragEnd = { dragging = null },
                        onDrag = { change, drag ->
                            change.consume()
                            val t = draft.trim ?: TrimSpec(0, duration)
                            val deltaMs = (drag.x / size.width * duration).toLong()
                            draft.trim =
                                if (dragging == true) {
                                    t.copy(startMs = (t.startMs + deltaMs).coerceIn(0, t.endMs - 1000))
                                } else {
                                    t.copy(endMs = (t.endMs + deltaMs).coerceIn(t.startMs + 1000, duration))
                                }
                        },
                    )
                },
            ) {
                val sx = trim.startMs.toFloat() / duration * size.width
                val ex = trim.endMs.toFloat() / duration * size.width
                val dim = Color.Black.copy(alpha = 0.65f)
                drawRect(dim, Offset.Zero, Size(sx, size.height))
                drawRect(dim, Offset(ex, 0f), Size(size.width - ex, size.height))
                val handle = with(density) { 12.dp.toPx() }
                val line = with(density) { 3.dp.toPx() }
                drawRect(accent, Offset(sx, 0f), Size(ex - sx, line))
                drawRect(accent, Offset(sx, size.height - line), Size(ex - sx, line))
                drawRect(accent, Offset(sx, 0f), Size(handle, size.height))
                drawRect(accent, Offset(ex - handle, 0f), Size(handle, size.height))
                listOf(sx + handle / 2, ex - handle / 2).forEach { x ->
                    drawLine(Color.White, Offset(x, size.height * 0.35f), Offset(x, size.height * 0.65f), with(density) { 2.dp.toPx() })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatDuration(trim.startMs)} – ${formatDuration(trim.endMs)} · ${trim.lengthMs / 1000} s",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(if (draft.muted) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sound", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Switch(checked = !draft.muted, onCheckedChange = { draft.muted = !it })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Alt text and privacy
// ---------------------------------------------------------------------------------------------

@Composable
private fun AltTools(draft: MediaDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = draft.alt,
            onValueChange = {
                draft.alt = it
                draft.altSuggested = false
            },
            placeholder = { Text("Describe it for people who can't see it", color = Color.White.copy(alpha = 0.45f)) },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                ),
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft.altSuggested) {
            AssistChip(
                onClick = {
                    draft.alt = ""
                    draft.altSuggested = false
                },
                label = { Text("Suggested on this device · clear", color = Color.White) },
                leadingIcon = { Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

@Composable
private fun PrivacyTools(draft: MediaDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PrivacyRow("Sensitive", "Blurred until tapped", draft.sensitive) { draft.sensitive = it }
        PrivacyRow("Remove location", "And camera details", draft.stripLocation) { draft.stripLocation = it }
    }
}

@Composable
private fun PrivacyRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Start)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
