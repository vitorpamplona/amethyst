package com.vitorpamplona.amethyst

object OptOutFromFilters {
    var optOutFromFilters: Boolean = false

    fun start(optOut: Boolean) {
        optOutFromFilters = optOut
    }
}
