package com.vitorpamplona.amethyst.service.previews

import androidx.compose.runtime.Immutable
import java.net.URL

@Immutable
data class UrlInfoItem(
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val image: String = ""
) {
    val verifiedUrl = kotlin.runCatching { URL(url) }.getOrNull()
    val imageUrlFullPath =
        if (image.startsWith("/")) {
            URL(verifiedUrl, image).toString()
        } else {
            image
        }

    fun allFetchComplete(): Boolean {
        return title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()
    }
}
