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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.moderation.HashtagSpamSettings
import kotlin.math.roundToInt

/**
 * Settings UI for the hashtag-spam content filter.
 *
 * Decoupled-thumb pattern: a local [Float] state drives the [Slider] thumb,
 * the [StateFlow] only sees the committed [Int] on [onValueChangeFinished].
 * Prevents per-tick recomposition storms in the feed while the user drags.
 */
@Composable
fun HashtagSpamSettingsSection(
    settings: HashtagSpamSettings,
    modifier: Modifier = Modifier,
) {
    val enabled by settings.enabled.collectAsState()
    val committed by settings.threshold.collectAsState()

    var live by remember { mutableFloatStateOf(committed.toFloat()) }
    LaunchedEffect(committed) { live = committed.toFloat() }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Hashtag-spam filter",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = settings::setEnabled)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (enabled) "On" else "Off",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Hide notes with more than ${live.roundToInt()} hashtags",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = live,
                onValueChange = { live = it },
                onValueChangeFinished = { settings.setThreshold(live.roundToInt()) },
                valueRange =
                    HashtagSpamSettings.MIN_THRESHOLD
                        .toFloat()..HashtagSpamSettings.MAX_THRESHOLD.toFloat(),
                steps = HashtagSpamSettings.MAX_THRESHOLD - HashtagSpamSettings.MIN_THRESHOLD - 1,
            )
            Text(
                text = "Long-form articles and posts from people you follow are always shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
