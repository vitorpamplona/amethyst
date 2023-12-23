package com.vitorpamplona.amethyst.ui.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DisplayFollowingHashtagsInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = remember { baseNote.event } ?: return

    val userFollowState by accountViewModel.userFollows.observeAsState()
    var firstTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = userFollowState) {
        launch(Dispatchers.Default) {
            val followingTags = userFollowState?.user?.cachedFollowingTagSet() ?: emptySet()
            val newFirstTag = noteEvent.firstIsTaggedHashes(followingTags)

            if (firstTag != newFirstTag) {
                launch(Dispatchers.Main) {
                    firstTag = newFirstTag
                }
            }
        }
    }

    firstTag?.let {
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisplayTagList(it, nav)
            }
        }
    }
}

@Composable
private fun DisplayTagList(firstTag: String, nav: (String) -> Unit) {
    val displayTag = remember(firstTag) { AnnotatedString(" #$firstTag") }
    val route = remember(firstTag) { "Hashtag/$firstTag" }

    ClickableText(
        text = displayTag,
        onClick = { nav(route) },
        style = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.primary.copy(
                alpha = 0.52f
            )
        ),
        maxLines = 1
    )
}
