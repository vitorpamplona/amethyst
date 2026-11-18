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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.mavenReleases.KmpPlatform
import com.vitorpamplona.amethyst.commons.mavenReleases.MavenReleaseKeys
import com.vitorpamplona.amethyst.commons.mavenReleases.MavenReleaseSummary
import com.vitorpamplona.amethyst.commons.mavenReleases.MavenVersion
import com.vitorpamplona.amethyst.commons.mavenReleases.MavenVersions
import com.vitorpamplona.amethyst.commons.mavenReleases.ReleaseKeyLookup
import com.vitorpamplona.amethyst.commons.mavenReleases.ReleaseKeyVerdict
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.mavenReleases.MavenVersionsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kc_maven_artifacts
import com.vitorpamplona.amethyst.commons.resources.kc_maven_key_checking
import com.vitorpamplona.amethyst.commons.resources.kc_maven_key_unreachable
import com.vitorpamplona.amethyst.commons.resources.kc_maven_malformed
import com.vitorpamplona.amethyst.commons.resources.kc_maven_manifest_size
import com.vitorpamplona.amethyst.commons.resources.kc_maven_newer
import com.vitorpamplona.amethyst.commons.resources.kc_maven_newer_open
import com.vitorpamplona.amethyst.commons.resources.kc_maven_no_key
import com.vitorpamplona.amethyst.commons.resources.kc_maven_not_a_domain
import com.vitorpamplona.amethyst.commons.resources.kc_maven_other_key
import com.vitorpamplona.amethyst.commons.resources.kc_maven_other_modules
import com.vitorpamplona.amethyst.commons.resources.kc_maven_platform_js
import com.vitorpamplona.amethyst.commons.resources.kc_maven_platform_jvm
import com.vitorpamplona.amethyst.commons.resources.kc_maven_platform_tvos
import com.vitorpamplona.amethyst.commons.resources.kc_maven_platform_wasm
import com.vitorpamplona.amethyst.commons.resources.kc_maven_platform_watchos
import com.vitorpamplona.amethyst.commons.resources.kc_maven_release
import com.vitorpamplona.amethyst.commons.resources.kc_maven_setup
import com.vitorpamplona.amethyst.commons.resources.kc_maven_setup_hint
import com.vitorpamplona.amethyst.commons.resources.kc_maven_snapshot
import com.vitorpamplona.amethyst.commons.resources.kc_maven_verified
import com.vitorpamplona.amethyst.commons.resources.nip82_os_android
import com.vitorpamplona.amethyst.commons.resources.nip82_os_ios
import com.vitorpamplona.amethyst.commons.resources.nip82_os_linux
import com.vitorpamplona.amethyst.commons.resources.nip82_os_macos
import com.vitorpamplona.amethyst.commons.resources.nip82_os_windows
import com.vitorpamplona.amethyst.commons.ui.components.CopyableCode
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenModule
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenPublication
import com.vitorpamplona.quartz.nipFCMavenRepositories.release.MavenReleaseEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource

/**
 * Kinds 3071 and 33071, NIP-FC: a release (or `-SNAPSHOT` build) of a library published as Maven
 * artifacts on Nostr + Blossom. Built to answer what a developer scrolling past wants to know:
 *
 * - **What is it:** the library's name, its group and version, and the platforms it ships for.
 * - **Can I trust it:** whether the key its group's domain names as `releases` signed it, the rule
 *   every NIP-FC resolver applies. Anyone can sign a release claiming any group; one that fails
 *   this check is flagged and loses its setup snippets, since a build would never resolve it.
 * - **Is it current:** a newer version by the same signer, when the relays have one.
 * - **How do I use it:** the dependency line, and behind a toggle the Magpie settings that let
 *   Gradle resolve the group from Nostr, each with a copy button.
 */
@Composable
fun RenderMavenPublication(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event ?: return
    val event = noteEvent as? MavenPublication ?: return
    val summary = remember(noteEvent) { MavenReleaseSummary(event) }
    val root = summary.root

    val lookup = root?.let { rememberReleaseKeyLookup(it.groupId, accountViewModel) }
    val verdict = lookup?.value?.let { MavenReleaseKeys.verdict(it, noteEvent.pubKey) }
    val impostor = verdict == ReleaseKeyVerdict.OTHER_KEY

    KindCard(
        icon = MaterialSymbols.Code,
        title = root?.artifactId ?: summary.rootKey ?: kindName(noteEvent.kind),
        subtitle = root?.groupId,
        tone =
            when {
                !summary.isWellFormed || impostor -> KindTone.Negative
                summary.isSnapshot -> KindTone.Neutral
                else -> KindTone.Primary
            },
        status = if (summary.isWellFormed) summary.version else stringRes(Res.string.kc_maven_malformed),
    ) {
        if (root != null) {
            ReleaseKeyLine(lookup?.value, verdict)
        }
        PlatformChips(summary.platforms)
        if (summary.otherModules.isNotEmpty()) {
            KindCardSectionChips(stringRes(Res.string.kc_maven_other_modules), summary.otherModules, max = 6)
        }
        if (root != null && !impostor) {
            NewerVersionBanner(baseNote, noteEvent.pubKey, root, summary.version, accountViewModel, nav)
        }
        if (!impostor && summary.isWellFormed) {
            summary.dependency?.let { CopyableCode(it) }
            summary.settings?.let { GradleSetup(it) }
        }
        MavenFooter(summary)
    }
}

/** The group's release key, looked up once per group and remembered across cards. */
@Composable
private fun rememberReleaseKeyLookup(
    groupId: String,
    accountViewModel: AccountViewModel,
): State<ReleaseKeyLookup?> =
    produceState<ReleaseKeyLookup?>(null, groupId) {
        value = MavenReleaseKeys.cachedLookup(groupId, accountViewModel.nip05ClientBuilder())
    }

/** One line on the signer: verified, someone else's key, no key to check against, or not known yet. */
@Composable
private fun ReleaseKeyLine(
    lookup: ReleaseKeyLookup?,
    verdict: ReleaseKeyVerdict?,
) {
    val domain =
        when (lookup) {
            // Unknown despite a key: the host that couldn't be read is the one to name.
            is ReleaseKeyLookup.Found -> if (verdict == ReleaseKeyVerdict.UNKNOWN) lookup.skipped else lookup.domain
            is ReleaseKeyLookup.None -> lookup.domain
            is ReleaseKeyLookup.Unreachable -> lookup.domain
            else -> null
        }
    val (symbol, tone, text) =
        when {
            lookup == null -> Triple(MaterialSymbols.HourglassEmpty, KindTone.Neutral, stringRes(Res.string.kc_maven_key_checking))
            verdict == ReleaseKeyVerdict.VERIFIED -> Triple(MaterialSymbols.CheckCircle, KindTone.Positive, stringRes(Res.string.kc_maven_verified, domain ?: ""))
            verdict == ReleaseKeyVerdict.OTHER_KEY -> Triple(MaterialSymbols.Warning, KindTone.Negative, stringRes(Res.string.kc_maven_other_key, domain ?: ""))
            lookup is ReleaseKeyLookup.NotADomain -> Triple(MaterialSymbols.Info, KindTone.Warning, stringRes(Res.string.kc_maven_not_a_domain))
            verdict == ReleaseKeyVerdict.NO_KEY -> Triple(MaterialSymbols.Info, KindTone.Warning, stringRes(Res.string.kc_maven_no_key, domain ?: ""))
            else -> Triple(MaterialSymbols.ErrorOutline, KindTone.Neutral, stringRes(Res.string.kc_maven_key_unreachable, domain ?: ""))
        }
    val color = tone.color()
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(symbol, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(15.dp), tint = color)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (tone == KindTone.Neutral) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
    }
}

/** The target families as tinted chips: what a multiplatform library runs on, at a glance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformChips(platforms: List<KmpPlatform>) {
    if (platforms.isEmpty()) return
    val accent = MaterialTheme.colorScheme.primary
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        platforms.forEach { platform ->
            Text(
                text = stringRes(platformLabel(platform)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private fun platformLabel(platform: KmpPlatform): StringResource =
    when (platform) {
        KmpPlatform.ANDROID -> Res.string.nip82_os_android
        KmpPlatform.JVM -> Res.string.kc_maven_platform_jvm
        KmpPlatform.IOS -> Res.string.nip82_os_ios
        KmpPlatform.MACOS -> Res.string.nip82_os_macos
        KmpPlatform.TVOS -> Res.string.kc_maven_platform_tvos
        KmpPlatform.WATCHOS -> Res.string.kc_maven_platform_watchos
        KmpPlatform.LINUX -> Res.string.nip82_os_linux
        KmpPlatform.WINDOWS -> Res.string.nip82_os_windows
        KmpPlatform.JS -> Res.string.kc_maven_platform_js
        KmpPlatform.WASM -> Res.string.kc_maven_platform_wasm
    }

/**
 * "Version X is out" when the signer has released a newer version of this library: loads the
 * library's releases from relays while the card is visible and opens the newest on a tap.
 */
@Composable
private fun NewerVersionBanner(
    baseNote: Note,
    signer: HexKey,
    module: MavenModule,
    version: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (version == null) return
    MavenVersionsFilterAssemblerSubscription(baseNote, signer, module, accountViewModel.dataSources().mavenVersions)

    val newest by remember(signer, module) {
        LocalCache
            .observeNotes(MavenReleaseEvent.filter(listOf(signer), module))
            .map { notes -> MavenVersions.newestRelease(notes.mapNotNull { it.event as? MavenReleaseEvent }, module) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }.collectAsStateWithLifecycle(initialValue = null)

    val release = newest ?: return
    val newer = release.version() ?: return
    if (!MavenVersion.isNewer(newer, version)) return

    val accent = KindTone.Positive.color()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.12f))
                .clickable {
                    val note = LocalCache.getOrCreateNote(release.id)
                    routeFor(note, accountViewModel.account)?.let { nav.nav(it) }
                }.padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Icon(MaterialSymbols.ArrowUpward, contentDescription = null, modifier = Modifier.size(15.dp), tint = accent)
        Text(
            text = stringRes(Res.string.kc_maven_newer, newer),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringRes(Res.string.kc_maven_newer_open),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
        Icon(MaterialSymbols.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
    }
}

/** A toggle that shows the Magpie settings block: needed once per project, so folded away. */
@Composable
private fun GradleSetup(settings: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { open = !open }
                .padding(vertical = 2.dp, horizontal = 2.dp),
    ) {
        Text(
            text = stringRes(Res.string.kc_maven_setup),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            if (open) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (open) {
        Text(
            text = stringRes(Res.string.kc_maven_setup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CopyableCode(settings)
    }
}

/** "Library release · 13 artifacts · 6 KB manifest · nostr.download". */
@Composable
private fun MavenFooter(summary: MavenReleaseSummary) {
    val servers = remember(summary) { summary.servers.map { HttpUrlFormatter.displayUrl(it) } }
    KindCardMeta(
        listOf(
            stringRes(if (summary.isSnapshot) Res.string.kc_maven_snapshot else Res.string.kc_maven_release),
            if (summary.artifactCount > 0) pluralStringRes(Res.plurals.kc_maven_artifacts, summary.artifactCount, summary.artifactCount) else null,
            summary.manifestSize?.let { stringRes(Res.string.kc_maven_manifest_size, formatFileSize(it)) },
            servers.firstOrNull(),
        ),
    )
}
