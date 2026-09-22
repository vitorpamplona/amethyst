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
package com.vitorpamplona.amethyst.ui

import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes as commonsPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes as commonsStringRes

// Every user-facing string now lives in the commons Compose catalog
// (com.vitorpamplona.amethyst.commons.resources.Res.string.*), so these are thin
// aliases over the commons bridge. They exist only so the ~500 files that import
// `com.vitorpamplona.amethyst.ui.stringRes` did not all have to change their import
// in the same commit; a file moving to commons swaps this import for
// `com.vitorpamplona.amethyst.commons.ui.stringRes` and nothing else.
//
// The Android `R.string` overloads that used to live here (and the LruCache behind
// them, which existed because Resources.getString measured >1ms on some phones) are
// gone with the last R.string reference. compose-resources parses each locale file
// once into a process-wide cache, so repeat lookups are map hits.

@Composable
fun stringRes(id: StringResource): String = commonsStringRes(id)

@Composable
fun stringRes(
    id: StringResource,
    vararg args: Any,
): String = commonsStringRes(id, *args)

@Composable
fun pluralStringRes(
    id: PluralStringResource,
    count: Int,
    vararg args: Any,
): String = if (args.isEmpty()) commonsPluralStringRes(id, count) else commonsPluralStringRes(id, count, *args)

// Caches most common icons in the app to avoid using disk. Drawables are still
// Android resources - only strings moved to the Compose catalog.
private val iconCache = LruCache<Int, LruCache<Int, Painter>>(30)

fun resourceCacheInit() {
    iconCache
}

/**
 * This cache can only be used if the painter is the only copy on the screen
 * It should store a separate Painter for each size. It's safe to just assume
 * Different compositions use different sizes.
 */
@Composable
fun painterRes(
    @DrawableRes resourceId: Int,
    sizeReference: Int,
): Painter {
    val bySize = iconCache.get(resourceId)
    bySize?.get(sizeReference)?.let { return it }

    val loaded = painterResource(resourceId)

    // Store on the FIRST miss as well. This previously created the per-size cache but never
    // put `loaded` into it, so a resource had to be requested three times before it could
    // ever hit: once to install the (empty) inner cache, once to populate it, once to read it.
    val sizes = bySize ?: LruCache<Int, Painter>(10).also { iconCache.put(resourceId, it) }
    sizes.put(sizeReference, loaded)

    return loaded
}
