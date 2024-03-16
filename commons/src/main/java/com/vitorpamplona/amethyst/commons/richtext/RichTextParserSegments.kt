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
package com.vitorpamplona.amethyst.commons.richtext

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

@Immutable
data class RichTextViewerState(
    val urlSet: ImmutableSet<String>,
    val imagesForPager: ImmutableMap<String, MediaUrlContent>,
    val imageList: ImmutableList<MediaUrlContent>,
    val customEmoji: ImmutableMap<String, String>,
    val paragraphs: ImmutableList<ParagraphState>,
)

@Immutable
data class ParagraphState(val words: ImmutableList<Segment>, val isRTL: Boolean)

@Immutable
open class Segment(val segmentText: String)

@Immutable
class ImageSegment(segment: String) : Segment(segment)

@Immutable
class LinkSegment(segment: String) : Segment(segment)

@Immutable
class EmojiSegment(segment: String) : Segment(segment)

@Immutable
class InvoiceSegment(segment: String) : Segment(segment)

@Immutable
class WithdrawSegment(segment: String) : Segment(segment)

@Immutable
class CashuSegment(segment: String) : Segment(segment)

@Immutable
class EmailSegment(segment: String) : Segment(segment)

@Immutable
class PhoneSegment(segment: String) : Segment(segment)

@Immutable
class BechSegment(segment: String) : Segment(segment)

@Immutable
open class HashIndexSegment(segment: String, val hex: String, val extras: String?) :
    Segment(segment)

@Immutable
class HashIndexUserSegment(segment: String, hex: String, extras: String?) :
    HashIndexSegment(segment, hex, extras)

@Immutable
class HashIndexEventSegment(segment: String, hex: String, extras: String?) :
    HashIndexSegment(segment, hex, extras)

@Immutable
class HashTagSegment(segment: String, val hashtag: String, val extras: String?) : Segment(segment)

@Immutable
class SchemelessUrlSegment(segment: String, val url: String, val extras: String?) :
    Segment(segment)

@Immutable
class RegularTextSegment(segment: String) : Segment(segment)
