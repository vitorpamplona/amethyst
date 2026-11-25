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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.frontpage.ObserverPlace
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.front_page_weather
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_celsius
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_fahrenheit
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_none_found
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_off
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_private
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_search
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_search_hint
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_title
import com.vitorpamplona.amethyst.commons.resources.front_page_weather_turn_off
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/** The weather setting as the print controls take it: the schedule's place, how to change it, and how to search for one. */
@Immutable
class WeatherSetting(
    val place: ObserverPlace?,
    val onChange: (ObserverPlace?) -> Unit,
    val search: suspend (String) -> List<ObserverPlace>,
)

/** "Weather": the place the paper's forecast is for, or Off; opens the place picker. */
@Composable
internal fun WeatherRow(
    setting: WeatherSetting,
    ink: Color,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { picking = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.LocationOn, contentDescription = null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringRes(Res.string.front_page_weather), style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        Text(
            setting.place?.let { "${it.name} · ${stringRes(if (it.fahrenheit) Res.string.front_page_weather_fahrenheit else Res.string.front_page_weather_celsius)}" }
                ?: stringRes(Res.string.front_page_weather_off),
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.8f),
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    if (picking) {
        WeatherPlaceDialog(
            current = setting.place,
            search = setting.search,
            onDone = { chosen ->
                picking = false
                if (chosen != setting.place) setting.onChange(chosen)
            },
            onDismiss = { picking = false },
        )
    }
}

/** Search a city, pick it and its units; or turn the weather off. */
@Composable
private fun WeatherPlaceDialog(
    current: ObserverPlace?,
    search: suspend (String) -> List<ObserverPlace>,
    onDone: (ObserverPlace?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ObserverPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(current) }

    fun runSearch() {
        if (query.isBlank() || searching) return
        searching = true
        scope.launch {
            results =
                try {
                    search(query)
                } finally {
                    searching = false
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.front_page_weather_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringRes(Res.string.front_page_weather_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    trailingIcon = {
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = ::runSearch) { Icon(MaterialSymbols.Search, contentDescription = stringRes(Res.string.front_page_weather_search)) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                results?.let { found ->
                    if (found.isEmpty()) {
                        Text(stringRes(Res.string.front_page_weather_none_found), style = MaterialTheme.typography.bodySmall)
                    }
                    found.take(6).forEach { place ->
                        Text(
                            place.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (place == chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { chosen = place }
                                    .padding(vertical = 8.dp),
                        )
                    }
                }
                chosen?.let { place ->
                    Text(place.name, style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false, true).forEachIndexed { index, fahrenheit ->
                            SegmentedButton(
                                selected = place.fahrenheit == fahrenheit,
                                onClick = { chosen = place.copy(fahrenheit = fahrenheit) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            ) {
                                Text(stringRes(if (fahrenheit) Res.string.front_page_weather_fahrenheit else Res.string.front_page_weather_celsius))
                            }
                        }
                    }
                }
                Text(stringRes(Res.string.front_page_weather_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(chosen) }, enabled = chosen != null) { Text(stringRes(Res.string.save)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onDone(null) }) { Text(stringRes(Res.string.front_page_weather_turn_off)) }
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
            }
        },
    )
}
