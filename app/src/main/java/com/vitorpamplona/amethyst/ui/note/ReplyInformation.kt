package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

@Composable
fun ReplyInformation(replyTo: List<Note>?, mentions: List<User>?, account: Account, navController: NavController) {
  ReplyInformation(replyTo, mentions, account) {
    navController.navigate("User/${it.pubkeyHex}")
  }
}

@Composable
fun ReplyInformation(replyTo: List<Note>?, dupMentions: List<User>?, account: Account, prefix: String = "", onUserTagClick: (User) -> Unit) {
  val mentions = dupMentions?.toSet()?.sortedBy { !account.userProfile().isFollowing(it) }
  var expanded by remember { mutableStateOf((mentions?.size ?: 0) <= 2) }

  FlowRow() {
    if (mentions != null && mentions.isNotEmpty()) {
      if (replyTo != null && replyTo.isNotEmpty()) {
        val repliesToDisplay = if (expanded) mentions else mentions.take(2)

        Text(
          "replying to ",
          fontSize = 13.sp,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )

        repliesToDisplay.forEachIndexed { idx, user ->
          val innerUserState by user.live().metadata.observeAsState()
          val innerUser = innerUserState?.user

          innerUser?.let { myUser ->
            ClickableText(
              AnnotatedString("${prefix}@${myUser.toBestDisplayName()}"),
              style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
              onClick = { onUserTagClick(myUser) }
            )

            if (expanded) {
              if (idx < repliesToDisplay.size - 2) {
                Text(
                  ", ",
                  fontSize = 13.sp,
                  color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
              } else if (idx < repliesToDisplay.size - 1) {
                Text(
                  " and ",
                  fontSize = 13.sp,
                  color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
              }
            } else {
              if (idx < repliesToDisplay.size - 1) {
                Text(
                  ", ",
                  fontSize = 13.sp,
                  color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
              } else if (idx < repliesToDisplay.size) {
                Text(
                  " and ",
                  fontSize = 13.sp,
                  color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )

                ClickableText(
                  AnnotatedString("${mentions.size-2}"),
                  style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
                  onClick = { expanded = true }
                )

                Text(
                  " others",
                  fontSize = 13.sp,
                  color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
              }
            }
          }
        }
      }
    }
  }
}


@Composable
fun ReplyInformationChannel(replyTo: List<Note>?, mentions: List<User>?, channel: Channel, navController: NavController) {
  ReplyInformationChannel(replyTo, mentions, channel,
    onUserTagClick = {
      navController.navigate("User/${it.pubkeyHex}")
    },
    onChannelTagClick = {
      navController.navigate("Channel/${it.idHex}")
    }
  )
}


@Composable
fun ReplyInformationChannel(replyTo: List<Note>?,
                     mentions: List<User>?,
                     baseChannel: Channel,
                     prefix: String = "",
                     onUserTagClick: (User) -> Unit,
                     onChannelTagClick: (Channel) -> Unit
) {
  val channelState by baseChannel.live.observeAsState()
  val channel = channelState?.channel ?: return

  FlowRow() {
    Text(
      "in channel ",
      fontSize = 13.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    )

    ClickableText(
      AnnotatedString("${channel.info.name} "),
      style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
      onClick = { onChannelTagClick(channel) }
    )

    if (mentions != null && mentions.isNotEmpty()) {
      if (replyTo != null && replyTo.isNotEmpty()) {
        Text(
          "replying to ",
          fontSize = 13.sp,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )

        val mentionSet = mentions.toSet()

        mentionSet.forEachIndexed { idx, user ->
          val innerUserState by user.live().metadata.observeAsState()
          val innerUser = innerUserState?.user

          innerUser?.let { myUser ->
            ClickableText(
              AnnotatedString("${prefix}@${myUser.toBestDisplayName()}"),
              style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
              onClick = { onUserTagClick(myUser) }
            )

            if (idx < mentionSet.size - 2) {
              Text(
                ", ",
                fontSize = 13.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
              )
            } else if (idx < mentionSet.size - 1) {
              Text(
                " and ",
                fontSize = 13.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
              )
            }
          }
        }
      }
    }
  }
}