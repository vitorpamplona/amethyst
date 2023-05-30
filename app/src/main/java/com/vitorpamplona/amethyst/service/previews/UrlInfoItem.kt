package com.vitorpamplona.amethyst.service.previews

data class UrlInfoItem(
    var url: String = "",
    var title: String = "",
    var description: String = "",
    var image: String = ""
) {
    fun allFetchComplete(): Boolean {
        return title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()
    }
}
