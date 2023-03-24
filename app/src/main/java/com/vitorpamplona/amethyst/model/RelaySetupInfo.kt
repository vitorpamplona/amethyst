package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.service.relays.FeedType

data class RelaySetupInfo(
    val url: String,
    val read: Boolean,
    val write: Boolean,
    val errorCount: Int = 0,
    val downloadCountInBytes: Int = 0,
    val uploadCountInBytes: Int = 0,
    val spamCount: Int = 0,
    val feedTypes: Set<FeedType>
)
