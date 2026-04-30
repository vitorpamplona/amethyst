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
package com.vitorpamplona.quartz.nip01Core.cache.projection

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Lifecycle state of an [ObservableEventStore.project] flow.
 *
 *  - [Loading] is the initial state before the seed query completes.
 *    The UI should show a spinner / skeleton here.
 *  - [Loaded] holds the current deduped, sorted list of slots. Every
 *    membership change publishes a fresh [Loaded] instance; in-place
 *    addressable updates do *not* publish (the slots' own flows do).
 */
sealed interface ProjectionState<out T : Event> {
    data object Loading : ProjectionState<Nothing>

    data class Loaded<T : Event>(
        val items: List<MutableStateFlow<T>>,
    ) : ProjectionState<T>
}

inline fun <T : Event> ProjectionState<T>.filterItems(predicate: (MutableStateFlow<T>) -> Boolean): ProjectionState<T> =
    when (this) {
        is ProjectionState.Loading -> this
        is ProjectionState.Loaded -> ProjectionState.Loaded(items.filter(predicate))
    }

inline fun <T : Event, R : Event> ProjectionState<T>.mapItems(transform: (MutableStateFlow<T>) -> MutableStateFlow<R>): ProjectionState<R> =
    when (this) {
        is ProjectionState.Loading -> ProjectionState.Loading
        is ProjectionState.Loaded -> ProjectionState.Loaded(items.map(transform))
    }

inline fun <T : Event> Flow<ProjectionState<T>>.filterItems(crossinline predicate: (MutableStateFlow<T>) -> Boolean): Flow<ProjectionState<T>> = map { it.filterItems(predicate) }

inline fun <T : Event> Flow<ProjectionState<T>>.mapItems(crossinline transform: (MutableStateFlow<T>) -> MutableStateFlow<T>): Flow<ProjectionState<T>> = map { it.mapItems(transform) }
