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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Stable
class AccountSyncedSettings(
    internalSettings: AccountSyncedSettingsInternal,
) {
    val reactions =
        AccountReactionPreferences(
            MutableStateFlow(internalSettings.reactions.reactionChoices.toImmutableList()),
            MutableStateFlow(internalSettings.reactions.reactionRowItems.toImmutableList()),
        )
    val zaps =
        AccountZapPreferences(
            MutableStateFlow(internalSettings.zaps.zapAmountChoices.toImmutableList()),
            MutableStateFlow(internalSettings.zaps.defaultZapType),
        )
    val languages =
        AccountLanguagePreferences(
            MutableStateFlow(internalSettings.languages.dontTranslateFrom),
            MutableStateFlow(internalSettings.languages.languagePreferences),
            MutableStateFlow(internalSettings.languages.translateTo),
        )
    val security =
        AccountSecurityPreferences(
            MutableStateFlow(internalSettings.security.showSensitiveContent),
            internalSettings.security.warnAboutPostsWithReports,
            MutableStateFlow(internalSettings.security.filterSpamFromStrangers),
            MutableStateFlow(internalSettings.security.maxHashtagLimit),
            MutableStateFlow(internalSettings.security.sendKind0EventsToLocalRelay),
        )
    val videoPlayer =
        AccountVideoPlayerPreferences(
            MutableStateFlow(internalSettings.videoPlayer.buttonItems.toImmutableList()),
        )

    fun toInternal(): AccountSyncedSettingsInternal =
        AccountSyncedSettingsInternal(
            reactions = AccountReactionPreferencesInternal(reactions.reactionChoices.value, reactions.reactionRowItems.value),
            zaps =
                AccountZapPreferencesInternal(
                    zaps.zapAmountChoices.value,
                    zaps.defaultZapType.value,
                ),
            languages =
                AccountLanguagePreferencesInternal(
                    languages.dontTranslateFrom.value,
                    languages.languagePreferences.value,
                    languages.translateTo.value,
                ),
            security =
                AccountSecurityPreferencesInternal(
                    security.showSensitiveContent.value,
                    security.warnAboutPostsWithReports,
                    security.filterSpamFromStrangers.value,
                    security.maxHashtagLimit.value,
                    security.sendKind0EventsToLocalRelay.value,
                ),
            videoPlayer = AccountVideoPlayerPreferencesInternal(videoPlayer.buttonItems.value),
        )

    fun updateFrom(syncedSettingsInternal: AccountSyncedSettingsInternal) {
        val newReactionChoices = syncedSettingsInternal.reactions.reactionChoices.toImmutableList()
        if (!equalImmutableLists(reactions.reactionChoices.value, newReactionChoices)) {
            reactions.reactionChoices.tryEmit(newReactionChoices)
        }

        val newReactionRowItems = syncedSettingsInternal.reactions.reactionRowItems.toImmutableList()
        if (!equalImmutableLists(reactions.reactionRowItems.value, newReactionRowItems)) {
            reactions.reactionRowItems.tryEmit(newReactionRowItems)
        }

        val newZapChoices = syncedSettingsInternal.zaps.zapAmountChoices.toImmutableList()
        if (!equalImmutableLists(zaps.zapAmountChoices.value, newZapChoices)) {
            zaps.zapAmountChoices.tryEmit(newZapChoices)
        }

        if (zaps.defaultZapType.value != syncedSettingsInternal.zaps.defaultZapType) {
            zaps.defaultZapType.tryEmit(syncedSettingsInternal.zaps.defaultZapType)
        }

        if (languages.dontTranslateFrom.value != syncedSettingsInternal.languages.dontTranslateFrom) {
            languages.dontTranslateFrom.value = syncedSettingsInternal.languages.dontTranslateFrom
        }

        if (languages.languagePreferences.value != syncedSettingsInternal.languages.languagePreferences) {
            languages.languagePreferences.value = syncedSettingsInternal.languages.languagePreferences
        }

        if (languages.translateTo.value != syncedSettingsInternal.languages.translateTo) {
            languages.translateTo.value = syncedSettingsInternal.languages.translateTo
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

        if (security.maxHashtagLimit.value != syncedSettingsInternal.security.maxHashtagLimit) {
            security.maxHashtagLimit.tryEmit(syncedSettingsInternal.security.maxHashtagLimit)
        }

        if (security.sendKind0EventsToLocalRelay.value != syncedSettingsInternal.security.sendKind0EventsToLocalRelay) {
            security.sendKind0EventsToLocalRelay.tryEmit(syncedSettingsInternal.security.sendKind0EventsToLocalRelay)
        }

        val newVideoPlayerButtonItems = syncedSettingsInternal.videoPlayer.buttonItems.toImmutableList()
        if (!equalImmutableLists(videoPlayer.buttonItems.value, newVideoPlayerButtonItems)) {
            videoPlayer.buttonItems.tryEmit(newVideoPlayerButtonItems)
        }
    }

    fun dontTranslateFromFilteredBySpokenLanguages(): Set<String> = languages.dontTranslateFrom.value - getLanguagesSpokenByUser()
}

@Stable
class AccountReactionPreferences(
    var reactionChoices: MutableStateFlow<ImmutableList<String>>,
    var reactionRowItems: MutableStateFlow<ImmutableList<ReactionRowItem>>,
)

@Stable
class AccountVideoPlayerPreferences(
    var buttonItems: MutableStateFlow<ImmutableList<VideoPlayerButtonItem>>,
)

@Stable
class AccountZapPreferences(
    var zapAmountChoices: MutableStateFlow<ImmutableList<Long>>,
    val defaultZapType: MutableStateFlow<LnZapEvent.ZapType>,
)

@Stable
class AccountLanguagePreferences(
    var dontTranslateFrom: MutableStateFlow<Set<String>>,
    var languagePreferences: MutableStateFlow<Map<String, String>>,
    var translateTo: MutableStateFlow<String>,
) {
    // ---
    // language services
    // ---
    fun toggleDontTranslateFrom(languageCode: String) {
        dontTranslateFrom.update {
            if (it.contains(languageCode)) {
                it - languageCode
            } else {
                it + languageCode
            }
        }
    }

    fun addDontTranslateFrom(languageCode: String) {
        dontTranslateFrom.update { it + languageCode }
    }

    fun removeDontTranslateFrom(languageCode: String) {
        dontTranslateFrom.update { it - languageCode }
    }

    fun translateToContains(languageCode: String) = translateTo.value.contains(languageCode)

    fun updateTranslateTo(languageCode: String): Boolean {
        if (translateTo.value != languageCode) {
            translateTo.tryEmit(languageCode)
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
        languagePreferences.update {
            if (key !in it) {
                it + Pair(key, preference)
            } else {
                if (it.get(key) == preference) {
                    it.minus(key)
                } else {
                    it + Pair(key, preference)
                }
            }
        }
    }

    fun preferenceBetween(
        source: String,
        target: String,
    ): String? = languagePreferences.value["$source,$target"]
}

@Stable
class AccountSecurityPreferences(
    val showSensitiveContent: MutableStateFlow<Boolean?> = MutableStateFlow(null),
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val maxHashtagLimit: MutableStateFlow<Int> = MutableStateFlow(5),
    var sendKind0EventsToLocalRelay: MutableStateFlow<Boolean> = MutableStateFlow(false),
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

    fun updateMaxHashtagLimit(limit: Int): Boolean =
        if (maxHashtagLimit.value != limit) {
            maxHashtagLimit.update { limit }
            true
        } else {
            false
        }

    fun updateSendKind0EventsToLocalRelay(send: Boolean): Boolean =
        if (send != sendKind0EventsToLocalRelay.value) {
            sendKind0EventsToLocalRelay.tryEmit(send)
            true
        } else {
            false
        }
}
