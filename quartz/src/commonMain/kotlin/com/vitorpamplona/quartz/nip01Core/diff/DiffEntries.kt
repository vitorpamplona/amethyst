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
package com.vitorpamplona.quartz.nip01Core.diff

/** The default tag → [DiffEntry] mapping every event starts from. */
object DiffEntries {
    /**
     * Tag names clients rewrite for their own bookkeeping. They carry nothing the user
     * entered, so they never show up in a diff.
     */
    val BOOKKEEPING_TAGS = setOf("alt", "client", "d", "expiration")

    fun fromTag(tag: Array<String>): DiffEntry? {
        if (tag.isEmpty() || tag[0] in BOOKKEEPING_TAGS) return null
        val value = tag.getOrNull(1) ?: return DiffEntry.OtherTag(tag.toList())
        return when (tag[0]) {
            "p" -> DiffEntry.Person(value, tag.getOrNull(2)?.ifBlank { null }, tag.getOrNull(3)?.ifBlank { null })
            "t" -> DiffEntry.Hashtag(value)
            "word" -> DiffEntry.Word(value)
            "g" -> DiffEntry.Geohash(value)
            "r", "relay" ->
                when (tag.getOrNull(2)) {
                    "read" -> DiffEntry.Relay(value, read = true, write = false)
                    "write" -> DiffEntry.Relay(value, read = false, write = true)
                    else -> DiffEntry.Relay(value)
                }
            "e" -> DiffEntry.EventRef(value, tag.getOrNull(2)?.ifBlank { null })
            "a" -> DiffEntry.AddressRef(value, tag.getOrNull(2)?.ifBlank { null })
            else -> DiffEntry.OtherTag(tag.toList())
        }
    }
}
