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
package androidx.compose.material3.pullrefresh

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.Drag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.platform.inspectable
import androidx.compose.ui.unit.Velocity

/**
 * A nested scroll modifier that provides scroll events to [state].
 *
 * Note that this modifier must be added above a scrolling container, such as a lazy column, in
 * order to receive scroll events. For example:
 *
 * @param state The [PullRefreshState] associated with this pull-to-refresh component. The state
 *   will be updated by this modifier.
 * @param enabled If not enabled, all scroll delta and fling velocity will be ignored.
 * @sample androidx.compose.material.samples.PullRefreshSample
 */
fun Modifier.pullRefresh(
    state: PullRefreshState,
    enabled: Boolean = true,
) = inspectable(
    inspectorInfo =
        debugInspectorInfo {
            name = "pullRefresh"
            properties["state"] = state
            properties["enabled"] = enabled
        },
) {
    Modifier.pullRefresh(state::onPull, state::onRelease, enabled)
}

/**
 * A nested scroll modifier that provides [onPull] and [onRelease] callbacks to aid building custom
 * pull refresh components.
 *
 * Note that this modifier must be added above a scrolling container, such as a lazy column, in
 * order to receive scroll events. For example:
 *
 * @param onPull Callback for dispatching vertical scroll delta, takes float pullDelta as argument.
 *   Positive delta (pulling down) is dispatched only if the child does not consume it (i.e. pulling
 *   down despite being at the top of a scrollable component), whereas negative delta (swiping up)
 *   is dispatched first (in case it is needed to push the indicator back up), and then the
 *   unconsumed delta is passed on to the child. The callback returns how much delta was consumed.
 * @param onRelease Callback for when drag is released, takes float flingVelocity as argument. The
 *   callback returns how much velocity was consumed - in most cases this should only consume
 *   velocity if pull refresh has been dragged already and the velocity is positive (the fling is
 *   downwards), as an upwards fling should typically still scroll a scrollable component beneath
 *   the pullRefresh. This is invoked before any remaining velocity is passed to the child.
 * @param enabled If not enabled, all scroll delta and fling velocity will be ignored and neither
 *   [onPull] nor [onRelease] will be invoked.
 * @sample androidx.compose.material.samples.CustomPullRefreshSample
 */
fun Modifier.pullRefresh(
    onPull: (pullDelta: Float) -> Float,
    onRelease: suspend (flingVelocity: Float) -> Float,
    enabled: Boolean = true,
) = inspectable(
    inspectorInfo =
        debugInspectorInfo {
            name = "pullRefresh"
            properties["onPull"] = onPull
            properties["onRelease"] = onRelease
            properties["enabled"] = enabled
        },
) {
    Modifier.nestedScroll(PullRefreshNestedScrollConnection(onPull, onRelease, enabled))
}

private class PullRefreshNestedScrollConnection(
    private val onPull: (pullDelta: Float) -> Float,
    private val onRelease: suspend (flingVelocity: Float) -> Float,
    private val enabled: Boolean,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        when {
            !enabled -> Offset.Zero
            source == Drag && available.y < 0 -> Offset(0f, onPull(available.y)) // Swiping up
            else -> Offset.Zero
        }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        when {
            !enabled -> Offset.Zero
            source == Drag && available.y > 0 -> Offset(0f, onPull(available.y)) // Pulling down
            else -> Offset.Zero
        }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return Velocity(0f, onRelease(available.y))
    }
}
