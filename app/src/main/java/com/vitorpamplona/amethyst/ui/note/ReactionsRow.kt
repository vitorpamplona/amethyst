package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ReactionsRow(note: Note, account: Account, accountViewModel: AccountViewModel) {
  val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)

  var popupExpanded by remember { mutableStateOf(false) }

  var wantsToReplyTo by remember {
    mutableStateOf<Note?>(null)
  }

  if (wantsToReplyTo != null)
    NewPostView({ wantsToReplyTo = null }, wantsToReplyTo, account)

  Row(
    modifier = Modifier
      .padding(top = 8.dp)
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
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
      color = grayTint,
      modifier = Modifier.weight(1f)
    )

    IconButton(
      modifier = Modifier.then(Modifier.size(24.dp)),
      onClick = { if (account.isWriteable()) accountViewModel.boost(note) }
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
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
      modifier = Modifier.weight(1f)
    )

    IconButton(
      modifier = Modifier.then(Modifier.size(24.dp)),
      onClick = { if (account.isWriteable()) accountViewModel.reactTo(note) }
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
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
      modifier = Modifier.weight(1f)
    )


    IconButton(
      modifier = Modifier.then(Modifier.size(24.dp)),
      onClick = { }
    ) {
      Icon(
        imageVector = Icons.Outlined.BarChart,
        null,
        modifier = Modifier.size(19.dp),
        tint = grayTint
      )
    }

    Row(modifier = Modifier.weight(1f)) {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data("https://counter.amethyst.social/${note.idHex}.svg?label=+&color=00000000")
          .crossfade(true)
          .diskCachePolicy(CachePolicy.DISABLED)
          .memoryCachePolicy(CachePolicy.ENABLED)
          .build(),
        contentDescription = "View count",
        modifier = Modifier.height(24.dp),
        colorFilter = ColorFilter.tint(grayTint)
      )
    }


    IconButton(
      modifier = Modifier.then(Modifier.size(24.dp)),
      onClick = { popupExpanded = true }
    ) {
      Icon(
        imageVector = Icons.Default.MoreVert,
        null,
        modifier = Modifier.size(15.dp),
        tint = grayTint,
      )
    }
  }

  NoteDropDownMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
}

fun showCount(size: Int?): String {
  if (size == null) return " "
  return if (size == 0) return " " else "$size"
}