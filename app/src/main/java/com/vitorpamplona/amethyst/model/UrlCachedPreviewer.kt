package com.vitorpamplona.amethyst.model

import android.util.LruCache
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.previews.BahaUrlPreview
import com.vitorpamplona.amethyst.service.previews.IUrlPreviewCallback
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState

object UrlCachedPreviewer {
    var cache = LruCache<String, UrlPreviewState>(100)
        private set

    fun previewInfo(url: String, onReady: (UrlPreviewState) -> Unit) {
        checkNotInMainThread()

        cache[url]?.let {
            onReady(it)
            return
        }

        BahaUrlPreview(
            url,
            object : IUrlPreviewCallback {
                override fun onComplete(urlInfo: UrlInfoItem) {
                    cache[url]?.let {
                        if (it is UrlPreviewState.Loaded || it is UrlPreviewState.Empty) {
                            onReady(it)
                            return
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

                override fun onFailed(throwable: Throwable) {
                    cache[url]?.let {
                        onReady(it)
                        return
                    }

                    val state = UrlPreviewState.Error(throwable.message ?: "Error Loading url preview")
                    cache.put(url, state)
                    onReady(state)
                }
            }
        ).fetchUrlPreview()
    }
}
