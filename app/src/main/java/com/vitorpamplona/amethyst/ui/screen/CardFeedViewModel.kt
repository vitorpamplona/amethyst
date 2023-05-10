package com.vitorpamplona.amethyst.ui.screen

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.BadgeAwardEvent
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class NotificationViewModel : CardFeedViewModel(NotificationFeedFilter)

open class CardFeedViewModel(val localFilter: FeedFilter<Note>) : ViewModel() {
    private val _feedContent = MutableStateFlow<CardFeedState>(CardFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    private var lastAccount: Account? = null
    private var lastNotes: List<Note>? = null

    fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    @Synchronized
    private fun refreshSuspended() {
        val notes = localFilter.loadTop()

        val thisAccount = (localFilter as? NotificationFeedFilter)?.account
        val lastNotesCopy = if (thisAccount == lastAccount) lastNotes else null

        val oldNotesState = _feedContent.value
        if (lastNotesCopy != null && oldNotesState is CardFeedState.Loaded) {
            val newCards = convertToCard(notes.minus(lastNotesCopy))
            if (newCards.isNotEmpty()) {
                lastNotes = notes
                lastAccount = (localFilter as? NotificationFeedFilter)?.account
                updateFeed((oldNotesState.feed.value + newCards).distinctBy { it.id() }.sortedBy { it.createdAt() }.reversed())
            }
        } else {
            val cards = convertToCard(notes)
            lastNotes = notes
            lastAccount = (localFilter as? NotificationFeedFilter)?.account
            updateFeed(cards)
        }
    }

    private fun convertToCard(notes: Collection<Note>): List<Card> {
        val reactionsPerEvent = mutableMapOf<Note, MutableList<Note>>()
        notes
            .filter { it.event is ReactionEvent }
            .forEach {
                val reactedPost = it.replyTo?.lastOrNull() { it.event !is ChannelMetadataEvent && it.event !is ChannelCreateEvent }
                if (reactedPost != null) {
                    reactionsPerEvent.getOrPut(reactedPost, { mutableListOf() }).add(it)
                }
            }

        // val reactionCards = reactionsPerEvent.map { LikeSetCard(it.key, it.value) }
        val zapsPerUser = mutableMapOf<User, MutableMap<Note, Note>>()
        val zapsPerEvent = mutableMapOf<Note, MutableMap<Note, Note>>()
        notes
            .filter { it.event is LnZapEvent }
            .forEach { zapEvent ->
                val zappedPost = zapEvent.replyTo?.lastOrNull() { it.event !is ChannelMetadataEvent && it.event !is ChannelCreateEvent }
                if (zappedPost != null) {
                    val zapRequest = zappedPost.zaps.filter { it.value == zapEvent }.keys.firstOrNull()
                    if (zapRequest != null) {
                        // var newZapRequestEvent = LocalCache.checkPrivateZap(zapRequest.event as Event)
                        // zapRequest.event = newZapRequestEvent
                        zapsPerEvent.getOrPut(zappedPost, { mutableMapOf() }).put(zapRequest, zapEvent)
                    }
                } else {
                    val event = (zapEvent.event as LnZapEvent)
                    val author = event.zappedAuthor().mapNotNull {
                        LocalCache.checkGetOrCreateUser(
                            it
                        )
                    }.firstOrNull()
                    if (author != null) {
                        val zapRequest = author.zaps.filter { it.value == zapEvent }.keys.firstOrNull()
                        if (zapRequest != null) {
                            zapsPerUser.getOrPut(author, { mutableMapOf() })
                                .put(zapRequest, zapEvent)
                        }
                    }
                }
            }

        // val zapCards = zapsPerEvent.map { ZapSetCard(it.key, it.value) }

        val boostsPerEvent = mutableMapOf<Note, MutableList<Note>>()
        notes
            .filter { it.event is RepostEvent }
            .forEach {
                val boostedPost = it.replyTo?.lastOrNull() { it.event !is ChannelMetadataEvent && it.event !is ChannelCreateEvent }
                if (boostedPost != null) {
                    boostsPerEvent.getOrPut(boostedPost, { mutableListOf() }).add(it)
                }
            }

        val allBaseNotes = zapsPerEvent.keys + boostsPerEvent.keys + reactionsPerEvent.keys
        val multiCards = allBaseNotes.map { baseNote ->
            val boostsInCard = boostsPerEvent[baseNote] ?: emptyList()
            val reactionsInCard = reactionsPerEvent[baseNote] ?: emptyList()
            val zapsInCard = zapsPerEvent[baseNote] ?: emptyMap()

            val singleList = (boostsInCard + zapsInCard.values + reactionsInCard).sortedBy { it.createdAt() }.reversed()
            singleList.chunked(50).map { chunk ->
                MultiSetCard(
                    baseNote,
                    boostsInCard.filter { it in chunk },
                    reactionsInCard.filter { it in chunk },
                    zapsInCard.filter { it.value in chunk }
                )
            }
        }.flatten()

        val userZaps = zapsPerUser.map {
            ZapUserSetCard(
                it.key,
                it.value
            )
        }

        val textNoteCards = notes.filter { it.event !is ReactionEvent && it.event !is RepostEvent && it.event !is LnZapEvent }.map {
            if (it.event is PrivateDmEvent) {
                MessageSetCard(it)
            } else if (it.event is BadgeAwardEvent) {
                BadgeCard(it)
            } else {
                NoteCard(it)
            }
        }

        return (multiCards + textNoteCards + userZaps).sortedBy { it.createdAt() }.reversed()
    }

    private fun updateFeed(notes: List<Card>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = _feedContent.value

            if (notes.isEmpty()) {
                _feedContent.update { CardFeedState.Empty }
            } else if (currentState is CardFeedState.Loaded) {
                // updates the current list
                currentState.feed.value = notes
            } else {
                _feedContent.update { CardFeedState.Loaded(mutableStateOf(notes)) }
            }
        }
    }

    private fun refreshFromOldState(newItems: Set<Note>) {
        val oldNotesState = _feedContent.value

        val thisAccount = (localFilter as? NotificationFeedFilter)?.account
        val lastNotesCopy = if (thisAccount == lastAccount) lastNotes else null

        if (lastNotesCopy != null && localFilter is AdditiveFeedFilter && oldNotesState is CardFeedState.Loaded) {
            val filteredNewList = localFilter.applyFilter(newItems)
            val actuallyNew = filteredNewList.minus(lastNotesCopy)

            val newCards = convertToCard(actuallyNew)
            if (newCards.isNotEmpty()) {
                lastNotes = lastNotesCopy + newItems
                lastAccount = (localFilter as? NotificationFeedFilter)?.account
                updateFeed((oldNotesState.feed.value + newCards).distinctBy { it.id() }.sortedBy { it.createdAt() }.reversed())
            }
        } else {
            // Refresh Everything
            refreshSuspended()
        }
    }

    @OptIn(ExperimentalTime::class)
    private val bundler = BundledUpdate(250, Dispatchers.IO) {
        // adds the time to perform the refresh into this delay
        // holding off new updates in case of heavy refresh routines.
        val (value, elapsed) = measureTimedValue {
            refreshSuspended()
        }
        Log.d("Time", "${this.javaClass.simpleName} Card update $elapsed")
    }
    private val bundlerInsert = BundledInsert<Set<Note>>(250, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate()
    }

    @OptIn(ExperimentalTime::class)
    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) {
            val (value, elapsed) = measureTimedValue {
                refreshFromOldState(it.flatten().toSet())
            }
            Log.d("Time", "${this.javaClass.simpleName} Card additive update $elapsed")
        }
    }

    private val cacheListener: (Set<Note>) -> Unit = { newNotes ->
        if (localFilter is AdditiveFeedFilter && _feedContent.value is CardFeedState.Loaded) {
            invalidateInsertData(newNotes)
        } else {
            // Refresh Everything
            invalidateData()
        }
    }

    init {
        LocalCache.live.observeForever(cacheListener)
    }

    fun clear() {
        lastAccount = null
        lastNotes = null
    }

    override fun onCleared() {
        clear()
        LocalCache.live.removeObserver(cacheListener)
        super.onCleared()
    }
}
