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
package com.vitorpamplona.amethyst.commons.platform

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Resolves Compose Multiplatform string resources outside of @Composable scope.
 *
 * Compose MP's [stringResource] is @Composable-only. This object wraps the
 * suspend [getString] function via [runBlocking] so callers in ViewModels,
 * services, and other non-composable code can resolve strings synchronously.
 *
 * Usage:
 * ```
 * val label = StringResolver.resolve(Res.string.some_key)
 * val formatted = StringResolver.resolve(Res.string.some_key, count)
 * ```
 *
 * For coroutine-aware callers, prefer [resolveAsync] to avoid blocking.
 */
object StringResolver {
    /**
     * Resolves a string resource synchronously (blocking).
     * Safe to call from any thread, but prefer [resolveAsync] inside coroutines.
     */
    fun resolve(resource: StringResource): String = runBlocking { getString(resource) }

    /**
     * Resolves a formatted string resource synchronously (blocking).
     */
    fun resolve(
        resource: StringResource,
        vararg args: Any,
    ): String = runBlocking { getString(resource, *args) }

    /**
     * Resolves a string resource as a suspend function.
     * Prefer this inside coroutine scopes to avoid blocking a thread.
     */
    suspend fun resolveAsync(resource: StringResource): String = getString(resource)

    /**
     * Resolves a formatted string resource as a suspend function.
     */
    suspend fun resolveAsync(
        resource: StringResource,
        vararg args: Any,
    ): String = getString(resource, *args)
}
