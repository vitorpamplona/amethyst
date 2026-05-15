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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-screen index of the most recent NIP-34 status event (kinds
 * 1630-1633) per target id, kept up to date from
 * [LocalCache.live.newEventBundles]. Status events are not tracked in
 * `Note.replies` (see `LocalCache.computeReplyTo`), so the only way to
 * find them otherwise would be a full cache scan per row.
 */
object GitStatusIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    private val mutableLatestByTarget = MutableStateFlow<Map<HexKey, GitStatusEvent>?>(null)
    val latestByTarget: StateFlow<Map<HexKey, GitStatusEvent>?> = mutableLatestByTarget.asStateFlow()

    fun startIfNeeded() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // Subscribe to bundle updates BEFORE the initial scan via onStart, so any
            // events that arrive between scan start and collector attach are picked up.
            LocalCache.live.newEventBundles
                .onStart {
                    val initial = HashMap<HexKey, GitStatusEvent>()
                    LocalCache.notes.forEach { _, note ->
                        val event = note.event as? GitStatusEvent ?: return@forEach
                        val target = event.rootEventId() ?: return@forEach
                        val current = initial[target]
                        if (current == null || event.createdAt > current.createdAt) {
                            initial[target] = event
                        }
                    }
                    mutableLatestByTarget.value = initial
                }.collect { bundle -> processBundle(bundle) }
        }
    }

    private fun processBundle(bundle: Set<Note>) {
        val snapshot = mutableLatestByTarget.value ?: emptyMap()
        var modified: HashMap<HexKey, GitStatusEvent>? = null
        for (note in bundle) {
            val event = note.event as? GitStatusEvent ?: continue
            val target = event.rootEventId() ?: continue
            val map = modified ?: snapshot
            val current = map[target]
            if (current == null || event.createdAt > current.createdAt) {
                if (modified == null) modified = HashMap(snapshot)
                modified[target] = event
            }
        }
        modified?.let { mutableLatestByTarget.value = it }
    }
}
