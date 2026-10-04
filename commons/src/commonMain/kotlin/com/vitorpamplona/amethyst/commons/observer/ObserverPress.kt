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
package com.vitorpamplona.amethyst.commons.observer

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.IPubKeyEntity
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prints one reader's paper in the background and holds the result.
 *
 * Printing takes a while — fourteen ranked desks, the web of trust's reactions
 * to everything they returned, and a byline for every author — so it runs on
 * the account's scope, not a screen's: the reader can leave and the banner
 * reports progress until the paper is ready to open.
 *
 * One press per account. [state] drives the banner and the screen; [edition]
 * keeps the last good paper across a reprint or a failure, so a failed reprint
 * never takes away the paper the reader already has.
 */
class ObserverPress(
    private val reader: HexKey,
    private val pull: ObserverPull,
    private val scope: CoroutineScope,
    /**
     * Hands every event the paper prints — the stories and their authors'
     * profiles — to the app's cache, so each post draws its author's picture
     * and tapping it opens the thread immediately.
     */
    private val onEvents: (List<Event>) -> Unit = {},
    private val now: () -> Long = { TimeUtils.now() },
) {
    enum class Step {
        CHECKING_LENS,
        READING_DESKS,
        READING_SIGNALS,
        READING_BYLINES,
        LAYING_OUT,
    }

    @Immutable
    sealed interface State {
        data object Idle : State

        data class Printing(
            val step: Step,
            val desksDone: Int = 0,
            val desksTotal: Int = ObserverDesk.entries.size,
            val startedAt: Long,
        ) : State {
            /** A predictable bar: the desks are most of the wait, the rest is a few quick reads. */
            val fraction: Float
                get() =
                    when (step) {
                        Step.CHECKING_LENS -> 0.05f
                        Step.READING_DESKS -> 0.05f + 0.6f * desksDone / desksTotal.coerceAtLeast(1)
                        Step.READING_SIGNALS -> 0.7f
                        Step.READING_BYLINES -> 0.85f
                        Step.LAYING_OUT -> 0.95f
                    }
        }

        /** The lens cannot rank yet. No paper is printed some other way: see [ObserverReadiness]. */
        data class NoLens(
            val reason: ObserverReadiness.State,
        ) : State

        data class Ready(
            val printedAt: Long,
            /** False until the reader opens the paper; the banner offers it until then. */
            val seen: Boolean,
        ) : State

        data class Failed(
            val message: String?,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _edition = MutableStateFlow<ObserverEdition?>(null)
    val edition: StateFlow<ObserverEdition?> = _edition.asStateFlow()

    /**
     * True while the paper screen is open. The screen shows progress inline, so
     * the floating banner stays out of the way, and a paper finished in front
     * of the reader counts as seen at once.
     */
    val readerIsLooking = MutableStateFlow(false)

    private var job: Job? = null

    val isPrinting: Boolean get() = _state.value is State.Printing

    /** Starts a fresh edition for the last 24 hours. A press already running is left alone. */
    fun print() {
        if (job?.isActive == true) return
        job =
            scope.launch(Dispatchers.IO) {
                val startedAt = now()
                try {
                    _state.value = State.Printing(Step.CHECKING_LENS, startedAt = startedAt)
                    val until = now()
                    val since = until - WINDOW_SECONDS

                    val readiness = ObserverReadiness.assess(pull.readiness(reader, since))
                    if (readiness != ObserverReadiness.State.READY) {
                        _state.value = State.NoLens(readiness)
                        return@launch
                    }

                    _state.value = State.Printing(Step.READING_DESKS, startedAt = startedAt)
                    val corpus =
                        pull.corpus(reader, since, until) { done, total ->
                            _state.update { current ->
                                if (current is State.Printing) current.copy(desksDone = done, desksTotal = total) else current
                            }
                        }

                    _state.value = State.Printing(Step.READING_SIGNALS, startedAt = startedAt)
                    val stories = corpus.all()
                    val engagement = pull.engagement(reader, since, until, stories)

                    _state.value = State.Printing(Step.READING_BYLINES, startedAt = startedAt)
                    val profiles = pull.profiles(bylinesFor(reader, stories))
                    val names = ObserverPull.displayNames(profiles)

                    _state.value = State.Printing(Step.LAYING_OUT, startedAt = startedAt)
                    val edition =
                        ObserverEditor.edit(
                            ObserverCorpus(corpus.reader, since, until, corpus.ranked, engagement, names, corpus.dayNotes),
                        )

                    onEvents(stories + profiles.values)
                    _edition.value = edition
                    _state.value = State.Ready(printedAt = now(), seen = readerIsLooking.value)
                } catch (e: CancellationException) {
                    _state.value = State.Idle
                    throw e
                } catch (e: Exception) {
                    Log.w("ObserverPress", "Could not print the paper", e)
                    _state.value = State.Failed(e.message)
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    /** The reader opened the paper: stop offering it in the banner. */
    fun markSeen() {
        _state.update { if (it is State.Ready && !it.seen) it.copy(seen = true) else it }
    }

    /** Clears a finished, failed or refused run from the banner. The last edition stays. */
    fun dismiss() {
        _state.update {
            when (it) {
                is State.Ready -> it.copy(seen = true)
                is State.Failed, is State.NoLens -> State.Idle
                else -> it
            }
        }
    }

    companion object {
        /** Fixed, not "since last visit": the Observer's settled decision. */
        const val WINDOW_SECONDS = 24 * 60 * 60L

        /**
         * Everyone the page will name: every author, everyone a highlight
         * quotes (they wrote the sentence but signed nothing in the window), and
         * the reader, whose posts are excluded from every desk and so would
         * otherwise never have their name looked up.
         */
        internal fun bylinesFor(
            reader: HexKey,
            stories: List<Event>,
        ): Set<HexKey> =
            buildSet {
                add(reader)
                stories.forEach { event ->
                    add(event.pubKey)
                    if (event.kind == 9802) event.tagValue("p")?.takeIf { it.length == 64 }?.let(::add)
                    // `nostr:npub…` mentions, so the page prints `@name` rather than a key.
                    Nip19Parser.nip19regex.findAll(event.content).forEach { match ->
                        (Nip19Parser.uriToRoute(match.value)?.entity as? IPubKeyEntity)?.let { add(it.hex) }
                    }
                }
            }
    }
}
