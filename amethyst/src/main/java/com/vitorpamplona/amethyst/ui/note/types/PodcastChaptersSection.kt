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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.podcasts.PodcastChapter
import com.vitorpamplona.quartz.podcasts.PodcastChapters
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

private sealed interface ChaptersUiState {
    data object Loading : ChaptersUiState

    data class Loaded(
        val chapters: List<PodcastChapter>,
    ) : ChaptersUiState

    data object Failed : ChaptersUiState
}

/**
 * Fetches the episode's off-event Podcasting-2.0 chapters document on first composition and renders
 * it as a tinted list of `timestamp — title` rows. Fetch is lazy (callers gate it behind an expand
 * toggle) so scrolling a feed never triggers network. On failure or empty, renders nothing.
 */
@Composable
fun PodcastChaptersSection(
    chaptersUrl: String,
    accountViewModel: AccountViewModel,
) {
    val state by produceState<ChaptersUiState>(ChaptersUiState.Loading, chaptersUrl) {
        val client = accountViewModel.httpClientBuilder.okHttpClientForPreview(chaptersUrl)
        val parsed = loadChapters(chaptersUrl, client)
        value = if (parsed != null) ChaptersUiState.Loaded(parsed.chapters) else ChaptersUiState.Failed
    }

    when (val current = state) {
        is ChaptersUiState.Loading ->
            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }

        is ChaptersUiState.Failed -> {}

        is ChaptersUiState.Loaded -> {
            val chapters = current.chapters
            if (chapters.isEmpty()) return

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                chapters.forEach { chapter ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = formatTimestamp(chapter.startSeconds()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 44.dp),
                        )
                        Text(
                            text = chapter.title ?: stringRes(R.string.podcast_chapters),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.grayText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private suspend fun loadChapters(
    url: String,
    client: OkHttpClient,
): PodcastChapters? =
    withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).executeAsync().use { response ->
                if (response.isSuccessful) PodcastChapters.parse(response.body.string()) else null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("PodcastChapters", "Failed to load chapters from $url", e)
            null
        }
    }

private fun formatTimestamp(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
