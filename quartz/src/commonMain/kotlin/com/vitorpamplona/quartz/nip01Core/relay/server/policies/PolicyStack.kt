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
package com.vitorpamplona.quartz.nip01Core.relay.server.policies

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult

class PolicyStack(
    vararg policies: IRelayPolicy,
) : IRelayPolicy {
    val policies = policies.toList()

    override fun onConnect(send: (Message) -> Unit) {
        policies.forEach { it.onConnect(send) }
    }

    override fun accept(cmd: EventCmd) = runPolicies(cmd) { p, c -> p.accept(c) }

    override fun accept(cmd: ReqCmd) = runPolicies(cmd) { p, c -> p.accept(c) }

    override fun accept(cmd: CountCmd) = runPolicies(cmd) { p, c -> p.accept(c) }

    override fun accept(cmd: AuthCmd) = runPolicies(cmd) { p, c -> p.accept(c) }

    private inline fun <T : Command> runPolicies(
        initialCmd: T,
        operation: (IRelayPolicy, T) -> PolicyResult<T>,
    ): PolicyResult<T> {
        var currentCmd = initialCmd
        for (policy in policies) {
            val result = operation(policy, currentCmd)
            if (result is PolicyResult.Rejected) return result
            currentCmd = (result as PolicyResult.Accepted).cmd
        }
        return PolicyResult.Accepted(currentCmd)
    }

    override fun canSendToSession(event: Event): Boolean = policies.all { it.canSendToSession(event) }
}
