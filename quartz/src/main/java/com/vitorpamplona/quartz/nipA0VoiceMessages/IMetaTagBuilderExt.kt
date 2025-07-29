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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.DurationTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.HashSha256Tag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.WaveformTag

/**
 * Contains the IMeta tags that are used by Picture events.
 */
fun IMetaTagBuilder.hash(hash: HexKey) = add(HashSha256Tag.TAG_NAME, hash)

fun IMetaTagBuilder.duration(size: Int) = add(DurationTag.TAG_NAME, size.toString())

fun IMetaTagBuilder.waveform(wave: List<Int>) = add(WaveformTag.TAG_NAME, WaveformTag.assembleWave(wave))
