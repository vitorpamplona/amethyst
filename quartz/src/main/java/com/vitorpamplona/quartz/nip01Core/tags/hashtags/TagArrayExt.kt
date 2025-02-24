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
package com.vitorpamplona.quartz.nip01Core.tags.hashtags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.anyTagged
import com.vitorpamplona.quartz.nip01Core.core.firstAnyLowercaseTaggedValue
import com.vitorpamplona.quartz.nip01Core.core.forEachTagged
import com.vitorpamplona.quartz.nip01Core.core.hasTagWithContent
import com.vitorpamplona.quartz.nip01Core.core.isAnyLowercaseTagged
import com.vitorpamplona.quartz.nip01Core.core.isTagged
import com.vitorpamplona.quartz.nip01Core.core.mapValues

fun TagArray.forEachHashTag(onEach: (eventId: HexKey) -> Unit) = this.forEachTagged(HashtagTag.TAG_NAME, onEach)

fun TagArray.anyHashTag(onEach: (str: String) -> Boolean) = this.anyTagged(HashtagTag.TAG_NAME, onEach)

fun TagArray.hasHashtags() = this.hasTagWithContent(HashtagTag.TAG_NAME)

fun TagArray.hashtags() = this.mapValues(HashtagTag.TAG_NAME)

fun TagArray.countHashtags() = this.count(HashtagTag::isTagged)

fun TagArray.isTaggedHash(hashtag: String) = this.isTagged(HashtagTag.TAG_NAME, hashtag, true)

fun TagArray.isTaggedHashes(hashtags: Set<String>) = this.isAnyLowercaseTagged(HashtagTag.TAG_NAME, hashtags)

fun TagArray.firstIsTaggedHashes(hashtags: Set<String>) = this.firstAnyLowercaseTaggedValue(HashtagTag.TAG_NAME, hashtags)
