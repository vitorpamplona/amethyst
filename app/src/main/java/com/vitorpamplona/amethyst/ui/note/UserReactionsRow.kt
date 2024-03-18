/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.note

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.chart.composed.ComposedChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.composed.plus
import com.patrykandpatrick.vico.core.entry.entryOf
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import com.vitorpamplona.amethyst.ui.screen.loggedIn.showAmountAxis
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.RoyalBlue
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.RepostEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UserReactionsRow(
    model: UserReactionsViewModel,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(10.dp),
    ) {
        Row(verticalAlignment = CenterVertically, modifier = Modifier.width(68.dp)) {
            Text(
                text = stringResource(id = R.string.today),
                fontWeight = FontWeight.Bold,
            )

            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            UserReplyModel(model)
        }

        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            UserBoostModel(model)
        }

        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            UserReactionModel(model)
        }

        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            UserZapModel(model)
        }
    }
}

@Composable
private fun UserZapModel(model: UserReactionsViewModel) {
    Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = stringResource(R.string.zaps),
        modifier = Size24Modifier,
        tint = BitcoinOrange,
    )

    Spacer(modifier = Modifier.width(8.dp))

    UserZapReaction(model)
}

@Composable
private fun UserReactionModel(model: UserReactionsViewModel) {
    Icon(
        painter = painterResource(R.drawable.ic_liked),
        null,
        modifier = Size20Modifier,
        tint = Color.Unspecified,
    )

    Spacer(modifier = StdHorzSpacer)

    UserLikeReaction(model)
}

@Composable
private fun UserBoostModel(model: UserReactionsViewModel) {
    Icon(
        painter = painterResource(R.drawable.ic_retweeted),
        null,
        modifier = Size24Modifier,
        tint = Color.Unspecified,
    )

    Spacer(modifier = StdHorzSpacer)

    UserBoostReaction(model)
}

@Composable
private fun UserReplyModel(model: UserReactionsViewModel) {
    Icon(
        painter = painterResource(R.drawable.ic_comment),
        null,
        modifier = Size20Modifier,
        tint = RoyalBlue,
    )

    Spacer(modifier = StdHorzSpacer)

    UserReplyReaction(model)
}

@Stable
class UserReactionsViewModel(val account: Account) : ViewModel() {
    val user: User = account.userProfile()

    private var _reactions = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var _boosts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var _zaps = MutableStateFlow<Map<String, BigDecimal>>(emptyMap())
    private var _replies = MutableStateFlow<Map<String, Int>>(emptyMap())

    private var _chartModel = MutableStateFlow<ComposedChartEntryModel<ChartEntryModel>?>(null)
    private var _axisLabels = MutableStateFlow<List<String>>(emptyList())

    val reactions = _reactions.asStateFlow()
    val boosts = _boosts.asStateFlow()
    val zaps = _zaps.asStateFlow()
    val replies = _replies.asStateFlow()

    val chartModel = _chartModel.asStateFlow()
    val axisLabels = _axisLabels.asStateFlow()

    private var takenIntoAccount = setOf<HexKey>()
    private val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd") // SimpleDateFormat()

    val todaysReplyCount = _replies.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysBoostCount = _boosts.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysReactionCount = _reactions.map { showCount(it[today()]) }.distinctUntilChanged()
    val todaysZapAmount = _zaps.map { showAmountAxis(it[today()]) }.distinctUntilChanged()

    var shouldShowDecimalsInAxis = false

    fun formatDate(createAt: Long): String {
        return sdf.format(
            Instant.ofEpochSecond(createAt).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        )
    }

    fun today() = sdf.format(LocalDateTime.now())

    private suspend fun initializeSuspend() {
        checkNotInMainThread()

        val currentUser = user.pubkeyHex

        val reactions = mutableMapOf<String, Int>()
        val boosts = mutableMapOf<String, Int>()
        val zaps = mutableMapOf<String, BigDecimal>()
        val replies = mutableMapOf<String, Int>()
        val takenIntoAccount = mutableSetOf<HexKey>()

        LocalCache.notes.forEach { _, it ->
            val noteEvent = it.event
            if (noteEvent != null && !takenIntoAccount.contains(noteEvent.id())) {
                if (noteEvent is ReactionEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        reactions[netDate] = (reactions[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id())
                    }
                } else if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey() != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt())
                        boosts[netDate] = (boosts[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id())
                    }
                } else if (noteEvent is LnZapEvent) {
                    if (
                        noteEvent.isTaggedUser(currentUser)
                    ) { // the user might be sending his own receipts noteEvent.pubKey != currentUser
                        val netDate = formatDate(noteEvent.createdAt)
                        zaps[netDate] =
                            (zaps[netDate] ?: BigDecimal.ZERO) + (noteEvent.amount ?: BigDecimal.ZERO)
                        takenIntoAccount.add(noteEvent.id())
                    }
                } else if (noteEvent is BaseTextNoteEvent) {
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
                        takenIntoAccount.add(noteEvent.id())
                    }
                }
            }
        }

        this.takenIntoAccount = takenIntoAccount
        this._reactions.emit(reactions)
        this._replies.emit(replies)
        this._zaps.emit(zaps)
        this._boosts.emit(boosts)

        refreshChartModel()
    }

    suspend fun addToStatsSuspend(newBlockNotes: Set<Set<Note>>) {
        checkNotInMainThread()

        val currentUser = user.pubkeyHex

        val reactions = this._reactions.value.toMutableMap()
        val boosts = this._boosts.value.toMutableMap()
        val zaps = this._zaps.value.toMutableMap()
        val replies = this._replies.value.toMutableMap()
        val takenIntoAccount = this.takenIntoAccount.toMutableSet()
        var hasNewElements = false

        newBlockNotes.forEach { newNotes ->
            newNotes.forEach {
                val noteEvent = it.event
                if (noteEvent != null && !takenIntoAccount.contains(noteEvent.id())) {
                    if (noteEvent is ReactionEvent) {
                        if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                            val netDate = formatDate(noteEvent.createdAt)
                            reactions[netDate] = (reactions[netDate] ?: 0) + 1
                            takenIntoAccount.add(noteEvent.id())
                            hasNewElements = true
                        }
                    } else if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
                        if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey() != currentUser) {
                            val netDate = formatDate(noteEvent.createdAt())
                            boosts[netDate] = (boosts[netDate] ?: 0) + 1
                            takenIntoAccount.add(noteEvent.id())
                            hasNewElements = true
                        }
                    } else if (noteEvent is LnZapEvent) {
                        if (
                            noteEvent.isTaggedUser(currentUser)
                        ) { //  && noteEvent.pubKey != currentUser User might be sending his own receipts
                            val netDate = formatDate(noteEvent.createdAt)
                            zaps[netDate] =
                                (zaps[netDate] ?: BigDecimal.ZERO) + (noteEvent.amount ?: BigDecimal.ZERO)
                            takenIntoAccount.add(noteEvent.id())
                            hasNewElements = true
                        }
                    } else if (noteEvent is BaseTextNoteEvent) {
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
                            takenIntoAccount.add(noteEvent.id())
                            hasNewElements = true
                        }
                    }
                }
            }
        }

        if (hasNewElements) {
            this.takenIntoAccount = takenIntoAccount
            this._reactions.emit(reactions)
            this._replies.emit(replies)
            this._zaps.emit(zaps)
            this._boosts.emit(boosts)

            refreshChartModel()
        }
    }

    private suspend fun refreshChartModel() {
        checkNotInMainThread()

        val day = 24 * 60 * 60L
        val now = LocalDateTime.now()
        val displayAxisFormatter = DateTimeFormatter.ofPattern("EEE")

        val dataAxisLabels = listOf(6, 5, 4, 3, 2, 1, 0).map { sdf.format(now.minusSeconds(day * it)) }

        val listOfCountCurves =
            listOf(
                dataAxisLabels.mapIndexed { index, dateStr ->
                    entryOf(index, _replies.value[dateStr]?.toFloat() ?: 0f)
                },
                dataAxisLabels.mapIndexed { index, dateStr ->
                    entryOf(index, _boosts.value[dateStr]?.toFloat() ?: 0f)
                },
                dataAxisLabels.mapIndexed { index, dateStr ->
                    entryOf(index, _reactions.value[dateStr]?.toFloat() ?: 0f)
                },
            )

        val listOfValueCurves =
            listOf(
                dataAxisLabels.mapIndexed { index, dateStr ->
                    entryOf(index, _zaps.value[dateStr]?.toFloat() ?: 0f)
                },
            )

        val chartEntryModelProducer1 = ChartEntryModelProducer(listOfCountCurves).getModel()
        val chartEntryModelProducer2 = ChartEntryModelProducer(listOfValueCurves).getModel()

        chartEntryModelProducer1?.let { chart1 ->
            chartEntryModelProducer2?.let { chart2 ->
                this.shouldShowDecimalsInAxis = shouldShowDecimals(chart2.minY, chart2.maxY)

                this._axisLabels.emit(
                    listOf(6, 5, 4, 3, 2, 1, 0).map {
                        displayAxisFormatter.format(now.minusSeconds(day * it))
                    },
                )
                this._chartModel.emit(chart1.plus(chart2))
            }
        }
    }

    // determine if the min max are so close that they render to the same number.
    fun shouldShowDecimals(
        min: Float,
        max: Float,
    ): Boolean {
        val step = (max - min) / 8

        var previous = showAmountAxis(min.toBigDecimal())
        for (i in 1..7) {
            val current = showAmountAxis((min + (i * step)).toBigDecimal())
            if (previous == current) {
                return true
            }
            previous = current
        }

        return false
    }

    var collectorJob: Job? = null

    init {
        Log.d("Init", "User Reactions Row")
        viewModelScope.launch(Dispatchers.IO) {
            initializeSuspend()

            collectorJob =
                viewModelScope.launch(Dispatchers.IO) {
                    LocalCache.live.newEventBundles.collect { newNotes ->
                        checkNotInMainThread()

                        invalidateInsertData(newNotes)
                    }
                }
        }
    }

    private val bundlerInsert = BundledInsert<Set<Note>>(250, Dispatchers.IO)

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) { addToStatsSuspend(it) }
    }

    override fun onCleared() {
        collectorJob?.cancel()
        bundlerInsert.cancel()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        super.onCleared()
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <UserReactionsViewModel : ViewModel> create(modelClass: Class<UserReactionsViewModel>): UserReactionsViewModel {
            return UserReactionsViewModel(account) as UserReactionsViewModel
        }
    }
}

@Composable
fun UserReplyReaction(model: UserReactionsViewModel) {
    val showCounts by model.todaysReplyCount.collectAsStateWithLifecycle("")

    Text(
        showCounts,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
}

@Composable
fun UserBoostReaction(model: UserReactionsViewModel) {
    val boosts by model.todaysBoostCount.collectAsStateWithLifecycle("")

    Text(
        boosts,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
}

@Composable
fun UserLikeReaction(model: UserReactionsViewModel) {
    val reactions by model.todaysReactionCount.collectAsStateWithLifecycle("")

    Text(
        text = reactions,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
}

@Composable
fun UserZapReaction(model: UserReactionsViewModel) {
    val amount by model.todaysZapAmount.collectAsStateWithLifecycle("")
    Text(
        amount,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
}
