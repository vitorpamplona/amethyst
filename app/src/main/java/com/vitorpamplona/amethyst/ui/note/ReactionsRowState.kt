package com.vitorpamplona.amethyst.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ReactionsRowState(baseNote: Note, accountViewModel: AccountViewModel) {
  val accountState by accountViewModel.accountLiveData.observeAsState()
  val account = accountState?.account

  val noteState by baseNote.live.observeAsState()
  val note = noteState?.note

  if (account == null || note == null) return

  ReactionsRow(note, account, accountViewModel)
}