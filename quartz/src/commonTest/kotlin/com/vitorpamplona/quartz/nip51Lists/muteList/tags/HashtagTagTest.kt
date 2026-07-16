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
package com.vitorpamplona.quartz.nip51Lists.muteList.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HashtagTagTest {
    private val hashtag = "bitcoin"

    @Test fun isTagged_acceptsHashtag() {
        assertTrue(HashtagTag.isTagged(arrayOf("t", hashtag)))
    }

    @Test fun isTagged_rejectsEmpty() {
        assertFalse(HashtagTag.isTagged(arrayOf("t", "")))
    }

    @Test fun isTagged_rejectsWrongPrefix() {
        assertFalse(HashtagTag.isTagged(arrayOf("word", hashtag)))
    }

    @Test fun parse_extractsHashtag() {
        val tag = assertNotNull(HashtagTag.parse(arrayOf("t", hashtag)))
        assertEquals(hashtag, tag.hashtag)
    }

    @Test fun parse_rejectsEmpty() {
        assertNull(HashtagTag.parse(arrayOf("t", "")))
    }

    @Test fun parse_rejectsWrongPrefix() {
        assertNull(HashtagTag.parse(arrayOf("p", hashtag)))
    }

    @Test fun parseId_extractsValue() {
        assertEquals(hashtag, HashtagTag.parseId(arrayOf("t", hashtag)))
    }

    @Test fun toTagArray_roundTrips() {
        val parsed = assertNotNull(HashtagTag.parse(HashtagTag(hashtag).toTagArray()))
        assertEquals(hashtag, parsed.hashtag)
    }

    @Test fun muteTagCompanion_parsesHashtagTag() {
        val parsed = assertNotNull(MuteTag.parse(arrayOf("t", hashtag)))
        assertTrue(parsed is HashtagTag)
    }

    @Test fun muteTagCompanion_isTaggedRecognizesHashtagTag() {
        assertTrue(MuteTag.isTagged(arrayOf("t", hashtag)))
    }

    @Test fun muteTagCompanion_doesNotConfuseHashtagWithWord() {
        // "t" must parse as a hashtag, not a word; "word" must parse as a word.
        assertTrue(MuteTag.parse(arrayOf("t", hashtag)) is HashtagTag)
        assertTrue(MuteTag.parse(arrayOf("word", hashtag)) is WordTag)
    }
}
