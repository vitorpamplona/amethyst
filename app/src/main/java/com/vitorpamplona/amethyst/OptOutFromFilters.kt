package com.vitorpamplona.amethyst

object OptOutFromFilters {
    var filterSpamFromStrangers: Boolean = true

    fun start(filterSpam: Boolean) {
        filterSpamFromStrangers = filterSpam
    }
}
