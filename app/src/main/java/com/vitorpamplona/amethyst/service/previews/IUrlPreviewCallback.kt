package com.vitorpamplona.amethyst.service.previews

interface IUrlPreviewCallback {
    suspend fun onComplete(urlInfo: UrlInfoItem)
    suspend fun onFailed(throwable: Throwable)
}
