package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable

@Stable
class Settings(
    var automaticallyShowImages: Boolean? = null,
    var automaticallyStartPlayback: Boolean? = null,
    var preferredLanguage: String? = null
)
