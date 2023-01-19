package com.vitorpamplona.amethyst.ui.note

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.model.User

@Composable
fun UsernameDisplay(user: User, weight: Modifier = Modifier) {
    if (user.bestUsername() != null || user.bestDisplayName() != null) {
      if (user.bestDisplayName().isNullOrBlank()) {
        Text(
          "@${(user.bestUsername() ?: "")}",
          fontWeight = FontWeight.Bold,
        )
      } else {
        Text(
          user.bestDisplayName() ?: "",
          fontWeight = FontWeight.Bold,
        )
        Text(
          "@${(user.bestUsername() ?: "")}",
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = weight
        )
      }
    } else {
      Text(
        user.pubkeyDisplayHex,
        fontWeight = FontWeight.Bold,
      )
    }
}