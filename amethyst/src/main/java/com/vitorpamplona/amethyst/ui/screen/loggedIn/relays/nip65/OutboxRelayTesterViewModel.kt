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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip65

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Stable
class OutboxRelayTesterViewModel : ViewModel() {
    sealed class TestState {
        object Idle : TestState()

        object Testing : TestState()

        object Success : TestState()

        data class Failed(
            val message: String,
        ) : TestState()
    }

    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private val _results = MutableStateFlow<Map<NormalizedRelayUrl, TestState>>(emptyMap())
    val results = _results.asStateFlow()

    fun isTesting() = _results.value.values.any { it is TestState.Testing }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun testRelays(relayUrls: List<NormalizedRelayUrl>) {
        if (relayUrls.isEmpty() || isTesting()) return

        _results.value = relayUrls.associateWith { TestState.Testing }

        accountViewModel.launchSigner {
            val expirationTime = TimeUtils.now() + 300 // 5 minutes

            val testEvent =
                account.signer.sign<TextNoteEvent>(
                    TextNoteEvent.build("Relay connectivity test. This event expires in 5 minutes and will be deleted.") {
                        expiration(expirationTime)
                    },
                )

            val deletionEvent =
                account.signer.sign<DeletionEvent>(
                    DeletionEvent.build(listOf(testEvent)),
                )

            val listener =
                object : IRelayClientListener {
                    override fun onIncomingMessage(
                        relay: IRelayClient,
                        msgStr: String,
                        msg: Message,
                    ) {
                        if (msg is OkMessage && msg.eventId == testEvent.id) {
                            if (msg.success) {
                                _results.update { it + (relay.url to TestState.Success) }
                                account.client.send(deletionEvent, setOf(relay.url))
                            } else {
                                _results.update { it + (relay.url to TestState.Failed(msg.message)) }
                            }
                        }
                    }
                }

            account.client.subscribe(listener)

            relayUrls.forEach { relayUrl ->
                account.client.send(testEvent, setOf(relayUrl))
            }

            delay(30_000)

            account.client.unsubscribe(listener)

            _results.update { current ->
                current.mapValues { (_, state) ->
                    if (state is TestState.Testing) TestState.Failed("Timeout") else state
                }
            }
        }
    }

    fun clearResults() {
        _results.value = emptyMap()
    }
}
