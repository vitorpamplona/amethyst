package com.vitorpamplona.amethyst.service.previews

interface IUrlPreviewCallback {
    fun onComplete(urlInfo: UrlInfoItem)
    fun onFailed(throwable: Throwable)
}
