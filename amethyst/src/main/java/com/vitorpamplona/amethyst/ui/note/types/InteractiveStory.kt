/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList

@Composable
fun RenderInteractiveStory(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseRootEvent = baseNote.event as? InteractiveStoryBaseEvent ?: return
    val address = baseNote.address() ?: return

    // keep updating the root event with new versions
    val note = observeNote(baseNote)
    val rootEvent = note.value?.note?.event as? InteractiveStoryBaseEvent ?: return

    // keep updating the reading state event with new versions
    val readingStateNote = accountViewModel.getInteractiveStoryReadingState(address.toValue())
    val readingState by observeNoteEvent<InteractiveStoryReadingStateEvent>(readingStateNote)

    val currentScene = readingState?.currentScene()

    if (currentScene != null && currentScene != rootEvent.address()) {
        LoadAddressableNote(currentScene, accountViewModel) { currentSceneBaseNote ->
            currentSceneBaseNote?.let {
                val currentSceneEvent by observeNoteEvent<InteractiveStoryBaseEvent>(it)

                currentSceneEvent?.let {
                    RenderInteractiveStory(
                        section = it,
                        onSelect = {
                            val event = it.event as? InteractiveStoryBaseEvent ?: return@RenderInteractiveStory
                            accountViewModel.updateInteractiveStoryReadingState(baseRootEvent, event)
                        },
                        onRestart = {
                            accountViewModel.updateInteractiveStoryReadingState(baseRootEvent, rootEvent)
                        },
                        makeItShort = makeItShort,
                        canPreview = canPreview,
                        quotesLeft = quotesLeft,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    } else {
        RenderInteractiveStory(
            section = rootEvent,
            onSelect = {
                val event = it.event as? InteractiveStoryBaseEvent ?: return@RenderInteractiveStory
                accountViewModel.updateInteractiveStoryReadingState(baseRootEvent, event)
            },
            onRestart = {
                accountViewModel.updateInteractiveStoryReadingState(baseRootEvent, rootEvent)
            },
            makeItShort = makeItShort,
            canPreview = canPreview,
            quotesLeft = quotesLeft,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun RenderInteractiveStory(
    section: InteractiveStoryBaseEvent,
    onSelect: (AddressableNote) -> Unit,
    onRestart: () -> Unit,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    section.title()?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
        ) {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    TranslatableRichTextViewer(
        content = section.content,
        canPreview = canPreview && !makeItShort,
        quotesLeft = quotesLeft,
        modifier = Modifier.fillMaxWidth(),
        tags = EmptyTagList,
        backgroundColor = backgroundColor,
        id = section.id,
        callbackUri = null,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    val options = section.options()

    if (options.isNotEmpty()) {
        Column(Modifier.padding(top = 10.dp)) {
            options.forEach { opt ->
                LoadAddressableNote(opt.address, accountViewModel) { note ->
                    if (note != null) {
                        EventFinderFilterAssemblerSubscription(note)

                        OutlinedButton(
                            onClick = { onSelect(note) },
                        ) {
                            Text(opt.option)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.padding(top = 10.dp)) {
            OutlinedButton(
                onClick = onRestart,
            ) {
                Text("Restart")
            }
        }
    }
}

@Stable
class StoryReadingState {
    private var sectionList = mutableMapOf<HexKey, Note>()
    private var sectionToShowId: HexKey? = null

    val sectionToShow: MutableState<Note?> = mutableStateOf(null)

    fun readSection(note: Note) {
        sectionList[note.idHex] = note
        sectionToShowId = note.idHex
        sectionToShow.value = note
    }
}
