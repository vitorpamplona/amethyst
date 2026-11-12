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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.frontpage.AgentModel
import com.vitorpamplona.amethyst.commons.frontpage.ModelChoice
import com.vitorpamplona.amethyst.commons.frontpage.ModelChoices
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.front_page_model_dialog_title
import com.vitorpamplona.amethyst.commons.resources.front_page_model_none_found
import com.vitorpamplona.amethyst.commons.resources.front_page_model_other_vendor
import com.vitorpamplona.amethyst.commons.resources.front_page_model_search
import com.vitorpamplona.amethyst.commons.resources.front_page_model_use_typed
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSectionLabel
import com.vitorpamplona.amethyst.commons.ui.components.M3LazyActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.PillTextField
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier

/**
 * The model choice in the app's grouped-dialog style (the feed selector's): models under their
 * vendor with readable names and the id beneath, the current one checked, a search field once the
 * list is long, and a row to use a typed id the list doesn't hold.
 */
@Composable
fun ModelPickerDialog(
    ids: List<String>,
    byId: Map<String, AgentModel>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchable = ids.size >= ModelChoices.SEARCH_FROM
    val groups = remember(ids, byId, query) { ModelChoices.group(ids, byId, query) }
    val typed = remember(ids, query) { ModelChoices.typedId(query, ids) }
    val other = stringRes(Res.string.front_page_model_other_vendor)

    fun pick(id: String) {
        onSelect(id)
        onDismiss()
    }

    M3LazyActionDialog(
        title = stringRes(Res.string.front_page_model_dialog_title),
        onDismiss = onDismiss,
        header =
            if (searchable) {
                {
                    PillTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringRes(Res.string.front_page_model_search),
                        leading = MaterialSymbols.Search,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            } else {
                null
            },
    ) {
        if (typed != null) {
            item(key = "typed") {
                M3ActionSection {
                    ModelRow(
                        name = stringRes(Res.string.front_page_model_use_typed, typed),
                        id = null,
                        selected = typed == selected,
                        icon = true,
                        onClick = { pick(typed) },
                    )
                }
            }
        }
        if (groups.isEmpty() && typed == null && query.isNotBlank()) {
            item(key = "none") {
                Text(
                    stringRes(Res.string.front_page_model_none_found, query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        groups.forEach { group ->
            item(key = "vendor:${group.label}") {
                M3ActionSection {
                    Column {
                        M3ActionSectionLabel(group.label.ifEmpty { other })
                        group.models.forEach { choice -> ChoiceRow(choice, choice.id == selected) { pick(choice.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: ModelChoice,
    selected: Boolean,
    onClick: () -> Unit,
) = ModelRow(choice.name, choice.id.takeIf { it != choice.name }, selected, icon = false, onClick = onClick)

@Composable
private fun ModelRow(
    name: String,
    id: String?,
    selected: Boolean,
    icon: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon) {
            Icon(MaterialSymbols.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Size20Modifier)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            id?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
