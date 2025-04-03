/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.note.elements

import android.R.attr.maxLines
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.buildLinkString
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.firstIsTaggedHashes

@Composable
fun DisplayFollowingHashtagsInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userFollowState by accountViewModel.account.liveKind3Follows.collectAsStateWithLifecycle()
    var firstTag by remember(baseNote) { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = userFollowState) {
        val newFirstTag = baseNote.event?.firstIsTaggedHashes(userFollowState.hashtags)

        if (firstTag != newFirstTag) {
            firstTag = newFirstTag
        }
    }

    firstTag?.let {
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { DisplayTagList(it, nav) }
        }
    }
}

@Composable
private fun DisplayTagList(
    firstTag: String,
    nav: INav,
) {
    Text(
        text =
            remember(firstTag) {
                buildLinkString(" #$firstTag") {
                    nav.nav(Route.Hashtag(firstTag))
                }
            },
        style = LocalTextStyle.current.copy(MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    )
}
