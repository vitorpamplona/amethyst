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
package com.vitorpamplona.amethyst.desktop.ui.notifications

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.desktop.ui.NotificationItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface NotificationGroup {
    val newestTimestamp: Long
    val id: String

    @Immutable
    data class Single(
        val item: NotificationItem,
    ) : NotificationGroup {
        override val newestTimestamp: Long get() = item.timestamp
        override val id: String get() = "single-" + item.event.id
    }

    @Immutable
    data class ReactionsOn(
        val targetNoteId: String,
        val dayBucket: String,
        val items: List<NotificationItem.Reaction>,
    ) : NotificationGroup {
        override val newestTimestamp: Long get() = items.first().timestamp
        override val id: String get() = "rx-$targetNoteId-$dayBucket"
    }

    @Immutable
    data class RepostsOn(
        val targetNoteId: String,
        val dayBucket: String,
        val items: List<NotificationItem.Repost>,
    ) : NotificationGroup {
        override val newestTimestamp: Long get() = items.first().timestamp
        override val id: String get() = "rp-$targetNoteId-$dayBucket"
    }
}

private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun dayBucketFor(epochSec: Long): String = DAY_FORMAT.format(Date(epochSec * 1_000L))

private fun targetNoteId(item: NotificationItem): String? =
    item.event.tags
        .firstOrNull { it.size > 1 && it[0] == "e" }
        ?.get(1)

/**
 * Bucket reactions and reposts by (target-note, day). Preserve newest-first
 * ordering across groups. Zaps, mentions, replies stay individual.
 */
fun groupNotifications(items: List<NotificationItem>): List<NotificationGroup> {
    if (items.isEmpty()) return emptyList()

    val out = ArrayList<NotificationGroup>(items.size)
    val rxBuckets = LinkedHashMap<String, MutableList<NotificationItem.Reaction>>()
    val rxKeyToTarget = HashMap<String, Pair<String, String>>()
    val rpBuckets = LinkedHashMap<String, MutableList<NotificationItem.Repost>>()
    val rpKeyToTarget = HashMap<String, Pair<String, String>>()

    // Track insertion position in `out` for each bucket key so we can replace
    // the placeholder once we know the full list.
    val bucketSlot = HashMap<String, Int>()

    for (item in items) {
        when (item) {
            is NotificationItem.Reaction -> {
                val target = targetNoteId(item)
                if (target == null) {
                    out.add(NotificationGroup.Single(item))
                    continue
                }
                val day = dayBucketFor(item.timestamp)
                val key = "rx|$target|$day"
                val list =
                    rxBuckets.getOrPut(key) {
                        ArrayList<NotificationItem.Reaction>().also {
                            rxKeyToTarget[key] = target to day
                            bucketSlot[key] = out.size
                            // Placeholder — replaced on flush below.
                            out.add(NotificationGroup.ReactionsOn(target, day, emptyList()))
                        }
                    }
                list.add(item)
            }
            is NotificationItem.Repost -> {
                val target = targetNoteId(item)
                if (target == null) {
                    out.add(NotificationGroup.Single(item))
                    continue
                }
                val day = dayBucketFor(item.timestamp)
                val key = "rp|$target|$day"
                val list =
                    rpBuckets.getOrPut(key) {
                        ArrayList<NotificationItem.Repost>().also {
                            rpKeyToTarget[key] = target to day
                            bucketSlot[key] = out.size
                            out.add(NotificationGroup.RepostsOn(target, day, emptyList()))
                        }
                    }
                list.add(item)
            }
            else -> out.add(NotificationGroup.Single(item))
        }
    }

    // Materialize bucket contents into their placeholder positions.
    for ((key, list) in rxBuckets) {
        val (target, day) = rxKeyToTarget.getValue(key)
        val slot = bucketSlot.getValue(key)
        out[slot] = NotificationGroup.ReactionsOn(target, day, list.toList())
    }
    for ((key, list) in rpBuckets) {
        val (target, day) = rpKeyToTarget.getValue(key)
        val slot = bucketSlot.getValue(key)
        out[slot] = NotificationGroup.RepostsOn(target, day, list.toList())
    }

    return out
}
