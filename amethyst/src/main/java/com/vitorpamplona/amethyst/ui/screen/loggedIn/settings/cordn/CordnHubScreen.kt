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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_backup
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_backup_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_coordinators
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_coordinators_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_keypackages
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_keypackages_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_link
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_link_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_migrate
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_migrate_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_title
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import org.jetbrains.compose.resources.StringResource

/**
 * One door into cordn, instead of five rows in the account settings list.
 *
 * cordn's pages — coordinators, key packages, link inspection, backup,
 * migration — are each visited rarely and only by someone already thinking
 * about cordn. As flat entries they made it the biggest feature in that list
 * by count and among the least used, pushing everything else down. Grouping
 * them costs one tap and keeps every page searchable under a name a user
 * would actually look for.
 */
@Composable
fun CordnHubScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_hub_title), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(Res.string.cordn_hub_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HubEntry(Res.string.cordn_hub_coordinators, Res.string.cordn_hub_coordinators_desc) { nav.nav(Route.CordnCoordinators) }
            HubEntry(Res.string.cordn_hub_keypackages, Res.string.cordn_hub_keypackages_desc) { nav.nav(Route.CordnKeyPackages) }
            HubEntry(Res.string.cordn_hub_link, Res.string.cordn_hub_link_desc) { nav.nav(Route.CordnLink) }
            HubEntry(Res.string.cordn_hub_backup, Res.string.cordn_hub_backup_desc) { nav.nav(Route.CordnBackup) }
            HubEntry(Res.string.cordn_hub_migrate, Res.string.cordn_hub_migrate_desc) { nav.nav(Route.CordnMigrate) }
        }
    }
}

@Composable
private fun HubEntry(
    title: StringResource,
    description: StringResource,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringRes(title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringRes(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
