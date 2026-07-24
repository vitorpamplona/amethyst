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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
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

// Per-drag-move auto-scroll step (CSS px) when a handle is dragged into the surface's top/bottom edge zone.
private const val AUTOSCROLL_STEP_CSS = 40.0

/**
 * Drives the selection loupe from a handle drag: [active] shows/hides the bubble, [fingerLayerPx] is the
 * drag point in the tab layer's px (where the bubble floats), and ([surfaceX], [surfaceY]) is the same point
 * in surface px (what the provider captures around). Called on drag start/move with `true`, on end with `false`.
 */
private typealias OnMagnify = (active: Boolean, fingerLayerPx: Offset, surfaceX: Float, surfaceY: Float) -> Unit

/** Sends a CSS-px positional IME op (caret move / selection extend) to the page; [edge] is null for a caret move. */
private fun EmbeddedImeBridge.sendFieldOp(
    type: String,
    edge: String?,
    cssX: Float,
    cssY: Float,
) = sendImeOp(
    JSONObject()
        .put("type", type)
        .apply { if (edge != null) put("edge", edge) }
        .put("x", cssX.toDouble())
        .put("y", cssY.toDouble())
        .toString(),
)

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
            // Key on the controller too: a theme rebuild replaces the controller under the same id, and
            // the new one needs a fresh SandboxedSdkView (the factory below attaches the surface once).
            key(session.id, session.controller) {
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
        //
        // Gated on the active *id*, not on a live controller: right after [EmbeddedTabHost.rebuildAll] (an
        // account switch or a theme flip) the active tab has no session at all for a frame or more, and a
        // SandboxedSdkView with no adapter paints nothing but its background — a black rectangle that reads
        // as broken. Treat "no session yet" as "still loading" so the spinner covers that window too.
        val activeController = EmbeddedTabHost.sessions.firstOrNull { it.id == activeId }?.controller
        if (activeId != null && bounds.width > 0f && bounds.height > 0f) {
            var loadStatus by remember(activeId) { mutableStateOf(activeController?.loadStatus ?: EmbeddedLoadStatus()) }
            var timedOut by remember(activeId) { mutableStateOf(false) }
            DisposableEffect(activeId, activeController) {
                // No session yet — the tab is mid-rebuild after an account switch, or being warmed for the
                // first time. Fall back to the "still loading" state so the spinner covers the surface
                // instead of leaving a bare black rectangle where a dead/absent adapter paints nothing.
                loadStatus = activeController?.loadStatus ?: EmbeddedLoadStatus()
                activeController?.onLoadStatusChanged = { loadStatus = it }
                onDispose { activeController?.onLoadStatusChanged = null }
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
                            // A tab with no session yet can't have failed — it hasn't started. Keep it on
                            // the spinner so a re-arming tab never flashes an error the user can't act on.
                            failed = activeController != null && (loadStatus.failed || timedOut),
                            onRetry = {
                                timedOut = false
                                activeController?.retry()
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
        // The embedded WebView can't show Chrome's copy/paste toolbar or selection handles in its cross-process
        // surface, so we draw our own over the page and route actions through the hidden EditText (which mirrors
        // the selection). All the show/hide state lives in one [SelectionUiState] so the rules — toolbar hides
        // while dragging or scrolling, handles hide while scrolling — are expressed in one place.
        val sel = remember { SelectionUiState() }
        DisposableEffect(imeBridge) {
            imeView.bind(imeBridge)
            imeView.onRangeSelectionChanged = { sel.onFieldRangeToggle(it) }
            imeView.onEdited = { sel.onEdited() }
            imeBridge?.onImeEvent = { event ->
                when (event) {
                    is ImeEvent.Focus -> {
                        imeView.onPageFocus(event)
                        // A field took focus → the browser dropped any page-text selection; clear its overlay so
                        // the stale page handles/Copy bar don't sit above (and steal drags from) the field UI.
                        sel.onPageSelection(null)
                        // Cancel any in-flight scroll-hide from the page phase: otherwise the field's own
                        // selection-reveal scrolls keep it armed and the new field handles/toolbar never appear.
                        sel.scrolling = false
                        sel.onFieldGeometry(event.geometry, event.text.isNotEmpty())
                    }
                    ImeEvent.Blur -> {
                        imeView.onPageBlur()
                        sel.onBlur()
                    }
                    is ImeEvent.State -> {
                        imeView.onPageState(event)
                        sel.onFieldGeometry(event.geometry, event.text.isNotEmpty())
                    }
                    is ImeEvent.PageSelection -> sel.onPageSelection(event.takeIf { it.active })
                    is ImeEvent.Scroll -> sel.scrolling = event.active
                    is ImeEvent.CaretTap -> sel.onCaretTap(event.geometry)
                }
            }
            onDispose {
                imeBridge?.onImeEvent = null
                imeView.onRangeSelectionChanged = null
                imeView.onEdited = null
                sel.reset()
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

        // Native-style auto-hide: the insertion handle disappears after a few seconds of inactivity; the next
        // caret tap re-emits geometry and re-shows it. A drag keeps it alive (dragging gate) so it never
        // vanishes mid-drag; the key re-arms whenever the caret moves.
        val caretForTimeout = sel.insertionHandle
        LaunchedEffect(caretForTimeout, sel.dragging) {
            if (caretForTimeout != null && !sel.dragging) {
                delay(4_000)
                sel.hideCaret()
            }
        }

        // Selection loupe (magnifier #4): while a caret/selection handle is dragged, show a magnified live
        // slice of the page. Host-side PixelCopy can't read the sandbox surface (ERROR_SOURCE_NO_DATA), so the
        // pixels are captured in the `:napplet` provider and shipped back here (see [EmbeddedMagnifierProbe]).
        // The handles report the drag point via [onMagnify]; we track it for the bubble position and ask the
        // provider for frames, throttled to one in flight (with a 100 ms timeout) so a fast drag can't flood
        // the IPC channel. The bubble follows the finger every move; the bitmap refreshes as frames land.
        val magProbe = imeBridge as? EmbeddedMagnifierProbe
        val magnifier = remember { MagnifierUiState() }
        val magBubble = DpSize(132.dp, 74.dp)
        val magZoom = 1.5f
        // Source rect (surface px) = bubble px / zoom, so the provider-scaled frame lands ≈ bubble-sized.
        val magSrcW = with(density) { (magBubble.width.toPx() / magZoom).roundToInt() }
        val magSrcH = with(density) { (magBubble.height.toPx() / magZoom).roundToInt() }
        DisposableEffect(magProbe) {
            magProbe?.onMagnifierFrame = { frame ->
                if (magnifier.visible) {
                    magnifier.awaitingFrame = false
                    BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size)?.let { magnifier.image = it.asImageBitmap() }
                }
            }
            onDispose {
                magProbe?.onMagnifierFrame = null
                magnifier.hide()
            }
        }
        // Auto-scroll (#9): while dragging a handle near the surface's top/bottom edge, nudge the embedded
        // content so the selection can keep extending past the viewport — like Android. We scroll on each
        // drag-move that's in the edge zone (the finger is usually still micro-moving); the shim keeps the
        // overlays up during this programmatic scroll and re-reports geometry so they track.
        val edgeZonePx = with(density) { 56.dp.toPx() }
        val onMagnify: OnMagnify = { active, fingerPx, surfaceX, surfaceY ->
            // Drives the loupe, the toolbar-hide-while-dragging rule (and the dragged handle hides itself
            // locally), edge auto-scroll, and suspending the nav drawer's edge swipe — one drag lifecycle.
            sel.dragging = active
            EmbeddedSelectionDrag.dragging = active
            if (active && bounds.height > 0f) {
                val oy = bounds.top - layerOrigin.y
                val dy =
                    when {
                        fingerPx.y < oy + edgeZonePx -> -AUTOSCROLL_STEP_CSS
                        fingerPx.y > oy + bounds.height - edgeZonePx -> AUTOSCROLL_STEP_CSS
                        else -> 0.0
                    }
                if (dy != 0.0) imeBridge?.sendImeOp(JSONObject().put("type", "ime.autoscroll").put("dy", dy).toString())
            }
            if (!active || magProbe == null) {
                magnifier.hide()
            } else {
                magnifier.visible = true
                magnifier.anchorPx = fingerPx
                val nowMs = SystemClock.uptimeMillis()
                if (!magnifier.awaitingFrame || nowMs - magnifier.lastRequestUptimeMs > 100L) {
                    magnifier.awaitingFrame = true
                    magnifier.lastRequestUptimeMs = nowMs
                    magProbe.requestMagnifier(surfaceX, surfaceY, magSrcW, magSrcH, magZoom)
                }
            }
        }

        val originX = bounds.left - layerOrigin.x
        val originY = bounds.top - layerOrigin.y
        val haveBounds = bounds.width > 0f && bounds.height > 0f

        // In-field (<input>/<textarea>) range selection: cut/copy/paste/select-all routed to the hidden
        // EditText, plus draggable start/end handles (drag → `ime.fieldextend`). The toolbar hides while a
        // handle is dragged or the page scrolls; the handles hide only while scrolling.
        val fieldItems =
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
        val fieldHandles = sel.fieldHandles
        if (haveBounds && fieldHandles != null) {
            RangeSelectionOverlay(
                geometry = fieldHandles,
                surfaceOriginX = originX,
                surfaceOriginY = originY,
                scale = bounds.width / fieldHandles.viewportWidth,
                showToolbar = sel.fieldToolbar != null,
                toolbarItems = fieldItems,
                onMagnify = onMagnify,
                onExtend = { edge, cssX, cssY -> imeBridge?.sendFieldOp("ime.fieldextend", edge, cssX, cssY) },
            )
        } else if (haveBounds && sel.fieldToolbar != null) {
            // Authoritative range but no geometry yet → a centered fallback toolbar (no handles).
            EmbeddedSelectionToolbar(items = fieldItems, centerXpx = originX + bounds.width / 2f, topYpx = originY + with(density) { 8.dp.toPx() })
        }

        // Bare caret in a field (no range): a draggable insertion handle under the cursor, like Android's.
        val caretGeom = sel.insertionHandle
        if (haveBounds && caretGeom?.caretX != null && caretGeom.caretBottom != null) {
            val scale = bounds.width / caretGeom.viewportWidth
            // Half the caret's line height (layer px), so the loupe capture centers on the text line.
            val caretLineHalf =
                (((caretGeom.caretTop?.let { caretGeom.caretBottom - it }) ?: 0f) * scale / 2f)
                    .coerceIn(0f, with(density) { 28.dp.toPx() })
            InsertionHandle(
                tipPx = Offset(originX + caretGeom.caretX * scale, originY + caretGeom.caretBottom * scale),
                surfaceOriginX = originX,
                surfaceOriginY = originY,
                scale = scale,
                lineHalfPx = caretLineHalf,
                onMagnify = onMagnify,
                onTap = { sel.toggleInsertionPopup() },
                onDragTo = { cssX, cssY -> imeBridge?.sendFieldOp("ime.caretmove", null, cssX, cssY) },
            )
        }

        // Tapping the bare insertion handle opens a small Paste / Select-all popup above the caret (native).
        val popupCaret = sel.insertionPopupAt
        if (haveBounds && popupCaret?.caretX != null && popupCaret.caretTop != null) {
            val scale = bounds.width / popupCaret.viewportWidth
            val gap = with(density) { 8.dp.toPx() }
            val toolbarH = with(density) { 44.dp.toPx() }
            val caretTopPx = originY + popupCaret.caretTop * scale
            val caretBottomPx = originY + (popupCaret.caretBottom ?: popupCaret.caretTop) * scale
            val topY = if (caretTopPx - toolbarH - gap > originY) caretTopPx - toolbarH - gap else caretBottomPx + gap
            EmbeddedSelectionToolbar(
                items =
                    listOf(
                        "Paste" to {
                            imeView.pasteClipboard()
                            sel.hideInsertionPopup()
                        },
                        "Select all" to {
                            imeView.selectAllText()
                            sel.hideInsertionPopup()
                        },
                    ),
                centerXpx = originX + popupCaret.caretX * scale,
                topYpx = topY,
            )
        }

        // Plain page-text selection: a Copy bar over the selection + a drag handle at each end (same rules).
        val pageHandles = sel.pageHandles
        val pageSel = sel.pageSelection
        if (haveBounds && pageHandles != null && pageSel != null) {
            RangeSelectionOverlay(
                geometry = pageHandles,
                surfaceOriginX = originX,
                surfaceOriginY = originY,
                scale = bounds.width / pageHandles.viewportWidth,
                showToolbar = sel.pageToolbar != null,
                toolbarItems =
                    listOf(
                        "Copy" to {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("selection", pageSel.text))
                        },
                    ),
                onMagnify = onMagnify,
                onExtend = { edge, cssX, cssY -> imeBridge?.sendFieldOp("ime.pageextend", edge, cssX, cssY) },
            )
        }

        // The loupe sits above everything else, following the active handle/caret drag.
        Magnifier(magnifier, layerSize, magBubble)
    }
}

/**
 * Host-drawn selection overlay shared by in-field (`<input>`/`<textarea>`) and plain page-text selections:
 * a floating toolbar above the selection (only when [showToolbar] — it's hidden mid-drag) plus a draggable
 * handle at each end. [geometry] is in the page's CSS px; [scale] and the surface origin map those to this
 * layer's px. Dragging a handle reports the new CSS-px target via [onExtend], which the shim turns into a
 * selection extension; the resulting geometry update repositions everything.
 */
@Composable
private fun RangeSelectionOverlay(
    geometry: SelectionGeometry,
    surfaceOriginX: Float,
    surfaceOriginY: Float,
    scale: Float,
    showToolbar: Boolean,
    toolbarItems: List<Pair<String, () -> Unit>>,
    onMagnify: OnMagnify?,
    onExtend: (edge: String, cssX: Float, cssY: Float) -> Unit,
) {
    val density = LocalDensity.current

    fun mapX(css: Float) = surfaceOriginX + css * scale

    fun mapY(css: Float) = surfaceOriginY + css * scale

    if (showToolbar) {
        val toolbarH = with(density) { 44.dp.toPx() }
        val gap = with(density) { 8.dp.toPx() }
        val topPx = mapY(geometry.top)
        val toolbarY = if (topPx - toolbarH - gap > surfaceOriginY) topPx - toolbarH - gap else mapY(geometry.bottom) + gap
        EmbeddedSelectionToolbar(
            items = toolbarItems,
            centerXpx = mapX((geometry.left + geometry.right) / 2f),
            topYpx = toolbarY,
        )
    }

    // Half the selection's line height (layer px), clamped — the bounding box spans every selected line, so
    // cap it at a sane single-line height so the loupe centers on the dragged endpoint's line, not the middle
    // of a tall multi-line box.
    val lineHalfPx = ((geometry.bottom - geometry.top) * scale / 2f).coerceAtMost(with(density) { 24.dp.toPx() })
    SelectionHandle(Offset(mapX(geometry.startX), mapY(geometry.startBottom)), isStart = true, surfaceOriginX, surfaceOriginY, scale, lineHalfPx, onMagnify) { x, y -> onExtend("start", x, y) }
    SelectionHandle(Offset(mapX(geometry.endX), mapY(geometry.endBottom)), isStart = false, surfaceOriginX, surfaceOriginY, scale, lineHalfPx, onMagnify) { x, y -> onExtend("end", x, y) }
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
    lineHalfPx: Float,
    onMagnify: OnMagnify?,
    onDragTo: (cssX: Float, cssY: Float) -> Unit,
) {
    val sizeDp = 22.dp
    val sizePx = with(LocalDensity.current) { sizeDp.toPx() }
    val color = MaterialTheme.colorScheme.primary
    val currentTip by rememberUpdatedState(tipPx)
    val currentMagnify by rememberUpdatedState(onMagnify)
    var dragTip by remember { mutableStateOf<Offset?>(null) }
    val tip = dragTip ?: tipPx

    // Loupe capture: X follows the finger, Y is locked to the authoritative endpoint's line ([currentTip] is
    // the foot, so lift by half the line height) — so the bubble shows the line being edited, not wherever the
    // finger drifts vertically.
    fun magnify(
        fingerPx: Offset,
        active: Boolean = true,
    ) = currentMagnify?.invoke(active, fingerPx, fingerPx.x - surfaceOriginX, currentTip.y - surfaceOriginY - lineHalfPx)
    // Place the box so its pointed corner lands on the tip: start = top-right corner, end = top-left corner.
    val boxLeft = if (isStart) tip.x - sizePx else tip.x
    Box(
        Modifier
            .absoluteOffset { IntOffset(boxLeft.roundToInt(), tip.y.roundToInt()) }
            .size(sizeDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragTip = currentTip
                        magnify(currentTip)
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        val np = (dragTip ?: currentTip) + delta
                        dragTip = np
                        onDragTo((np.x - surfaceOriginX) / scale, (np.y - surfaceOriginY) / scale)
                        magnify(np)
                    },
                    onDragEnd = {
                        dragTip = null
                        magnify(Offset.Zero, active = false)
                    },
                    onDragCancel = {
                        dragTip = null
                        magnify(Offset.Zero, active = false)
                    },
                )
            },
    ) {
        // Hide the teardrop while THIS handle is being dragged — the loupe stands in for it, like Android.
        if (dragTip == null) {
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
    lineHalfPx: Float,
    onMagnify: OnMagnify?,
    onTap: () -> Unit,
    onDragTo: (cssX: Float, cssY: Float) -> Unit,
) {
    val density = LocalDensity.current
    val wPx = with(density) { 20.dp.toPx() }
    val hPx = wPx * 1.4f
    val color = MaterialTheme.colorScheme.primary
    val currentTip by rememberUpdatedState(tipPx)
    val currentMagnify by rememberUpdatedState(onMagnify)
    val currentTap by rememberUpdatedState(onTap)

    // Loupe capture: X follows the finger, Y is locked to the authoritative caret's line ([currentTip] is the
    // caret foot, so lift by half the line height) — so the bubble shows the edited line, not wherever the
    // finger drifts vertically (e.g. a diagonal drag in a textarea).
    fun magnify(
        fingerPx: Offset,
        active: Boolean = true,
    ) = currentMagnify?.invoke(active, fingerPx, fingerPx.x - surfaceOriginX, currentTip.y - surfaceOriginY - lineHalfPx)
    // Track the finger separately from what we draw: the handle renders at the AUTHORITATIVE caret (tipPx,
    // which snaps to a character position as the move round-trips through the shim), while the finger drives
    // the move. So the handle stays glued to the line/text and clamps to the field instead of trailing off.
    var fingerPx by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .absoluteOffset { IntOffset((tipPx.x - wPx / 2f).roundToInt(), tipPx.y.roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            // One unified gesture so a TAP (toggle the Paste/Select-All popup) and a DRAG (move the caret)
            // don't fight each other. We consume the down so the tap never bleeds to the surface (which would
            // place a caret there and dismiss the popup we're opening).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var dragging = false
                    var fp = currentTip
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!dragging) {
                                currentTap()
                            } else {
                                fingerPx = null
                                magnify(Offset.Zero, active = false)
                            }
                            break
                        }
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            fp = currentTip
                            magnify(currentTip)
                        }
                        if (dragging) {
                            // Read the delta BEFORE consuming: positionChange() returns Offset.Zero once the
                            // change isConsumed, so consuming first (or the sandbox surface consuming the move in
                            // an earlier pass) would freeze `fp` at the caret — the handle drags but the caret
                            // never moves. positionChangeIgnoreConsumed() is immune to both.
                            fp += change.positionChangeIgnoreConsumed()
                            change.consume()
                            fingerPx = fp
                            onDragTo((fp.x - surfaceOriginX) / scale, (fp.y - surfaceOriginY) / scale)
                            magnify(fp)
                        }
                    }
                }
            },
    ) {
        // Hide the teardrop while dragging — the loupe stands in for it, like Android.
        if (fingerPx == null) {
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
    // Center in a SINGLE layout pass: measure the bar, then place it at centerX − width/2. (The old
    // measure-then-offset approach rendered it left-edge-at-centerX for one frame, so it visibly slid in
    // from the right each time the bar was (re)composed.)
    Layout(
        content = {
            Surface(
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
        },
    ) { measurables, constraints ->
        val bar = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            bar.place((centerXpx - bar.width / 2f).roundToInt(), topYpx.roundToInt())
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
                // Consume the DOWN (not just the up) so the tap never bleeds through to the embedded surface
                // beneath — otherwise tapping the bar over non-editable page area blurs the field.
                .pointerInput(label) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            onClick()
                        }
                    }
                }.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
