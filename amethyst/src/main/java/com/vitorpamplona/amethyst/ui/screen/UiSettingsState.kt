/**
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
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.ProfileGalleryType
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Stable
class UiSettingsState(
    val uiSettingsFlow: UiSettingsFlow,
    val isMobileOrMeteredConnection: StateFlow<Boolean>,
    val scope: CoroutineScope,
) {
    val showProfilePictures =
        combine(
            uiSettingsFlow.automaticallyShowProfilePictures,
            isMobileOrMeteredConnection,
        ) { showProfilePictures, isOnMobileOrMeteredConnection ->
            when (showProfilePictures) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            when (uiSettingsFlow.automaticallyShowProfilePictures.value) {
                ConnectivityType.WIFI_ONLY -> !isMobileOrMeteredConnection.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            },
        )

    val showUrlPreview =
        combine(
            uiSettingsFlow.automaticallyShowUrlPreview,
            isMobileOrMeteredConnection,
        ) { automaticallyShowUrlPreview, isOnMobileOrMeteredConnection ->
            when (automaticallyShowUrlPreview) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            when (uiSettingsFlow.automaticallyShowUrlPreview.value) {
                ConnectivityType.WIFI_ONLY -> !isMobileOrMeteredConnection.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            },
        )

    val startVideoPlayback =
        combine(
            uiSettingsFlow.automaticallyStartPlayback,
            isMobileOrMeteredConnection,
        ) { automaticallyStartPlayback, isOnMobileOrMeteredConnection ->
            when (automaticallyStartPlayback) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            when (uiSettingsFlow.automaticallyStartPlayback.value) {
                ConnectivityType.WIFI_ONLY -> !isMobileOrMeteredConnection.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            },
        )

    val showImages =
        combine(
            uiSettingsFlow.automaticallyShowImages,
            isMobileOrMeteredConnection,
        ) { automaticallyShowImages, isOnMobileOrMeteredConnection ->
            when (automaticallyShowImages) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            when (uiSettingsFlow.automaticallyShowImages.value) {
                ConnectivityType.WIFI_ONLY -> !isMobileOrMeteredConnection.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            },
        )

    fun modernGalleryStyle() =
        when (uiSettingsFlow.gallerySet.value) {
            ProfileGalleryType.CLASSIC -> false
            ProfileGalleryType.MODERN -> true
        }

    fun isPerformanceMode() = uiSettingsFlow.featureSet.value == FeatureSetType.PERFORMANCE

    fun isNotPerformanceMode() = uiSettingsFlow.featureSet.value != FeatureSetType.PERFORMANCE

    fun isCompleteUIMode() = uiSettingsFlow.featureSet.value == FeatureSetType.COMPLETE

    fun isImmersiveScrollingActive() = uiSettingsFlow.automaticallyHideNavigationBars.value == BooleanType.ALWAYS
}
