package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Immutable

@Immutable
data class TranslationConfig(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
    val showOriginal: Boolean
)
