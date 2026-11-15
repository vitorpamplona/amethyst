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
import androidx.compose.material3.PrimaryTabRow
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
    val tabs = listOf("A Chips", "B Inline", "C Studio", "D Failure")
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
        }
        key(tab) {
            when (tab) {
                0 -> PrototypeHost(MarkerStyle.CHIPS)
                1 -> PrototypeHost(MarkerStyle.BLOCKS)
                2 -> PrototypeHost(MarkerStyle.BLOCKS, openStudioOn = 1)
                else -> PrototypeHost(MarkerStyle.BLOCKS, failing = setOf(2))
            }
        }
    }
}

@Composable
fun PrototypeHost(
    style: MarkerStyle,
    failing: Set<Int> = emptySet(),
    openStudioOn: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val composer =
        remember {
            sampleComposer(scope).also { c ->
                c.failNext += failing
                openStudioOn?.let { c.editing = c.draft(it) }
            }
        }
    var nextId by remember { mutableIntStateOf(10) }
    PrototypeScreen(composer, style, onAddMedia = {
        val id = nextId++
        composer.addMedia(
            if (id % 2 == 0) {
                MediaDraft(id, DraftKind.IMAGE, sunsetPhoto(seed = id), 4032, 3024, 3_200_000)
            } else {
                MediaDraft(id, DraftKind.IMAGE, forestPhoto(), 3024, 4032, 4_100_000)
            },
        )
    })
}

/** The composer with the studio over it when a file is open. */
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
        composer.editing?.let { draft ->
            key(draft.id) {
                MediaStudio(composer, draft, onDone = { composer.editing = null }, initialTool = studioTool, holdingOriginal = holdingOriginal)
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
