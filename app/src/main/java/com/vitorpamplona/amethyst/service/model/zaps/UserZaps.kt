package com.vitorpamplona.amethyst.service.model.zaps

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.LnZapEventInterface
import com.vitorpamplona.amethyst.ui.screen.ZapReqResponse

object UserZaps {
    fun forProfileFeed(zaps: Map<Note, Note?>?): List<ZapReqResponse> {
        if (zaps == null) return emptyList()

        return (
            zaps
                .mapNotNull { entry ->
                    entry.value?.let {
                        ZapReqResponse(entry.key, it)
                    }
                }
                .sortedBy { (it.zapEvent.event as? LnZapEventInterface)?.amount() }
                .reversed()
            )
    }
}
