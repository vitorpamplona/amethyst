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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import kotlinx.coroutines.CoroutineScope

/*
 * PROTOTYPE ONLY. Run `main` from the IDE (it lives in commonsUI's jvmTest source set, so the
 * gutter run icon works without any Gradle task) to click through the prototypes in a window.
 * Every tab picks media the same way: the add button opens MediaStudio with that screen's config.
 * MediaComposerPrototypesRenderTest renders the same screens to PNGs.
 */

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Media composer prototypes",
            state = rememberWindowState(width = 430.dp, height = 920.dp),
        ) {
            AmethystPreviewTheme(dark = true) { MediaComposerPrototypeGallery() }
        }
    }

@Composable
fun MediaComposerPrototypeGallery() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Post", "Reply (compact)", "Chat", "Profile", "Failure")
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
        }
        key(tab) {
            when (tab) {
                0 -> PrototypeHost(MarkerStyle.BLOCKS)
                1 -> PrototypeHost(MarkerStyle.CHIPS)
                2 -> ChatPrototypeHost()
                3 -> ProfilePrototypeHost()
                else -> PrototypeHost(MarkerStyle.BLOCKS, failing = setOf(2))
            }
        }
    }
}

private var pickCounter = 100

/** What a gallery pick hands over in the prototypes: a photo, sometimes two. */
fun fakePick(count: Int = 1): List<MediaDraft> =
    (0 until count).map {
        val id = pickCounter++
        if (id % 2 == 0) {
            MediaDraft(id, DraftKind.IMAGE, sunsetPhoto(seed = id), 4032, 3024, 3_200_000)
        } else {
            MediaDraft(id, DraftKind.IMAGE, forestPhoto(), 3024, 4032, 4_100_000)
        }
    }

@Composable
fun PrototypeHost(
    style: MarkerStyle,
    failing: Set<Int> = emptySet(),
) {
    val scope = rememberCoroutineScope()
    val composer = remember { sampleComposer(scope).also { it.failNext += failing } }
    PrototypeScreen(composer, style, onAddMedia = { composer.pick(fakePick(2)) })
}

/**
 * The composer with the Studio over it: for a fresh pick (Cancel undoes it, Add inserts it) or for
 * a file already in the post (Done closes, remove takes it out of the text).
 */
@Composable
fun PrototypeScreen(
    composer: PrototypeComposer,
    style: MarkerStyle,
    onAddMedia: () -> Unit = {},
    studioTool: StudioTool? = null,
    holdingOriginal: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        ComposerPrototype(composer, style, onAddMedia)
        val picking = composer.picking
        val editing = composer.editing
        when {
            picking != null && picking.isNotEmpty() ->
                MediaStudio(
                    drafts = picking,
                    initial = picking.first(),
                    config = StudioConfig.postPick(),
                    onConfirm = composer::confirmPick,
                    onCancel = composer::cancelPick,
                    onRemove = { d ->
                        picking.remove(d)
                        if (picking.isEmpty()) composer.cancelPick()
                    },
                    initialTool = studioTool,
                    holdingOriginal = holdingOriginal,
                )
            editing != null ->
                key(editing.id) {
                    MediaStudio(
                        drafts = composer.orderedDrafts(),
                        initial = editing,
                        config = StudioConfig.postEdit(),
                        onConfirm = { composer.editing = null },
                        onRemove = { d ->
                            composer.remove(d)
                            if (composer.drafts.isEmpty()) composer.editing = null
                        },
                        initialTool = studioTool,
                        holdingOriginal = holdingOriginal,
                    )
                }
        }
    }
}

/** A note with three files: a photo with an on-device alt suggestion, a trimmed video, a cropped photo. */
fun sampleComposer(scope: CoroutineScope): PrototypeComposer {
    val c = PrototypeComposer(scope)
    val drafts = sampleDrafts()
    c.message.setTextAndPlaceCursorAtEnd("Golden hour at the coast, then the city lights on the way home.")
    c.addMedia(drafts[0])
    c.message.edit {
        append("Worth the three-hour drive. #photography")
        selection = TextRange(length)
    }
    c.addMedia(drafts[1])
    c.addMedia(drafts[2])
    drafts[0].alt = "The sun setting over a calm sea behind a dark mountain ridge"
    drafts[0].altSuggested = true
    drafts[1].trim = TrimSpec(3_000, 18_000)
    drafts[2].crop = CropSpec(rect = centeredCrop(drafts[2], AspectPreset.PORTRAIT), aspect = AspectPreset.PORTRAIT)
    return c
}
