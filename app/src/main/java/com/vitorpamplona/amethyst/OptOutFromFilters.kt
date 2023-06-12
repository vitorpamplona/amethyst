package com.vitorpamplona.amethyst

object OptOutFromFilters {
    var warnAboutPostsWithReports: Boolean = true
    var filterSpamFromStrangers: Boolean = true

    fun start(warnAboutReports: Boolean, filterSpam: Boolean) {
        warnAboutPostsWithReports = warnAboutReports
        filterSpamFromStrangers = filterSpam
    }
}
