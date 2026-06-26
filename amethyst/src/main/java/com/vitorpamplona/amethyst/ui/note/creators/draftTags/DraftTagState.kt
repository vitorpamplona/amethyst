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
package com.vitorpamplona.amethyst.ui.note.creators.draftTags

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.model.AddressableNote
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Stable
class DraftTagState {
    var current: String by mutableStateOf(newTag())
    var usedDraftTags by mutableStateOf(setOf(current))

    private var noteBuilder: ((tag: String) -> AddressableNote)? = null

    /**
     * Strong reference to the AddressableNote backing the [current] draft tag, kept alive so
     * LocalCache's weak reference can't garbage-collect it before a deletion needs it (which
     * would orphan the draft on the relays). It is the live cached note for the tag, so its
     * `event` reflects the draft automatically as it is saved or removed — no need to re-assign
     * it after each save. Rebuilt whenever the tag changes ([set]/[rotate]); valid once [start]
     * wires the builder, which happens when the composer is initialized.
     */
    lateinit var note: AddressableNote
        private set

    private val _versions = MutableStateFlow(0)

    @OptIn(FlowPreview::class)
    val versions = _versions.debounce(1000)

    @OptIn(ExperimentalUuidApi::class)
    fun newTag() = Uuid.random().toString()

    /** Wires the tag -> note builder and builds the note for the current tag. */
    fun start(builder: (tag: String) -> AddressableNote) {
        noteBuilder = builder
        note = builder(current)
    }

    fun rotate() {
        set(newTag())
        _versions.update { 0 }
    }

    fun set(existingTag: String) {
        current = existingTag
        usedDraftTags += existingTag
        noteBuilder?.let { note = it(existingTag) }
    }

    fun newVersion() {
        _versions.update { it + 1 }
    }
}
