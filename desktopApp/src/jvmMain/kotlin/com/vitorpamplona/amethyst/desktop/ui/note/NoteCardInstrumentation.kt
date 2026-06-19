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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Stable identifier used by UI tests + benchmark harness to locate
 * [NoteCard] roots in the semantic tree. Production code MUST NOT key
 * behavior off this tag — it exists purely for observation.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 3.1.
 */
const val NOTE_CARD_TEST_TAG: String = "amethyst.desktop.note_card.root"

/**
 * Optional callback fired from [NoteCard]'s `Modifier.onPlaced` site.
 *
 * Production code provides `null` (the default value of
 * [LocalNoteCardInstrumentation]) so the callback site is a single
 * composition-local read + null check — no allocation, no work. Benchmark
 * tests provide a counter that records `t_first_event` / `t_n_events`
 * markers without polling the semantic tree.
 */
fun interface NoteCardInstrumentation {
    fun onPlaced(noteId: String)
}

/**
 * Composition local read by [NoteCard]. Default `null` — no test
 * harness wired, no overhead. Tests override via `CompositionLocalProvider`.
 */
val LocalNoteCardInstrumentation: ProvidableCompositionLocal<NoteCardInstrumentation?> =
    compositionLocalOf { null }
