package com.vitorpamplona.amethyst.model

import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
fun checkForHashtagWithIcon(tag: String): HashtagIcon? {
    if (tag.lowercase() == "bitcoin" || tag.lowercase() == "btc") {
        return HashtagIcon(R.drawable.ht_btc, "Bitcoin", Color(0xFFF2A900))
    } else if (tag.lowercase() == "nostr") {
        return HashtagIcon(R.drawable.ht_nostr, "Nostr", Color(0xFF9C59FF))
    }
    return null
}
class HashtagIcon(
    val icon: Int,
    val description: String,
    val color: Color
)
