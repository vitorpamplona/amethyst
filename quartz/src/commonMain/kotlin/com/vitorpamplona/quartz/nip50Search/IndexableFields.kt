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
package com.vitorpamplona.quartz.nip50Search

/**
 * An event's searchable text decomposed by ROLE, for full-text backends that
 * weight fields instead of indexing one concatenated blob
 * ([SearchableEvent.indexableContent]'s flat form). Produced per kind by
 * [SearchFieldExtractor].
 *
 * A kind is either PROFILE-shaped ([Profile] — kind-0-style identity: kind 0
 * itself, app handlers) or CONTENT-shaped ([Tiered] — title above summary
 * above body). The shape is part of the value, so a consumer discriminates on
 * the type instead of keeping its own list of profile kinds. Both shapes
 * carry a website role — a profile's homepage ([Profile.website]), or a
 * content kind's affiliation URLs ([Tiered.websites]: repo, trackers,
 * bookmarked page) — declared on each shape rather than the interface, so
 * [None] answers no question that doesn't apply to it.
 *
 * Multi-valued roles are carried UNJOINED, as lists: which separator to use —
 * or whether to index the values separately — is the backend's decision, and
 * once values are pre-joined a backend can't unmix them. All strings are
 * trimmed and non-empty.
 */
sealed interface IndexableFields {
    fun isEmpty(): Boolean

    /** No searchable text — the extraction result for non-searchable kinds. */
    data object None : IndexableFields {
        override fun isEmpty(): Boolean = true
    }

    /** Kind-0-shaped identity, each field in its own role. An all-null value means "profile-shaped kind, nothing indexable". */
    data class Profile(
        val name: String? = null,
        val displayName: String? = null,
        val about: String? = null,
        val nip05: String? = null,
        val lud16: String? = null,
        val website: String? = null,
    ) : IndexableFields {
        override fun isEmpty(): Boolean = this == EMPTY

        private companion object {
            val EMPTY = Profile()
        }
    }

    /**
     * Content decomposed by priority tier: [primary] (title-like values),
     * [secondary] (summary/description-like values), [text] (the body — the
     * one inherently single-valued role), plus the raw [hashtags] and
     * [locations] tag values.
     */
    data class Tiered(
        val primary: List<String> = emptyList(),
        val secondary: List<String> = emptyList(),
        val text: String? = null,
        val hashtags: List<String> = emptyList(),
        val locations: List<String> = emptyList(),
        val websites: List<String> = emptyList(),
    ) : IndexableFields {
        override fun isEmpty(): Boolean = this == EMPTY

        private companion object {
            val EMPTY = Tiered()
        }
    }
}
