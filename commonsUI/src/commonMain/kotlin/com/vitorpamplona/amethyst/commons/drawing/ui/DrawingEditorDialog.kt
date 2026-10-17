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
package com.vitorpamplona.amethyst.commons.drawing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackgroundStore
import com.vitorpamplona.amethyst.commons.drawing.DrawingEditorState
import com.vitorpamplona.amethyst.commons.drawing.DrawingPaper
import com.vitorpamplona.amethyst.commons.drawing.DrawingTool
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawing_add_to_post
import com.vitorpamplona.amethyst.commons.resources.drawing_alt_text
import com.vitorpamplona.amethyst.commons.resources.drawing_background_chalkboard
import com.vitorpamplona.amethyst.commons.resources.drawing_background_dots
import com.vitorpamplona.amethyst.commons.resources.drawing_background_grid
import com.vitorpamplona.amethyst.commons.resources.drawing_background_notebook
import com.vitorpamplona.amethyst.commons.resources.drawing_background_photo
import com.vitorpamplona.amethyst.commons.resources.drawing_background_photo_failed
import com.vitorpamplona.amethyst.commons.resources.drawing_background_plain
import com.vitorpamplona.amethyst.commons.resources.drawing_background_remove
import com.vitorpamplona.amethyst.commons.resources.drawing_background_saved
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_mint
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_pink
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_sky
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_yellow
import com.vitorpamplona.amethyst.commons.resources.drawing_color_black
import com.vitorpamplona.amethyst.commons.resources.drawing_color_blue
import com.vitorpamplona.amethyst.commons.resources.drawing_color_green
import com.vitorpamplona.amethyst.commons.resources.drawing_color_orange
import com.vitorpamplona.amethyst.commons.resources.drawing_color_purple
import com.vitorpamplona.amethyst.commons.resources.drawing_color_red
import com.vitorpamplona.amethyst.commons.resources.drawing_color_white
import com.vitorpamplona.amethyst.commons.resources.drawing_color_yellow
import com.vitorpamplona.amethyst.commons.resources.drawing_discard
import com.vitorpamplona.amethyst.commons.resources.drawing_discard_message
import com.vitorpamplona.amethyst.commons.resources.drawing_discard_title
import com.vitorpamplona.amethyst.commons.resources.drawing_export_failed
import com.vitorpamplona.amethyst.commons.resources.drawing_keep_drawing
import com.vitorpamplona.amethyst.commons.resources.drawing_making_gif
import com.vitorpamplona.amethyst.commons.resources.drawing_mirror
import com.vitorpamplona.amethyst.commons.resources.drawing_open
import com.vitorpamplona.amethyst.commons.resources.drawing_redo
import com.vitorpamplona.amethyst.commons.resources.drawing_size
import com.vitorpamplona.amethyst.commons.resources.drawing_title
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_brush
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_eraser
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_pencil
import com.vitorpamplona.amethyst.commons.resources.drawing_tool_spray
import com.vitorpamplona.amethyst.commons.resources.drawing_undo
import com.vitorpamplona.amethyst.commons.service.image.encodeArgbAsPng
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.StringResource

/** The composer's "draw by hand" tool: opens the editor and hands back the finished drawing. */
@Composable
fun DrawingButton(
    readMedia: suspend (MediaUri) -> ByteArray?,
    backgrounds: DrawingBackgroundStore?,
    enabled: Boolean = true,
    onDrawing: (drawing: RenderedDrawing, alt: String?) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    if (show) {
        DrawingEditorDialog(
            readMedia = readMedia,
            backgrounds = backgrounds,
            onDismiss = { show = false },
            onDone = { drawing, alt ->
                show = false
                onDrawing(drawing, alt)
            },
        )
    }

    val label = stringRes(Res.string.drawing_open)
    HoverTooltip(label) {
        IconButton(onClick = { show = true }, enabled = enabled) {
            Icon(
                symbol = MaterialSymbols.Draw,
                contentDescription = label,
                modifier = Modifier.height(22.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Full-window editor for a hand drawing: the background, the pen, an alt text,
 * and "Add to post", which renders the replay GIF and the still PNG and hands
 * them over. On a wide window the tools sit beside the paper instead of under it.
 *
 * [readMedia] reads a picture the user picks as a background; [backgrounds]
 * keeps it for next time (null: the picture is only kept while the editor is open).
 */
@Composable
fun DrawingEditorDialog(
    readMedia: suspend (MediaUri) -> ByteArray?,
    backgrounds: DrawingBackgroundStore?,
    onDismiss: () -> Unit,
    onDone: (drawing: RenderedDrawing, alt: String?) -> Unit,
) {
    val state = remember { DrawingEditorState() }
    var alt by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val close = {
        if (progress == null) {
            if (state.isEmpty) onDismiss() else confirmDiscard = true
        }
    }

    Dialog(
        onDismissRequest = close,
        properties = edgeToEdgeDialogProperties(dismissOnClickOutside = false),
    ) {
        SetDialogToEdgeToEdge()
        Scaffold(
            topBar = {
                ActionTopBar(
                    postRes = Res.string.drawing_add_to_post,
                    titleRes = Res.string.drawing_title,
                    isActive = { !state.isEmpty && progress == null },
                    onCancel = close,
                    onPost = {
                        exportFailed = false
                        progress = 0f
                        val strokes = state.strokes.toList()
                        val background = state.background
                        scope.launch {
                            try {
                                val drawing = DrawingExporter.export(background, strokes) { progress = it }
                                onDone(drawing, alt.trim().ifEmpty { null })
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w("DrawingEditorDialog", "Could not export the drawing", e)
                                exportFailed = true
                            } finally {
                                progress = null
                            }
                        }
                    },
                    additionalActions = {
                        IconButton(onClick = state::undo, enabled = state.canUndo) {
                            Icon(MaterialSymbols.AutoMirrored.Undo, stringRes(Res.string.drawing_undo))
                        }
                        IconButton(onClick = state::redo, enabled = state.canRedo) {
                            Icon(MaterialSymbols.AutoMirrored.Redo, stringRes(Res.string.drawing_redo))
                        }
                    },
                )
            },
        ) { pad ->
            Surface(
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe(),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= WIDE_LAYOUT) {
                        Row(Modifier.fillMaxSize()) {
                            DrawingCanvas(state, Modifier.weight(1f).fillMaxSize().padding(16.dp))
                            Column(
                                Modifier
                                    .width(SIDE_PANEL)
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                DrawingTools(state, readMedia, backgrounds)
                                AltTextField(alt) { alt = it }
                                if (exportFailed) ExportFailedText()
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            DrawingCanvas(state, Modifier.weight(1f).fillMaxWidth().padding(12.dp))
                            Column(
                                Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DrawingTools(state, readMedia, backgrounds)
                                AltTextField(alt) { alt = it }
                                if (exportFailed) ExportFailedText()
                            }
                        }
                    }
                }
            }
        }

        progress?.let { ExportingOverlay(it) }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringRes(Res.string.drawing_discard_title)) },
                text = { Text(stringRes(Res.string.drawing_discard_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        onDismiss()
                    }) { Text(stringRes(Res.string.drawing_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringRes(Res.string.drawing_keep_drawing)) }
                },
            )
        }
    }
}

@Composable
private fun AltTextField(
    alt: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = alt,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringRes(Res.string.drawing_alt_text)) },
        singleLine = true,
    )
}

@Composable
private fun ExportFailedText() {
    Text(stringRes(Res.string.drawing_export_failed), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ExportingOverlay(progress: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .background(SCRIM)
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringRes(Res.string.drawing_making_gif))
            }
        }
    }
}

/** Background, tools, colours and pen size. */
@Composable
fun DrawingTools(
    state: DrawingEditorState,
    readMedia: suspend (MediaUri) -> ByteArray?,
    backgrounds: DrawingBackgroundStore?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackgroundRow(state, readMedia, backgrounds)
        ToolRow(state)
        ColorRow(state)
        SizeRow(state)
    }
}

/** A picture saved in [DrawingBackgroundStore], with its bytes for the thumbnail and for loading. */
private class SavedBackground(
    val path: Path,
    val png: ByteArray,
)

@Composable
private fun BackgroundRow(
    state: DrawingEditorState,
    readMedia: suspend (MediaUri) -> ByteArray?,
    backgrounds: DrawingBackgroundStore?,
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Without a store, the user's picture still stays in the row while the editor is open.
    var unsaved by remember { mutableStateOf(state.background as? DrawingBackground.Photo) }
    var saved by remember { mutableStateOf(emptyList<SavedBackground>()) }
    var selectedSaved by remember { mutableStateOf<Path?>(null) }

    suspend fun reloadSaved() {
        val store = backgrounds ?: return
        saved = store.list().mapNotNull { path -> store.read(path)?.let { SavedBackground(path, it) } }
    }

    LaunchedEffect(backgrounds) { reloadSaved() }

    /** Loads [bytes] as the background; the picture, or null when it could not be read. */
    suspend fun use(bytes: ByteArray): DrawingBackground.Photo? {
        loading = true
        failed = false
        val loaded =
            try {
                PhotoBackgroundLoader.load(context, bytes)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("DrawingEditorDialog", "Could not load the background picture", e)
                null
            }
        loading = false
        if (loaded == null) failed = true else state.changeBackground(loaded)
        return loaded
    }

    if (picking) {
        GallerySelectSingle(imagesOnly = true) { media ->
            picking = false
            if (media == null) return@GallerySelectSingle
            scope.launch {
                val bytes = readMedia(media.uri)
                if (bytes == null) {
                    Log.w("DrawingEditorDialog") { "Could not read the picked picture ${media.uri}" }
                    failed = true
                    return@launch
                }
                val loaded = use(bytes) ?: return@launch
                val store = backgrounds
                if (store == null) {
                    unsaved = loaded
                } else {
                    // Keep the cropped, scaled copy, not the original: it is what the editor needs next time.
                    val png = withContext(Dispatchers.Default) { encodeArgbAsPng(loaded.pixels, loaded.width, loaded.height) }
                    selectedSaved = store.save(png)
                    reloadSaved()
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Wraps instead of scrolling sideways: a mouse cannot drag a strip, so its last tiles were out of reach.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PAPERS.forEach { (paper, label) ->
                val background = remember(paper) { DrawingBackground.Paper(paper) }
                BackgroundSwatch(background, stringRes(label), selected = state.background == background) {
                    selectedSaved = null
                    state.changeBackground(background)
                }
            }
            unsaved?.let { picked ->
                BackgroundSwatch(picked, stringRes(Res.string.drawing_background_photo), selected = state.background === picked) {
                    state.changeBackground(picked)
                }
            }
            saved.forEach { item ->
                val selected = selectedSaved == item.path && state.background is DrawingBackground.Photo
                SavedBackgroundSwatch(
                    item = item,
                    selected = selected,
                    onClick = {
                        if (!loading) {
                            scope.launch { if (use(item.png) != null) selectedSaved = item.path }
                        }
                    },
                    onRemove = {
                        scope.launch {
                            backgrounds?.delete(item.path)
                            if (selected) state.changeBackground(DrawingBackground.Paper(DrawingPaper.PLAIN))
                            selectedSaved = null
                            reloadSaved()
                        }
                    },
                )
            }
            val addPhoto = stringRes(Res.string.drawing_background_photo)
            HoverTooltip(addPhoto) {
                Box(
                    Modifier
                        .size(SWATCH)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable(enabled = !loading, role = Role.Button, onClickLabel = addPhoto) { picking = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(MaterialSymbols.AddPhotoAlternate, addPhoto, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (failed) Text(stringRes(Res.string.drawing_background_photo_failed), color = MaterialTheme.colorScheme.error)
    }
}

/** A saved picture's thumbnail; once selected, it also offers to remove the picture from the device. */
@Composable
private fun SavedBackgroundSwatch(
    item: SavedBackground,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val label = stringRes(Res.string.drawing_background_saved)
    val shape = RoundedCornerShape(8.dp)
    val request =
        remember(item.path) {
            ImageRequest
                .Builder(context)
                .data(item.png)
                .size(THUMBNAIL_PX)
                .build()
        }
    Box(Modifier.size(SWATCH)) {
        HoverTooltip(label) {
            AsyncImage(
                model = request,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .border(if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            )
        }
        if (selected) {
            val remove = stringRes(Res.string.drawing_background_remove)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = remove, onClick = onRemove)
                    .semantics { contentDescription = remove },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MaterialSymbols.Close, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** A small preview of [background], drawn by the same painter the paper uses. */
@Composable
private fun BackgroundSwatch(
    background: DrawingBackground,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val painter = remember(background) { BackgroundPainter(background) }
    val shape = RoundedCornerShape(8.dp)
    HoverTooltip(label) {
        Canvas(
            Modifier
                .size(SWATCH)
                .clip(shape)
                .border(if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
        ) {
            with(painter) {
                drawBase()
                drawOverlay()
            }
        }
    }
}

@Composable
private fun ToolRow(state: DrawingEditorState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolButton(state, DrawingTool.BRUSH, MaterialSymbols.Brush, Res.string.drawing_tool_brush)
        ToolButton(state, DrawingTool.PENCIL, MaterialSymbols.Edit, Res.string.drawing_tool_pencil)
        ToolButton(state, DrawingTool.SPRAY, null, Res.string.drawing_tool_spray)
        ToolButton(state, DrawingTool.ERASER, MaterialSymbols.InkEraser, Res.string.drawing_tool_eraser)
        Spacer(Modifier.weight(1f))
        val mirror = stringRes(Res.string.drawing_mirror)
        HoverTooltip(mirror) {
            IconToggleButton(checked = state.mirror, onCheckedChange = { state.mirror = it }) {
                Icon(MaterialSymbols.Flip, mirror, tint = toggleTint(state.mirror))
            }
        }
    }
}

@Composable
private fun ToolButton(
    state: DrawingEditorState,
    tool: DrawingTool,
    symbol: MaterialSymbol?,
    label: StringResource,
) {
    val text = stringRes(label)
    val selected = state.tool == tool
    HoverTooltip(text) {
        IconToggleButton(checked = selected, onCheckedChange = { state.tool = tool }) {
            if (symbol != null) {
                Icon(symbol, text, tint = toggleTint(selected))
            } else {
                SprayGlyph(toggleTint(selected), text)
            }
        }
    }
}

/** Material Symbols has no spray can, so the spray tool shows a little cloud of dots. */
@Composable
private fun SprayGlyph(
    color: Color,
    description: String,
) {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = description }) {
        val dots = listOf(0.5f to 0.5f, 0.3f to 0.35f, 0.7f to 0.3f, 0.25f to 0.65f, 0.72f to 0.7f, 0.5f to 0.18f, 0.5f to 0.82f, 0.15f to 0.48f, 0.85f to 0.5f)
        dots.forEachIndexed { i, (x, y) ->
            drawCircle(color, radius = size.width * (if (i == 0) 0.09f else 0.06f), center = Offset(x * size.width, y * size.height))
        }
    }
}

@Composable
private fun toggleTint(selected: Boolean) = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun ColorRow(state: DrawingEditorState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        INKS.forEach { (argb, label) ->
            val description = stringRes(label)
            val selected = state.color == argb && state.tool != DrawingTool.ERASER
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { state.pickColor(argb) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (selected) 30.dp else 26.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun SizeRow(state: DrawingEditorState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringRes(Res.string.drawing_size), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.width,
            onValueChange = { state.width = it },
            valueRange = DrawingEditorState.MIN_WIDTH..DrawingEditorState.MAX_WIDTH,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        // The pen's real size at a typical phone width, capped to the box.
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            val dot = (state.width * 360f).coerceIn(3f, 36f).dp
            val color = if (state.tool == DrawingTool.ERASER) eraserColor(state.background) else Color(state.color)
            Box(
                Modifier
                    .size(dot)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

/** What the size preview shows for the eraser: the paper, or white over a picture. */
private fun eraserColor(background: DrawingBackground) = (background as? DrawingBackground.Paper)?.let { Color(it.paper.background) } ?: Color.White

private val PAPERS =
    listOf(
        DrawingPaper.PLAIN to Res.string.drawing_background_plain,
        DrawingPaper.STICKY_YELLOW to Res.string.drawing_background_sticky_yellow,
        DrawingPaper.STICKY_PINK to Res.string.drawing_background_sticky_pink,
        DrawingPaper.STICKY_MINT to Res.string.drawing_background_sticky_mint,
        DrawingPaper.STICKY_SKY to Res.string.drawing_background_sticky_sky,
        DrawingPaper.NOTEBOOK to Res.string.drawing_background_notebook,
        DrawingPaper.GRID to Res.string.drawing_background_grid,
        DrawingPaper.DOTS to Res.string.drawing_background_dots,
        DrawingPaper.CHALKBOARD to Res.string.drawing_background_chalkboard,
    )

private val INKS =
    listOf(
        DrawingEditorState.DEFAULT_COLOR to Res.string.drawing_color_black,
        0xFFFFFFFF.toInt() to Res.string.drawing_color_white,
        0xFFE5484D.toInt() to Res.string.drawing_color_red,
        0xFFF5A524.toInt() to Res.string.drawing_color_orange,
        0xFFF7D54A.toInt() to Res.string.drawing_color_yellow,
        0xFF30A46C.toInt() to Res.string.drawing_color_green,
        0xFF2F6FDF.toInt() to Res.string.drawing_color_blue,
        0xFF8E4EC6.toInt() to Res.string.drawing_color_purple,
    )

private val SCRIM = Color(0x66000000)
private val WIDE_LAYOUT = 720.dp
private val SIDE_PANEL = 360.dp
private val SWATCH = 44.dp
private const val THUMBNAIL_PX = 132
