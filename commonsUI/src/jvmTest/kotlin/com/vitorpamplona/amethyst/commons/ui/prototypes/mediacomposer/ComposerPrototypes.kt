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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import kotlin.math.roundToInt

/*
 * PROTOTYPE ONLY. The post composer as a host of MediaStudio. Picking files opens the Studio
 * (StudioConfig.postPick); "Add" puts each file in the text at the cursor; tapping a file reopens
 * the Studio on it (StudioConfig.postEdit). The composer itself has no media settings at all.
 *
 *  B. MarkerStyle.BLOCKS: the media is drawn inside the text field, at its place in the text.
 *     The default for full composers (notes, comments, long-form, issues, classifieds).
 *  A. MarkerStyle.CHIPS:  the text holds a chip ("▣ Photo 1") and a filmstrip under the text shows
 *     the files. For composers too small for a 210 dp block (reply sheets, chat rows).
 *
 * Both upload only on Post: the field locks, every marker shows its progress, the Post button
 * counts the files, and a failure brings the composer back with the text untouched and the failed
 * file marked, with Retry / another server / smaller.
 */

enum class MarkerStyle { CHIPS, BLOCKS }

@Composable
fun ComposerPrototype(
    composer: PrototypeComposer,
    style: MarkerStyle,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            ComposerTopBar(composer)
            AnimatedVisibility(composer.phase == ComposerPhase.NEEDS_ATTENTION) {
                FailedUploadBanner(composer)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp)) {
                    AvatarStandIn()
                    when (style) {
                        MarkerStyle.CHIPS -> ChipMessageField(composer, Modifier.weight(1f))
                        MarkerStyle.BLOCKS -> BlockMessageField(composer, Modifier.weight(1f))
                    }
                }
                if (style == MarkerStyle.CHIPS && composer.drafts.isNotEmpty()) {
                    MediaFilmstrip(composer, onAddMedia, Modifier.padding(start = 56.dp, top = 8.dp))
                }
            }
            HorizontalDivider()
            ComposerToolbar(composer, onAddMedia)
        }
    }
}

@Composable
private fun ComposerTopBar(composer: PrototypeComposer) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) { Icon(MaterialSymbols.Close, contentDescription = "Close") }
            Spacer(Modifier.weight(1f))
            if (composer.phase == ComposerPhase.PUBLISHING) {
                TextButton(onClick = composer::cancelPublishing) { Text("Cancel") }
            }
            PostButton(composer)
        }
        if (composer.phase == ComposerPhase.PUBLISHING) {
            val ordered = composer.orderedDrafts()
            val progress = if (ordered.isEmpty()) 1f else ordered.map { it.status.overallProgress() }.average().toFloat()
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

private fun DraftStatus.overallProgress(): Float =
    when (this) {
        DraftStatus.Local -> 0f
        is DraftStatus.Preparing -> progress * 0.3f
        is DraftStatus.Uploading -> 0.3f + progress * 0.7f
        is DraftStatus.Failed -> 0f
        is DraftStatus.Uploaded -> 1f
    }

@Composable
private fun PostButton(composer: PrototypeComposer) {
    val ordered = composer.orderedDrafts()
    val done = ordered.count { it.status is DraftStatus.Uploaded }
    Button(
        // Stays lit while uploading: it is the progress readout, and Cancel sits beside it.
        onClick = { if (composer.canPost()) composer.post() },
        enabled = composer.phase == ComposerPhase.PUBLISHING || (composer.canPost() && (composer.message.text.isNotBlank() || ordered.isNotEmpty())),
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = Modifier.padding(end = 8.dp).height(36.dp),
    ) {
        when (composer.phase) {
            ComposerPhase.PUBLISHING -> {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(if (ordered.isEmpty()) "Posting" else "Uploading $done/${ordered.size}")
            }
            ComposerPhase.NEEDS_ATTENTION -> Text("Try again")
            ComposerPhase.PUBLISHED -> Text("Posted")
            ComposerPhase.EDITING -> Text("Post")
        }
    }
}

/** One banner for every failure, naming the file by the number its marker shows. */
@Composable
private fun FailedUploadBanner(composer: PrototypeComposer) {
    val failed = composer.orderedDrafts().filter { it.status is DraftStatus.Failed }
    val first = failed.firstOrNull() ?: return
    var serverMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val name = if (first.kind == DraftKind.VIDEO) "Video" else "Photo"
                    Text(
                        if (failed.size == 1) "$name ${composer.numberOf(first)} didn't upload" else "${failed.size} files didn't upload",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        (first.status as DraftStatus.Failed).reason + ". Nothing was posted.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(Modifier.padding(start = 26.dp)) {
                TextButton(onClick = { failed.forEach { composer.failNext.remove(it.id) }.also { composer.post() } }) { Text("Retry") }
                Box {
                    TextButton(onClick = { serverMenu = true }) { Text("Other server") }
                    ServerMenu(composer, serverMenu, onDismiss = { serverMenu = false }, onPicked = {
                        failed.forEach { composer.failNext.remove(it.id) }
                        composer.post()
                    })
                }
                TextButton(onClick = { first.quality = QualityStop.SMALL }) { Text("Make smaller") }
            }
        }
    }
}

@Composable
private fun ServerMenu(
    composer: PrototypeComposer,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPicked: () -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        PrototypeComposer.SERVERS.forEach { server ->
            DropdownMenuItem(
                text = { Text(server) },
                leadingIcon = { if (server == composer.server) Icon(MaterialSymbols.Check, contentDescription = null) },
                onClick = {
                    composer.server = server
                    onDismiss()
                    onPicked()
                },
            )
        }
    }
}

@Composable
private fun messageTextStyle() =
    MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onBackground,
        lineHeight = 22.sp,
    )

// ---------------------------------------------------------------------------------------------
// A. Chips
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChipMessageField(
    composer: PrototypeComposer,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val transformation =
        remember(colors) {
            MediaChipTransformation(composer, colors.secondaryContainer, colors.onSecondaryContainer, colors.errorContainer, colors.onErrorContainer)
        }
    BasicTextField(
        state = composer.message,
        readOnly = composer.phase == ComposerPhase.PUBLISHING,
        outputTransformation = transformation,
        textStyle = messageTextStyle(),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.fillMaxWidth(),
        decorator = { inner ->
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (composer.message.text.isEmpty()) Text("What's on your mind?", color = colors.onSurfaceVariant, style = messageTextStyle())
                inner()
            }
        },
    )
}

@Composable
private fun MediaFilmstrip(
    composer: PrototypeComposer,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        composer.orderedDrafts().forEach { draft ->
            MediaCard(
                draft = draft,
                number = composer.numberOf(draft),
                compact = true,
                selected = false,
                onOpen = { composer.editing = draft },
                onRemove = { composer.remove(draft) },
                onRetry = { composer.retry(draft) },
                locked = composer.phase == ComposerPhase.PUBLISHING,
                modifier = Modifier.size(width = 132.dp, height = 132.dp),
            )
        }
        Box(
            Modifier
                .size(width = 64.dp, height = 132.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = onAddMedia),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MaterialSymbols.AddPhotoAlternate, contentDescription = "Add media", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// B. Blocks inside the text
// ---------------------------------------------------------------------------------------------

private val BlockHeight = 210.dp
private val BlockGap = 10.dp
private val FieldPaddingH = 10.dp
private val FieldPaddingV = 6.dp

@Composable
private fun BlockMessageField(
    composer: PrototypeComposer,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val transformation = remember(density) { MediaBlockTransformation(with(density) { (BlockHeight + BlockGap * 2).toSp() }) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val raw = composer.message.text
    val selection = composer.message.selection
    // The cursor next to a block selects the block: the tall line would draw a 230 dp caret.
    val selectedId = if (selection.collapsed) draftAtCursor(raw, selection.start) else null

    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            state = composer.message,
            readOnly = composer.phase == ComposerPhase.PUBLISHING,
            outputTransformation = transformation,
            onTextLayout = { getResult -> layout = getResult() },
            textStyle = messageTextStyle(),
            cursorBrush = SolidColor(if (selectedId != null) Color.Transparent else colors.primary),
            modifier = Modifier.fillMaxWidth(),
            decorator = { inner ->
                Box(Modifier.padding(horizontal = FieldPaddingH, vertical = FieldPaddingV)) {
                    if (raw.isEmpty()) Text("What's on your mind?", color = colors.onSurfaceVariant, style = messageTextStyle())
                    inner()
                }
            },
        )
        layout?.let { l ->
            blockTops(raw, l).forEach { (id, top) ->
                val draft = composer.draft(id) ?: return@forEach
                val y = with(density) { (top + (FieldPaddingV + BlockGap).toPx()).roundToInt() }
                MediaCard(
                    draft = draft,
                    number = composer.numberOf(draft),
                    compact = false,
                    selected = id == selectedId,
                    onOpen = { composer.editing = draft },
                    onRemove = { composer.remove(draft) },
                    onRetry = { composer.retry(draft) },
                    locked = composer.phase == ComposerPhase.PUBLISHING,
                    modifier =
                        Modifier
                            .offset { IntOffset(0, y) }
                            .padding(horizontal = FieldPaddingH)
                            .fillMaxWidth()
                            .height(BlockHeight),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The media card: one component for the filmstrip tile and the inline block
// ---------------------------------------------------------------------------------------------

/**
 * Everything about one file, on the file: its number (matching its marker), what will be uploaded
 * (quality and size), whether it has alt text, a content warning, an edit, and its upload state.
 * Tap opens MediaStudio; the only other control is remove.
 */
@Composable
fun MediaCard(
    draft: MediaDraft,
    number: Int,
    compact: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val status = draft.status
    val border =
        when {
            status is DraftStatus.Failed -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
            selected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    Box(
        modifier
            .clip(shape)
            .border(border, shape)
            .clickable(enabled = !locked, onClick = onOpen),
    ) {
        DraftImage(draft, Modifier.fillMaxSize())

        if (draft.kind == DraftKind.VIDEO && status is DraftStatus.Local) {
            Icon(
                MaterialSymbols.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                filled = true,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(if (compact) 36.dp else 48.dp)
                        .overMediaCircle()
                        .padding(6.dp),
            )
        }

        // Top: number (matches the marker in the text) and remove.
        MediaPill(number.toString(), Modifier.align(Alignment.TopStart).padding(6.dp))
        if (!locked && status !is DraftStatus.Uploaded) {
            Icon(
                MaterialSymbols.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .overMediaCircle()
                        .clickable(onClick = onRemove)
                        .padding(4.dp),
            )
        }

        // Bottom: what will be sent. Compact tiles keep only the size and the alt badge.
        Row(
            Modifier.align(Alignment.BottomStart).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val hasAlt = draft.alt.isNotBlank()
            MediaPill(
                "ALT",
                background = if (hasAlt) Color.Black.copy(alpha = 0.62f) else Color(0xFFFFB74D).copy(alpha = 0.92f),
                color = if (hasAlt) Color.White else Color.Black,
            )
            if (draft.sensitive) MediaPill("CW")
            val size = formatBytes(draft.estimatedBytes())
            val sizeText =
                when {
                    compact -> size
                    draft.kind == DraftKind.VIDEO -> "${draft.quality.videoLabel} · ${formatDuration(draft.trim?.lengthMs ?: 0)} · $size"
                    else -> "${draft.quality.label} · $size"
                }
            MediaPill(sizeText)
            if (draft.isEdited && !compact) MediaPill("Edited")
        }

        StatusOverlay(status, compact, onRetry, Modifier.matchParentSize())
    }
}

@Composable
fun StatusOverlay(
    status: DraftStatus,
    compact: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    when (status) {
        DraftStatus.Local -> {}

        is DraftStatus.Preparing, is DraftStatus.Uploading -> {
            val (label, progress) =
                if (status is DraftStatus.Preparing) "Preparing" to status.progress * 0.3f else "Uploading" to 0.3f + (status as DraftStatus.Uploading).progress * 0.7f
            Box(modifier.background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { progress }, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(if (compact) 32.dp else 44.dp))
                    if (!compact) {
                        Spacer(Modifier.height(6.dp))
                        Text("$label ${(progress * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        is DraftStatus.Failed -> {
            Box(modifier.background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Icon(MaterialSymbols.ErrorOutline, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(28.dp))
                    if (!compact) {
                        Text(status.reason, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    AssistChip(
                        onClick = onRetry,
                        label = { Text("Retry", color = Color.White) },
                        leadingIcon = { Icon(MaterialSymbols.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }

        is DraftStatus.Uploaded -> {
            Box(modifier, contentAlignment = Alignment.BottomEnd) {
                Icon(
                    MaterialSymbols.CheckCircle,
                    contentDescription = "Uploaded",
                    tint = Color(0xFF81C784),
                    filled = true,
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Toolbar: the server is a chip, not a form field
// ---------------------------------------------------------------------------------------------

@Composable
private fun ComposerToolbar(
    composer: PrototypeComposer,
    onAddMedia: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAddMedia) { Icon(MaterialSymbols.AddPhotoAlternate, contentDescription = "Add media", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = {}) { Icon(MaterialSymbols.CameraAlt, contentDescription = "Camera", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = {}) { Icon(MaterialSymbols.Poll, contentDescription = "Poll", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = {}) { Icon(MaterialSymbols.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.weight(1f))
        if (composer.drafts.isNotEmpty()) {
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { menu = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MaterialSymbols.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(composer.server, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(MaterialSymbols.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ServerMenu(composer, menu, onDismiss = { menu = false })
            }
        }
    }
}
