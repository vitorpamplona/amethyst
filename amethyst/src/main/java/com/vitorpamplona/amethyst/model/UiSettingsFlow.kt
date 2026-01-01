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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class UiSettingsFlow(
    val theme: MutableStateFlow<ThemeType> = MutableStateFlow(ThemeType.SYSTEM),
    val preferredLanguage: MutableStateFlow<String?> = MutableStateFlow(null),
    val automaticallyShowImages: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val automaticallyStartPlayback: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val automaticallyShowUrlPreview: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val automaticallyHideNavigationBars: MutableStateFlow<BooleanType> = MutableStateFlow(BooleanType.ALWAYS),
    val automaticallyShowProfilePictures: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val dontShowPushNotificationSelector: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val dontAskForNotificationPermissions: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val featureSet: MutableStateFlow<FeatureSetType> = MutableStateFlow(FeatureSetType.SIMPLIFIED),
    val gallerySet: MutableStateFlow<ProfileGalleryType> = MutableStateFlow(ProfileGalleryType.CLASSIC),
) {
    val listOfFlows: List<Flow<Any?>> =
        listOf<Flow<Any?>>(
            theme,
            preferredLanguage,
            automaticallyShowImages,
            automaticallyStartPlayback,
            automaticallyShowUrlPreview,
            automaticallyHideNavigationBars,
            automaticallyShowProfilePictures,
            dontShowPushNotificationSelector,
            dontAskForNotificationPermissions,
            featureSet,
            gallerySet,
        )

    // emits at every change in any of the propertyes.
    val propertyWatchFlow: Flow<UiSettings> =
        combine<Any?, UiSettings>(listOfFlows) { flows: Array<Any?> ->
            UiSettings(
                flows[0] as ThemeType,
                flows[1] as String?,
                flows[2] as ConnectivityType,
                flows[3] as ConnectivityType,
                flows[4] as ConnectivityType,
                flows[5] as BooleanType,
                flows[6] as ConnectivityType,
                flows[7] as Boolean,
                flows[8] as Boolean,
                flows[9] as FeatureSetType,
                flows[10] as ProfileGalleryType,
            )
        }

    fun toSettings(): UiSettings =
        UiSettings(
            theme.value,
            preferredLanguage.value,
            automaticallyShowImages.value,
            automaticallyStartPlayback.value,
            automaticallyShowUrlPreview.value,
            automaticallyHideNavigationBars.value,
            automaticallyShowProfilePictures.value,
            dontShowPushNotificationSelector.value,
            dontAskForNotificationPermissions.value,
            featureSet.value,
            gallerySet.value,
        )

    fun update(torSettings: UiSettings): Boolean {
        var any = false

        if (theme.value != torSettings.theme) {
            theme.tryEmit(torSettings.theme)
            any = true
        }
        if (preferredLanguage.value != torSettings.preferredLanguage) {
            preferredLanguage.tryEmit(torSettings.preferredLanguage)
            any = true
        }
        if (automaticallyShowImages.value != torSettings.automaticallyShowImages) {
            automaticallyShowImages.tryEmit(torSettings.automaticallyShowImages)
            any = true
        }
        if (automaticallyStartPlayback.value != torSettings.automaticallyStartPlayback) {
            automaticallyStartPlayback.tryEmit(torSettings.automaticallyStartPlayback)
            any = true
        }
        if (automaticallyShowUrlPreview.value != torSettings.automaticallyShowUrlPreview) {
            automaticallyShowUrlPreview.tryEmit(torSettings.automaticallyShowUrlPreview)
            any = true
        }
        if (automaticallyHideNavigationBars.value != torSettings.automaticallyHideNavigationBars) {
            automaticallyHideNavigationBars.tryEmit(torSettings.automaticallyHideNavigationBars)
            any = true
        }
        if (automaticallyShowProfilePictures.value != torSettings.automaticallyShowProfilePictures) {
            automaticallyShowProfilePictures.tryEmit(torSettings.automaticallyShowProfilePictures)
            any = true
        }
        if (dontShowPushNotificationSelector.value != torSettings.dontShowPushNotificationSelector) {
            dontShowPushNotificationSelector.tryEmit(torSettings.dontShowPushNotificationSelector)
            any = true
        }
        if (dontAskForNotificationPermissions.value != torSettings.dontAskForNotificationPermissions) {
            dontAskForNotificationPermissions.tryEmit(torSettings.dontAskForNotificationPermissions)
            any = true
        }
        if (featureSet.value != torSettings.featureSet) {
            featureSet.tryEmit(torSettings.featureSet)
            any = true
        }
        if (gallerySet.value != torSettings.gallerySet) {
            gallerySet.tryEmit(torSettings.gallerySet)
            any = true
        }

        return any
    }

    fun dontShowPushNotificationSelector() {
        if (!dontShowPushNotificationSelector.value) {
            dontShowPushNotificationSelector.tryEmit(true)
        }
    }

    fun dontAskForNotificationPermissions() {
        if (!dontAskForNotificationPermissions.value) {
            dontAskForNotificationPermissions.tryEmit(true)
        }
    }

    companion object {
        fun build(torSettings: UiSettings): UiSettingsFlow =
            UiSettingsFlow(
                MutableStateFlow(torSettings.theme),
                MutableStateFlow(torSettings.preferredLanguage),
                MutableStateFlow(torSettings.automaticallyShowImages),
                MutableStateFlow(torSettings.automaticallyStartPlayback),
                MutableStateFlow(torSettings.automaticallyShowUrlPreview),
                MutableStateFlow(torSettings.automaticallyHideNavigationBars),
                MutableStateFlow(torSettings.automaticallyShowProfilePictures),
                MutableStateFlow(torSettings.dontShowPushNotificationSelector),
                MutableStateFlow(torSettings.dontAskForNotificationPermissions),
                MutableStateFlow(torSettings.featureSet),
                MutableStateFlow(torSettings.gallerySet),
            )
    }
}
