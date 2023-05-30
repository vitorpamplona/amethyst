package com.vitorpamplona.amethyst.ui.components

import com.vitorpamplona.amethyst.service.previews.UrlInfoItem

sealed class UrlPreviewState {
    object Loading : UrlPreviewState()
    class Loaded(val previewInfo: UrlInfoItem) : UrlPreviewState()
    object Empty : UrlPreviewState()
    class Error(val errorMessage: String) : UrlPreviewState()
}
