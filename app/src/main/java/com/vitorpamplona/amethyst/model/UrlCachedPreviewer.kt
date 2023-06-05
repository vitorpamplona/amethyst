package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.previews.BahaUrlPreview
import com.vitorpamplona.amethyst.service.previews.IUrlPreviewCallback
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem

object UrlCachedPreviewer {
    var cache = mapOf<String, UrlInfoItem>()
        private set
    var failures = mapOf<String, Throwable>()
        private set

    fun previewInfo(url: String, callback: IUrlPreviewCallback? = null) {
        checkNotInMainThread()

        cache[url]?.let {
            callback?.onComplete(it)
            return
        }

        failures[url]?.let {
            callback?.onFailed(it)
            return
        }

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
