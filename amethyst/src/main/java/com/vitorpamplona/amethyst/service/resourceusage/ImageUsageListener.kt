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
package com.vitorpamplona.amethyst.service.resourceusage

import coil3.EventListener
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
import coil3.request.Options

/**
 * Counts image decodes and fetches, split by visibility.
 *
 * Decoding is the CPU-bearing half of an image: `net.image.*` already records
 * what the bytes cost on the wire, but turning a JPEG into a bitmap is
 * arithmetic, and on a feed it happens hundreds of times. It lands in the
 * `cpu.dispatch.*` bucket (Coil decodes on `Dispatchers.IO`, which shares the
 * coroutine scheduler), so this is what tells that bucket's share apart from
 * everything else running there.
 *
 * The background half is a tripwire rather than a metric: an image decoded while
 * the app is closed is work whose result nobody can see. A steady
 * `coil.decodes.bg.count` means a prefetch or a preload is running on the
 * firehose's schedule instead of the user's.
 *
 * Attached as a Coil `EventListener`, whose callbacks fire on Coil's own
 * dispatchers; each does one counter increment.
 */
class ImageUsageListener(
    private val accountant: ResourceUsageAccountant,
    private val isForeground: () -> Boolean,
) : EventListener() {
    private fun visibility() = if (isForeground()) UsageKeys.FG else UsageKeys.BG

    override fun decodeEnd(
        request: ImageRequest,
        decoder: Decoder,
        options: Options,
        result: DecodeResult?,
    ) {
        accountant.add(UsageKeys.coilDecodes(visibility()), 1)
    }

    override fun fetchEnd(
        request: ImageRequest,
        fetcher: Fetcher,
        options: Options,
        result: FetchResult?,
    ) {
        accountant.add(UsageKeys.coilFetches(visibility()), 1)
    }
}
