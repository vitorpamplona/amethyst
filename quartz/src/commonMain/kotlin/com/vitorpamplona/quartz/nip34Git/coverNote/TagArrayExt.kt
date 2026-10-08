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
package com.vitorpamplona.quartz.nip34Git.coverNote

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag

/**
 * The covered item. The spec writes `["e", <id>, <relay>, "root"]`; the notes seen on relays
 * omit the marker, so an unmarked `e` with a well-formed id is accepted when no marked root exists.
 */
fun TagArray.coverNoteRoot(): MarkedETag? =
    fastFirstNotNullOfOrNull(MarkedETag::parseRoot)
        ?: fastFirstNotNullOfOrNull { tag -> MarkedETag.parseId(tag)?.let { MarkedETag.parseAllThreadTags(tag) } }

fun TagArray.coverNoteRootAuthor() = fastFirstNotNullOfOrNull(PTag::parseKey)

fun TagArray.coverNoteRootKind() = fastFirstNotNullOfOrNull(KindTag::parse)

fun TagArray.coverNoteQuotes() = mapNotNull(QTag::parse)
