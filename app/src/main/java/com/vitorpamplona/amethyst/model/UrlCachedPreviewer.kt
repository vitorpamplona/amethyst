package com.vitorpamplona.amethyst.model

import com.baha.url.preview.BahaUrlPreview
import com.baha.url.preview.IUrlPreviewCallback
import com.baha.url.preview.UrlInfoItem
import com.vitorpamplona.amethyst.ui.components.imageExtension
import com.vitorpamplona.amethyst.ui.components.isValidURL
import com.vitorpamplona.amethyst.ui.components.noProtocolUrlValidator
import com.vitorpamplona.amethyst.ui.components.videoExtension
import java.util.concurrent.ConcurrentHashMap

object UrlCachedPreviewer {
  val cache = ConcurrentHashMap<String, UrlInfoItem>()

  fun previewInfo(url: String, callback: IUrlPreviewCallback? = null) {
    cache[url]?.let {
      callback?.onComplete(it)
      return
    }

    BahaUrlPreview(url, object : IUrlPreviewCallback {
      override fun onComplete(urlInfo: UrlInfoItem) {
        cache.put(url, urlInfo)
        callback?.onComplete(urlInfo)
      }

      override fun onFailed(throwable: Throwable) {
        callback?.onFailed(throwable)
      }
    }).fetchUrlPreview()
  }

  fun findUrlsInMessage(message: String): List<String> {
    return message.split('\n').map { paragraph ->
      paragraph.split(' ').filter { word: String ->
        isValidURL(word) || noProtocolUrlValidator.matcher(word).matches()
      }
    }.flatten()
  }

  fun preloadPreviewsFor(note: Note) {
    note.event?.content?.let {
      findUrlsInMessage(it).forEach {
        val removedParamsFromUrl = it.split("?")[0].toLowerCase()
        if (imageExtension.matcher(removedParamsFromUrl).matches()) {
          // Preload Images? Isn't this too heavy?
        } else if (videoExtension.matcher(removedParamsFromUrl).matches()) {
          // Do nothing for now.
        } else {
          previewInfo(it)
        }
      }
    }
  }
}