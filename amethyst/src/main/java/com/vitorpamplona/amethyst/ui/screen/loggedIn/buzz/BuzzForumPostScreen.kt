/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composer for a Buzz **forum post** (NIP kind 45001) — the root of a forum thread in a
 * workspace channel. The post body is the event content (Buzz forum posts carry no
 * separate title). Published to the channel's host relay via `build_forum_post`'s Quartz
 * equivalent ([ForumPostEvent.build]). Reached from the Threads tab's compose FAB on a
 * Buzz-dialect relay.
 */
@Composable
fun BuzzForumPostScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var body by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.buzz_forum_new_title), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = body,
                onValueChange = {
                    body = it
                    error = null
                },
                label = { Text(stringRes(R.string.buzz_forum_body_label)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPosting,
            )

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl)
                    if (relay == null) {
                        error = "Invalid relay url"
                        return@Button
                    }
                    isPosting = true
                    error = null
                    // Publish on IO, then pop back on the composition's main scope.
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                accountViewModel.account.signAndSendPrivatelyOrBroadcast(
                                    ForumPostEvent.build(channelId, body.trim()),
                                ) { listOf(relay) }
                            }
                            nav.popBack()
                        } catch (e: Exception) {
                            isPosting = false
                            error = "Failed to post: ${e.message ?: e::class.simpleName}"
                        }
                    }
                },
                enabled = body.isNotBlank() && !isPosting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isPosting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringRes(R.string.buzz_forum_post_action))
                }
            }
        }
    }
}
