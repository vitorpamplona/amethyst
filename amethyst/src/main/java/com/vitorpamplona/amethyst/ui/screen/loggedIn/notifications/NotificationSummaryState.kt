/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import android.util.Log
import androidx.compose.runtime.Stable
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.data.MutableExtraStore
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.note.showCount
import com.vitorpamplona.ammolite.relays.BundledInsert
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val ShowDecimals = ExtraStore.Key<Boolean>()
val BottomAxisLabelKey = ExtraStore.Key<List<String>>()

@Stable
class NotificationSummaryState(
    val account: Account,
) {
    val user: User = account.userProfile()

    private var reactions = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var boosts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var zaps = MutableStateFlow<Map<String, BigDecimal>>(emptyMap())
    private var replies = MutableStateFlow<Map<String, Int>>(emptyMap())

    private var _chartModel = MutableStateFlow<CartesianChartModel?>(null)
    val chartModel = _chartModel.asStateFlow()

    private var takenIntoAccount = setOf<HexKey>()
    private val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd") // SimpleDateFormat()

    val todaysReplyCount = replies.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysBoostCount = boosts.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysReactionCount = reactions.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysZapAmount = zaps.map { showAmountInteger(it[today()]) }.distinctUntilChanged()

    fun formatDate(createAt: Long): String =
        sdf.format(
            Instant.ofEpochSecond(createAt).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        )

    fun today() = sdf.format(LocalDateTime.now())

    public suspend fun initializeSuspend() {
        checkNotInMainThread()

        val currentUser = user.pubkeyHex

        val reactions = mutableMapOf<String, Int>()
        val boosts = mutableMapOf<String, Int>()
        val zaps = mutableMapOf<String, BigDecimal>()
        val replies = mutableMapOf<String, Int>()
        val takenIntoAccount = mutableSetOf<HexKey>()

        LocalCache.notes.forEach { _, it ->
            val noteEvent = it.event
            if (noteEvent != null && !takenIntoAccount.contains(noteEvent.id)) {
                if (noteEvent is ReactionEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        reactions[netDate] = (reactions[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id)
                    }
                } else if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        boosts[netDate] = (boosts[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id)
                    }
                } else if (noteEvent is LnZapEvent) {
                    // the user might be sending his own receipts noteEvent.pubKey != currentUser
                    if (noteEvent.isTaggedUser(currentUser)) {
                        val netDate = formatDate(noteEvent.createdAt)
                        zaps[netDate] = (zaps[netDate] ?: BigDecimal.ZERO) + (noteEvent.amount ?: BigDecimal.ZERO)
                        takenIntoAccount.add(noteEvent.id)
                    }
                } else if (noteEvent is BaseThreadedEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val isCitation =
                            noteEvent.findCitations().any {
                                LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == currentUser
                            }

                        val netDate = formatDate(noteEvent.createdAt)
                        if (isCitation) {
                            boosts[netDate] = (boosts[netDate] ?: 0) + 1
                        } else {
                            replies[netDate] = (replies[netDate] ?: 0) + 1
                        }
                        takenIntoAccount.add(noteEvent.id)
                    }
                }
            }
        }

        this.takenIntoAccount = takenIntoAccount
        this.reactions.emit(reactions)
        this.replies.emit(replies)
        this.zaps.emit(zaps)
        this.boosts.emit(boosts)

        refreshChartModel()
    }

    suspend fun addToStatsSuspend(newBlockNotes: Set<Set<Note>>) {
        checkNotInMainThread()

        val currentUser = user.pubkeyHex

        val reactions = this.reactions.value.toMutableMap()
        val boosts = this.boosts.value.toMutableMap()
        val zaps = this.zaps.value.toMutableMap()
        val replies = this.replies.value.toMutableMap()
        val takenIntoAccount = this.takenIntoAccount.toMutableSet()
        var hasNewElements = false

        newBlockNotes.forEach { newNotes ->
            newNotes.forEach {
                val noteEvent = it.event
                if (noteEvent != null && !takenIntoAccount.contains(noteEvent.id)) {
                    if (noteEvent is ReactionEvent) {
                        if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                            val netDate = formatDate(noteEvent.createdAt)
                            reactions[netDate] = (reactions[netDate] ?: 0) + 1
                            takenIntoAccount.add(noteEvent.id)
                            hasNewElements = true
                        }
                    } else if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
                        if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                            val netDate = formatDate(noteEvent.createdAt)
                            boosts[netDate] = (boosts[netDate] ?: 0) + 1
                            takenIntoAccount.add(noteEvent.id)
                            hasNewElements = true
                        }
                    } else if (noteEvent is LnZapEvent) {
                        if (noteEvent.isTaggedUser(currentUser)) {
                            //  && noteEvent.pubKey != currentUser User might be sending his own receipts
                            val netDate = formatDate(noteEvent.createdAt)
                            zaps[netDate] = (zaps[netDate] ?: BigDecimal.ZERO) + (noteEvent.amount ?: BigDecimal.ZERO)
                            takenIntoAccount.add(noteEvent.id)
                            hasNewElements = true
                        }
                    } else if (noteEvent is BaseThreadedEvent) {
                        if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                            val isCitation =
                                noteEvent.findCitations().any {
                                    LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == currentUser
                                }

                            val netDate = formatDate(noteEvent.createdAt)
                            if (isCitation) {
                                boosts[netDate] = (boosts[netDate] ?: 0) + 1
                            } else {
                                replies[netDate] = (replies[netDate] ?: 0) + 1
                            }
                            takenIntoAccount.add(noteEvent.id)
                            hasNewElements = true
                        }
                    }
                }
            }
        }

        if (hasNewElements) {
            this.takenIntoAccount = takenIntoAccount
            this.reactions.emit(reactions)
            this.replies.emit(replies)
            this.zaps.emit(zaps)
            this.boosts.emit(boosts)

            refreshChartModel()
        }
    }

    private suspend fun refreshChartModel() {
        checkNotInMainThread()

        val now = LocalDateTime.now()

        val dataAxisLabelIndexes = listOf(-6, -5, -4, -3, -2, -1, 0)
        val dataAxisLabels = dataAxisLabelIndexes.map { sdf.format(now.plusDays(it.toLong())) }

        val chart1 =
            LineCartesianLayerModel.build {
                series(dataAxisLabelIndexes, dataAxisLabels.map { replies.value[it]?.toFloat() ?: 0f })
                series(dataAxisLabelIndexes, dataAxisLabels.map { boosts.value[it]?.toFloat() ?: 0f })
                series(dataAxisLabelIndexes, dataAxisLabels.map { reactions.value[it]?.toFloat() ?: 0f })
            }

        val chart2 =
            LineCartesianLayerModel.build {
                series(dataAxisLabelIndexes, dataAxisLabels.map { zaps.value[it]?.toFloat() ?: 0f })
            }

        val model = CartesianChartModel(chart1, chart2)

        val mutableStore = MutableExtraStore()

        mutableStore[ShowDecimals] = shouldShowDecimals(chart2.minY, chart2.maxY)

        this._chartModel.emit(model.copy(mutableStore))
    }

    // determine if the min max are so close that they render to the same number.
    fun shouldShowDecimals(
        min: Double,
        max: Double,
    ): Boolean {
        val step = (max - min) / 8

        var previous = showAmountInteger(min.toBigDecimal())
        for (i in 1..7) {
            val current = showAmountInteger((min + (i * step)).toBigDecimal())
            if (previous == current) {
                return true
            }
            previous = current
        }

        return false
    }

    private val bundlerInsert = BundledInsert<Set<Note>>(250, Dispatchers.IO)

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) { addToStatsSuspend(it) }
    }

    fun destroy() {
        bundlerInsert.cancel()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
    }
}
