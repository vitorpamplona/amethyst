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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ebook_open_file
import com.vitorpamplona.amethyst.commons.resources.ebook_read
import com.vitorpamplona.amethyst.commons.resources.magazine_issue_label
import com.vitorpamplona.amethyst.commons.resources.magazine_issues
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.note.platform.PdfViewerDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.collections.immutable.toImmutableList

// A card lists the latest issues rather than a whole run; the rest are a tap away in each issue.
// A quoted magazine gets fewer, since each row loads a cover and observes its own note.
private const val MAX_PREVIEW_ISSUES = 12
private const val MAX_QUOTED_ISSUES = 3

private val MaxMastheadHeight = 240.dp

private const val ISSUE_COVER_ASPECT = 2f / 3f
private val IssueCoverWidth = 40.dp

/*
 * PressStr's three kinds: ebooks (33953), magazines (34609) and magazine issues (39731).
 *
 * Ebooks and issues are files with a cover, so they share one card: the cover beside the title,
 * a byline, what the file is, the blurb and a button that opens it. A PDF opens in the in-app
 * reader. The card never draws the inline first-page preview a PDF link gets: that downloads the
 * whole file to show a page the `image` cover already shows, and issues in the wild run to 150 MB.
 * Anything else (EPUB) goes to the system, since no reader in the app can show it.
 */

/** Renders a kind-33953 PressStr ebook. */
@Composable
fun RenderEbook(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: 33953 is addressable, so a revised ebook replaces the event on the
    // same Note instance, and a bare read of `event` registers no snapshot dependency.
    val observedEvent by observeNoteEvent<EbookEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val file = remember(noteEvent) { pressStrFile(note, noteEvent.url(), noteEvent.mimeType(), noteEvent.title(), noteEvent.hash()) }
    val open = rememberFileOpener(file, accountViewModel)

    Column(Modifier.fillMaxWidth()) {
        PressStrHeader(
            cover = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
            title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
            byline = remember(noteEvent) { noteEvent.authors().joinToString(", ").ifBlank { null } },
            // What a reader decides on before opening a book: its format, size, language and age.
            detail =
                remember(noteEvent) {
                    detailLine(
                        noteEvent.formatLabel(),
                        noteEvent.size(),
                        noteEvent.language(),
                        noteEvent.publishedAt(),
                    )
                },
            summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
            onCoverClick = open,
            accountViewModel = accountViewModel,
        )

        PressStrBody(note, noteEvent, makeItShort, canPreview, quotesLeft, backgroundColor, file, open, accountViewModel, nav)
    }
}

/**
 * Renders a kind-39731 magazine issue, with its magazine's name as the byline.
 *
 * The magazine is resolved from the issue's `a` tag. Issues routinely reach a relay their
 * magazine never did, so until (or unless) it arrives the byline is simply absent.
 */
@Composable
fun RenderMagazineIssue(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: 39731 is addressable, so a re-uploaded issue replaces the event on
    // the same Note instance.
    val observedEvent by observeNoteEvent<MagazineIssueEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val file = remember(noteEvent) { pressStrFile(note, noteEvent.url(), noteEvent.mimeType(), noteEvent.title(), noteEvent.hash()) }
    val open = rememberFileOpener(file, accountViewModel)
    // Anyone can tag a magazine's address. Only its own publisher's issues belong to it, so a
    // stranger's issue gets no byline rather than borrowing a magazine it is not part of.
    val magazine =
        remember(noteEvent) {
            noteEvent.magazine()?.takeIf { it.pubKeyHex == noteEvent.pubKey }?.let { Address(it.kind, it.pubKeyHex, it.dTag) }
        }
    val issueLabel = remember(noteEvent) { noteEvent.issue() }

    Column(Modifier.fillMaxWidth()) {
        val header =
            @Composable { byline: String?, onBylineClick: (() -> Unit)? ->
                PressStrHeader(
                    cover = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
                    title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
                    byline = byline,
                    onBylineClick = onBylineClick,
                    detail =
                        listOfNotNull(
                            issueLabel?.let { issueLabelText(it) },
                            remember(noteEvent) { detailLine(noteEvent.formatLabel(), noteEvent.size(), null, noteEvent.publishedAt()) },
                        ).takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
                    onCoverClick = open,
                    accountViewModel = accountViewModel,
                )
            }

        if (magazine != null) {
            LoadAddressableNote(magazine) { magazineNote ->
                if (magazineNote != null) {
                    val magazineEvent by observeNoteEvent<MagazineEvent>(magazineNote, accountViewModel)
                    header(magazineEvent?.titleOrIdentifier()) { nav.nav(Route.Note(magazineNote.idHex)) }
                } else {
                    header(null, null)
                }
            }
        } else {
            header(null, null)
        }

        PressStrBody(note, noteEvent, makeItShort, canPreview, quotesLeft, backgroundColor, file, open, accountViewModel, nav)
    }
}

/**
 * Renders a kind-34609 magazine: its masthead, what it is about, and its latest issues.
 *
 * Nothing on the magazine lists its issues; each issue points back at it with an `a` tag. The
 * engagement watcher fetches those (`FilterRepliesAndReactionsToAddresses`) while the magazine is
 * on screen, and the card lists the ones in the cache. Only the magazine's own author can add an
 * issue to it here: anyone can tag the address, but an issue someone else signed is not part of
 * the publication.
 */
@Composable
fun RenderMagazine(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val observedEvent by observeNoteEvent<MagazineEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val masthead = remember(noteEvent) { noteEvent.image()?.ifBlank { null } }
    val summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } }
    val website = remember(noteEvent) { noteEvent.website()?.ifBlank { null } }
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxWidth()) {
        masthead?.let {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(6.dp))) {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = stringRes(Res.string.preview_card_image_for, it),
                    // Fit within a capped height: a masthead is a logo, so it must not be cropped,
                    // and a tall one must not take over the feed.
                    contentScale = ContentScale.Fit,
                    mainImageModifier = Modifier.fillMaxWidth().heightIn(max = MaxMastheadHeight),
                    loadedImageModifier = Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = null,
                    onError = null,
                )
            }
        }

        Text(
            text = remember(noteEvent) { noteEvent.titleOrIdentifier() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        summary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        website?.let {
            Text(
                text = it.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.lessImportantLink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // runCatching: the handler throws when no app takes the link, and the URL is the publisher's.
                modifier = Modifier.padding(top = 4.dp).clickable { runCatching { uriHandler.openUri(it) } },
            )
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        MagazineIssues(noteEvent, if (makeItShort) MAX_QUOTED_ISSUES else MAX_PREVIEW_ISSUES, accountViewModel, nav)

        DisplayUncitedHashtags(noteEvent, note.toNostrUri(), accountViewModel, nav)
    }
}

@Composable
private fun MagazineIssues(
    magazine: MagazineEvent,
    maxIssues: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val address = remember(magazine) { magazine.addressTag() }
    val issuesFlow =
        remember(address) {
            accountViewModel.account.cache.observeNotes(
                Filter(
                    kinds = listOf(MagazineIssueEvent.KIND),
                    authors = listOf(magazine.pubKey),
                    tags = mapOf("a" to listOf(address)),
                ),
            )
        }

    val notes by issuesFlow.collectAsStateWithLifecycle(emptyList())

    // Newest issue first, by when it came out rather than when it was posted: a publisher
    // uploading a back catalogue posts the oldest issue last. Each key is read once before the
    // sort, since publishedAt() scans the tags. The list only re-emits when an issue is added, so
    // each row observes its own note for edits.
    val issues =
        remember(notes, maxIssues) {
            notes
                .map { issueNote ->
                    val issue = issueNote.event as? MagazineIssueEvent
                    issueNote to (issue?.let { it.publishedAt() ?: it.createdAt } ?: Long.MIN_VALUE)
                }.sortedByDescending { it.second }
                .take(maxIssues)
                .map { it.first }
                .toImmutableList()
        }

    if (issues.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringRes(Res.string.magazine_issues),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        issues.forEachIndexed { index, issueNote ->
            MagazineIssueRow(issueNote, address, showDivider = index > 0, accountViewModel) { nav.nav(Route.Note(issueNote.idHex)) }
        }
    }
}

@Composable
private fun MagazineIssueRow(
    issueNote: Note,
    magazineAddress: String,
    showDivider: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    // Observed, not read once: an edited issue replaces the event on the same Note, and the list
    // above does not re-emit for it.
    val observedIssue by observeNoteEvent<MagazineIssueEvent>(issueNote, accountViewModel)
    val issue = observedIssue ?: return

    // A newer version can move the issue to another magazine, or drop the link.
    val stillHere = remember(issue) { issue.magazine()?.toTag() == magazineAddress }
    if (!stillHere) return

    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        val cover = remember(issue) { issue.image()?.ifBlank { null } }
        val shape = RoundedCornerShape(3.dp)
        val coverModifier = Modifier.width(IssueCoverWidth).aspectRatio(ISSUE_COVER_ASPECT).clip(shape)

        Box(coverModifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            cover?.let {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = stringRes(Res.string.preview_card_image_for, it),
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier.fillMaxSize(),
                    loadedImageModifier = coverModifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = null,
                    onError = null,
                )
            }
        }

        Column(Modifier.padding(start = 10.dp)) {
            Text(
                text = remember(issue) { issue.titleOrIdentifier() },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val label = remember(issue) { issue.issue() }?.let { issueLabelText(it) }
            val date = remember(issue) { issue.publishedAt()?.let { formatLongDate(it) } }
            listOfNotNull(label, date).takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }
    }
}

/**
 * "Issue 7" for a bare number, the label as published otherwise: publishers also write
 * "Issue 7", "May 2024" or "Spring 2025", and "Issue Issue 7" or "Issue May 2024" read wrong.
 */
@Composable
private fun issueLabelText(label: String): String = if (label.first().isDigit()) stringRes(Res.string.magazine_issue_label, label) else label

/** The cover beside the title, byline, file details and summary: the top of a book-like card. */
@Composable
private fun PressStrHeader(
    cover: String?,
    title: String,
    byline: String?,
    detail: String?,
    summary: String?,
    onCoverClick: (() -> Unit)?,
    accountViewModel: AccountViewModel,
    onBylineClick: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(if (onCoverClick != null) Modifier.clickable(onClick = onCoverClick) else Modifier) {
            PublicationCover(cover, accountViewModel)
        }

        Column(Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            byline?.let {
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (onBylineClick != null) MaterialTheme.colorScheme.lessImportantLink else MaterialTheme.colorScheme.grayText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onBylineClick != null) Modifier.clickable(onClick = onBylineClick) else Modifier,
                )
            }

            detail?.let {
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }

            summary?.let {
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The blurb, the button that opens the file, and the hashtags: the bottom of a book-like card. */
@Composable
private fun PressStrBody(
    note: Note,
    noteEvent: Event,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    file: PressStrFile?,
    open: (() -> Unit)?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (noteEvent.content.isNotBlank()) {
        val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

        TranslatableRichTextViewer(
            content = noteEvent.content,
            canPreview = canPreview && !makeItShort,
            quotesLeft = quotesLeft,
            modifier = Modifier.fillMaxWidth(),
            tags = tags,
            backgroundColor = backgroundColor,
            id = note.idHex,
            callbackUri = note.toNostrUri(),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (file != null && open != null) {
        FilledTonalButton(onClick = open, modifier = Modifier.padding(top = 6.dp)) {
            Icon(
                symbol = MaterialSymbols.MenuBook,
                contentDescription = null,
                modifier = Size18Modifier,
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringRes(if (file.pdf != null) Res.string.ebook_read else Res.string.ebook_open_file))
        }
    }

    DisplayUncitedHashtags(noteEvent, note.toNostrUri(), accountViewModel, nav)
}

/** The file a card opens: [pdf] when the in-app reader can show it, else just its [url]. */
private class PressStrFile(
    val url: String,
    val pdf: MediaUrlPdf?,
)

private fun pressStrFile(
    note: Note,
    url: String?,
    mimeType: String?,
    title: String?,
    hash: String?,
): PressStrFile? {
    val fileUrl = url?.ifBlank { null } ?: return null
    val pdf =
        if (RichTextParser.classifyMedia(fileUrl, mimeType) == MediaContentKind.PDF) {
            MediaUrlPdf(url = fileUrl, description = title, hash = hash, uri = note.toNostrUri(), mimeType = mimeType)
        } else {
            null
        }
    return PressStrFile(fileUrl, pdf)
}

/** Opens [file] in the in-app reader when it is a PDF, else in the system; null when there is none. */
@Composable
private fun rememberFileOpener(
    file: PressStrFile?,
    accountViewModel: AccountViewModel,
): (() -> Unit)? {
    // Keyed on the URL, not the file object: a new version of the event rebuilds the object, and
    // keying on it closed an open reader whenever an edit arrived.
    val url = file?.url
    var reading by remember(url) { mutableStateOf(false) }
    val currentFile by rememberUpdatedState(file)
    val uriHandler by rememberUpdatedState(LocalUriHandler.current)

    val pdf = file?.pdf
    if (reading && pdf != null) {
        PdfViewerDialog(pdf, onDismiss = { reading = false }, accountViewModel = accountViewModel)
    }

    return remember(url) {
        if (url == null) {
            null
        } else {
            {
                val current = currentFile
                if (current?.pdf != null) {
                    reading = true
                } else if (current != null) {
                    // The handler throws when no app takes the scheme (`ipfs:`, `magnet:`, a typo).
                    runCatching { uriHandler.openUri(current.url) }
                }
            }
        }
    }
}

/** "PDF · 2 MB · EN · Tue, Jun 16, 2026", skipping whatever the event left out. */
private fun detailLine(
    format: String?,
    size: Long?,
    language: String?,
    publishedAt: Long?,
): String? =
    listOfNotNull(
        format,
        size?.let { countToHumanReadableBytes(it) },
        language?.uppercase(),
        publishedAt?.let { formatLongDate(it) },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** "PDF", "EPUB", or the MIME subtype as published when it is something else. */
private fun EbookEvent.formatLabel(): String? =
    when {
        isPdf() -> "PDF"
        isEpub() -> "EPUB"
        else -> mimeType()?.substringAfter('/')?.ifBlank { null }?.uppercase()
    }

private fun MagazineIssueEvent.formatLabel(): String? = if (isPdf()) "PDF" else mimeType().substringAfter('/').ifBlank { null }?.uppercase()
