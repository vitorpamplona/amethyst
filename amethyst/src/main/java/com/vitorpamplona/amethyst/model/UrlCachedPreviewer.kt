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
package com.vitorpamplona.amethyst.model

import android.util.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.service.previews.BahaUrlPreview
import com.vitorpamplona.amethyst.service.previews.IUrlPreviewCallback
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState

@Stable
object UrlCachedPreviewer {
    var cache = LruCache<String, UrlPreviewState>(100)
        private set

    suspend fun previewInfo(
        url: String,
        onReady: suspend (UrlPreviewState) -> Unit,
    ) {
        cache[url]?.let {
            onReady(it)
            return
        }

        BahaUrlPreview(
            url,
            object : IUrlPreviewCallback {
                override suspend fun onComplete(urlInfo: UrlInfoItem) {
                    cache[url]?.let {
                        if (it is UrlPreviewState.Loaded || it is UrlPreviewState.Empty) {
                            onReady(it)
                            return
                        }
                    }

                    val state =
                        if (urlInfo.fetchComplete() && urlInfo.url == url) {
                            UrlPreviewState.Loaded(urlInfo)
                        } else {
                            UrlPreviewState.Empty
                        }

                    cache.put(url, state)
                    onReady(state)
                }

                override suspend fun onFailed(throwable: Throwable) {
                    cache[url]?.let {
                        onReady(it)
                        return
                    }

                    val state = UrlPreviewState.Error(throwable.message ?: "Error Loading url preview")
                    cache.put(url, state)
                    onReady(state)
                }
            },
        )
            .fetchUrlPreview()
    }
}
