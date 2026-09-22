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
package com.vitorpamplona.amethyst.ui.note.types.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.ui.note.getGradient
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log

/** How many members a list card shows before it asks to be expanded. Matches DisplayPeopleList. */
private const val COLLAPSED_MEMBERS = 3

/**
 * The shared shape of every NIP-51 list card: a name, an optional description, the members, and —
 * when the viewer is not the list's author — an honest line about the ones they cannot see.
 *
 * Each list kind supplies its own title, its own member type and its own row, because a mute list
 * of pubkeys and a bookmark list of notes are not the same thing and should not pretend to be.
 * What they share is this frame.
 */
@Composable
fun <T> ListCard(
    title: String,
    description: String?,
    items: List<T>,
    /** True when the event carries private members this viewer has no key for. */
    hasUnreadablePrivateItems: Boolean,
    backgroundColor: MutableState<Color>,
    itemContent: @Composable (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) items else items.take(COLLAPSED_MEMBERS)

    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(5.dp),
        textAlign = TextAlign.Center,
    )

    description?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Said even when some members are public, because otherwise the visible ones read as the whole
    // list. A list whose members are *all* private — which is what divine.video publishes — would
    // otherwise show a title and nothing else, and look broken rather than closed.
    if (hasUnreadablePrivateItems) {
        Text(
            text = stringRes(R.string.nip51_list_has_private_members),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Box {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
            shown.forEach { itemContent(it) }
        }

        if (items.size > COLLAPSED_MEMBERS && !expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = true }
            }
        }
    }
}

/**
 * Decrypts a list's private members, and keeps "could not read them" apart from "there are none".
 *
 * `null` means we never got the plaintext — the viewer is not the author, or the decrypt failed.
 * An empty list means we read the content and it holds nothing, which is an empty list and must
 * not be labelled private: its author can see straight into it.
 */
@Composable
fun <T> loadPrivateItems(
    noteEvent: Event,
    accountViewModel: AccountViewModel,
    load: suspend (NostrSigner) -> List<T>?,
): State<List<T>?> =
    produceState<List<T>?>(initialValue = null, noteEvent) {
        if (noteEvent.pubKey != accountViewModel.account.signer.pubKey) return@produceState
        value =
            try {
                load(accountViewModel.account.signer)
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                Log.w("NIP51ListCard", "Cannot decrypt private members of ${noteEvent.id}", e)
                null
            }
    }

/**
 * True when this event is withholding members from the viewer.
 *
 * Both halves matter: an empty `content` is a list with no private members at all, and a non-null
 * [privateItems] means we already read them, so neither is hidden.
 */
fun Event.hidesPrivateMembers(privateItems: List<*>?): Boolean = content.isNotEmpty() && privateItems == null

/** A list's own name if it has one, else the `d` tag it is addressed by, else the kind's name. */
@Composable
fun listTitle(
    explicitTitle: String?,
    dTag: String?,
    fallback: Int,
): String =
    explicitTitle?.takeIf { it.isNotBlank() }
        ?: dTag?.takeIf { it.isNotBlank() }
        ?: stringRes(fallback)

/** Remembers the concatenation of a list's public and (once decrypted) private members. */
@Composable
fun <T> rememberAllMembers(
    public: List<T>,
    private: List<T>?,
): List<T> = remember(public, private) { if (private == null) public else public + private }
