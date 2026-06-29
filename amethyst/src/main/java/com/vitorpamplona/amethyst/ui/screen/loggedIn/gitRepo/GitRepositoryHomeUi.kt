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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.git.GitCommit
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

private val CardShape = RoundedCornerShape(15.dp)

// ---------------------------------------------------------------------------
// Hero / identity — name, description and topics in the dashboard language.
// ---------------------------------------------------------------------------

/** The top-bar title: owner avatar + project name, with the repo description as a subtitle. */
@Composable
fun RepoTitleBar(
    event: GitRepositoryEvent?,
    fallback: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val owner = event?.pubKey?.let { LocalCache.checkGetOrCreateUser(it) }
    val description = event?.description()?.takeIf { it.isNotBlank() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (owner != null) {
            ClickableUserPicture(
                baseUser = owner,
                size = 28.dp,
                accountViewModel = accountViewModel,
                onClick = { nav.nav(Route.Profile(it.pubkeyHex)) },
            )
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = event?.name() ?: fallback,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The repository's topic chips and personal-fork badge (name + description live in the top bar). */
@Composable
fun RepoHero(event: GitRepositoryEvent) {
    val topics = remember(event) { event.hashtags().filter { it.isNotBlank() } }
    val isFork = event.isPersonalFork()
    if (topics.isEmpty() && !isFork) return

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isFork) PillChip(stringRes(R.string.git_repo_personal_fork))
        topics.forEach { PillChip("#$it") }
    }
}

@Composable
private fun PillChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.grayText,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------
// Maintainers — a compact avatar cluster.
// ---------------------------------------------------------------------------

@Composable
fun RepoMaintainersRow(
    event: GitRepositoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val maintainers = remember(event) { listOfNotNull(event.pubKey).plus(event.maintainers()).distinct() }
    if (maintainers.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringRes(R.string.git_repo_maintained_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.grayText,
        )
        maintainers.take(6).forEach { hex -> MaintainerAvatar(hex, accountViewModel, nav) }
        if (maintainers.size > 6) {
            Text(
                text = "+" + (maintainers.size - 6),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
private fun MaintainerAvatar(
    pubKeyHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val user = LocalCache.checkGetOrCreateUser(pubKeyHex) ?: return
    ClickableUserPicture(
        baseUser = user,
        size = 28.dp,
        accountViewModel = accountViewModel,
        onClick = { nav.nav(Route.Profile(it.pubkeyHex)) },
    )
}

// ---------------------------------------------------------------------------
// Social pulse — zaps, reactions and comments on the repository announcement.
// ---------------------------------------------------------------------------

@Composable
fun RepoSocialRow(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The standard note footer: reply, boost, like, zap (+ zapraiser and the reaction gallery),
    // so the repository announcement gets the exact same interactions as any other note.
    ReactionsRow(
        baseNote = note,
        showReactionDetail = true,
        addPadding = false,
        editState = null,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

// ---------------------------------------------------------------------------
// Stat tiles — git facts (branches, tags, files, last updated).
// ---------------------------------------------------------------------------

@Composable
fun RepoStatTiles(
    branches: Int,
    tags: Int,
    files: Int,
    updatedEpochSec: Long?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(MaterialSymbols.Commit, branches.toString(), stringRes(R.string.git_repo_stat_branches), Modifier.weight(1f))
        StatTile(MaterialSymbols.Tag, tags.toString(), stringRes(R.string.git_repo_stat_tags), Modifier.weight(1f))
        StatTile(MaterialSymbols.Description, compactCount(files), stringRes(R.string.git_repo_stat_files), Modifier.weight(1f))
        StatTile(MaterialSymbols.Schedule, updatedEpochSec?.let { relativeShort(it) } ?: "—", stringRes(R.string.git_repo_stat_updated), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(
    symbol: MaterialSymbol,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Language breakdown bar — proportional colours by file count.
// ---------------------------------------------------------------------------

class LanguageSlice(
    val name: String,
    val fraction: Float,
    val color: Color,
)

@Composable
fun RepoLanguageBar(slices: List<LanguageSlice>) {
    if (slices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
        ) {
            slices.forEach { slice ->
                Box(
                    modifier =
                        Modifier
                            .weight(slice.fraction.coerceAtLeast(0.0001f))
                            .height(10.dp)
                            .background(slice.color),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(slice.color))
                    Text(
                        text = slice.name + "  " + (slice.fraction * 100).toInt() + "%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
            }
        }
    }
}

private class LangDef(
    val name: String,
    val color: Long,
) {
    override fun equals(other: Any?) = other is LangDef && other.name == name

    override fun hashCode() = name.hashCode()
}

private val EXT_TO_LANG: Map<String, LangDef> =
    buildMap {
        fun add(
            def: LangDef,
            vararg exts: String,
        ) = exts.forEach { put(it, def) }
        add(LangDef("Kotlin", 0xFFA97BFF), "kt", "kts")
        add(LangDef("Java", 0xFFB07219), "java")
        add(LangDef("JavaScript", 0xFFF1E05A), "js", "mjs", "cjs", "jsx")
        add(LangDef("TypeScript", 0xFF3178C6), "ts", "tsx")
        add(LangDef("Python", 0xFF3572A5), "py")
        add(LangDef("Ruby", 0xFF701516), "rb")
        add(LangDef("Rust", 0xFFDEA584), "rs")
        add(LangDef("Go", 0xFF00ADD8), "go")
        add(LangDef("C", 0xFF555555), "c", "h")
        add(LangDef("C++", 0xFFF34B7D), "cpp", "cc", "cxx", "hpp", "hh", "hxx")
        add(LangDef("C#", 0xFF178600), "cs")
        add(LangDef("Swift", 0xFFF05138), "swift")
        add(LangDef("PHP", 0xFF4F5D95), "php")
        add(LangDef("Shell", 0xFF89E051), "sh", "bash", "zsh", "ksh")
        add(LangDef("HTML", 0xFFE34C26), "html", "htm")
        add(LangDef("CSS", 0xFF563D7C), "css")
        add(LangDef("SCSS", 0xFFC6538C), "scss")
        add(LangDef("Markdown", 0xFF083FA1), "md", "markdown")
        add(LangDef("JSON", 0xFF40803F), "json")
        add(LangDef("YAML", 0xFFCB171E), "yml", "yaml")
        add(LangDef("XML", 0xFF0060AC), "xml")
        add(LangDef("Gradle", 0xFF02303A), "gradle")
        add(LangDef("Dart", 0xFF00B4AB), "dart")
        add(LangDef("TOML", 0xFF9C4221), "toml")
    }

private const val OTHER_COLOR = 0xFFBBBBBB

/** Buckets files by recognised language (by extension), returning the top slices + an "Other" remainder. */
fun computeLanguageBreakdown(files: List<String>): List<LanguageSlice> {
    if (files.isEmpty()) return emptyList()
    val counts = HashMap<LangDef, Int>()
    var other = 0
    for (f in files) {
        val ext = f.substringAfterLast('.', "").lowercase()
        val def = EXT_TO_LANG[ext]
        if (def == null) other++ else counts[def] = (counts[def] ?: 0) + 1
    }
    val total = files.size.toFloat()
    val ranked = counts.entries.sortedByDescending { it.value }
    val top = ranked.take(6)
    val remainder = ranked.drop(6).sumOf { it.value } + other

    val slices =
        top.map { LanguageSlice(it.key.name, it.value / total, Color(it.key.color)) }.toMutableList()
    if (remainder > 0) {
        slices.add(LanguageSlice("Other", remainder / total, Color(OTHER_COLOR)))
    }
    return slices
}

// ---------------------------------------------------------------------------
// Last-commit strip.
// ---------------------------------------------------------------------------

@Composable
fun RepoLastCommit(
    commit: GitCommit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(MaterialSymbols.Commit, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = commit.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = commit.authorName + " · " + relativeShort(commit.authorTimeSec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = commit.shortOid,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.grayText,
        )
    }
}

// ---------------------------------------------------------------------------
// Recent activity pulse — newest issues / patches / status events.
// ---------------------------------------------------------------------------

@Composable
fun RepoActivityPulse(
    items: List<Note>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (items.isEmpty()) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 6.dp),
    ) {
        Text(
            text = stringRes(R.string.git_repo_recent_activity),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.grayText,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        items.forEach { ActivityRow(it, accountViewModel, nav) }
    }
}

@Composable
private fun ActivityRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event ?: return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav { routeFor(note, accountViewModel.account) } }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = activityIcon(event),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = gitSubjectOf(event) ?: stringRes(R.string.git_untitled),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TimeAgo(note)
    }
}

private fun activityIcon(event: Event): MaterialSymbol =
    when (event) {
        is GitIssueEvent -> MaterialSymbols.ErrorOutline
        is GitPullRequestEvent -> MaterialSymbols.CallMerge
        is GitPatchEvent -> MaterialSymbols.CallMerge
        else -> MaterialSymbols.Commit
    }

// ---------------------------------------------------------------------------
// Helpers.
// ---------------------------------------------------------------------------

/** Compacts large counts: 1234 -> "1.2k", 2_500_000 -> "2.5M". */
private fun compactCount(n: Int): String =
    when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> trimDecimal(n / 1_000.0) + "k"
        else -> trimDecimal(n / 1_000_000.0) + "M"
    }

private fun trimDecimal(v: Double): String {
    val rounded = (v * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** A coarse "time since" label for epoch seconds: "5m", "3h", "2d", "4w", "1y". */
private fun relativeShort(epochSec: Long): String {
    val deltaSec = (System.currentTimeMillis() / 1000) - epochSec
    if (deltaSec < 60) return "now"
    val mins = deltaSec / 60
    if (mins < 60) return mins.toString() + "m"
    val hours = mins / 60
    if (hours < 24) return hours.toString() + "h"
    val days = hours / 24
    if (days < 7) return days.toString() + "d"
    val weeks = days / 7
    if (weeks < 52) return weeks.toString() + "w"
    return (days / 365).toString() + "y"
}
