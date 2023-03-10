package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.BadgeAwardEvent
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class NotificationViewModel : CardFeedViewModel(NotificationFeedFilter)

open class CardFeedViewModel(val dataSource: FeedFilter<Note>) : ViewModel() {
    private val _feedContent = MutableStateFlow<CardFeedState>(CardFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    private var lastNotes: List<Note>? = null

    fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    @Synchronized
    private fun refreshSuspended() {
        val notes = dataSource.loadTop()

        val lastNotesCopy = lastNotes

        val oldNotesState = feedContent.value
        if (lastNotesCopy != null && oldNotesState is CardFeedState.Loaded) {
            val newCards = convertToCard(notes.minus(lastNotesCopy))
            if (newCards.isNotEmpty()) {
                lastNotes = notes
                updateFeed((oldNotesState.feed.value + newCards).distinctBy { it.id() }.sortedBy { it.createdAt() }.reversed())
            }
        } else {
            val cards = convertToCard(notes)
            lastNotes = notes
            updateFeed(cards)
        }
    }

    private fun convertToCard(notes: List<Note>): List<Card> {
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

        val zapsPerEvent = mutableMapOf<Note, MutableMap<Note, Note>>()
        notes
            .filter { it.event is LnZapEvent }
            .forEach { zapEvent ->
                val zappedPost = zapEvent.replyTo?.lastOrNull() { it.event !is ChannelMetadataEvent && it.event !is ChannelCreateEvent }
                if (zappedPost != null) {
                    val zapRequest = zappedPost.zaps.filter { it.value == zapEvent }.keys.firstOrNull()
                    if (zapRequest != null) {
                        zapsPerEvent.getOrPut(zappedPost, { mutableMapOf() }).put(zapRequest, zapEvent)
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

        // val boostCards = boostsPerEvent.map { BoostSetCard(it.key, it.value) }

        val allBaseNotes = zapsPerEvent.keys + boostsPerEvent.keys + reactionsPerEvent.keys
        val multiCards = allBaseNotes.map {
            MultiSetCard(
                it,
                boostsPerEvent.get(it) ?: emptyList(),
                reactionsPerEvent.get(it) ?: emptyList(),
                zapsPerEvent.get(it) ?: emptyMap()
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

        return (multiCards + textNoteCards).sortedBy { it.createdAt() }.reversed()
    }

    private fun updateFeed(notes: List<Card>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = feedContent.value

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

    var handlerWaiting = AtomicBoolean()

    fun invalidateData() {
        if (handlerWaiting.getAndSet(true)) return

        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            try {
                delay(150)
                refresh()
            } finally {
                withContext(NonCancellable) {
                    handlerWaiting.set(false)
                }
            }
        }
    }

    private val cacheListener: (LocalCacheState) -> Unit = {
        invalidateData()
    }

    init {
        LocalCache.live.observeForever(cacheListener)
    }

    override fun onCleared() {
        LocalCache.live.removeObserver(cacheListener)
        super.onCleared()
    }
}
