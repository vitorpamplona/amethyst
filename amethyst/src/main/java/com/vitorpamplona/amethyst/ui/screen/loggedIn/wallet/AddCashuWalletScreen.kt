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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCashuWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.init(accountViewModel) }

    val mints = remember { mutableStateListOf<String>() }
    var mintInput by remember { mutableStateOf("") }
    var autoGenPrivkey by remember { mutableStateOf(true) }
    var manualPrivkey by remember { mutableStateOf("") }
    val createState by viewModel.createState.collectAsState()

    LaunchedEffect(createState) {
        if (createState is CashuWalletCreateState.Success) {
            nav.popBack()
            viewModel.resetCreateState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.wallet_add_cashu_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Text(
                text = stringRes(R.string.cashu_mints),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            mints.forEachIndexed { index, mint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mint,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { mints.removeAt(index) }) {
                            Icon(
                                symbol = MaterialSymbols.Delete,
                                contentDescription = stringRes(R.string.cashu_remove_mint),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = mintInput,
                    onValueChange = { mintInput = it },
                    label = { Text(stringRes(R.string.cashu_mint_url)) },
                    placeholder = { Text("https://mint.example.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val trimmed = mintInput.trim().trimEnd('/')
                        if (trimmed.isNotEmpty() && trimmed !in mints) {
                            mints.add(trimmed)
                            mintInput = ""
                        }
                    },
                    enabled = mintInput.isNotBlank(),
                ) {
                    Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringRes(R.string.cashu_p2pk_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.cashu_p2pk_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringRes(R.string.cashu_p2pk_autogen))
                Switch(checked = autoGenPrivkey, onCheckedChange = { autoGenPrivkey = it })
            }

            if (!autoGenPrivkey) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualPrivkey,
                    onValueChange = { manualPrivkey = it },
                    label = { Text(stringRes(R.string.cashu_p2pk_manual_label)) },
                    placeholder = { Text("hex…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val err = (createState as? CashuWalletCreateState.Error)?.message
            if (err != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveWallet(
                        mints = mints.toList(),
                        autoGenPrivkey = autoGenPrivkey,
                        manualPrivkey = manualPrivkey.takeIf { !autoGenPrivkey },
                    )
                },
                enabled =
                    mints.isNotEmpty() &&
                        createState !is CashuWalletCreateState.Saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(R.string.wallet_save))
            }
        }
    }
}
