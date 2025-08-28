/**
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
package com.vitorpamplona.quartz.nip01Core.tags.kinds

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.core.anyTagged
import com.vitorpamplona.quartz.nip01Core.core.forEachTagged

fun TagArray.forEachKind(onEach: (eventId: HexKey) -> Unit) = this.forEachTagged(KindTag.TAG_NAME, onEach)

fun TagArray.anyKind(onEach: (str: String) -> Boolean) = this.anyTagged(KindTag.TAG_NAME, onEach)

fun TagArray.hasKind() = this.any(KindTag::match)

fun TagArray.kinds() = this.mapNotNull(KindTag::parse)

fun TagArray.isTaggedKind(kind: Int): Boolean = this.any(KindTag::isTagged, kind.toString())

fun TagArray.isTaggedKinds(kinds: Set<Int>) = this.any(KindTag::isIn, kinds.mapTo(HashSet()) { it.toString() })
