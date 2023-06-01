package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem

@Immutable
sealed class UrlPreviewState {
    object Loading : UrlPreviewState()
    class Loaded(val previewInfo: UrlInfoItem) : UrlPreviewState()
    object Empty : UrlPreviewState()
    class Error(val errorMessage: String) : UrlPreviewState()
}
