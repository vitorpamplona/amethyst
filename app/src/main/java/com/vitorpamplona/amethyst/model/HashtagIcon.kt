package com.vitorpamplona.amethyst.model
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
fun checkForHashtagWithIcon(tag: String): HashtagIcon? {
    when (tag.lowercase()) {
        "bitcoin", "btc" -> return HashtagIcon(R.drawable.ht_btc, "Bitcoin", Color.Unspecified)
        "nostr" -> return HashtagIcon(R.drawable.ht_nostr, "Nostr", Color.Unspecified)
        "lightning", "lightningnetwork" -> return HashtagIcon(R.drawable.ht_lightning, "Lightning", Color.Unspecified)
        "zap", "zaps", "zapathon", "zapraiser", "zaplife" -> return HashtagIcon(R.drawable.zap, "Zap", Color.Unspecified)
        "amethyst" -> return HashtagIcon(R.drawable.amethyst, "Amethyst", Color.Unspecified)
        else -> return null
    }
}
class HashtagIcon(
    val icon: Int,
    val description: String,
    val color: Color
)
