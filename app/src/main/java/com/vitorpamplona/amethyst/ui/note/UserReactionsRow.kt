package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.chart.composed.ComposedChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.composed.plus
import com.patrykandpatrick.vico.core.entry.entryOf
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import com.vitorpamplona.amethyst.ui.screen.loggedIn.showAmountAxis
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.RoyalBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UserReactionsRow(
    model: UserReactionsViewModel,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = CenterVertically, modifier = Modifier.width(68.dp)) {
            Text(
                text = stringResource(id = R.string.today),
                fontWeight = FontWeight.Bold
            )

            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
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
    val zaps by model.zaps.collectAsState()
    UserZapReaction(showAmountAxis(zaps[model.today()]))
}

@Composable
private fun UserReactionModel(model: UserReactionsViewModel) {
    val reactions by model.reactions.collectAsState()
    UserLikeReaction(reactions[model.today()])
}

@Composable
private fun UserBoostModel(model: UserReactionsViewModel) {
    val boosts by model.boosts.collectAsState()
    UserBoostReaction(boosts[model.today()])
}

@Composable
private fun UserReplyModel(model: UserReactionsViewModel) {
    val replies by model.replies.collectAsState()
    UserReplyReaction(replies[model.today()])
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

    fun formatDate(createAt: Long): String {
        return sdf.format(
            Instant.ofEpochSecond(createAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        )
    }

    fun today() = sdf.format(LocalDateTime.now())

    private suspend fun initializeSuspend() {
        val currentUser = user.pubkeyHex

        val reactions = mutableMapOf<String, Int>()
        val boosts = mutableMapOf<String, Int>()
        val zaps = mutableMapOf<String, BigDecimal>()
        val replies = mutableMapOf<String, Int>()
        val takenIntoAccount = mutableSetOf<HexKey>()

        LocalCache.notes.values.forEach {
            val noteEvent = it.event
            if (noteEvent != null && !takenIntoAccount.contains(noteEvent.id())) {
                if (noteEvent is ReactionEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        reactions[netDate] = (reactions[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id())
                    }
                } else if (noteEvent is RepostEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        boosts[netDate] = (boosts[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id())
                    }
                } else if (noteEvent is LnZapEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        zaps[netDate] = (zaps[netDate] ?: BigDecimal.ZERO) + (noteEvent.amount ?: BigDecimal.ZERO)
                        takenIntoAccount.add(noteEvent.id())
                    }
                } else if (noteEvent is TextNoteEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        replies[netDate] = (replies[netDate] ?: 0) + 1
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

    suspend fun addToStatsSuspend(newNotes: Set<Note>) {
        val currentUser = user.pubkeyHex

        val reactions = this._reactions.value.toMutableMap()
        val boosts = this._boosts.value.toMutableMap()
        val zaps = this._zaps.value.toMutableMap()
        val replies = this._replies.value.toMutableMap()
        val takenIntoAccount = this.takenIntoAccount.toMutableSet()
        var hasNewElements = false

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
                } else if (noteEvent is RepostEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        boosts[netDate] = (boosts[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id())
                        hasNewElements = true
                    }
                } else if (noteEvent is LnZapEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        zaps[netDate] = (zaps[netDate] ?: BigDecimal.ZERO) + (noteEvent.amount ?: BigDecimal.ZERO)
                        takenIntoAccount.add(noteEvent.id())
                        hasNewElements = true
                    }
                } else if (noteEvent is TextNoteEvent) {
                    if (noteEvent.isTaggedUser(currentUser) && noteEvent.pubKey != currentUser) {
                        val netDate = formatDate(noteEvent.createdAt)
                        replies[netDate] = (replies[netDate] ?: 0) + 1
                        takenIntoAccount.add(noteEvent.id())
                        hasNewElements = true
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
        val day = 24 * 60 * 60L
        val now = LocalDateTime.now()
        val displayAxisFormatter = DateTimeFormatter.ofPattern("EEE")

        val dataAxisLabels = listOf(6, 5, 4, 3, 2, 1, 0).map { sdf.format(now.minusSeconds(day * it)) }

        val listOfCountCurves = listOf(
            dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, _replies.value[dateStr]?.toFloat() ?: 0f) },
            dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, _boosts.value[dateStr]?.toFloat() ?: 0f) },
            dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, _reactions.value[dateStr]?.toFloat() ?: 0f) }
        )

        val listOfValueCurves = listOf(
            dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, _zaps.value[dateStr]?.toFloat() ?: 0f) }
        )

        val chartEntryModelProducer1 = ChartEntryModelProducer(listOfCountCurves).getModel()
        val chartEntryModelProducer2 = ChartEntryModelProducer(listOfValueCurves).getModel()

        this._axisLabels.emit(listOf(6, 5, 4, 3, 2, 1, 0).map { displayAxisFormatter.format(now.minusSeconds(day * it)) })
        this._chartModel.emit(chartEntryModelProducer1.plus(chartEntryModelProducer2))
    }

    var collectorJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            initializeSuspend()

            collectorJob = viewModelScope.launch(Dispatchers.IO) {
                LocalCache.live.newEventBundles.collect { newNotes ->
                    checkNotInMainThread()

                    invalidateInsertData(newNotes)
                }
            }
        }
    }

    private val bundlerInsert = BundledInsert<Set<Note>>(250, Dispatchers.IO)

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) {
            addToStatsSuspend(it.flatten().toSet())
        }
    }

    override fun onCleared() {
        collectorJob?.cancel()
        super.onCleared()
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <UserReactionsViewModel : ViewModel> create(modelClass: Class<UserReactionsViewModel>): UserReactionsViewModel {
            return UserReactionsViewModel(account) as UserReactionsViewModel
        }
    }
}

@Composable
fun UserReplyReaction(
    replyCount: Int?
) {
    val showCounts = remember(replyCount) { showCount(replyCount) }

    Icon(
        painter = painterResource(R.drawable.ic_comment),
        null,
        modifier = Modifier.size(20.dp),
        tint = RoyalBlue
    )

    Spacer(modifier = Modifier.width(10.dp))

    Text(
        showCounts,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

@Composable
fun UserBoostReaction(
    boostCount: Int?
) {
    val showCounts = remember(boostCount) { showCount(boostCount) }

    Icon(
        painter = painterResource(R.drawable.ic_retweeted),
        null,
        modifier = Modifier.size(24.dp),
        tint = Color.Unspecified
    )

    Spacer(modifier = Modifier.width(10.dp))

    Text(
        showCounts,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

@Composable
fun UserLikeReaction(
    likeCount: Int?
) {
    val showCounts = remember(likeCount) { showCount(likeCount) }

    Icon(
        painter = painterResource(R.drawable.ic_liked),
        null,
        modifier = Modifier.size(20.dp),
        tint = Color.Unspecified
    )

    Spacer(modifier = Modifier.width(10.dp))

    Text(
        showCounts,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

@Composable
fun UserZapReaction(
    amount: String
) {
    Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = stringResource(R.string.zaps),
        modifier = Modifier.size(24.dp),
        tint = BitcoinOrange
    )

    Spacer(modifier = Modifier.width(8.dp))

    Text(
        amount,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}
