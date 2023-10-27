package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.R

@Stable
data class Settings(
    val theme: ThemeType = ThemeType.SYSTEM,
    val preferredLanguage: String? = null,
    val automaticallyShowImages: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyStartPlayback: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyShowUrlPreview: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyHideNavigationBars: BooleanType = BooleanType.ALWAYS,
    val automaticallyShowProfilePictures: ConnectivityType = ConnectivityType.ALWAYS,
    val dontShowPushNotificationSelector: Boolean = false,
    val dontAskForNotificationPermissions: Boolean = false
)

enum class ThemeType(val screenCode: Int, val resourceId: Int) {
    SYSTEM(0, R.string.system),
    LIGHT(1, R.string.light),
    DARK(2, R.string.dark)
}

fun parseThemeType(code: Int?): ThemeType {
    return when (code) {
        ThemeType.SYSTEM.screenCode -> ThemeType.SYSTEM
        ThemeType.LIGHT.screenCode -> ThemeType.LIGHT
        ThemeType.DARK.screenCode -> ThemeType.DARK
        else -> {
            ThemeType.SYSTEM
        }
    }
}

enum class ConnectivityType(val prefCode: Boolean?, val screenCode: Int, val resourceId: Int) {
    ALWAYS(null, 0, R.string.connectivity_type_always),
    WIFI_ONLY(true, 1, R.string.connectivity_type_wifi_only),
    NEVER(false, 2, R.string.connectivity_type_never)
}

fun parseConnectivityType(code: Boolean?): ConnectivityType {
    return when (code) {
        ConnectivityType.ALWAYS.prefCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.prefCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.prefCode -> ConnectivityType.NEVER
        else -> {
            ConnectivityType.ALWAYS
        }
    }
}

fun parseConnectivityType(screenCode: Int): ConnectivityType {
    return when (screenCode) {
        ConnectivityType.ALWAYS.screenCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.screenCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.screenCode -> ConnectivityType.NEVER
        else -> {
            ConnectivityType.ALWAYS
        }
    }
}

enum class BooleanType(val prefCode: Boolean?, val screenCode: Int, val reourceId: Int) {
    ALWAYS(null, 0, R.string.connectivity_type_always),
    NEVER(false, 1, R.string.connectivity_type_never)
}

fun parseBooleanType(code: Boolean?): BooleanType {
    return when (code) {
        BooleanType.ALWAYS.prefCode -> BooleanType.ALWAYS
        BooleanType.NEVER.prefCode -> BooleanType.NEVER
        else -> {
            BooleanType.ALWAYS
        }
    }
}

fun parseBooleanType(screenCode: Int): BooleanType {
    return when (screenCode) {
        BooleanType.ALWAYS.screenCode -> BooleanType.ALWAYS
        BooleanType.NEVER.screenCode -> BooleanType.NEVER
        else -> {
            BooleanType.ALWAYS
        }
    }
}
