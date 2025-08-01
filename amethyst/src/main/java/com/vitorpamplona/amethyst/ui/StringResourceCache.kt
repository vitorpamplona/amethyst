/**
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Cache for stringResource because it seems to be > 1ms function in some phones
 */
private val resourceCache = LruCache<Int, String>(300)
private var resourceCacheLanguage: String? = null

// Caches most common icons in the app to avoid using disk
private val iconCache = LruCache<Int, LruCache<Int, Painter>>(30)

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
    val cached = iconCache.get(resourceId)
    if (cached != null) {
        val composition = cached.get(sizeReference)
        if (composition != null) {
            return composition
        }
    }

    val loaded = painterResource(resourceId)

    if (cached == null) {
        iconCache.put(resourceId, LruCache<Int, Painter>(10))
    } else {
        cached.put(sizeReference, loaded)
    }

    return loaded
}
