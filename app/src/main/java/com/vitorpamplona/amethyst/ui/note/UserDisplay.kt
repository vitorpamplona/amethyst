package com.vitorpamplona.amethyst.ui.note

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.model.User

@Composable
fun UserDisplay(user: User) {
    if (user.bestUsername() != null || user.bestDisplayName() != null) {
      Text(
        user.bestDisplayName() ?: "",
        fontWeight = FontWeight.Bold,
      )
      Text(
        "@${(user.bestUsername() ?: "")}",
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
      )
    } else {
      Text(
        user.pubkeyDisplayHex,
        fontWeight = FontWeight.Bold,
      )
    }
}