package com.vitorpamplona.amethyst.model

import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
fun checkForHashtagWithIcon(tag: String): HashtagIcon? {
    if (tag.lowercase() == "bitcoin" || tag.lowercase() == "btc") {
        return HashtagIcon(R.drawable.ht_btc, "Bitcoin", Color.Unspecified)
    } else if (tag.lowercase() == "nostr") {
        return HashtagIcon(R.drawable.ht_nostr, "Nostr", Color.Unspecified)
    } else if (tag.lowercase() == "zap" || tag.lowercase() == "zap" || tag.lowercase() == "zapathon" || tag.lowercase() == "zapraiser" || tag.lowercase() == "zaplife" || tag.lowercase() == "lightning") {
        return HashtagIcon(R.drawable.lightning, "Zap", Color.Unspecified)
    } else if (tag.lowercase() == "amethyst") {
        return HashtagIcon(R.drawable.amethyst, "Amethyst", Color.Unspecified)
    }
    return null
}
class HashtagIcon(
    val icon: Int,
    val description: String,
    val color: Color
)
