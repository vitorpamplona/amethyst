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
package com.vitorpamplona.amethyst.commons.nip34Git.create

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * The account's GRASP servers (kind 10317) in preference order: the cached list, or the
 * newest one the outbox relays return within [idleTimeoutMs]. Null when the user has
 * never published one, which is when ngit falls back to
 * [com.vitorpamplona.quartz.nip34Git.grasp.GraspServer.DEFAULT_SERVERS].
 */
suspend fun Account.myGraspServers(idleTimeoutMs: Long = 5_000L): List<String>? =
    newestOwn<UserGraspListEvent>(
        address = UserGraspListEvent.createAddress(pubKey),
        filter = Filter(kinds = listOf(UserGraspListEvent.KIND), authors = listOf(pubKey), limit = 1),
        idleTimeoutMs = idleTimeoutMs,
    )?.grasps()

/**
 * The account's own repository announcement under [identifier], from the cache or the outbox
 * relays. A new repository must not reuse one: the announcement is addressable, so publishing
 * under the same `d` tag would silently replace the existing repository.
 */
suspend fun Account.findMyGitRepository(
    identifier: String,
    idleTimeoutMs: Long = 5_000L,
): GitRepositoryEvent? =
    newestOwn<GitRepositoryEvent>(
        address = Address(GitRepositoryEvent.KIND, pubKey, identifier),
        filter = Filter(kinds = listOf(GitRepositoryEvent.KIND), authors = listOf(pubKey), tags = mapOf("d" to listOf(identifier)), limit = 1),
        idleTimeoutMs = idleTimeoutMs,
    )

private suspend inline fun <reified T : Event> Account.newestOwn(
    address: Address,
    filter: Filter,
    idleTimeoutMs: Long,
): T? {
    (cache.getAddressableNoteIfExists(address)?.event as? T)?.let { return it }

    val relays = outboxRelays.flow.value
    if (relays.isEmpty()) return null

    val newest =
        client
            .fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = idleTimeoutMs)
            .filterIsInstance<T>()
            .maxByOrNull { it.createdAt }
            ?: return null

    cache.justConsume(newest, null, false)
    return newest
}
