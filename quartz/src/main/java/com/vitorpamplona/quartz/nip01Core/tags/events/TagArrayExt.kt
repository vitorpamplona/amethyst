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
package com.vitorpamplona.quartz.nip01Core.tags.events

import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.firstMapTagged
import com.vitorpamplona.quartz.nip01Core.core.forEachTagged
import com.vitorpamplona.quartz.nip01Core.core.isTagged
import com.vitorpamplona.quartz.nip01Core.core.mapTagged
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged
import com.vitorpamplona.quartz.nip01Core.core.mapValues
import com.vitorpamplona.quartz.nip19Bech32.parse

fun TagArray.forEachTaggedEventId(onEach: (eventId: HexKey) -> Unit) = this.forEachTagged(ETag.TAG_NAME, onEach)

fun <R> TagArray.mapTaggedEventId(map: (eventId: HexKey) -> R) = this.mapValueTagged(ETag.TAG_NAME, map)

fun TagArray.taggedEvents() = this.mapTagged(ETag.TAG_NAME) { ETag.parse(it) }

fun TagArray.taggedEventIds() = this.mapValues(ETag.TAG_NAME)

fun TagArray.firstTaggedEvent() = this.firstMapTagged(ETag.TAG_NAME) { ETag.parse(it) }

fun TagArray.isTaggedEvent(idHex: String) = this.isTagged(ETag.TAG_NAME, idHex)
