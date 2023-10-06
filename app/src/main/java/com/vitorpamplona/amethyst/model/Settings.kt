package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.R

@Stable
class Settings(
    var preferredLanguage: String? = null,
    var automaticallyShowImages: ConnectivityType = ConnectivityType.WIFI_ONLY,
    var automaticallyStartPlayback: ConnectivityType = ConnectivityType.WIFI_ONLY,
    var automaticallyShowUrlPreview: ConnectivityType = ConnectivityType.WIFI_ONLY,
    var automaticallyHideNavigationBars: BooleanType = BooleanType.ALWAYS,
    var automaticallyShowProfilePictures: ConnectivityType = ConnectivityType.WIFI_ONLY
)

enum class ConnectivityType(val prefCode: Boolean?, val screenCode: Int, val reourceId: Int) {
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
