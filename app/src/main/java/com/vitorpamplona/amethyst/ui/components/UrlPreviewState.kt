package com.vitorpamplona.amethyst.ui.components

import com.baha.url.preview.UrlInfoItem

sealed class UrlPreviewState {
    object Loading: UrlPreviewState()
    class Loaded(val previewInfo: UrlInfoItem): UrlPreviewState()
    object Empty: UrlPreviewState()
    class Error(val errorMessage: String): UrlPreviewState()
}
