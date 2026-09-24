/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.FeatureSetType

/**
 * The display choices composables read on almost every card, resolved once near the composition
 * root and handed down through [LocalDisplaySettings], so shared composables do not need the
 * account's ViewModel to follow them.
 *
 * The defaults are what a front end without these settings (previews, desktop today) gets:
 * everything on.
 */
@Immutable
data class DisplaySettings(
    /** Load profile pictures (off on metered connections when the user asked for WIFI_ONLY). */
    val showProfilePictures: Boolean = true,
    /** Performance mode: skip robohashes, crossfades and other decoration. */
    val performanceMode: Boolean = false,
    /** Play videos and animated images (GIF/AVIF avatars) without a tap. */
    val autoPlayVideos: Boolean = true,
    val showUrlPreview: Boolean = true,
    val showImages: Boolean = true,
    val startVideoPlayback: Boolean = true,
) {
    /** Robohash fallbacks are skipped in performance mode (a plain face icon instead). */
    val loadRobohash: Boolean get() = !performanceMode
}

val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }

/** Collects [settings] into a [DisplaySettings] that follows every change to it. */
@Composable
fun collectDisplaySettings(settings: UiSettingsState): DisplaySettings {
    val showProfilePictures by settings.showProfilePictures.collectAsStateWithLifecycle()
    val featureSet by settings.uiSettingsFlow.featureSet.collectAsStateWithLifecycle()
    val autoPlayVideos by settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val showUrlPreview by settings.showUrlPreview.collectAsStateWithLifecycle()
    val showImages by settings.showImages.collectAsStateWithLifecycle()
    val startVideoPlayback by settings.startVideoPlayback.collectAsStateWithLifecycle()

    return remember(showProfilePictures, featureSet, autoPlayVideos, showUrlPreview, showImages, startVideoPlayback) {
        DisplaySettings(
            showProfilePictures = showProfilePictures,
            performanceMode = featureSet == FeatureSetType.PERFORMANCE,
            autoPlayVideos = autoPlayVideos,
            showUrlPreview = showUrlPreview,
            showImages = showImages,
            startVideoPlayback = startVideoPlayback,
        )
    }
}
