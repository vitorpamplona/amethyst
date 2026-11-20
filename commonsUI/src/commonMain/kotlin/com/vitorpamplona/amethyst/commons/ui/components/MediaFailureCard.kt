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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.network.HttpException
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_failed_gone_detail
import com.vitorpamplona.amethyst.commons.resources.media_failed_gone_gif
import com.vitorpamplona.amethyst.commons.resources.media_failed_gone_image
import com.vitorpamplona.amethyst.commons.resources.media_failed_not_media_gif
import com.vitorpamplona.amethyst.commons.resources.media_failed_not_media_image
import com.vitorpamplona.amethyst.commons.resources.media_failed_open_in_browser
import com.vitorpamplona.amethyst.commons.resources.media_failed_payment
import com.vitorpamplona.amethyst.commons.resources.media_failed_refused
import com.vitorpamplona.amethyst.commons.resources.media_failed_refused_detail
import com.vitorpamplona.amethyst.commons.resources.media_failed_unknown_gif
import com.vitorpamplona.amethyst.commons.resources.media_failed_unknown_image
import com.vitorpamplona.amethyst.commons.resources.media_failed_unreachable
import com.vitorpamplona.amethyst.commons.resources.media_failed_unreachable_detail
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.service.MediaLoadFailure
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import okio.IOException

/**
 * The cause behind an image loader error: the HTTP status when there was one, a network failure
 * for an I/O error, and otherwise bytes that did not decode. Null (a failure with no throwable)
 * is [MediaLoadFailure.UNKNOWN].
 */
fun mediaLoadFailureOf(throwable: Throwable?): MediaLoadFailure =
    when {
        throwable == null -> MediaLoadFailure.UNKNOWN
        throwable is HttpException -> MediaLoadFailure.fromHttpStatus(throwable.response.code)
        // Android's ImageDecoder.DecodeException is an IOException too, but the bytes arrived.
        throwable is IOException && throwable::class.simpleName?.contains("Decode") != true -> MediaLoadFailure.UNREACHABLE
        else -> MediaLoadFailure.NOT_MEDIA
    }

/**
 * The server to name for [url]: its host, or for a `blossom:` URI the first server it hints at
 * (`xs`), else the blob's file name, since the URI itself names no server.
 */
private fun failureHost(url: String): String {
    if (url.startsWith("blossom:", ignoreCase = true)) {
        val uri = BlossomUri.parse(url) ?: return url
        return uri.servers.firstNotNullOfOrNull { OmniboxInput.hostOf(it) } ?: uri.filename()
    }
    return OmniboxInput.hostOf(url) ?: url
}

/** What a failed image or GIF says, worded for its [failure] and the [host] it came from. */
private class FailureWords(
    val icon: MaterialSymbol,
    val title: String,
    val detail: String?,
)

@Composable
private fun failureWords(
    failure: MediaLoadFailure,
    animated: Boolean,
    host: String,
): FailureWords =
    when (failure) {
        MediaLoadFailure.GONE ->
            FailureWords(
                MaterialSymbols.HideImage,
                stringRes(if (animated) Res.string.media_failed_gone_gif else Res.string.media_failed_gone_image),
                stringRes(Res.string.media_failed_gone_detail, host),
            )
        MediaLoadFailure.UNREACHABLE ->
            FailureWords(
                MaterialSymbols.CloudOff,
                stringRes(Res.string.media_failed_unreachable, host),
                stringRes(Res.string.media_failed_unreachable_detail),
            )
        MediaLoadFailure.REFUSED ->
            FailureWords(
                MaterialSymbols.Lock,
                stringRes(Res.string.media_failed_refused, host),
                stringRes(Res.string.media_failed_refused_detail),
            )
        MediaLoadFailure.PAYMENT_REQUIRED ->
            FailureWords(MaterialSymbols.Lock, stringRes(Res.string.media_failed_payment, host), null)
        MediaLoadFailure.NOT_MEDIA ->
            FailureWords(
                MaterialSymbols.BrokenImage,
                stringRes(if (animated) Res.string.media_failed_not_media_gif else Res.string.media_failed_not_media_image),
                host,
            )
        MediaLoadFailure.UNKNOWN ->
            FailureWords(
                MaterialSymbols.BrokenImage,
                stringRes(if (animated) Res.string.media_failed_unknown_gif else Res.string.media_failed_unknown_image),
                host,
            )
    }

/** Below this a card drops its detail line and button; below [TILE_MIN] it keeps only the icon. */
private val FULL_MIN = 190.dp
private val TILE_MIN = 110.dp

/**
 * An image or GIF that could not be loaded, in the slot its loading state had, so the note does
 * not jump: the blurhash dimmed under an icon, what went wrong, and one action (Retry when trying
 * again can help, else Open in browser).
 *
 * Sized by [modifier]. When it fixes the height (a known ratio, a gallery cell) the card fills it
 * and adapts: icon, title, detail and button from [FULL_MIN] up, icon and title above [TILE_MIN],
 * the icon alone in a small tile (taps reach the caller, which opens the viewer and its full card).
 * When it doesn't (an unknown shape, at most a height ceiling) the card is a compact row, the
 * held-back [MediaDownloadRow]'s failed twin.
 *
 * [onRetry] restarts the load; null where the caller can't, and Retry is then not offered.
 */
@Composable
fun MediaFailureCard(
    url: String,
    failure: MediaLoadFailure,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    blurhash: String? = null,
    thumbhash: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val isBlossomUri = url.startsWith("blossom:", ignoreCase = true)
    val host = remember(url) { failureHost(url) }
    val words = failureWords(failure, animated, host)
    val retry = onRetry?.takeIf { failure.retryable }
    // A `blossom:` URI isn't a web page: it goes to an app that resolves it, as its link did.
    val blossomOpener = if (isBlossomUri) rememberBlossomUriOpener() else null
    val uriHandler = LocalUriHandler.current
    val open: () -> Unit = {
        if (blossomOpener != null) {
            blossomOpener.open(url) { _, _ -> }
        } else {
            runCatching { uriHandler.openUri(url) }
        }
    }
    val action: Pair<String, () -> Unit> =
        if (retry != null) {
            stringRes(Res.string.retry) to retry
        } else {
            stringRes(Res.string.media_failed_open_in_browser) to open
        }

    BoxWithConstraints(modifier) {
        // A slot with a shape (a known ratio, a gallery cell) fixes the height; without one only a
        // ceiling, or nothing, bounds it, and a card stretched to that ceiling would be all scrim.
        if (!constraints.hasBoundedHeight || constraints.minHeight == 0) {
            FailureRow(words, action)
        } else {
            FailureSlot(words, action, blurhash, thumbhash, full = maxHeight >= FULL_MIN && maxWidth >= FULL_MIN, medium = maxHeight >= TILE_MIN && maxWidth >= TILE_MIN)
        }
    }
}

@Composable
private fun FailureSlot(
    words: FailureWords,
    action: Pair<String, () -> Unit>,
    blurhash: String?,
    thumbhash: String?,
    full: Boolean,
    medium: Boolean,
) {
    val hasHash = blurhash != null || thumbhash != null
    val onSlot = if (hasHash) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (hasHash) {
            DisplayBlurHash(blurhash, null, ContentScale.Crop, Modifier.fillMaxSize(), thumbhash = thumbhash)
            // A blurhash on its own reads as "on its way": dim it so the card says it isn't.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }

        if (full || medium) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(words.icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = onSlot)
                Text(
                    text = words.title,
                    color = onSlot,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (full) {
                    words.detail?.let {
                        Text(
                            text = it,
                            color = onSlot.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(onClick = action.second) { Text(action.first) }
                }
            }
        } else {
            Icon(words.icon, contentDescription = words.title, modifier = Modifier.size(28.dp), tint = onSlot)
        }
    }
}

@Composable
private fun FailureRow(
    words: FailureWords,
    action: Pair<String, () -> Unit>,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(words.icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(
                text = words.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            words.detail?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = action.second) { Text(action.first) }
    }
}
