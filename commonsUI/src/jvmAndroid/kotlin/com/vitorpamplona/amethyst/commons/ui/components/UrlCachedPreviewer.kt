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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.collection.LruCache
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.preview.UrlPreview
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Stable
object UrlCachedPreviewer {
    var cache = LruCache<String, UrlPreviewState>(100)
        private set

    suspend fun previewInfo(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
        onReady: suspend (UrlPreviewState) -> Unit,
    ) {
        cache[url]?.let {
            onReady(it)
            return
        }

        UrlPreview().fetch(
            url,
            okHttpClient,
            onComplete = { urlInfo ->
                cache[url]?.let {
                    if (it is UrlPreviewState.Loaded || it is UrlPreviewState.Empty) {
                        onReady(it)
                        return@fetch
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
            },
            onFailed = { throwable ->
                cache[url]?.let {
                    onReady(it)
                    return@fetch
                }

                val state = UrlPreviewState.Error(throwable.message ?: "Error Loading url preview")
                cache.put(url, state)
                onReady(state)
            },
        )
    }
}

/** Loads the OpenGraph preview of [url] through [UrlCachedPreviewer], on the account's preview HTTP client. */
fun AccountViewModel.urlPreview(
    url: String,
    onResult: suspend (UrlPreviewState) -> Unit,
) {
    viewModelScope.launch(Dispatchers.IO) {
        UrlCachedPreviewer.previewInfo(url, httpClientBuilder::okHttpClientForPreview, onResult)
    }
}
