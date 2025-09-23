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
package com.vitorpamplona.quartz.nipA0VoiceMessages

import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.DurationTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.HashSha256Tag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.MimeTypeTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.WaveformTag

fun IMetaTag.hash() = properties.get(HashSha256Tag.TAG_NAME)

fun IMetaTag.duration() = properties.get(DurationTag.TAG_NAME)

fun IMetaTag.waveform() = properties.get(WaveformTag.TAG_NAME)

fun IMetaTag.mimeType() = properties.get(MimeTypeTag.TAG_NAME)
