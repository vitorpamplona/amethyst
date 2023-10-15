package com.vitorpamplona.amethyst.service.previews

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class BahaUrlPreview(val url: String, var callback: IUrlPreviewCallback?) {
    private val imageExtensionArray = arrayOf(".gif", ".png", ".jpg", ".jpeg", ".bmp", ".webp")

    suspend fun fetchUrlPreview(timeOut: Int = 30000) = withContext(Dispatchers.IO) {
        try {
            fetch(timeOut)
        } catch (t: Throwable) {
            callback?.onFailed(t)
        }
    }

    private suspend fun fetch(timeOut: Int = 30000) {
        lateinit var urlInfoItem: UrlInfoItem
        if (checkIsImageUrl()) {
            urlInfoItem = UrlInfoItem(url = url, image = url)
        } else {
            val document = getDocument(url, timeOut)
            urlInfoItem = parseHtml(url, document)
        }
        callback?.onComplete(urlInfoItem)
    }

    private fun checkIsImageUrl(): Boolean {
        val uri = Uri.parse(url)
        var isImage = false
        for (imageExtension in imageExtensionArray) {
            if (uri.path != null && uri.path!!.toLowerCase(Locale.getDefault()).endsWith(imageExtension)) {
                isImage = true
                break
            }
        }
        return isImage
    }

    fun cleanUp() {
        callback = null
    }
}
