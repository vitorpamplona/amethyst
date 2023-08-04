package com.vitorpamplona.amethyst.model

import android.util.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.service.previews.BahaUrlPreview
import com.vitorpamplona.amethyst.service.previews.IUrlPreviewCallback
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
object UrlCachedPreviewer {
    var cache = LruCache<String, UrlPreviewState>(100)
        private set

    suspend fun previewInfo(
        url: String,
        onReady: suspend (UrlPreviewState) -> Unit
    ) = withContext(Dispatchers.IO) {
        cache[url]?.let {
            onReady(it)
            return@withContext
        }

        BahaUrlPreview(
            url,
            object : IUrlPreviewCallback {
                override suspend fun onComplete(urlInfo: UrlInfoItem) = withContext(Dispatchers.IO) {
                    cache[url]?.let {
                        if (it is UrlPreviewState.Loaded || it is UrlPreviewState.Empty) {
                            onReady(it)
                            return@withContext
                        }
                    }

                    val state = if (urlInfo.allFetchComplete() && urlInfo.url == url) {
                        UrlPreviewState.Loaded(urlInfo)
                    } else {
                        UrlPreviewState.Empty
                    }

                    cache.put(url, state)
                    onReady(state)
                }

                override suspend fun onFailed(throwable: Throwable) = withContext(Dispatchers.IO) {
                    cache[url]?.let {
                        onReady(it)
                        return@withContext
                    }

                    val state = UrlPreviewState.Error(throwable.message ?: "Error Loading url preview")
                    cache.put(url, state)
                    onReady(state)
                }
            }
        ).fetchUrlPreview()
    }
}
