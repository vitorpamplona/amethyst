package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem

@Immutable
sealed class UrlPreviewState {

    @Immutable
    object Loading : UrlPreviewState()

    @Immutable
    class Loaded(val previewInfo: UrlInfoItem) : UrlPreviewState()

    @Immutable
    object Empty : UrlPreviewState()

    @Immutable
    class Error(val errorMessage: String) : UrlPreviewState()
}
