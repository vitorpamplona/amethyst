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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

// How far off-screen a parked (inactive) warm tab is shifted — well past any real screen width.
private val OFFSCREEN_SHIFT = 10_000.dp

/**
 * The persistent surface layer: a full-window overlay (mounted once in the app shell, below the
 * navigation drawer and dialogs) that renders **every** warm embedded session's [SandboxedSdkView] and
 * keeps it attached. The active session is positioned over the current tab's reserved content area; the
 * rest are parked off-screen but stay attached, so their sessions never detach/close — that's what
 * preserves their state across tab swaps.
 *
 * The surface is z-ordered *below* the client window (privacysandbox.ui locks it there), which still
 * forwards touch input to the provider yet lets Compose draw over it — so the active tab's
 * [TopControlSheet] is rendered on top of the surface here. Each surface is wrapped in an
 * [EmbeddedSurfaceTouchHolder] so a scroll gesture isn't stolen by a host-side ancestor (the
 * cross-process WebView can't defend its own gesture).
 *
 * [barFavoriteIds] are the favorites currently configured as bottom-bar tabs; warm-keep is scoped to
 * them ([EmbeddedTabHost.retainOnly]), plus whatever is momentarily active.
 */
@OptIn(ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedTabLayer(barFavoriteIds: List<String>) {
    val activeId = EmbeddedTabHost.activeId

    // Keep only bottom-row apps warm (plus the active tab, even mid-removal). A favorite removed from
    // the bar drops its warm session here.
    LaunchedEffect(barFavoriteIds, activeId) {
        EmbeddedTabHost.retainOnly(barFavoriteIds.toSet() + setOfNotNull(activeId))
    }

    val bounds = EmbeddedTabHost.contentBounds
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // While the soft keyboard is up (hosted by [RemoteImeView] in this window), shrink the active
    // surface so its bottom clears the keyboard — the embedded WebView then reflows and scrolls the
    // focused field into view. Only the portion of the keyboard that overlaps the surface counts.
    // Use the *snapped* animation target rather than the animated `ime` inset: the cross-process surface
    // resize is expensive (a SurfaceControlViewHost reconfigure each frame), so we resize once to the
    // final height instead of on every frame of the keyboard slide-in/out.
    val imeBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                layerOrigin = it.positionInWindow()
                layerSize = it.size
            },
    ) {
        EmbeddedTabHost.sessions.forEach { session ->
            key(session.id) {
                val active = session.id == activeId

                LaunchedEffect(active) {
                    if (active) session.controller.onShown() else session.controller.onHidden()
                }

                val placement =
                    if (bounds.width > 0f && bounds.height > 0f) {
                        with(density) {
                            // Parked tabs keep the SAME size as the active one and are only shoved
                            // off-screen, so bringing one back is a pure translation — no resize, no
                            // surface re-render, no black flash between tabs. (Parking at 1dp forced a
                            // resize + re-render on every switch, which flashed black for ~1s.)
                            val left = (bounds.left - layerOrigin.x).toDp() + (if (active) 0.dp else OFFSCREEN_SHIFT)
                            // Do NOT shrink the surface for the keyboard: resizing reconfigures the cross-process
                            // SurfaceControlViewHost surface, and the first frame presented after that reconfigure
                            // stalls ~1s (the per-focus "freeze"). Keep the surface full-size and let the page bring
                            // the focused field above the keyboard via the shim's scrollIntoView on focus.
                            @Suppress("UNUSED_EXPRESSION")
                            imeBottomPx
                            Modifier
                                .absoluteOffset(left, (bounds.top - layerOrigin.y).toDp())
                                .size(bounds.width.toDp(), bounds.height.toDp())
                        }
                    } else {
                        // No content bounds reported yet: park tiny off-screen until a tab is shown.
                        Modifier
                            .absoluteOffset(x = (-10000).dp)
                            .size(1.dp)
                    }

                AndroidView(
                    // Wrap the surface so it claims scroll gestures from host-side ancestors (the
                    // cross-process WebView can't request-disallow-intercept itself).
                    factory = { ctx ->
                        EmbeddedSurfaceTouchHolder(ctx).apply {
                            val surface = SandboxedSdkView(ctx)
                            addView(surface, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            session.controller.attachView(surface)
                        }
                    },
                    modifier = placement,
                )
            }
        }

        // Loading / error overlay for the active tab, drawn AFTER the surfaces so it covers the active
        // one's (opaque, pre-first-frame) surface — which itself sits above the nav screens, so the overlay
        // can't live in the screen. A spinner until a real page paints, or an error+retry when the load
        // failed or stalled, so a slow / blank / failed load isn't a bare black/white void.
        val activeController = EmbeddedTabHost.sessions.firstOrNull { it.id == activeId }?.controller
        if (activeController != null && bounds.width > 0f && bounds.height > 0f) {
            var loadStatus by remember(activeId) { mutableStateOf(activeController.loadStatus) }
            var timedOut by remember(activeId) { mutableStateOf(false) }
            DisposableEffect(activeId, activeController) {
                activeController.onLoadStatusChanged = { loadStatus = it }
                onDispose { activeController.onLoadStatusChanged = null }
            }
            // Safety net: nothing painted and nothing actively loading after a grace period → offer a retry.
            LaunchedEffect(activeId, loadStatus) {
                timedOut = false
                if (!loadStatus.hasLoadedReal && !loadStatus.failed) {
                    delay(12_000)
                    timedOut = true
                }
            }
            if (!loadStatus.hasLoadedReal) {
                with(density) {
                    Box(
                        Modifier
                            .absoluteOffset(
                                (bounds.left - layerOrigin.x).toDp(),
                                (bounds.top - layerOrigin.y).toDp(),
                            ).size(bounds.width.toDp(), bounds.height.toDp()),
                    ) {
                        EmbeddedLoadOverlay(
                            failed = loadStatus.failed || timedOut,
                            onRetry = {
                                timedOut = false
                                activeController.retry()
                            },
                        )
                    }
                }
            }
        }

        // The active tab's top pull-down sheet, drawn AFTER the surfaces so it sits on top of the
        // (z-below) surface, anchored to the top of the active tab's reserved bounds. Its expanded state
        // is owned here (reset per tab) so we can draw a full-area dismiss scrim behind the open sheet —
        // while collapsed, only the small grabber is interactive and page taps pass through.
        val chrome = EmbeddedTabHost.activeChrome
        val consoleBridge = activeController as? ConsoleBridge
        val consoleCount = consoleBridge?.consoleLogs?.size ?: 0

        if (chrome != null && bounds.width > 0f && bounds.height > 0f) {
            var sheetExpanded by remember(activeId) { mutableStateOf(false) }
            var consoleShowing by remember(activeId) { mutableStateOf(false) }
            var consoleExpanded by remember(activeId) { mutableStateOf(false) }

            if (sheetExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { sheetExpanded = false },
                )
            }

            with(density) {
                TopControlSheet(
                    chrome = chrome,
                    expanded = sheetExpanded,
                    onExpandedChange = { sheetExpanded = it },
                    consoleCount = consoleCount,
                    consoleShowing = consoleShowing,
                    onConsole =
                        if (consoleBridge != null) {
                            {
                                if (consoleShowing) {
                                    consoleShowing = false
                                } else {
                                    consoleShowing = true
                                    consoleExpanded = true
                                }
                            }
                        } else {
                            null
                        },
                    modifier =
                        Modifier
                            .absoluteOffset(
                                (bounds.left - layerOrigin.x).toDp(),
                                (bounds.top - layerOrigin.y).toDp(),
                            ).width(bounds.width.toDp()),
                )
            }

            // Bottom console panel: opened via the "Console" row in the top pull-down sheet.
            if (consoleShowing && consoleBridge != null) {
                with(density) {
                    BottomConsoleSheet(
                        logs = consoleBridge.consoleLogs,
                        expanded = consoleExpanded,
                        onExpandedChange = { consoleExpanded = it },
                        onClear = { consoleBridge.clearConsoleLogs() },
                        modifier =
                            Modifier
                                .absoluteOffset(
                                    (bounds.left - layerOrigin.x).toDp(),
                                    (bounds.top - layerOrigin.y).toDp(),
                                ).size(bounds.width.toDp(), bounds.height.toDp()),
                    )
                }
            }
        }

        // The embedded surface can't host the soft keyboard, so an invisible main-window EditText takes
        // it whenever a field in the ACTIVE tab focuses, relaying edits across [EmbeddedImeBridge]. Lives
        // here, in the main app window, so it can actually receive the IME.
        val context = LocalContext.current
        val imeBridge = EmbeddedTabHost.sessions.firstOrNull { it.id == activeId }?.controller as? EmbeddedImeBridge
        val imeView = remember { RemoteImeView(context) }
        // The embedded WebView can't show Chrome's copy/paste toolbar in its cross-process surface, so when
        // the page has a non-empty selection we show our own here, over the page, and route its actions to
        // the hidden EditText (which mirrors the selection) — see [RemoteImeView.onRangeSelectionChanged].
        var showSelectionToolbar by remember { mutableStateOf(false) }
        var fieldGeometry by remember { mutableStateOf<SelectionGeometry?>(null) }
        // Insertion (cursor) handle visibility: shown when a tap places/moves a bare caret, hidden while
        // typing — like Android. A caret-bearing geometry update means a tap; an edit means typing.
        var showInsertionHandle by remember { mutableStateOf(false) }
        var pageSelection by remember { mutableStateOf<ImeEvent.PageSelection?>(null) }
        DisposableEffect(imeBridge) {
            imeView.bind(imeBridge)
            imeView.onRangeSelectionChanged = { showSelectionToolbar = it }
            imeView.onEdited = { showInsertionHandle = false }
            imeBridge?.onImeEvent = { event ->
                when (event) {
                    is ImeEvent.Focus -> {
                        imeView.onPageFocus(event)
                        event.geometry?.let { fieldGeometry = it }
                        if (event.geometry?.caretX != null) showInsertionHandle = true
                    }
                    ImeEvent.Blur -> {
                        imeView.onPageBlur()
                        fieldGeometry = null
                        showInsertionHandle = false
                    }
                    is ImeEvent.State -> {
                        imeView.onPageState(event)
                        event.geometry?.let { fieldGeometry = it }
                        if (event.geometry?.caretX != null) showInsertionHandle = true
                    }
                    is ImeEvent.PageSelection -> pageSelection = event.takeIf { it.active }
                }
            }
            onDispose {
                imeBridge?.onImeEvent = null
                imeView.onRangeSelectionChanged = null
                imeView.onEdited = null
                showSelectionToolbar = false
                fieldGeometry = null
                showInsertionHandle = false
                pageSelection = null
                imeView.onPageBlur()
                imeView.bind(null)
            }
        }
        with(density) {
            AndroidView(
                factory = { imeView },
                modifier =
                    Modifier
                        .absoluteOffset(
                            (bounds.left - layerOrigin.x).toDp().coerceAtLeast(0.dp),
                            (bounds.top - layerOrigin.y).toDp().coerceAtLeast(0.dp),
                        ).size(1.dp),
            )
        }

        // Input-field selection: cut/copy/paste/select-all routed to the hidden EditText. The DOM exposes no
        // rect for a selection *inside* an input, so we anchor the bar above the field box (reported by the
        // shim), falling back to the top of the tab if the field geometry is missing.
        if (showSelectionToolbar && bounds.width > 0f && bounds.height > 0f) {
            val fg = fieldGeometry
            val items =
                listOf(
                    "Cut" to {
                        imeView.cutSelection()
                        Unit
                    },
                    "Copy" to {
                        imeView.copySelection()
                        Unit
                    },
                    "Paste" to {
                        imeView.pasteClipboard()
                        Unit
                    },
                    "Select all" to {
                        imeView.selectAllText()
                        Unit
                    },
                )
            val originX = bounds.left - layerOrigin.x
            val originY = bounds.top - layerOrigin.y
            val toolbarH = with(density) { 44.dp.toPx() }
            val gap = with(density) { 8.dp.toPx() }
            if (fg != null && fg.viewportWidth > 0f) {
                val scale = bounds.width / fg.viewportWidth
                val topPx = originY + fg.top * scale
                val toolbarY = if (topPx - toolbarH - gap > originY) topPx - toolbarH - gap else originY + fg.bottom * scale + gap
                EmbeddedSelectionToolbar(
                    items = items,
                    centerXpx = originX + ((fg.left + fg.right) / 2f) * scale,
                    topYpx = toolbarY,
                )
            } else {
                EmbeddedSelectionToolbar(
                    items = items,
                    centerXpx = originX + bounds.width / 2f,
                    topYpx = originY + gap,
                )
            }
        }

        // Bare caret in a field (no range): a draggable insertion handle under the cursor, like Android's.
        val fgCaret = fieldGeometry
        if (showInsertionHandle && !showSelectionToolbar && fgCaret?.caretX != null && fgCaret.caretBottom != null &&
            fgCaret.viewportWidth > 0f && bounds.width > 0f && bounds.height > 0f
        ) {
            val scale = bounds.width / fgCaret.viewportWidth
            val originX = bounds.left - layerOrigin.x
            val originY = bounds.top - layerOrigin.y
            InsertionHandle(
                tipPx = Offset(originX + fgCaret.caretX * scale, originY + fgCaret.caretBottom * scale),
                surfaceOriginX = originX,
                surfaceOriginY = originY,
                scale = scale,
                onDragTo = { cssX, cssY ->
                    imeBridge?.sendImeOp(
                        JSONObject()
                            .put("type", "ime.caretmove")
                            .put("x", cssX.toDouble())
                            .put("y", cssY.toDouble())
                            .toString(),
                    )
                },
            )
        }

        // Plain page-text selection: a Copy bar positioned over the selection + a drag handle at each end.
        val pageSel = pageSelection
        val geom = pageSel?.geometry
        if (geom != null && geom.viewportWidth > 0f && bounds.width > 0f && bounds.height > 0f) {
            PageSelectionOverlay(
                geometry = geom,
                surfaceOriginX = bounds.left - layerOrigin.x,
                surfaceOriginY = bounds.top - layerOrigin.y,
                scale = bounds.width / geom.viewportWidth,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("selection", pageSel.text))
                },
                onExtend = { edge, cssX, cssY ->
                    imeBridge?.sendImeOp(
                        JSONObject()
                            .put("type", "ime.pageextend")
                            .put("edge", edge)
                            .put("x", cssX.toDouble())
                            .put("y", cssY.toDouble())
                            .toString(),
                    )
                },
            )
        }
    }
}

/**
 * Host-drawn selection overlay for a plain page-text selection: a Copy bar above the selection plus a
 * draggable handle at each end. [geometry] is in the page's CSS px; [scale] and the surface origin map those
 * to this layer's px. Dragging a handle reports the new CSS-px target via [onExtend], which the shim turns
 * into a selection extension; the resulting geometry update repositions everything.
 */
@Composable
private fun PageSelectionOverlay(
    geometry: SelectionGeometry,
    surfaceOriginX: Float,
    surfaceOriginY: Float,
    scale: Float,
    onCopy: () -> Unit,
    onExtend: (edge: String, cssX: Float, cssY: Float) -> Unit,
) {
    val density = LocalDensity.current

    fun mapX(css: Float) = surfaceOriginX + css * scale

    fun mapY(css: Float) = surfaceOriginY + css * scale

    val toolbarH = with(density) { 44.dp.toPx() }
    val gap = with(density) { 8.dp.toPx() }
    val topPx = mapY(geometry.top)
    val toolbarY = if (topPx - toolbarH - gap > surfaceOriginY) topPx - toolbarH - gap else mapY(geometry.bottom) + gap

    EmbeddedSelectionToolbar(
        items = listOf("Copy" to onCopy),
        centerXpx = mapX((geometry.left + geometry.right) / 2f),
        topYpx = toolbarY,
    )

    SelectionHandle(Offset(mapX(geometry.startX), mapY(geometry.startBottom)), isStart = true, surfaceOriginX, surfaceOriginY, scale) { x, y -> onExtend("start", x, y) }
    SelectionHandle(Offset(mapX(geometry.endX), mapY(geometry.endBottom)), isStart = false, surfaceOriginX, surfaceOriginY, scale) { x, y -> onExtend("end", x, y) }
}

/**
 * A draggable selection-endpoint handle drawn as Android's teardrop: a disc with one squared corner that
 * points to the caret. The start handle points up-right (hangs to the left of the selection start); the end
 * handle points up-left. [tipPx] is the caret foot (this layer's px) where the point sits; a drag reports
 * the new tip position in the page's CSS px.
 */
@Composable
private fun SelectionHandle(
    tipPx: Offset,
    isStart: Boolean,
    surfaceOriginX: Float,
    surfaceOriginY: Float,
    scale: Float,
    onDragTo: (cssX: Float, cssY: Float) -> Unit,
) {
    val sizeDp = 22.dp
    val sizePx = with(LocalDensity.current) { sizeDp.toPx() }
    val color = MaterialTheme.colorScheme.primary
    val currentTip by rememberUpdatedState(tipPx)
    var dragTip by remember { mutableStateOf<Offset?>(null) }
    val tip = dragTip ?: tipPx
    // Place the box so its pointed corner lands on the tip: start = top-right corner, end = top-left corner.
    val boxLeft = if (isStart) tip.x - sizePx else tip.x
    Box(
        Modifier
            .absoluteOffset { IntOffset(boxLeft.roundToInt(), tip.y.roundToInt()) }
            .size(sizeDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragTip = currentTip },
                    onDrag = { change, delta ->
                        change.consume()
                        val np = (dragTip ?: currentTip) + delta
                        dragTip = np
                        onDragTo((np.x - surfaceOriginX) / scale, (np.y - surfaceOriginY) / scale)
                    },
                    onDragEnd = { dragTip = null },
                    onDragCancel = { dragTip = null },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val path =
                Path().apply {
                    if (isStart) {
                        moveTo(d, 0f)
                        lineTo(d, d / 2f)
                        arcTo(Rect(0f, 0f, d, d), 0f, 270f, false)
                        close()
                    } else {
                        moveTo(0f, 0f)
                        lineTo(0f, d / 2f)
                        arcTo(Rect(0f, 0f, d, d), 180f, -270f, false)
                        close()
                    }
                }
            drawPath(path, color)
        }
    }
}

/**
 * A draggable insertion (cursor) handle drawn as Android's upward teardrop, hanging below a bare caret with
 * its tip on the caret. [tipPx] is the caret foot (this layer's px); a drag reports the new caret position in
 * the page's CSS px, which the shim maps back to a character offset.
 */
@Composable
private fun InsertionHandle(
    tipPx: Offset,
    surfaceOriginX: Float,
    surfaceOriginY: Float,
    scale: Float,
    onDragTo: (cssX: Float, cssY: Float) -> Unit,
) {
    val density = LocalDensity.current
    val wPx = with(density) { 20.dp.toPx() }
    val hPx = wPx * 1.4f
    val color = MaterialTheme.colorScheme.primary
    val currentTip by rememberUpdatedState(tipPx)
    // Track the finger separately from what we draw: the handle renders at the AUTHORITATIVE caret (tipPx,
    // which snaps to a character position as the move round-trips through the shim), while the finger drives
    // the move. So the handle stays glued to the line/text and clamps to the field instead of trailing off.
    var fingerPx by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .absoluteOffset { IntOffset((tipPx.x - wPx / 2f).roundToInt(), tipPx.y.roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { fingerPx = currentTip },
                    onDrag = { change, delta ->
                        change.consume()
                        val np = (fingerPx ?: currentTip) + delta
                        fingerPx = np
                        onDragTo((np.x - surfaceOriginX) / scale, (np.y - surfaceOriginY) / scale)
                    },
                    onDragEnd = { fingerPx = null },
                    onDragCancel = { fingerPx = null },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width / 2f
            val cx = size.width / 2f
            val cy = size.height - r
            val path =
                Path().apply {
                    moveTo(cx, 0f)
                    lineTo(cx + r, cy)
                    arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 0f, 180f, false)
                    lineTo(cx, 0f)
                    close()
                }
            drawPath(path, color)
        }
    }
}

/**
 * Host-drawn copy/paste bar for an embedded page selection. Chrome can't present its own action-mode in the
 * cross-process surface, so this floats over the active tab; each action runs on the hidden [RemoteImeView]
 * (which mirrors the page's text + selection), so cut/paste relay back to the page through the normal path.
 * Uses pointer-input rather than `clickable` so tapping it doesn't pull focus off the EditText (which would
 * drop the keyboard and the selection).
 */
@Composable
private fun EmbeddedSelectionToolbar(
    items: List<Pair<String, () -> Unit>>,
    centerXpx: Float,
    topYpx: Float,
) {
    // Self-center: the toolbar's width depends on its items, so measure it and offset by half-width around
    // the requested centre-x (a one-frame left-aligned flash before the first measurement is acceptable).
    var widthPx by remember { mutableStateOf(0) }
    Surface(
        modifier =
            Modifier
                .absoluteOffset { IntOffset((centerXpx - widthPx / 2f).roundToInt(), topYpx.roundToInt()) }
                .onGloballyPositioned { widthPx = it.size.width },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, (label, action) ->
                if (i > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                SelectionToolbarItem(label, action)
            }
        }
    }
}

@Composable
private fun SelectionToolbarItem(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
