package com.vitorpamplona.amethyst.service.connectivitystatus

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

object ConnectivityStatus {
    private val onMobileData = mutableStateOf(false)
    val isOnMobileData: MutableState<Boolean> = onMobileData

    private val onWifi = mutableStateOf(false)
    val isOnWifi: MutableState<Boolean> = onWifi

    fun updateConnectivityStatus(isOnMobileData: Boolean, isOnWifi: Boolean) {
        if (onMobileData.value != isOnMobileData) {
            onMobileData.value = isOnMobileData
        }

        if (onWifi.value != isOnWifi) {
            onWifi.value = isOnWifi
        }
    }
}
