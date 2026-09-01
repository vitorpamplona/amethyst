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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * **Measurement probe — not a feature.**
 *
 * A feed card subscribes to dozens of `LocalCache` flows, and every subscription costs a
 * `collectAsStateWithLifecycle`: a `LifecycleEventObserver` allocated and registered, plus a
 * coroutine launched to run `repeatOnLifecycle`. With ~30 of those per note and ~13 new notes
 * composed per scroll, the question is how much of the card's composition time is the
 * *subscription machinery* rather than the UI it feeds.
 *
 * When `PROBE_NO_FLOW_STATE` is on, these return a plain snapshot state holding exactly the
 * value the real collector would have shown at first composition — so the card renders the
 * same pixels on the way in — and simply never subscribe. The delta between a probe build and
 * a normal one is the cost of flow→state creation.
 *
 * A probe build is **broken on purpose**: nothing live updates any more (no incoming reaction,
 * zap, boost or reply count ever moves). It exists to be measured and thrown away. The flag
 * defaults to `false`, so ordinary builds are byte-for-byte unaffected — the constant folds and
 * R8 drops the branch.
 *
 * Enable with:
 * ```
 * ./gradlew … -PprobeNoFlowState=true
 * ```
 */
@Composable
fun <T> StateFlow<T>.collectAsStateProbed(): State<T> =
    if (BuildConfig.PROBE_NO_FLOW_STATE) {
        remember(this) { mutableStateOf(value) }
    } else {
        collectAsStateWithLifecycle()
    }

@Composable
fun <T> Flow<T>.collectAsStateProbed(initial: T): State<T> =
    if (BuildConfig.PROBE_NO_FLOW_STATE) {
        remember(this) { mutableStateOf(initial) }
    } else {
        collectAsStateWithLifecycle(initial)
    }
