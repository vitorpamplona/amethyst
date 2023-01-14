package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.NewPostView

@Composable
fun ReactionsRow(note: Note, account: Account, boost: (Note) -> Unit, reactTo: (Note) -> Unit) {
  val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)

  var wantsToReplyTo by remember {
    mutableStateOf<Note?>(null)
  }

  if (wantsToReplyTo != null)
    NewPostView({ wantsToReplyTo = null }, wantsToReplyTo, account)

  Row(modifier = Modifier.padding(top = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      IconButton(
        modifier = Modifier.then(Modifier.size(24.dp)),
        onClick = { if (account.isWriteable()) wantsToReplyTo = note }
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_comment),
          null,
          modifier = Modifier.size(15.dp),
          tint = grayTint,
        )
      }

      Text(
        "  ${showCount(note.replies?.size)}",
        fontSize = 14.sp,
        color = grayTint
      )
    }
    Row(
      modifier = Modifier.padding(start = 40.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        modifier = Modifier.then(Modifier.size(24.dp)),
        onClick = { if (account.isWriteable()) boost(note) }
      ) {
        if (note.isBoostedBy(account.userProfile())) {
          Icon(
            painter = painterResource(R.drawable.ic_retweeted),
            null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified
          )
        } else {
          Icon(
            painter = painterResource(R.drawable.ic_retweet),
            null,
            modifier = Modifier.size(20.dp),
            tint = grayTint
          )
        }
      }

      Text(
        "  ${showCount(note.boosts?.size)}",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
      )
    }
    Row(
      modifier = Modifier.padding(start = 40.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        modifier = Modifier.then(Modifier.size(24.dp)),
        onClick = { if (account.isWriteable()) reactTo(note) }
      ) {
        if (note.isReactedBy(account.userProfile())) {
          Icon(
            painter = painterResource(R.drawable.ic_liked),
            null,
            modifier = Modifier.size(16.dp),
            tint = Color.Unspecified
          )
        } else {
          Icon(
            painter = painterResource(R.drawable.ic_like),
            null,
            modifier = Modifier.size(16.dp),
            tint = grayTint
          )
        }
      }

      Text(
        "  ${showCount(note.reactions?.size)}",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
      )
    }
    Row(
      modifier = Modifier.padding(start = 40.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        modifier = Modifier.then(Modifier.size(24.dp)),
        onClick = { }
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_share),
          null,
          modifier = Modifier.size(16.dp),
          tint = grayTint
        )
      }
    }
  }
}

fun showCount(size: Int?): String {
  if (size == null) return " "
  return if (size == 0) return " " else "$size"
}