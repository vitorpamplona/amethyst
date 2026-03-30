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
package com.vitorpamplona.quartz.nip90Dvms.tags

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun <T : Event> TagArrayBuilder<T>.inputUrl(url: String) = add(InputTag.assembleUrl(url))

fun <T : Event> TagArrayBuilder<T>.inputText(text: String) = add(InputTag.assembleText(text))

fun <T : Event> TagArrayBuilder<T>.inputEvent(eventId: HexKey) = add(InputTag.assembleEvent(eventId))

fun <T : Event> TagArrayBuilder<T>.inputJob(jobId: HexKey) = add(InputTag.assembleJob(jobId))

fun <T : Event> TagArrayBuilder<T>.inputPrompt(prompt: String) = add(InputTag.assemblePrompt(prompt))

fun <T : Event> TagArrayBuilder<T>.output(mimeType: String) = addUnique(OutputTag.assemble(mimeType))

fun <T : Event> TagArrayBuilder<T>.param(
    key: String,
    value: String,
) = add(arrayOf("param", key, value))

fun <T : Event> TagArrayBuilder<T>.param(
    key: String,
    vararg values: String,
) = add(arrayOf("param", key) + values)
