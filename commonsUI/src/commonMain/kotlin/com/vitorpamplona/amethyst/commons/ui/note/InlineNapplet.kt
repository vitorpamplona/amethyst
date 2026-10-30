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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.NappletInlineAsks
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_inline_failed
import com.vitorpamplona.amethyst.commons.resources.napplet_inline_needs_you
import com.vitorpamplona.amethyst.commons.resources.napplet_inline_run
import com.vitorpamplona.amethyst.commons.resources.napplet_inline_running
import com.vitorpamplona.amethyst.commons.resources.napplet_inline_stop
import com.vitorpamplona.amethyst.commons.resources.nsite_open
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletSurface
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val StageShape = RoundedCornerShape(12.dp)

// Wide enough for a game, short enough that a note doesn't fill the screen.
private const val STAGE_RATIO = 4f / 3f

/**
 * Which note's napplet is running. One at a time: starting another stops the first, so the feed
 * never runs more than one app.
 */
object InlineNappletRuns {
    private val mutableLive = MutableStateFlow<String?>(null)

    /** The card running now, if any. */
    val live: StateFlow<String?> = mutableLive.asStateFlow()

    fun start(card: String) {
        mutableLive.value = card
    }

    fun stop(card: String) {
        mutableLive.compareAndSet(card, null)
    }
}

/**
 * The last frame a napplet showed inside a note, by its coordinate, so a stopped run leaves a still
 * picture behind instead of its icon. Kept for the few napplets seen last.
 */
object InlineNappletPosters {
    private const val MAX = 8

    private val frames = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    fun put(
        coordinate: String,
        frame: ImageBitmap,
    ) {
        val current = frames.value
        val kept = if (current.size >= MAX && coordinate !in current) current.entries.drop(1).associate { it.key to it.value } else current - coordinate
        frames.value = kept + (coordinate to frame)
    }

    /** Follows the frame kept for [coordinate]: a run that just stopped saves it as it leaves. */
    fun frame(coordinate: String): Flow<ImageBitmap?> = frames.map { it[coordinate] }.distinctUntilChanged()
}

@OptIn(ExperimentalUuidApi::class)
private fun newId() = Uuid.random().toString()

/** Draws one run of an app inside a note: the platform's surface for instance `instanceId`. */
internal typealias InlineSurface = @Composable (instanceId: String, onStatus: (InlineNappletStatus) -> Unit, modifier: Modifier) -> Unit

/** [InlineNappletStage] for a NIP-5D napplet. */
@Composable
internal fun InlineNappletStage(
    manifest: NappletManifest,
    coordinate: String,
    title: String,
    icon: String?,
    onOpen: () -> Unit,
) = InlineNappletStage(coordinate, title, icon, onOpen) { instanceId, onStatus, modifier ->
    InlineNappletSurface(manifest, instanceId, onStatus, modifier)
}

/**
 * Where an app runs inside its note: a poster with Run here until the user starts it, then the
 * app itself ([surface]) under a bar the page can't draw over (what runs, Open, Stop). A napplet may
 * use what the user already granted it; anything that needs the user is refused and shown as a
 * request to Open it, where the question can be asked. Scrolling the note away stops it.
 * [coordinate] keys the last frame kept as its poster.
 */
@Composable
internal fun InlineNappletStage(
    coordinate: String,
    title: String,
    icon: String?,
    onOpen: () -> Unit,
    surface: InlineSurface,
) {
    val card = remember { newId() }
    val live by InlineNappletRuns.live.collectAsState()
    val running = live == card
    DisposableEffect(card) { onDispose { InlineNappletRuns.stop(card) } }

    val border = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().clip(StageShape).border(1.dp, border, StageShape)) {
        if (running) {
            LiveBar(
                onOpen = {
                    InlineNappletRuns.stop(card)
                    onOpen()
                },
                onStop = { InlineNappletRuns.stop(card) },
            )
        }
        Box(Modifier.fillMaxWidth().aspectRatio(STAGE_RATIO), contentAlignment = Alignment.Center) {
            if (running) {
                LiveRun(surface, title, onOpen = {
                    InlineNappletRuns.stop(card)
                    onOpen()
                })
            } else {
                Poster(coordinate, title, icon, onRun = { InlineNappletRuns.start(card) })
            }
        }
    }
}

/** Drawn by Amethyst above the page, so the page can't pose as the note or the app around it. */
@Composable
private fun LiveBar(
    onOpen: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.Shield, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(Res.string.napplet_inline_running),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpen) { Text(stringResource(Res.string.nsite_open)) }
        IconButton(onClick = onStop) {
            Icon(MaterialSymbols.Stop, contentDescription = stringResource(Res.string.napplet_inline_stop), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LiveRun(
    surface: InlineSurface,
    title: String,
    onOpen: () -> Unit,
) {
    // Retry starts a fresh instance: the one that failed is gone.
    var attempt by remember { mutableIntStateOf(0) }
    val instanceId = remember(attempt) { newId() }
    var status by remember(instanceId) { mutableStateOf(InlineNappletStatus.LOADING) }
    val asked by remember(instanceId) { NappletInlineAsks.shared.asked(instanceId) }.collectAsState(false)
    DisposableEffect(instanceId) { onDispose { NappletInlineAsks.shared.forget(instanceId) } }

    surface(instanceId, { status = it }, Modifier.fillMaxSize().swallowTaps())

    when (status) {
        // Opaque, so it also covers the surface before the page's first frame.
        InlineNappletStatus.LOADING ->
            Box(Modifier.fillMaxSize().swallowTaps().background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp))
            }
        InlineNappletStatus.FAILED ->
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.napplet_inline_failed, title),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
            }
        InlineNappletStatus.SHOWING -> {}
    }

    if (asked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.napplet_inline_needs_you, title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpen) { Text(stringResource(Res.string.nsite_open)) }
            }
        }
    }
}

@Composable
private fun Poster(
    coordinate: String,
    title: String,
    icon: String?,
    onRun: () -> Unit,
) {
    val frame = remember(coordinate) { InlineNappletPosters.frame(coordinate) }.collectAsState(null).value
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        if (frame != null) {
            Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.45f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (frame == null) AppIcon(icon, title)
            FilledTonalButton(onClick = onRun) {
                Icon(MaterialSymbols.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.napplet_inline_run))
            }
        }
    }
}

/**
 * A tap on a running app is the app's, whether it reached the page or not (it is still loading,
 * say): it must never open the note around it. Presses and releases are consumed in the
 * [PointerEventPass.Initial] pass, before the note's click detector looks at them, so the note
 * neither opens nor takes the keyboard (Space would then open it); the surface below forwards
 * them to the page all the same. Movement is left alone, so the feed still scrolls.
 */
private fun Modifier.swallowTaps(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach {
                    if (it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed()) it.consume()
                }
            }
        }
    }
