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
package com.vitorpamplona.quartz.cordn.spec00Coordinator

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The eleven coordinator tools, as a contract.
 *
 * [CoordinatorClient] is the only real implementation and this interface adds
 * nothing to it — no defaults, no behaviour. It exists so the layers above
 * (`CordnGroupSync`, and the group manager in `commons`) can be exercised
 * without standing up a relay, a transport and a server, which is otherwise the
 * price of testing a cursor loop.
 *
 * What it deliberately does **not** expose is identity. Which key signs which
 * call is fixed by [CoordinatorMethod] per `spec/00.md` §8, and a substitute
 * implementation cannot widen that, because there is no parameter to widen.
 */
interface ICoordinator {
    suspend fun publishKeyPackage(
        keyPackageRef: String,
        keyPackageBase64: String,
    ): PublishedKeyPackage

    suspend fun removeKeyPackages(keyPackageRefs: List<String>): List<String>

    suspend fun listKeyPackages(): List<AvailableKeyPackage>

    suspend fun takeKeyPackage(id: String): TakenKeyPackage?

    suspend fun storeWelcome(
        targetPubKey: HexKey,
        keyPackageRef: String,
        welcomeBase64: String,
        after: Long? = null,
    ): Long

    suspend fun takeWelcomes(consumed: List<ConsumedWelcomeRef> = emptyList()): List<PendingWelcome>

    suspend fun storeJoinRequest(
        gid: String,
        keyPackageRef: String,
    ): Long

    suspend fun takeJoinRequests(
        gids: List<String>,
        consumed: List<ConsumedJoinRequestRef> = emptyList(),
    ): List<JoinRequest>

    suspend fun postMessage(
        gid: String,
        sealedBase64: String,
    ): PostedMessage

    suspend fun fetchMessages(cursors: Map<String, Long?>): List<GroupMessage>

    suspend fun subscribeMessages(
        cursors: Map<String, Long?>,
        timeoutMs: Long,
        onMessage: (GroupMessage) -> Unit,
    )
}
