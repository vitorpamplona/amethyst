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
package com.vitorpamplona.quartz.nip50Search

import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentEvent
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.feedDefinition.FeedDefinitionEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.mediaStarterPack.MediaStarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableVideoEvent
import com.vitorpamplona.quartz.nip71Video.RegularVideoEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

/**
 * Decomposes every [SearchableEvent] into [IndexableFields] by priority tier:
 * title-like accessors primary, summary/description secondary, body tertiary,
 * with hashtag and location tags carried raw beside the tiers. Each explicit
 * branch splits exactly the accessors that kind's `indexableContent()`
 * concatenates — keep the two in sync when a kind's parsing changes. Kinds
 * without an explicit branch fall back to the [SearchableEvent] branch (whole
 * `indexableContent()` in the tertiary tier), so EVERY searchable kind,
 * current or future, is extracted.
 *
 * A kind may also fill the profile roles when it carries that shape: kind
 * 31990 goes through the kind-0 fields wholesale, and any kind with a
 * homepage/site URL fills [IndexableFields.websites]. Hashtags and `location`
 * tags are filled SYSTEMICALLY by the [tiers] funnel every content branch
 * uses, so recall never depends on a branch remembering them.
 *
 * Non-searchable kinds return [IndexableFields.None]. The extraction is
 * derived data baked into the build: stores should re-derive after upgrades
 * (see [com.vitorpamplona.quartz.nip01Core.store.IEventStore.reindexFullTextSearch]).
 */
object SearchFieldExtractor {
    /** Empty extractions always come back as [IndexableFields.None], whatever shape produced them. */
    fun extract(event: Event): IndexableFields = base(event).let { if (it.isEmpty()) IndexableFields.None else it }

    private fun base(event: Event): IndexableFields =
        when (event) {
            // kind 0 -> the profile fields, each in its own role.
            is MetadataEvent -> {
                val md = event.contactMetaData()
                if (md == null) {
                    IndexableFields.Profile()
                } else {
                    IndexableFields.Profile(
                        name = clean(md.name),
                        displayName = clean(md.displayName),
                        about = clean(md.about),
                        nip05 = clean(md.nip05),
                        lud16 = clean(md.lud16),
                        website = clean(md.website),
                    )
                }
            }

            is LongTextNoteEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is WikiNoteEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is ClassifiedsEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is GitRepositoryEvent -> {
                tiers(event, listOf(event.name()), listOf(event.description()), event.content, websites = event.webs())
            }

            is GitIssueEvent -> {
                tiers(event, event.subject(), null, event.content)
            }

            is GitPullRequestEvent -> {
                tiers(event, event.subject(), null, event.content)
            }

            is CommunityDefinitionEvent -> {
                tiers(event, listOf(event.name()), listOf(event.description(), event.rules()), event.content)
            }

            is EmojiPackEvent -> {
                tiers(event, event.titleOrName(), event.description(), event.content)
            }

            is ChannelCreateEvent -> {
                event.channelInfo().let { tiers(event, it.name, it.about, null) }
            }

            is ChannelMetadataEvent -> {
                event.channelInfo().let { tiers(event, it.name, it.about, null) }
            }

            is PictureEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is RegularVideoEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is AddressableVideoEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            // Torrents are searched by FILE NAME above all — index the file
            // list into the secondary tier, trackers as the affiliation URL.
            is TorrentEvent -> {
                tiers(event, listOf(event.title()), event.files().map { it.fileName }, event.content, websites = event.trackers())
            }

            is ThreadEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is FundraiserEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is NipTextEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is ExerciseTemplateEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is WorkoutRecordEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is CalendarEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is LiveActivitiesClipEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is CalendarDateSlotEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is CalendarTimeSlotEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is LiveActivitiesEvent -> {
                tiers(event, event.title(), event.summary(), event.content, website = event.streaming())
            }

            is InteractiveStoryBaseEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is MeetingSpaceEvent -> {
                tiers(event, event.room(), event.summary(), event.content)
            }

            is MeetingRoomEvent -> {
                tiers(event, event.title(), event.summary(), null)
            }

            // Code snippets are searched by language/runtime as much as name —
            // fold those keywords into the secondary tier, repo as affiliation.
            is CodeSnippetEvent -> {
                tiers(
                    event,
                    listOf(event.snippetName()),
                    listOf(event.snippetDescription(), event.language(), event.extension(), event.runtime()),
                    event.content,
                    websites = listOf(event.repo()),
                )
            }

            is BadgeDefinitionEvent -> {
                tiers(event, event.name(), event.description(), event.content)
            }

            is MusicPlaylistEvent -> {
                tiers(event, event.title(), event.description(), event.content)
            }

            is MusicTrackEvent -> {
                tiers(event, listOf(event.title()), listOf(event.artist(), event.album()), event.content)
            }

            is SoftwareApplicationEvent -> {
                tiers(event, listOf(event.name()), listOf(event.summary()), event.content, websites = listOf(event.url(), event.repository()))
            }

            is PodcastEpisodeEvent -> {
                tiers(event, event.title(), event.description(), event.content)
            }

            is PodcastMetadataEvent -> {
                tiers(event, listOf(event.title()), listOf(event.description()), null, websites = event.websites())
            }

            is GroupMetadataEvent -> {
                tiers(event, event.name(), event.about(), null)
            }

            is InterestSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is FollowListEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is MediaStarterPackEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is PictureCurationSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is ArticleCurationSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is VideoCurationSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is ReleaseArtifactSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is AppCurationSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is RelaySetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            // A web bookmark IS its URL — route it to the affiliation website
            // field so the bookmark is findable by its domain.
            is WebBookmarkEvent -> {
                tiers(event, event.title(), event.description(), null, website = event.url())
            }

            is NamedSiteEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is RootSiteEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is RootNappletEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is NappletSnapshotEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is NamedNappletEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is FeedDefinitionEvent -> {
                tiers(event, event.title(), null, null)
            }

            is LabeledBookmarkListEvent -> {
                tiers(event, event.titleOrName(), event.description(), null)
            }

            is PeopleListEvent -> {
                tiers(event, event.titleOrName(), event.description(), null)
            }

            is BookmarkListEvent -> {
                tiers(event, event.title(), null, null)
            }

            is OldBookmarkListEvent -> {
                tiers(event, event.title(), null, null)
            }

            is GoalEvent -> {
                tiers(event, null, event.summary(), event.content)
            }

            is HighlightEvent -> {
                tiers(event, emptyList(), listOf(event.comment(), event.context()), event.content)
            }

            is FileHeaderEvent -> {
                tiers(event, null, event.summary(), event.content)
            }

            is AudioTrackEvent -> {
                tiers(event, event.subject(), null, null)
            }

            // Buzz agent/workspace kinds carry their metadata as JSON in `content`;
            // split each decoded object the way its indexableContent() concatenates it.
            is AgentProfileEvent -> {
                event.profileOrNull()?.let { tiers(event, listOf(it.name, it.displayName), emptyList(), null) } ?: tiers(event, null, null, null)
            }

            is PersonaEvent -> {
                event.personaOrNull()?.let { tiers(event, it.displayName, null, it.systemPrompt) } ?: tiers(event, null, null, null)
            }

            is ManagedAgentEvent -> {
                event.agentOrNull()?.let { tiers(event, it.name, null, it.systemPrompt) } ?: tiers(event, null, null, null)
            }

            is TeamEvent -> {
                event.teamOrNull()?.let { tiers(event, it.name, it.description, it.instructions) } ?: tiers(event, null, null, null)
            }

            is WorkflowDefEvent -> {
                tiers(event, event.name(), null, event.content)
            }

            // kind 31990 — the app handler's metadata IS a UserMetadata clone,
            // so route it through the kind-0 profile fields: an app's
            // @-handle and site get the same treatment a person's do.
            is AppDefinitionEvent -> {
                val md = event.appMetaData()
                if (md == null) {
                    IndexableFields.Profile()
                } else {
                    IndexableFields.Profile(
                        // Per NIP-24 the deprecated `username` folds into `name`.
                        name = clean(md.name ?: md.username),
                        displayName = clean(md.displayName),
                        about = clean(md.about),
                        nip05 = clean(md.nip05),
                        lud16 = clean(md.lud16),
                        website = clean(md.website),
                    )
                }
            }

            // kind 1 LAST among the explicit branches, defensively: a future
            // kind extending the text-note base must hit its own branch first.
            is TextNoteEvent -> {
                tiers(event, event.subject(), null, event.content)
            }

            // Everything else Quartz can search, current or future: the whole
            // indexableContent lands in the tertiary tier.
            is SearchableEvent -> {
                tiers(event, null, null, event.indexableContent())
            }

            else -> {
                IndexableFields.None
            }
        }

    /** Single-value convenience over the list funnel — most kinds carry one title, one summary, one body. */
    private fun tiers(
        event: Event,
        primary: String?,
        secondary: String?,
        text: String?,
        website: String? = null,
    ) = tiers(event, listOf(primary), listOf(secondary), text, listOf(website))

    /**
     * The one funnel every content branch uses — [IndexableFields.Tiered.hashtags]
     * and [IndexableFields.Tiered.locations] are filled here, so no branch can
     * forget them. Values stay UNJOINED: separator choices belong to the backend.
     */
    private fun tiers(
        event: Event,
        primary: List<String?>,
        secondary: List<String?>,
        text: String?,
        websites: List<String?> = emptyList(),
    ) = IndexableFields.Tiered(
        primary = cleanAll(primary),
        secondary = cleanAll(secondary),
        text = clean(text),
        hashtags = cleanAll(event.tags.hashtags()),
        locations = locationValues(event),
        websites = cleanAll(websites),
    )

    /** Trim and drop empties at the single funnel every derived string passes through. */
    private fun clean(s: String?): String? = s?.trim()?.ifEmpty { null }

    private fun cleanAll(parts: List<String?>): List<String> = parts.mapNotNull { clean(it) }

    /**
     * Every `location` tag value, on ANY kind. Deliberately a raw scan, not a
     * typed accessor: Quartz's LocationTag classes are per-NIP (calendar,
     * picture, classifieds) and only those kinds expose locations(), while
     * this funnel must also catch location tags on kinds whose class doesn't
     * model them.
     */
    private fun locationValues(event: Event): List<String> = event.tags.mapNotNull { tag -> if (tag.getOrNull(0) != "location") null else clean(tag.getOrNull(1)) }
}
