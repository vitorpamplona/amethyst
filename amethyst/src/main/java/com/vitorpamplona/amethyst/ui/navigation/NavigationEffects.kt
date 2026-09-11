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
package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vitorpamplona.amethyst.ui.layouts.CappedScreenContent
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

// Per-entry hint stamped by Nav.navBottomBar marking that the entry was
// reached via a bottom-nav tab. Used in two places:
//   - composableFromEnd skips the horizontal slide on tab entries so
//     bottom-bar taps fall back to the NavHost-level fade.
//   - Nav.canPop hides the back arrow on tab roots even though Home
//     sits below them in the stack.
const val BOTTOM_NAV_ROOT_KEY = "bottomNavRoot"

fun NavBackStackEntry.isBottomNavRoot(): Boolean = savedStateHandle.get<Boolean>(BOTTOM_NAV_ROOT_KEY) == true

/**
 * The shell's current layout tier, mirrored for the transition specs below. Transition
 * lambdas run when a navigation starts — outside composition — so they can't read
 * LocalScreenLayout; AppNavigation mirrors the spec here instead.
 *
 * One navigation grammar, tier-scaled motion: phones keep full-width slides (a pushed
 * screen physically stacks on top), while large screens use short shared-axis moves —
 * content there swaps inside a persistent shell, and a full-pane slide from the right
 * reads as disconnected when the click came from the docked drawer on the left.
 */
object NavTransitionTier {
    @Volatile
    var isLargeScreen: Boolean = false
}

/**
 * Applies the wide-pane reading-column cap ([CappedScreenContent]) to a destination unless
 * it opted out with `capWidth = false`. Every builder below routes through this, so all
 * destinations — top bars included — share the centered column on large screens by default.
 */
@Composable
fun MaybeCappedScreen(
    capWidth: Boolean,
    content: @Composable () -> Unit,
) {
    if (capWidth) {
        CappedScreenContent(content)
    } else {
        content()
    }
}

/** Stock fade-transition destination, capped to the reading-column width on wide panes. */
inline fun <reified T : Any> NavGraphBuilder.composableCapped(noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit) {
    composable<T> { entry ->
        CappedScreenContent { content(entry) }
    }
}

/** [composableCapped], for a route that carries arguments. */
inline fun <reified T : Any> NavGraphBuilder.composableCappedArgs(noinline content: @Composable AnimatedContentScope.(T) -> Unit) {
    composable<T> { entry ->
        CappedScreenContent { content(entry.toRoute()) }
    }
}

inline fun <reified T : Any> NavGraphBuilder.composableFromEnd(
    capWidth: Boolean = true,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    PopFamilies.register(T::class, PopFamily.END)
    composable<T>(
        enterTransition = { if (targetState.isBottomNavRoot()) null else enterFromEnd() },
        exitTransition = { if (targetState.isBottomNavRoot()) null else exitBehind() },
        popEnterTransition = { if (initialState.isBottomNavRoot()) null else popEnterFromBehind() },
        popExitTransition = { if (initialState.isBottomNavRoot()) null else popExitToEnd() },
        content = { entry ->
            MaybeCappedScreen(capWidth) { content(entry) }
        },
    )
}

inline fun <reified T : Any> NavGraphBuilder.composableFromEndArgs(
    capWidth: Boolean = true,
    noinline content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    composableFromEnd<T>(capWidth) {
        content(it.toRoute<T>())
    }
}

inline fun <reified T : Any> NavGraphBuilder.composableFromBottom(
    capWidth: Boolean = true,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    PopFamilies.register(T::class, PopFamily.BOTTOM)
    composable<T>(
        enterTransition = { enterFromBottom() },
        exitTransition = { exitBehind() },
        popEnterTransition = { popEnterFromBehind() },
        popExitTransition = { popExitToBottom() },
        content = { entry ->
            MaybeCappedScreen(capWidth) { content(entry) }
        },
    )
}

inline fun <reified T : Any> NavGraphBuilder.composableFromBottomArgs(
    capWidth: Boolean = true,
    noinline content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    composableFromBottom<T>(capWidth) {
        content(it.toRoute())
    }
}

inline fun <reified T : Any> NavGraphBuilder.composableArgs(
    capWidth: Boolean = true,
    noinline content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    composable<T> { entry ->
        MaybeCappedScreen(capWidth) { content(entry.toRoute()) }
    }
}

val slideInVerticallyFromBottom = slideInVertically(animationSpec = tween(), initialOffsetY = { it })
val slideOutVerticallyToBottom = slideOutVertically(animationSpec = tween(), targetOffsetY = { it })

val slideInHorizontallyFromEnd = slideInHorizontally(animationSpec = tween(), initialOffsetX = { it })
val slideOutHorizontallyToEnd = slideOutHorizontally(animationSpec = tween(), targetOffsetX = { it })

val scaleIn = scaleIn(animationSpec = tween(), initialScale = 0.9f)
val scaleOut = scaleOut(animationSpec = tween(), targetScale = 0.9f)

/** Fraction of the pane a shared-axis move travels on large screens — a nudge, not a fly-in. */
private const val SHARED_AXIS_FRACTION = 10

val sharedAxisEnterFromEnd = slideInHorizontally(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeIn(animationSpec = tween())
val sharedAxisExitToEnd = slideOutHorizontally(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeOut(animationSpec = tween())
val sharedAxisEnterFromBottom = slideInVertically(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeIn(animationSpec = tween())
val sharedAxisExitToBottom = slideOutVertically(animationSpec = tween()) { it / SHARED_AXIS_FRACTION } + fadeOut(animationSpec = tween())

// The outgoing/incoming screen *behind* a push: on phones the pushed screen covers it, so a
// slight scale is enough; on large screens the incoming screen fades, so the one behind must
// fade too or both stay visible mid-transition.
val fadeScaleOut = scaleOut + fadeOut(animationSpec = tween())
val fadeScaleIn = scaleIn + fadeIn(animationSpec = tween())

fun enterFromEnd() = if (NavTransitionTier.isLargeScreen) sharedAxisEnterFromEnd else slideInHorizontallyFromEnd

fun popExitToEnd() = if (NavTransitionTier.isLargeScreen) sharedAxisExitToEnd else slideOutHorizontallyToEnd

fun enterFromBottom() = if (NavTransitionTier.isLargeScreen) sharedAxisEnterFromBottom else slideInVerticallyFromBottom

fun popExitToBottom() = if (NavTransitionTier.isLargeScreen) sharedAxisExitToBottom else slideOutVerticallyToBottom

fun exitBehind() = if (NavTransitionTier.isLargeScreen) fadeScaleOut else scaleOut

fun popEnterFromBehind() = if (NavTransitionTier.isLargeScreen) fadeScaleIn else scaleIn

/**
 * The NavHost-level fade both directions of a tab switch fall back to. Shared with
 * [BuildNavigation]'s own enter/exit so the gesture path and the button path cannot drift.
 */
val navShellFadeIn = fadeIn(animationSpec = tween(200))
val navShellFadeOut = fadeOut(animationSpec = tween(200))

/** Which builder a destination was declared with, for [predictivePopEnter] / [predictivePopExit]. */
enum class PopFamily { END, BOTTOM, NONE }

/**
 * Records the family of every destination the builders below declare.
 *
 * navigation-compose 2.10.0 gave a predictive back *gesture* its own transition pair —
 * `predictivePopEnterTransition` / `predictivePopExitTransition` — separate from popEnter/popExit,
 * and its defaults are `fadeIn` opposite `scaleOut(targetScale = 0.7f)`. So a swipe-from-edge back
 * stopped running the slides declared per route and started shrinking the outgoing screen toward
 * its centre, while a back *button* press still slid, because only the gesture takes that branch
 * (NavHost.kt: `if (inPredictiveBack) … else if (composeNavigator.isPop.value) …`).
 *
 * The per-destination overrides are `internal` in that release, so the only public lever is the
 * NavHost-level pair — which is handed the entries but not the builder that declared them. This is
 * how they find out. Registration happens as the graph is built and is idempotent, so recomposing
 * the host re-registers the same values.
 */
object PopFamilies {
    private val families = ConcurrentHashMap<KClass<*>, PopFamily>()

    fun register(
        route: KClass<*>,
        family: PopFamily,
    ) {
        families[route] = family
    }

    fun of(destination: NavDestination): PopFamily = families.entries.firstOrNull { destination.hasRoute(it.key) }?.value ?: PopFamily.NONE
}

/**
 * The gesture twin of the route-level `popEnterTransition`s, reproducing their rules: a tab root
 * revealed behind a pop falls back to the shell fade, anything else grows in from behind.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.predictivePopEnter(): EnterTransition =
    when (PopFamilies.of(targetState.destination)) {
        PopFamily.BOTTOM -> popEnterFromBehind()
        PopFamily.END -> if (initialState.isBottomNavRoot()) navShellFadeIn else popEnterFromBehind()
        PopFamily.NONE -> navShellFadeIn
    }

/**
 * The gesture twin of the route-level `popExitTransition`s: a modal leaves downward, a drill-in
 * leaves toward the end, and a tab entry fades — the same three answers a button back gives.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.predictivePopExit(): ExitTransition =
    when (PopFamilies.of(initialState.destination)) {
        PopFamily.BOTTOM -> popExitToBottom()
        PopFamily.END -> if (initialState.isBottomNavRoot()) navShellFadeOut else popExitToEnd()
        PopFamily.NONE -> navShellFadeOut
    }
