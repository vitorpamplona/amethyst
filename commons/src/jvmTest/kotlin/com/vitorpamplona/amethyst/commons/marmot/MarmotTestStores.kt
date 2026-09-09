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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore

// In-memory stand-ins for the durable stores a MarmotManager needs.
//
// Shared across the Marmot app-layer tests rather than re-declared per file:
// several of them turn on what survives a restart, and "restart" here means
// building a second manager over the SAME store instance. A per-file copy
// would quietly make each test's restart a different thing.

/** Stands in for a relay that accepts every commit, so epochs actually advance. */
val ACCEPTING_RELAY = MarmotPublisher { _, _ -> true }

class SnapshotStateStore : MlsGroupStateStore {
    private val states = mutableMapOf<String, ByteArray>()
    private val retained = mutableMapOf<String, List<ByteArray>>()

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

    override suspend fun delete(nostrGroupId: String) {
        states.remove(nostrGroupId)
        retained.remove(nostrGroupId)
    }

    override suspend fun listGroups(): List<String> = states.keys.toList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        retained[nostrGroupId] = retainedSecrets
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId] ?: emptyList()
}

class SnapshotMessageStore : MarmotMessageStore {
    private val messages = mutableMapOf<String, MutableList<String>>()
    private val snapshots = mutableMapOf<String, String>()

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) {
        val log = messages.getOrPut(nostrGroupId) { mutableListOf() }
        if (innerEventJson !in log) log.add(innerEventJson)
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> = messages[nostrGroupId]?.toList() ?: emptyList()

    override suspend fun delete(nostrGroupId: String) {
        messages.remove(nostrGroupId)
        snapshots.remove(nostrGroupId)
    }

    override suspend fun recordGroupSnapshot(
        nostrGroupId: String,
        snapshotJson: String,
    ) {
        snapshots[nostrGroupId] = snapshotJson
    }

    override suspend fun loadGroupSnapshot(nostrGroupId: String): String? = snapshots[nostrGroupId]
}

class SnapshotBundleStore : KeyPackageBundleStore {
    private var snapshot: ByteArray? = null

    override suspend fun save(snapshot: ByteArray) {
        this.snapshot = snapshot
    }

    override suspend fun load(): ByteArray? = snapshot

    override suspend fun delete() {
        snapshot = null
    }
}
