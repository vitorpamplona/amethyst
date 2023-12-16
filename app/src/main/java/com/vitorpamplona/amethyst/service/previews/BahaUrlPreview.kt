package com.vitorpamplona.amethyst.service.previews

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BahaUrlPreview(val url: String, var callback: IUrlPreviewCallback?) {
    suspend fun fetchUrlPreview(timeOut: Int = 30000) = withContext(Dispatchers.IO) {
        try {
            fetch(timeOut)
        } catch (t: Throwable) {
            callback?.onFailed(t)
        }
    }

    private suspend fun fetch(timeOut: Int = 30000) {
        callback?.onComplete(getDocument(url, timeOut))
    }

    fun cleanUp() {
        callback = null
    }
}
