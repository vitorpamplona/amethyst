package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.LikeSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun LikeSetCompose(likeSetCard: LikeSetCard, modifier: Modifier = Modifier, isInnerNote: Boolean = false, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by likeSetCard.note.live.observeAsState()
    val note = noteState?.note

    if (note?.event == null) {
        BlankNote(Modifier, isInnerNote)
    } else {
        Column(modifier = modifier) {
            Row(                modifier = Modifier
                .padding(
                    start = if (!isInnerNote) 12.dp else 0.dp,
                    end = if (!isInnerNote) 12.dp else 0.dp,
                    top = 10.dp)
            ) {

                // Draws the like picture outside the boosted card.
                if (!isInnerNote) {
                    Box(modifier = Modifier
                        .width(55.dp)
                        .padding(0.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_liked),
                            null,
                            modifier = Modifier.size(16.dp).align(Alignment.TopEnd),
                            tint = Color.Unspecified
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                    FlowRow() {
                        likeSetCard.likeEvents.forEach {
                            val cardNoteState by it.live.observeAsState()
                            val cardNote = cardNoteState?.note

                            if (cardNote?.author != null) {
                                val userState by cardNote.author!!.live.observeAsState()

                                AsyncImage(
                                    model = userState?.user?.profilePicture(),
                                    contentDescription = "Profile Image",
                                    modifier = Modifier
                                        .width(35.dp).height(35.dp)
                                        .height(35.dp)
                                        .clip(shape = CircleShape)
                                )
                            }
                        }
                    }

                    NoteCompose(note, Modifier.padding(top = 5.dp), true, accountViewModel, navController)
                }
            }
        }
    }
}