package com.vitorpamplona.amethyst.model
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
fun checkForHashtagWithIcon(tag: String): HashtagIcon? {
    return when (tag.lowercase()) {
        "bitcoin", "btc" -> HashtagIcon(R.drawable.ht_btc, "Bitcoin", Color.Unspecified, Modifier.padding(2.dp, 2.dp, 0.dp, 0.dp))
        "nostr" -> HashtagIcon(R.drawable.ht_nostr, "Nostr", Color.Unspecified, Modifier.padding(1.dp, 2.dp, 0.dp, 0.dp))
        "lightning", "lightningnetwork" -> HashtagIcon(R.drawable.ht_lightning, "Lightning", Color.Unspecified, Modifier.padding(1.dp, 2.dp, 0.dp, 0.dp))
        "zap", "zaps", "zapathon", "zapraiser", "zaplife", "zapper", "zappers" -> HashtagIcon(R.drawable.zap, "Zap", Color.Unspecified, Modifier.padding(1.dp, 2.dp, 0.dp, 0.dp))
        "amethyst" -> HashtagIcon(R.drawable.amethyst, "Amethyst", Color.Unspecified, Modifier.padding(4.dp, 2.dp, 0.dp, 0.dp))
        else -> null
    }
}
class HashtagIcon(
    val icon: Int,
    val description: String,
    val color: Color,
    val modifier: Modifier
)
