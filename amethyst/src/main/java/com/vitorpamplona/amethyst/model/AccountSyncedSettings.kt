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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

@Stable
class AccountSyncedSettings(
    internalSettings: AccountSyncedSettingsInternal,
) {
    val reactions = AccountReactionPreferences(MutableStateFlow(internalSettings.reactions.reactionChoices.toImmutableList()))
    val zaps =
        AccountZapPreferences(
            MutableStateFlow(internalSettings.zaps.zapAmountChoices.toImmutableList()),
            MutableStateFlow(internalSettings.zaps.defaultZapType),
        )
    val languages =
        AccountLanguagePreferences(
            internalSettings.languages.dontTranslateFrom,
            internalSettings.languages.languagePreferences,
            internalSettings.languages.translateTo,
        )
    val security =
        AccountSecurityPreferences(
            MutableStateFlow(internalSettings.security.showSensitiveContent),
            internalSettings.security.warnAboutPostsWithReports,
            MutableStateFlow(internalSettings.security.filterSpamFromStrangers),
        )

    fun toInternal(): AccountSyncedSettingsInternal =
        AccountSyncedSettingsInternal(
            reactions = AccountReactionPreferencesInternal(reactions.reactionChoices.value),
            zaps =
                AccountZapPreferencesInternal(
                    zaps.zapAmountChoices.value,
                    zaps.defaultZapType.value,
                ),
            languages =
                AccountLanguagePreferencesInternal(
                    languages.dontTranslateFrom,
                    languages.languagePreferences,
                    languages.translateTo,
                ),
            security =
                AccountSecurityPreferencesInternal(
                    security.showSensitiveContent.value,
                    security.warnAboutPostsWithReports,
                    security.filterSpamFromStrangers.value,
                ),
        )

    fun updateFrom(syncedSettingsInternal: AccountSyncedSettingsInternal) {
        val newReactionChoices = syncedSettingsInternal.reactions.reactionChoices.toImmutableList()
        if (!equalImmutableLists(reactions.reactionChoices.value, newReactionChoices)) {
            reactions.reactionChoices.tryEmit(newReactionChoices)
        }

        val newZapChoices = syncedSettingsInternal.zaps.zapAmountChoices.toImmutableList()
        if (!equalImmutableLists(zaps.zapAmountChoices.value, newZapChoices)) {
            zaps.zapAmountChoices.tryEmit(newZapChoices)
        }

        if (zaps.defaultZapType.value != syncedSettingsInternal.zaps.defaultZapType) {
            zaps.defaultZapType.tryEmit(syncedSettingsInternal.zaps.defaultZapType)
        }

        if (languages.dontTranslateFrom != syncedSettingsInternal.languages.dontTranslateFrom) {
            languages.dontTranslateFrom = syncedSettingsInternal.languages.dontTranslateFrom
        }

        if (languages.languagePreferences != syncedSettingsInternal.languages.languagePreferences) {
            languages.languagePreferences = syncedSettingsInternal.languages.languagePreferences
        }

        if (languages.translateTo != syncedSettingsInternal.languages.translateTo) {
            languages.translateTo = syncedSettingsInternal.languages.translateTo
        }

        if (security.showSensitiveContent.value != syncedSettingsInternal.security.showSensitiveContent) {
            security.showSensitiveContent.tryEmit(syncedSettingsInternal.security.showSensitiveContent)
        }

        if (security.filterSpamFromStrangers.value != syncedSettingsInternal.security.filterSpamFromStrangers) {
            security.filterSpamFromStrangers.tryEmit(syncedSettingsInternal.security.filterSpamFromStrangers)
        }

        if (security.warnAboutPostsWithReports != syncedSettingsInternal.security.warnAboutPostsWithReports) {
            security.warnAboutPostsWithReports = syncedSettingsInternal.security.warnAboutPostsWithReports
        }
    }
}

@Stable
class AccountReactionPreferences(
    var reactionChoices: MutableStateFlow<ImmutableList<String>>,
)

@Stable
class AccountZapPreferences(
    var zapAmountChoices: MutableStateFlow<ImmutableList<Long>>,
    val defaultZapType: MutableStateFlow<LnZapEvent.ZapType>,
)

@Stable
class AccountLanguagePreferences(
    var dontTranslateFrom: Set<String>,
    var languagePreferences: Map<String, String>,
    var translateTo: String,
) {
    // ---
    // language services
    // ---
    fun toggleDontTranslateFrom(languageCode: String) {
        if (!dontTranslateFrom.contains(languageCode)) {
            dontTranslateFrom = dontTranslateFrom.plus(languageCode)
        } else {
            dontTranslateFrom = dontTranslateFrom.minus(languageCode)
        }
    }

    fun translateToContains(languageCode: Locale) = translateTo.contains(languageCode.language)

    fun updateTranslateTo(languageCode: Locale): Boolean {
        if (translateTo != languageCode.language) {
            translateTo = languageCode.language
            return true
        }
        return false
    }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        val key = "$source,$target"
        if (key !in languagePreferences) {
            languagePreferences = languagePreferences + Pair(key, preference)
        } else {
            if (languagePreferences.get(key) == preference) {
                languagePreferences = languagePreferences.minus(key)
            } else {
                languagePreferences = languagePreferences + Pair(key, preference)
            }
        }
    }

    fun preferenceBetween(
        source: String,
        target: String,
    ): String? = languagePreferences["$source,$target"]
}

@Stable
class AccountSecurityPreferences(
    val showSensitiveContent: MutableStateFlow<Boolean?> = MutableStateFlow(null),
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: MutableStateFlow<Boolean> = MutableStateFlow(true),
) {
    fun updateShowSensitiveContent(show: Boolean?): Boolean {
        if (showSensitiveContent.value != show) {
            showSensitiveContent.update { show }
            return true
        }
        return false
    }

    fun updateWarnReports(warnReports: Boolean): Boolean =
        if (warnAboutPostsWithReports != warnReports) {
            warnAboutPostsWithReports = warnReports
            true
        } else {
            false
        }

    fun updateFilterSpam(filterSpam: Boolean): Boolean =
        if (filterSpam != filterSpamFromStrangers.value) {
            filterSpamFromStrangers.tryEmit(filterSpam)
            true
        } else {
            false
        }
}
