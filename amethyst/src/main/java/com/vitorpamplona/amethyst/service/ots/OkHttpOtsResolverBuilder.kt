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
package com.vitorpamplona.amethyst.service.ots

import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolverBuilder

class OkHttpOtsResolverBuilder(
    val okHttpClients: DualHttpClientManager,
    val shouldUseTorForUrl: (String) -> Boolean,
    val cache: OtsBlockHeightCache,
) : OtsResolverBuilder {
    fun getAPI(usingTor: Boolean) =
        if (usingTor) {
            OkHttpBitcoinExplorer.MEMPOOL_API_URL
        } else {
            OkHttpBitcoinExplorer.BLOCKSTREAM_API_URL
        }

    override fun build(): OtsResolver {
        val shouldUseTor = shouldUseTorForUrl(OkHttpBitcoinExplorer.MEMPOOL_API_URL)

        return OtsResolver(
            explorer =
                OkHttpBitcoinExplorer(
                    baseAPI = getAPI(usingTor = shouldUseTor),
                    client = okHttpClients.getHttpClient(shouldUseTor),
                    cache = cache,
                ),
            calendarBuilder =
                OkHttpCalendarBuilder {
                    okHttpClients.getHttpClient(shouldUseTorForUrl(it))
                },
        )
    }
}
