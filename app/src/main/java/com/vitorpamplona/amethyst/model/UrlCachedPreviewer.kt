package com.vitorpamplona.amethyst.model

import com.baha.url.preview.BahaUrlPreview
import com.baha.url.preview.IUrlPreviewCallback
import com.baha.url.preview.UrlInfoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object UrlCachedPreviewer {
    var cache = mapOf<String, UrlInfoItem>()
        private set
    var failures = mapOf<String, Throwable>()
        private set

    fun previewInfo(url: String, callback: IUrlPreviewCallback? = null) {
        cache[url]?.let {
            callback?.onComplete(it)
            return
        }

        failures[url]?.let {
            callback?.onFailed(it)
            return
        }

        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            BahaUrlPreview(
                url,
                object : IUrlPreviewCallback {
                    override fun onComplete(urlInfo: UrlInfoItem) {
                        cache = cache + Pair(url, urlInfo)
                        callback?.onComplete(urlInfo)
                    }

                    override fun onFailed(throwable: Throwable) {
                        failures = failures + Pair(url, throwable)
                        callback?.onFailed(throwable)
                    }
                }
            ).fetchUrlPreview()
        }
    }
}
