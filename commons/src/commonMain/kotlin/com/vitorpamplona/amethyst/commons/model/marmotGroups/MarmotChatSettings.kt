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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupBlobs
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.features.accountSync.AccountSyncState
import com.vitorpamplona.marmot.features.accountSync.SmallStateItem
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The three per-chat settings a Marmot chat has, the same ones the device group syncs (`account-sync.md`). */
enum class MarmotChatSetting(
    /** The small-state item name. */
    val wireName: String,
) {
    PIN("pin"),
    MUTE("mute"),
    ARCHIVE("archive"),
    ;

    companion object {
        fun of(wireName: String): MarmotChatSetting? = entries.firstOrNull { it.wireName == wireName }
    }
}

/** Which of this account's Marmot chats are pinned, muted (until when) and archived, by Nostr group id. */
@Immutable
data class MarmotChatSettingsSnapshot(
    val pinned: Set<HexKey> = emptySet(),
    /** Chat to the time its mute ends; [MUTED_FOREVER] for a mute with no end. */
    val muted: Map<HexKey, Long> = emptyMap(),
    val archived: Set<HexKey> = emptySet(),
) {
    fun isPinned(nostrGroupId: HexKey) = nostrGroupId in pinned

    fun isArchived(nostrGroupId: HexKey) = nostrGroupId in archived

    /** A mute whose time has passed is no mute: each device judges that by its own clock. */
    fun isMuted(
        nostrGroupId: HexKey,
        now: Long = TimeUtils.now(),
    ): Boolean = muted[nostrGroupId]?.let { it > now } == true

    fun isOn(
        nostrGroupId: HexKey,
        setting: MarmotChatSetting,
    ): Boolean =
        when (setting) {
            MarmotChatSetting.PIN -> isPinned(nostrGroupId)
            MarmotChatSetting.MUTE -> isMuted(nostrGroupId)
            MarmotChatSetting.ARCHIVE -> isArchived(nostrGroupId)
        }

    /** [setting] of [nostrGroupId] set from its small-state token: `true`, `false` or a mute time. */
    fun with(
        nostrGroupId: HexKey,
        setting: MarmotChatSetting,
        value: String,
    ): MarmotChatSettingsSnapshot {
        val on = value == "true"
        return when (setting) {
            MarmotChatSetting.PIN -> copy(pinned = if (on) pinned + nostrGroupId else pinned - nostrGroupId)
            MarmotChatSetting.ARCHIVE -> copy(archived = if (on) archived + nostrGroupId else archived - nostrGroupId)
            MarmotChatSetting.MUTE -> {
                val until = if (on) MUTED_FOREVER else value.toLongOrNull()
                copy(muted = if (until == null) muted - nostrGroupId else muted + (nostrGroupId to until))
            }
        }
    }

    /** Everything that differs from the defaults, as `(chat, setting, token)`: what a first device-group start seeds. */
    fun nonDefault(now: Long = TimeUtils.now()): List<Triple<HexKey, MarmotChatSetting, String>> =
        pinned.map { Triple(it, MarmotChatSetting.PIN, "true") } +
            muted.filterValues { it > now }.map { (id, until) -> Triple(id, MarmotChatSetting.MUTE, if (until == MUTED_FOREVER) "true" else until.toString()) } +
            archived.map { Triple(it, MarmotChatSetting.ARCHIVE, "true") }

    companion object {
        const val MUTED_FOREVER = Long.MAX_VALUE
    }
}

/**
 * Pin, mute and archive for this account's Marmot chats on this device: the state the chat list,
 * the chat screen and notifications read, persisted in [blobs] (encrypted at rest by the app) so it
 * survives a restart with or without device groups. With device groups on, the user's changes also
 * go to the account's other devices, and theirs arrive here through [applyRemote].
 */
class MarmotChatSettings(
    private val blobs: DeviceGroupBlobs?,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(MarmotChatSettingsSnapshot())
    val state: StateFlow<MarmotChatSettingsSnapshot> = _state.asStateFlow()
    private var loaded = false

    @Serializable
    private class Stored(
        val pinned: List<String> = emptyList(),
        val muted: Map<String, Long> = emptyMap(),
        val archived: List<String> = emptyList(),
    )

    /** Reads the saved settings once; later calls do nothing. */
    suspend fun load() =
        mutex.withLock {
            if (loaded) return@withLock
            loaded = true
            val bytes =
                try {
                    blobs?.load(RECORD)
                } catch (e: Exception) {
                    Log.w(TAG, "could not read the Marmot chat settings", e)
                    null
                } ?: return@withLock
            val stored =
                try {
                    json.decodeFromString(Stored.serializer(), bytes.decodeToString())
                } catch (e: Exception) {
                    Log.w(TAG, "unreadable Marmot chat settings", e)
                    return@withLock
                }
            // Anything changed before the load finished wins over what was saved.
            _state.update { now ->
                MarmotChatSettingsSnapshot(
                    pinned = stored.pinned.toSet() + now.pinned,
                    muted = stored.muted + now.muted,
                    archived = stored.archived.toSet() + now.archived,
                )
            }
        }

    /** The user turned [setting] of [nostrGroupId] on or off here. True when it changed. */
    suspend fun set(
        nostrGroupId: HexKey,
        setting: MarmotChatSetting,
        on: Boolean,
    ): Boolean = change(nostrGroupId, setting, on.toString())

    /** A value the account's device group settled on ([value]: its small-state token). True when it changed. */
    suspend fun applyRemote(
        nostrGroupId: HexKey,
        setting: MarmotChatSetting,
        value: String,
    ): Boolean = change(nostrGroupId, setting, value)

    /** A chat this device left: its settings go with it. */
    suspend fun forget(nostrGroupId: HexKey) {
        val before = _state.value
        val after = before.copy(pinned = before.pinned - nostrGroupId, muted = before.muted - nostrGroupId, archived = before.archived - nostrGroupId)
        if (after != before) {
            _state.value = after
            save()
        }
    }

    private suspend fun change(
        nostrGroupId: HexKey,
        setting: MarmotChatSetting,
        value: String,
    ): Boolean {
        var changed = false
        _state.update { before ->
            before.with(nostrGroupId, setting, value).also { changed = it != before }
        }
        if (changed) save()
        return changed
    }

    private suspend fun save() {
        val target = blobs ?: return
        val s = _state.value
        val stored =
            Stored(
                s.pinned.sorted(),
                s.muted.entries
                    .sortedBy { it.key }
                    .associate { it.key to it.value },
                s.archived.sorted(),
            )
        try {
            mutex.withLock { target.save(RECORD, json.encodeToString(Stored.serializer(), stored).encodeToByteArray()) }
        } catch (e: Exception) {
            Log.w(TAG, "could not save the Marmot chat settings", e)
        }
    }

    /**
     * Applies the account's small state, as the device group settled it, to this device's chats
     * (`account-sync.md` "Small state"): each pin, mute and archive value becomes this chat's, and each
     * read position goes to [onRead]. Items are keyed by MLS group id; chats [manager] does not hold, and
     * the ones [skip] names (the device group itself), are passed over: their items stay in the device
     * group's state until the chat arrives.
     */
    suspend fun applySmallState(
        state: AccountSyncState,
        manager: MarmotManager,
        skip: (HexKey) -> Boolean = { false },
        onRead: suspend (nostrGroupId: HexKey, read: SmallStateItem.Read) -> Unit = { _, _ -> },
    ) {
        val byMlsId = HashMap<HexKey, HexKey>()
        manager.activeGroupIds().forEach { nostrId ->
            manager.groupManager
                .getGroup(nostrId)
                ?.groupId
                ?.toHexKey()
                ?.let { byMlsId[it] = nostrId }
        }
        for (item in state.items) {
            val nostrId = byMlsId[item.chat] ?: continue
            if (skip(nostrId)) continue
            when (item) {
                is SmallStateItem.Read -> onRead(nostrId, item)
                is SmallStateItem.Versioned -> MarmotChatSetting.of(item.name)?.let { applyRemote(nostrId, it, item.value) }
                is SmallStateItem.Invite -> {}
            }
        }
    }

    companion object {
        private const val TAG = "MarmotChatSettings"
        const val RECORD = "marmot_chat_settings"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
