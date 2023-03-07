package com.vitorpamplona.amethyst.model

import com.baha.url.preview.BahaUrlPreview
import com.baha.url.preview.IUrlPreviewCallback
import com.baha.url.preview.UrlInfoItem
import com.vitorpamplona.amethyst.ui.components.imageExtension
import com.vitorpamplona.amethyst.ui.components.isValidURL
import com.vitorpamplona.amethyst.ui.components.noProtocolUrlValidator
import com.vitorpamplona.amethyst.ui.components.videoExtension
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

    fun findUrlsInMessage(message: String): List<String> {
        return message.split('\n').map { paragraph ->
            paragraph.split(' ').filter { word: String ->
                isValidURL(word) || noProtocolUrlValidator.matcher(word).matches()
            }
        }.flatten()
    }

    fun preloadPreviewsFor(note: Note) {
        note.event?.content()?.let {
            findUrlsInMessage(it).forEach {
                val removedParamsFromUrl = it.split("?")[0].lowercase()
                if (imageExtension.matcher(removedParamsFromUrl).matches()) {
                    // Preload Images? Isn't this too heavy?
                } else if (videoExtension.matcher(removedParamsFromUrl).matches()) {
                    // Do nothing for now.
                } else if (isValidURL(removedParamsFromUrl)) {
                    previewInfo(it)
                } else {
                    previewInfo("https://$it")
                }
            }
        }
    }
}
