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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult

/**
 * Defines custom behavior for this relay.
 */
interface IRelayPolicy {
    fun onConnect(send: (Message) -> Unit)

    /**
     * Evaluates whether an incoming EVENT command should be accepted.
     *
     * @param cmd The event the client wants to publish.
     * @return [PolicyResult.Accepted] to store the event, or [PolicyResult.Rejected] with a reason string.
     */
    fun accept(cmd: EventCmd): PolicyResult<EventCmd>

    /**
     * Evaluates a REQ command, optionally rewriting the filter list.
     *
     * @param cmd The filters from the REQ command.
     * @return [PolicyResult.Accepted] with an optional replacement filter list, or [PolicyResult.Rejected].
     */
    fun accept(cmd: ReqCmd): PolicyResult<ReqCmd>

    /**
     * Evaluates a COUNT command, optionally rewriting the filter list.
     *
     * @param cmd The filters from the COUNT command.
     * @return [PolicyResult.Accepted] to allow counting, or [PolicyResult.Rejected] with a reason.
     */
    fun accept(cmd: CountCmd): PolicyResult<CountCmd>

    /**
     * Evaluates whether an incoming AUTH command should be accepted.
     *
     * @param cmd The event the client wants to auth.
     * @return [PolicyResult.Accepted] to log in, or [PolicyResult.Rejected] with a reason string.
     */
    fun accept(cmd: AuthCmd): PolicyResult<AuthCmd>

    /**
     * Filters a live event before it is forwarded to a subscriber.
     *
     * Called for each event that matches a subscription's filters. Return
     * true to deliver the event, false to suppress it for this session.
     *
     * @param event The event about to be sent.
     */
    fun canSendToSession(event: Event): Boolean = true

    operator fun plus(other: IRelayPolicy) = listOf(this, other)
}

sealed interface PolicyResult<T : Command> {
    class Accepted<T : Command>(
        val cmd: T,
    ) : PolicyResult<T>

    class Rejected<T : Command>(
        val reason: String,
    ) : PolicyResult<T>
}
