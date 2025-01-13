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
package com.vitorpamplona.quartz.nip01Core.geohash

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent

fun Event.hasGeohashes() =
    if (this is CommentEvent) {
        this.hasGeohashes()
    } else {
        tags.hasGeohashes()
    }

fun Event.isTaggedGeoHashes(hashtags: Set<String>) =
    if (this is CommentEvent) {
        this.isTaggedGeoHashes(hashtags)
    } else {
        tags.isTaggedGeoHashes(hashtags)
    }

fun Event.isTaggedGeoHash(hashtag: String) =
    if (this is CommentEvent) {
        this.isTaggedGeoHash(hashtag)
    } else {
        tags.isTaggedGeoHash(hashtag)
    }

fun Event.geohashes() =
    if (this is CommentEvent) {
        geohashes()
    } else {
        tags.geohashes()
    }

fun Event.getGeoHash(): String? =
    if (this is CommentEvent) {
        getGeoHash()
    } else {
        tags.getGeoHash()
    }