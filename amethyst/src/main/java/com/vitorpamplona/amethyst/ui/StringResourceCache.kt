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

import android.content.Context
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes as commonsPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes as commonsStringRes

/**
 * Cache for stringResource because it seems to be > 1ms function in some phones
 */
private val resourceCache = LruCache<Int, String>(300)
private var resourceCacheLanguage: String? = null

// Caches most common icons in the app to avoid using disk
private val iconCache = LruCache<Int, LruCache<Int, Painter>>(30)

fun resourceCacheInit() {
    resourceCache
    resourceCacheLanguage
    iconCache
}

fun checkLanguage(currentLanguage: String) {
    if (resourceCacheLanguage == null) {
        resourceCacheLanguage = currentLanguage
    } else {
        if (resourceCacheLanguage != currentLanguage) {
            resourceCacheLanguage = currentLanguage
            resourceCache.evictAll()
        }
    }
}

@Composable
fun StringResSetup() {
    val config = LocalConfiguration.current
    if (!config.locales.isEmpty) {
        val language = config.locales.get(0).language
        LifecycleResumeEffect(language) {
            checkLanguage(language)

            onPauseOrDispose { }
        }
    }
}

@Composable
fun stringRes(id: Int): String = resourceCache.get(id) ?: stringResource(id).also { resourceCache.put(id, it) }

// Overloads for keys already migrated to commons Compose resources
// (com.vitorpamplona.amethyst.commons.resources.Res.string.*). They let a file
// mix migrated and unmigrated keys under this one import — overload resolution
// picks by argument type — so migrating a key is just R.string.x -> Res.string.x.
// When a whole file moves to commons, swap this import for
// com.vitorpamplona.amethyst.commons.ui.stringRes.
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

@Composable
fun stringRes(
    id: Int,
    vararg args: String,
): String =
    String
        .format(
            LocalConfiguration.current.locales.get(0),
            resourceCache.get(id) ?: stringResource(id).also { resourceCache.put(id, it) },
            *args,
        )

@Composable
fun stringRes(
    id: Int,
    vararg args: Int?,
): String =
    String
        .format(
            LocalConfiguration.current.locales.get(0),
            resourceCache.get(id) ?: stringResource(id).also { resourceCache.put(id, it) },
            *args,
        )

fun stringRes(
    ctx: Context,
    id: Int,
): String = resourceCache.get(id) ?: ctx.getString(id).also { resourceCache.put(id, it) }

fun stringRes(
    ctx: Context,
    id: Int,
    vararg args: String?,
): String {
    val res = ctx.resources

    return String
        .format(
            res.configuration.locales.get(0),
            resourceCache.get(id) ?: res.getString(id).also { resourceCache.put(id, it) },
            *args,
        )
}

fun stringRes(
    ctx: Context,
    id: Int,
    vararg args: Int?,
): String {
    val res = ctx.resources

    return String
        .format(
            res.configuration.locales.get(0),
            resourceCache.get(id) ?: res.getString(id).also { resourceCache.put(id, it) },
            *args,
        )
}

// Plural resolver for non-composable scope (e.g. onClick callbacks). Not cached:
// the resolved string varies by `count` quantity and the resourceCache is keyed by id.
fun pluralStringRes(
    ctx: Context,
    @PluralsRes id: Int,
    count: Int,
    vararg formatArgs: Any?,
): String = ctx.resources.getQuantityString(id, count, *formatArgs)

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
