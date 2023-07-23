package com.vitorpamplona.amethyst.service.connectivitystatus

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

object ConnectivityStatus {
    private val onMobileData = mutableStateOf(false)
    val isOnMobileData: MutableState<Boolean> = onMobileData

    fun updateConnectivityStatus(isOnMobileData: Boolean, isOnWifi: Boolean) {
        if (onMobileData.value != isOnMobileData) {
            onMobileData.value = isOnMobileData
        }
    }
}
