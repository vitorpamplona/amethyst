package com.vitorpamplona.amethyst.ui.note

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.unit.sp
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

@Composable
fun ReplyInformation(replyTo: MutableList<Note>?, mentions: List<User>?) {
  FlowRow() {
    /*
    if (replyTo != null && replyTo.isNotEmpty()) {
      Text(
        " in reply to ",
        fontSize = 13.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
      )
      replyTo.toSet().forEachIndexed { idx, note ->
        val innerNoteState by note.live.observeAsState()
        Text(
          "${innerNoteState?.note?.idDisplayHex}${if (idx < replyTo.size - 1) ", " else ""}",
          fontSize = 13.sp,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )
      }
    }
    */
    if (mentions != null && mentions.isNotEmpty()) {
      Text(
        "replying to ",
        fontSize = 13.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
      )
      mentions.toSet().forEachIndexed { idx, user ->
        val innerUserState by user.live.observeAsState()
        Text(
          "${innerUserState?.user?.toBestDisplayName()}",
          fontSize = 13.sp,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )

        if (idx < mentions.size - 2) {
          Text(
            ", ",
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
          )
        } else if (idx < mentions.size - 1) {
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