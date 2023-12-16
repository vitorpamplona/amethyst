package com.vitorpamplona.amethyst.service.previews

import androidx.compose.runtime.Immutable
import okhttp3.MediaType
import java.net.URL

@Immutable
class UrlInfoItem(
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val image: String = "",
    val mimeType: MediaType
) {
    val verifiedUrl = kotlin.runCatching { URL(url) }.getOrNull()
    val imageUrlFullPath =
        if (image.startsWith("/")) {
            URL(verifiedUrl, image).toString()
        } else {
            image
        }

    fun fetchComplete(): Boolean {
        return url.isNotEmpty() && image.isNotEmpty()
    }

    fun allFetchComplete(): Boolean {
        return title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()
    }
}
