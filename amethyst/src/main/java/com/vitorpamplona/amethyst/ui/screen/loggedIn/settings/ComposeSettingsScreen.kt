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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.model.AccountPoWPreferences
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.creators.pow.POW_PRESETS
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToLong

@Composable
fun ComposeSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.compose_settings), nav)
        },
    ) { padding ->
        ComposeSettingsContent(
            sharedPrefs = accountViewModel.settings.uiSettingsFlow,
            accountViewModel = accountViewModel,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun ComposeSettingsContent(
    sharedPrefs: UiSettingsFlow,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(R.string.compose_settings) {
            BooleanSwitchTile(
                flow = sharedPrefs.automaticallyCreateDrafts,
                icon = MaterialSymbols.Drafts,
                title = R.string.auto_create_drafts_setting_title,
                description = R.string.auto_create_drafts_setting_description,
            )
            SettingsDivider()
            BooleanSwitchTile(
                flow = sharedPrefs.automaticallyProposeAiImprovements,
                icon = MaterialSymbols.AutoAwesome,
                title = R.string.ai_writing_setting_title,
                description = R.string.ai_writing_setting_description,
            )
            SettingsDivider()
            BooleanSwitchTile(
                flow = sharedPrefs.useTrackedBroadcasts,
                icon = MaterialSymbols.CellTower,
                title = R.string.tracked_broadcasts_setting_title,
                description = R.string.tracked_broadcasts_setting_description,
            )
            SettingsDivider()
            BooleanSwitchTile(
                flow = sharedPrefs.suggestWorkoutsFromHealthConnect,
                icon = MaterialSymbols.DirectionsRun,
                title = R.string.suggest_workouts_setting_title,
                description = R.string.suggest_workouts_setting_description,
            )
            SettingsDivider()
            AddClientTagTile(accountViewModel)
            SettingsDivider()
            SignatureTile(sharedPrefs.composeSignature)
        }

        SettingsSection(R.string.pow_settings_title) {
            PowDifficultyTile(accountViewModel)
            SettingsDivider()
            PowCategoryChecklist(accountViewModel)
        }
    }
}

@Composable
private fun PowDifficultyTile(accountViewModel: AccountViewModel) {
    val difficulty by accountViewModel.account.settings.syncedSettings.proofOfWork
        .difficulty
        .collectAsStateWithLifecycle()

    SettingsBlockTile(
        icon = MaterialSymbols.Bolt,
        title = stringRes(R.string.pow_difficulty_title),
        description = stringRes(R.string.pow_difficulty_explainer),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = difficulty <= 0,
                onClick = { accountViewModel.updatePowDifficulty(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = POW_PRESETS.size + 1),
            ) {
                Text(stringRes(R.string.pow_difficulty_off))
            }
            POW_PRESETS.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = difficulty == preset,
                    onClick = { accountViewModel.updatePowDifficulty(preset) },
                    shape = SegmentedButtonDefaults.itemShape(index = index + 1, count = POW_PRESETS.size + 1),
                ) {
                    Text(preset.toString())
                }
            }
        }

        PowTimeEstimate(difficulty)
    }

    SettingsSubControlRow(
        title = stringRes(R.string.pow_custom_difficulty_title),
        description = stringRes(R.string.pow_custom_difficulty_explainer),
    ) {
        SettingsStepper(
            value = difficulty,
            min = 0,
            max = AccountPoWPreferences.MAX_POW_DIFFICULTY,
            unsetLabel = stringRes(R.string.pow_difficulty_off),
            onValueChange = accountViewModel::updatePowDifficulty,
        )
    }
}

/**
 * "≈ 45 s per post on this device" — turns the abstract bit count into a cost
 * the user can feel. The hash rate is benchmarked once (~250 ms on a worker
 * thread) and cached; the figure is a statistical mean, so any single post can
 * be luckier or unluckier.
 */
@Composable
private fun PowTimeEstimate(difficulty: Int) {
    if (difficulty <= 0) return

    val estimate by
        produceState<String?>(initialValue = null, difficulty) {
            val rate = PoWEstimator.hashesPerSecond()
            value = formatEstimate(PoWEstimator.estimateSeconds(difficulty, rate))
        }

    estimate?.let {
        Text(
            text = stringRes(R.string.pow_difficulty_estimate, it),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatEstimate(seconds: Double): String =
    when {
        seconds < 1.0 -> "<1s"
        seconds < 90.0 -> "${seconds.roundToLong()}s"
        seconds < 90.0 * 60.0 -> "${(seconds / 60.0).roundToLong()}m"
        seconds < 48.0 * 3600.0 -> "${(seconds / 3600.0).roundToLong()}h"
        else -> "${(seconds / 86400.0).roundToLong()}d"
    }

@Composable
private fun PowCategoryChecklist(accountViewModel: AccountViewModel) {
    val difficulty by accountViewModel.account.settings.syncedSettings.proofOfWork
        .difficulty
        .collectAsStateWithLifecycle()
    val enabledCategories by accountViewModel.account.settings.syncedSettings.proofOfWork
        .enabledCategories
        .collectAsStateWithLifecycle()

    val miningOn = difficulty > 0

    SettingsControlRow(
        icon = MaterialSymbols.Checklist,
        title = stringRes(R.string.pow_categories_title),
        description = stringRes(R.string.pow_categories_explainer),
    ) {}

    PoWCategory.entries.forEach { category ->
        val checked = category in enabledCategories
        SettingsSubControlRow(
            title = stringRes(category.titleRes()),
            description = stringRes(category.descriptionRes()),
            enabled = miningOn,
        ) {
            Switch(
                checked = checked,
                enabled = miningOn,
                onCheckedChange = { accountViewModel.updatePowCategory(category, it) },
            )
        }
    }
}

@StringRes
private fun PoWCategory.titleRes(): Int =
    when (this) {
        PoWCategory.SHORT_NOTES -> R.string.pow_category_short_notes
        PoWCategory.COMMENTS -> R.string.pow_category_comments
        PoWCategory.REPORTS -> R.string.pow_category_reports
        PoWCategory.LONG_FORM -> R.string.pow_category_long_form
        PoWCategory.VOICE -> R.string.pow_category_voice
        PoWCategory.REPOSTS -> R.string.pow_category_reposts
        PoWCategory.REACTIONS -> R.string.pow_category_reactions
        PoWCategory.PUBLIC_CHAT -> R.string.pow_category_public_chat
        PoWCategory.GIFT_WRAPS -> R.string.pow_category_gift_wraps
        PoWCategory.OTHER_PUBLIC -> R.string.pow_category_other_public
    }

@StringRes
private fun PoWCategory.descriptionRes(): Int =
    when (this) {
        PoWCategory.SHORT_NOTES -> R.string.pow_category_short_notes_explainer
        PoWCategory.COMMENTS -> R.string.pow_category_comments_explainer
        PoWCategory.REPORTS -> R.string.pow_category_reports_explainer
        PoWCategory.LONG_FORM -> R.string.pow_category_long_form_explainer
        PoWCategory.VOICE -> R.string.pow_category_voice_explainer
        PoWCategory.REPOSTS -> R.string.pow_category_reposts_explainer
        PoWCategory.REACTIONS -> R.string.pow_category_reactions_explainer
        PoWCategory.PUBLIC_CHAT -> R.string.pow_category_public_chat_explainer
        PoWCategory.GIFT_WRAPS -> R.string.pow_category_gift_wraps_explainer
        PoWCategory.OTHER_PUBLIC -> R.string.pow_category_other_public_explainer
    }

@Composable
private fun SignatureTile(flow: MutableStateFlow<String>) {
    val value by flow.collectAsState()

    SettingsBlockTile(
        icon = MaterialSymbols.EditNote,
        title = stringRes(R.string.compose_signature_setting_title),
        description = stringRes(R.string.compose_signature_setting_description),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { flow.tryEmit(it) },
            placeholder = { Text(stringRes(R.string.compose_signature_setting_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddClientTagTile(accountViewModel: AccountViewModel) {
    val addClientTag by accountViewModel.account.settings.syncedSettings.security
        .addClientTag
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Code,
        title = R.string.add_client_tag_title,
        description = R.string.add_client_tag_explainer,
        checked = addClientTag,
        onCheckedChange = accountViewModel::updateAddClientTag,
    )
}

@Composable
private fun BooleanSwitchTile(
    flow: MutableStateFlow<BooleanType>,
    icon: MaterialSymbol,
    @StringRes title: Int,
    @StringRes description: Int,
) {
    val value by flow.collectAsState()

    SwitchTile(
        icon = icon,
        title = title,
        description = description,
        checked = value == BooleanType.ALWAYS,
        onCheckedChange = { isOn ->
            flow.tryEmit(if (isOn) BooleanType.ALWAYS else BooleanType.NEVER)
        },
    )
}

@Composable
private fun SwitchTile(
    icon: MaterialSymbol,
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsControlRow(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun ComposeSettingsScreenPreview() {
    ThemeComparisonColumn {
        ComposeSettingsContent(UiSettingsFlow(), mockAccountViewModel())
    }
}
