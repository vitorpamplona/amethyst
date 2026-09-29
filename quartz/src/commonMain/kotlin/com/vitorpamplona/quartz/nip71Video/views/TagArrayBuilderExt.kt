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
package com.vitorpamplona.quartz.nip71Video.views

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.aTag.toATag
import com.vitorpamplona.quartz.nip01Core.tags.events.toETagArray
import com.vitorpamplona.quartz.nip71Video.AddressableVideoEvent
import com.vitorpamplona.quartz.nip71Video.views.tags.LoopsTag
import com.vitorpamplona.quartz.nip71Video.views.tags.PhaseTag
import com.vitorpamplona.quartz.nip71Video.views.tags.SourceTag
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewPhase
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewSource
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewedRange
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewedTag

/** Both pointers: the address for the video, the id for the exact version that was watched. */
fun <T : AddressableVideoEvent> TagArrayBuilder<VideoViewEvent>.video(video: EventHintBundle<T>) = addUnique(video.toATag().toATagArray()).addUnique(video.toETagArray())

fun TagArrayBuilder<VideoViewEvent>.phase(phase: ViewPhase) = addUnique(PhaseTag.assemble(phase))

fun TagArrayBuilder<VideoViewEvent>.viewed(range: ViewedRange) = addUnique(ViewedTag.assemble(range))

fun TagArrayBuilder<VideoViewEvent>.loops(loops: Double) = addUnique(LoopsTag.assemble(loops))

fun TagArrayBuilder<VideoViewEvent>.source(source: ViewSource) = addUnique(SourceTag.assemble(source))
