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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.actions.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordingResult
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceMessagePreview
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta

/**
 * The cordn room's composer, on the same field every other Amethyst chat uses.
 *
 * [ThinPaddingTextField] + [ThinSendButton] inside [EditFieldModifier], with the shared
 * mention machinery on top: [ShowUserSuggestionList] for `@` autocomplete,
 * [MentionPreservingInputTransformation] so an IME cannot rewrite half a bech32, and
 * [UrlUserTagOutputTransformation] so a pasted or completed mention reads as a name
 * while you type it. Before this the room had a bare `OutlinedTextField` over a
 * `String`: cordn could *render* mentions and highlight a message that named you, but
 * the only way to enter one was to type `nostr:npub1…` by hand.
 *
 * Mentions are inserted as resolved `nostr:` URIs (`forceNostrUri`). See the parameter's
 * KDoc — cordn has no send-time tagger, so the short `@npub1…` form would ship as text.
 *
 * Looking users up to offer them is the same safe direction as everywhere else on this
 * screen: profiles are public relay data the cache already holds, and reading one puts
 * no part of this conversation into it.
 */
@Composable
internal fun CordnComposer(
    room: CordnGroupChatroom,
    attaching: Boolean,
    pendingVoice: RecordingResult?,
    accountViewModel: AccountViewModel,
    onAttach: (Uri) -> Unit,
    onVoiceNote: (RecordingResult) -> Unit,
    onRemoveVoice: () -> Unit,
    onSend: (String) -> Unit,
) {
    // `room.draft` is the persisted String — commons holds the draft and may not depend
    // on Compose UI, so it cannot hold a TextFieldState. The field owns text *and*
    // selection; these two effects keep them equal. Both writes are guarded on
    // inequality, so neither direction can bounce off the other.
    val draftState = remember(room.gid) { TextFieldState(room.draft.value) }

    val suggestions =
        remember(room.gid, accountViewModel) {
            UserSuggestionState(
                accountViewModel.account,
                accountViewModel.nip05ClientBuilder(),
                // Members of this room rank above the rest of the address book: in a
                // group, the person you are about to name is almost always in it.
                priorityPubkeys = { room.members.value.toSet() },
            )
        }

    DisposableEffect(suggestions) {
        onDispose { suggestions.reset() }
    }

    LaunchedEffect(draftState, room) {
        snapshotFlow { draftState.text.toString() }.collect {
            if (room.draft.value != it) room.draft.value = it
        }
    }

    LaunchedEffect(draftState, room) {
        room.draft.collect { external ->
            // The screen writes the draft from outside on three paths: clearing it on
            // send, restoring it when a send fails, and loading a message's text into
            // it to edit. Cursor to the end, as editFromDraft does elsewhere.
            if (external != draftState.text.toString()) {
                if (external.isEmpty()) draftState.clearText() else draftState.setTextAndPlaceCursorAtEnd(external)
                // onTextChanged only fires for typing, so a list left open by a
                // half-typed "@na" survived the field being cleared on send.
                suggestions.reset()
            }
        }
    }

    // A recorded voice note is something to send even with nothing typed; the text
    // beside it rides along as its caption.
    val canPost by remember(pendingVoice) { derivedStateOf { draftState.text.isNotBlank() || pendingVoice != null } }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(onAttach)
        }

    Column(modifier = EditFieldModifier) {
        // The same preview the post screens use: listen back, re-record, or drop it.
        // Recording used to send the moment you released the button, which is a hard
        // thing to get right first time and impossible to take back afterwards.
        pendingVoice?.let { recording ->
            val meta =
                remember(recording) {
                    AudioMeta(
                        // Empty: nothing is uploaded yet, so the preview plays the
                        // local file instead.
                        url = "",
                        mimeType = recording.mimeType,
                        duration = recording.duration,
                        waveform = recording.amplitudes,
                    )
                }

            VoiceMessagePreview(
                voiceMetadata = meta,
                localFile = recording.file,
                onRemove = onRemoveVoice,
                onReRecord = onVoiceNote,
                isUploading = attaching,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ShowUserSuggestionList(
            suggestions,
            onSelect = { user ->
                suggestions.replaceCurrentWord(draftState, draftState.currentWord(), user, forceNostrUri = true)
                suggestions.reset()
            },
            accountViewModel = accountViewModel,
            modifier = SuggestionListDefaultHeightChat,
        )

        ThinPaddingTextField(
            state = draftState,
            onTextChanged = {
                // Only while the caret is a point: during a range selection the "current
                // word" is whatever is highlighted, which is not something being typed.
                if (draftState.selection.collapsed) {
                    val lastWord = draftState.currentWord()
                    if (lastWord.startsWith("@")) {
                        suggestions.processCurrentWord(lastWord)
                    } else {
                        suggestions.reset()
                    }
                }
            },
            // Anything the keyboard or a paste hands over as content — a GIF, a shared
            // image — takes the same encrypted-upload path as the file picker.
            onContentReceived = { uri, _ -> onAttach(uri) },
            inputTransformation = MentionPreservingInputTransformation,
            outputTransformation = UrlUserTagOutputTransformation(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            shape = EditFieldBorder,
            placeholder = {
                Text(
                    text = stringRes(R.string.cordn_composer_hint),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            leadingIcon = {
                Row {
                    IconButton(onClick = { picker.launch("*/*") }, enabled = !attaching) {
                        Icon(MaterialSymbols.AttachFile, contentDescription = stringRes(R.string.cordn_media_attach))
                    }
                    VoiceNoteButton(enabled = !attaching, onRecorded = onVoiceNote)
                }
            },
            trailingIcon = {
                ThinSendButton(
                    isActive = canPost,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    onSend(draftState.text.toString())
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
    }
}
