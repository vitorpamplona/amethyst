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
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactEvent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentEvent
import com.vitorpamplona.quartz.buzz.mpProjects.ProjectEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.ChannelSummaryEvent
import com.vitorpamplona.quartz.buzz.teamCatalog.TeamCatalogEvent
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmServerAnnouncementEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoAvatarEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoObjectEvent
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.citations.CitationEvent
import com.vitorpamplona.quartz.experimental.citations.HardcopyCitationEvent
import com.vitorpamplona.quartz.experimental.citations.PromptCitationEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.searchableListDescriptions
import com.vitorpamplona.quartz.experimental.decentralizedLists.searchableListExtraText
import com.vitorpamplona.quartz.experimental.decentralizedLists.searchableListTitles
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutTemplateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.predictionMarkets.PredictionMarketEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.trustedLists.TrustedListEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.BuildVerificationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.feedDefinition.FeedDefinitionEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.marketplace.MarketplaceEvent
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip23LongContent.draft.LongFormDraftEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.mediaStarterPack.MediaStarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableVideoEvent
import com.vitorpamplona.quartz.nip71Video.RegularVideoEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent

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

            is LongFormContentEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is LongFormDraftEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            is WikiArticleEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            // location() and categories() (`t`) close its indexableContent(),
            // and the funnel already carries both roles, so they are not passed.
            is ClassifiedsEvent -> {
                tiers(event, event.title(), event.summary(), event.content)
            }

            // NIP-15 kinds 30017/30018/30019/30020 -- the marketplace family
            // keeps its name and description inside a JSON `content` blob, so
            // the fallback dropped a stall/product/auction NAME into the body
            // tier, where a title can never reach the title band. Decoding
            // failures still reach the funnel, as they do for the buzz kinds.
            is StallEvent -> {
                event.stallData()?.let { tiers(event, it.name, it.description, null) } ?: tiers(event, null, null, null)
            }

            // ProductEvent.categories() is `t` under another name, so the
            // funnel already carries it in the hashtag role -- passing it
            // again would index the same words twice.
            is ProductEvent -> {
                event.productData()?.let { tiers(event, it.name, it.description, null) } ?: tiers(event, null, null, null)
            }

            is MarketplaceEvent -> {
                event.marketplaceData()?.let { tiers(event, it.name, it.about, null) } ?: tiers(event, null, null, null)
            }

            is AuctionEvent -> {
                event.auctionData()?.let { tiers(event, it.name, it.description, null) } ?: tiers(event, null, null, null)
            }

            // The clone URL is as much a repository's public address as its
            // homepage is -- `github.com/owner/repo.git` is how most people
            // would search for it -- so both fill the affiliation role.
            is GitRepositoryEvent -> {
                tiers(event, listOf(event.name()), listOf(event.description()), event.content, websites = (event.webs() + event.clones()).distinct())
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

            // kind 30030 -- `content` is NIP-44 ciphertext (private emoji tags),
            // never indexed; the public shortcodes are keywords beside the description.
            is EmojiPackEvent -> {
                tiers(event, listOf(event.titleOrName()), listOf(event.description()) + event.publicEmojiCodes(), null)
            }

            // kind 30031 -- the pack's name is the title; its description and every
            // sticker shortcode are what a picker search matches. Never the content.
            is StickerPackEvent -> {
                tiers(event, listOf(event.title()), listOf(event.description()) + event.stickers().map { it.code }, null)
            }

            // kind 36767 -- a shareable theme's name and description; never its colors,
            // font families or URLs, and `content` is empty by spec.
            is ThemeDefinitionEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is ChannelCreateEvent -> {
                event.channelInfo().let { tiers(event, it.name, it.about, null) }
            }

            is ChannelMetadataEvent -> {
                event.channelInfo().let { tiers(event, it.name, it.about, null) }
            }

            // Each image's imeta `alt` describes that picture, so it is the
            // summary tier; location() closes indexableContent() but is the
            // funnel's role already, so it is not passed.
            is PictureEvent -> {
                tiers(event, listOf(event.title()), event.imageDescriptions(), event.content)
            }

            // NIP-71 chapter (`segment`) titles are headings inside the video:
            // keywords beside the title, like a torrent's file names.
            is RegularVideoEvent -> {
                tiers(event, listOf(event.title()), event.segmentTitles(), event.content)
            }

            is AddressableVideoEvent -> {
                tiers(event, listOf(event.title()), event.segmentTitles(), event.content)
            }

            // kind 64 -- a PGN's player/event/site/opening tag pairs are the
            // names a game is found by; its {comments} are the prose. The move
            // text is never indexed (see PgnSearchText).
            is ChessGameEvent -> {
                event.searchText().let { tiers(event, it.headers, emptyList(), it.comments.joinToString("\n").ifEmpty { null }) }
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

            // kind 11316 -- a ContextVM server's name and blurb; the content
            // is the MCP initialize result (machine JSON) and stays out.
            is CvmServerAnnouncementEvent -> {
                tiers(event, event.serverName(), event.about(), null)
            }

            // kinds 11333/33331 -- the `name` tag is the shape's only human
            // text; the content is SNO geometry JSON.
            is SnoAvatarEvent -> {
                tiers(event, event.nameTag(), null, null)
            }

            is SnoObjectEvent -> {
                tiers(event, event.nameTag(), null, null)
            }

            // kinds 31/32/33 -- what the source is called is the title band;
            // the bibliographic names (author, editor, publisher, container,
            // model) are the keywords beside the citer's summary; the
            // citer's note is the body. Subclasses before the base.
            is HardcopyCitationEvent -> {
                tiers(
                    event,
                    listOf(event.title(), event.chapterTitle()),
                    listOf(event.summary(), event.author(), event.editor(), event.publishedIn(), event.publishedBy()),
                    event.content,
                )
            }

            is PromptCitationEvent -> {
                tiers(event, listOf(event.title()), listOf(event.summary(), event.author(), event.publishedBy(), event.llm()), event.content)
            }

            is CitationEvent -> {
                tiers(event, listOf(event.title()), listOf(event.summary(), event.author(), event.publishedBy()), event.content)
            }

            // kind 30142 -- both vocabularies' names are titles; descriptions,
            // the author and the facet labels (subjects, types, levels) are
            // the keywords that qualify it.
            is LearningResourceEvent -> {
                tiers(
                    event,
                    listOf(event.title(), event.alternateTitle()),
                    listOf(event.summary(), event.alternateSummary(), event.author()) + event.facetLabels(),
                    event.content,
                )
            }

            // kind 38000 (BAO Markets) -- the question is the title, the
            // outcome labels / resolution / cancel reason are short values
            // matched whole, and the social post is the body only when no
            // description supersedes it (see socialPost()).
            is PredictionMarketEvent -> {
                tiers(
                    event,
                    listOf(event.title()),
                    listOf(event.description()) + event.outcomes() + listOf(event.resolution(), event.cancelReason()),
                    event.socialPost(),
                )
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

            is WorkoutTemplateEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is CalendarCollectionEvent -> {
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

            // kinds 30296/30297 -- the option labels are the choices a reader
            // picks between: short answer-like values, so they sit in the
            // secondary tier unjoined, as poll options do.
            is InteractiveStoryBaseEvent -> {
                tiers(event, listOf(event.title()), listOf(event.summary()) + event.optionLabels(), event.content)
            }

            // endpoint() is the `streaming` tag -- the same role
            // LiveActivitiesEvent.streaming() fills above.
            is MeetingSpaceEvent -> {
                tiers(event, event.room(), event.summary(), event.content, website = event.endpoint())
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

            // kinds 30054/30055 -- the Podcasting 2.0 pair carries the same
            // title/description shape kind 54 does and was falling through:
            // an episode title indexed as body text. topics() is hashtags()
            // under another name, so the funnel carries it once. Host/guest
            // names and soundbite titles are short keywords beside the
            // description, like a music track's artist and album.
            is Podcasting20EpisodeEvent -> {
                tiers(event, listOf(event.title()), listOf(event.description()) + event.personNames() + event.soundbiteTitles(), event.content)
            }

            is Podcasting20TrailerEvent -> {
                tiers(event, event.title(), null, event.content)
            }

            is GroupMetadataEvent -> {
                tiers(event, event.name(), event.about(), null)
            }

            // kind 9002 edits the very metadata kind 39000 publishes, so it
            // splits the same way -- it was the only half of the pair falling
            // through. Its hashtags() is `t`, carried once by the funnel.
            is GroupEditMetadataEvent -> {
                tiers(event, event.name(), event.about(), null)
            }

            // kind 9007 -- the create carries the same name/about pair (Buzz requires it).
            is CreateGroupEvent -> {
                tiers(event, event.name(), event.about(), null)
            }

            // kind 39003 -- role names and their descriptions are short keyword-like values.
            is GroupRolesEvent -> {
                tiers(event, emptyList(), event.roles().flatMap { listOf(it.name, it.description) }, null)
            }

            is InterestSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is StarterPackEvent -> {
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

            // kind 30063 -- the body is a NIP-82 release's notes, and nothing for a
            // NIP-51 set (its content may be encrypted private items); the event
            // owns that guard, so both search paths apply the same one.
            is ReleaseArtifactSetEvent -> {
                tiers(event, event.title(), event.description(), event.searchableReleaseNotes())
            }

            is AppCurationSetEvent -> {
                tiers(event, event.titleOrName(), event.description(), null)
            }

            is RelaySetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            // A web bookmark IS its URL — route it to the affiliation website
            // field so the bookmark is findable by its domain.
            is WebBookmarkEvent -> {
                tiers(event, event.title(), event.description(), null, website = event.url())
            }

            // A site's `source` tag is the URL its files were published
            // from -- the same affiliation role a repo's homepage fills.
            is NamedSiteEvent -> {
                tiers(event, event.title(), event.description(), null, website = event.source())
            }

            is RootSiteEvent -> {
                tiers(event, event.title(), event.description(), null, website = event.source())
            }

            is SiteSnapshotEvent -> {
                tiers(event, event.title(), event.description(), null, website = event.source())
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

            // kinds 30392-30395 -- a Trusted List's title is the ONLY
            // human-authored text the family carries (`content` is a machine
            // echo of the membership, the member tags are hex ids, and
            // `metric`/`d` name the computation), so `indexableContent()` is
            // exactly `title()`. Without this branch the fallback below put
            // that title in the TERTIARY tier: a list titled "Verified Human"
            // matched the words on the same rung as a bio that happens to
            // mention them, and lost the prefix/typo columns a title gets.
            is TrustedListEvent -> {
                tiers(event, event.title(), null, null)
            }

            // kind 30382 -- a contact card's petname is a trust provider's
            // NAME for a person, the direct analogue of kind 0's `name`, and
            // its summary is the description beside it. The encrypted half of
            // the card stays out, as it always has: petName()/summary() read
            // the public tag array only, and build() puts both in the NIP-44
            // content, so a card authored by this library has NO public text
            // beyond its topics.
            //
            // topics() is deliberately absent: it is `TopicTag`, which is the
            // `t` tag under another name (same predicate, same array), so the
            // tiers() funnel already carries every topic in the hashtag role.
            // Passing them again would index the same words twice -- which is
            // what the fallback below was doing, since indexableContent()
            // concatenates the topics INTO the body while the funnel added
            // them as hashtags. Whether that role is tokenized or kept as
            // keywords is the backend's call, per IndexableFields.
            is UserAssertionEvent -> {
                tiers(event, event.petName(), event.summary(), null)
            }

            is FeedDefinitionEvent -> {
                tiers(event, event.title(), null, null)
            }

            is BookmarkSetEvent -> {
                tiers(event, event.titleOrName(), event.description(), null)
            }

            is FollowSetEvent -> {
                tiers(event, event.titleOrName(), event.description(), null)
            }

            is BookmarkListEvent -> {
                tiers(event, event.titleOrName(), null, null)
            }

            // kind 30008 -- a general badge set's NIP-51 title and description.
            is AcceptedBadgeSetEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            // kind 33534 -- a relay role's label is its name; the description explains it.
            is RelayRoleEvent -> {
                tiers(event, event.label(), event.description(), null)
            }

            is OldBookmarkListEvent -> {
                tiers(event, event.title(), null, null)
            }

            is ZapGoalEvent -> {
                tiers(event, null, event.summary(), event.content)
            }

            is HighlightEvent -> {
                tiers(event, emptyList(), listOf(event.comment(), event.searchableContext()), event.content)
            }

            is FileMetadataEvent -> {
                tiers(event, null, event.summary(), event.content)
            }

            // kinds 1065/1163 -- summary-only kinds. Their whole searchable
            // text IS a summary, so it belongs in the summary tier, next to
            // kind 1063's, rather than in the body tier the fallback gave it.
            is ChatMessageEncryptedFileHeaderEvent -> {
                tiers(event, null, event.summary(), null)
            }

            // kind 1065 now indexes its caption (`content`) too, exactly as
            // kind 1063 does, so it splits the same way.
            is FileStorageHeaderEvent -> {
                tiers(event, null, event.summary(), event.content)
            }

            is ProfileGalleryEntryEvent -> {
                tiers(event, null, event.summary(), null)
            }

            // kind 2473 -- a sighting IS its species, under both names: the
            // scientific one from the `n` tag and the vernacular one
            // commonName() parses out of the `alt`. The `alt` itself still
            // goes to the summary tier whole. It is usually just Birdstar's
            // boilerplate wrapper around those two names ("Bird detection:
            // <Common> (<Scientific>)"), so this repeats them in a second
            // role -- but only commonName()'s PREFIX match decides that the
            // alt is boilerplate, and a publisher can write anything after
            // the parenthetical. Dropping the alt on a prefix match lost that
            // tail from every role, which is precisely the drift against
            // indexableContent() this file exists to prevent: the repeat
            // costs a duplicate in the weakest role, the drop cost recall.
            is BirdDetectionEvent -> {
                tiers(event, listOf(event.speciesName(), event.commonName()), listOf(event.summary()), null)
            }

            // kind 12473 is a life LIST, not a sighting: its species names are
            // an unbounded collection, so they sit in the secondary tier with
            // a torrent's file names rather than claiming the title band once
            // per bird.
            is BirdexEvent -> {
                tiers(event, emptyList(), listOf(event.summary()) + event.speciesNames(), null)
            }

            // kind 38192 -- the save's title is a title; region and filename
            // are the keywords beside it, like a torrent's file names.
            is Ps1SaveEvent -> {
                tiers(event, listOf(event.saveTitle()), listOf(event.summary(), event.region(), event.filename()), null)
            }

            // kind 38383 -- an order is looked up by who is offering it;
            // currency and payment methods are the keywords that qualify it.
            is P2POrderEvent -> {
                tiers(event, listOf(event.makerName()), listOf(event.currency()) + event.paymentMethods().orEmpty(), null)
            }

            // kind 38385 -- a Mostro instance is looked up by its name; the currencies it
            // trades are the keywords that qualify it, as on an order.
            is MostroInfoEvent -> {
                tiers(event, listOf(event.instanceName()), event.fiatCurrenciesAccepted().orEmpty(), null)
            }

            // kinds 30301 / 30302 -- a Kanban board or card is found by its title; the description
            // qualifies it, as a list's does.
            is KanbanBoardEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            is KanbanCardEvent -> {
                tiers(event, event.title(), event.description(), null)
            }

            // kind 30301 (WalletScrutiny) -- the one-line description names what was reproduced;
            // the markdown report is the body.
            is BuildVerificationEvent -> {
                tiers(event, event.description(), null, event.report())
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
                event.personaOrNull()?.let { tiers(event, it.displayName, it.description, it.systemPrompt) } ?: tiers(event, null, null, null)
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

            // kind 30178 -- the team and its members are what it is called; the
            // instructions and member prompts are the body indexableContent() joins.
            is TeamCatalogEvent -> {
                event.catalogOrNull()?.let { catalog ->
                    tiers(
                        event,
                        listOf(catalog.name) + catalog.members.map { it.displayName },
                        listOf(catalog.description),
                        (listOf(catalog.instructions) + catalog.members.map { it.systemPrompt }).filterNotNull().joinToString("\n"),
                    )
                } ?: tiers(event, null, null, null)
            }

            // kind 45010 -- textBody() already drops a JSON (non-prose) payload.
            is ArtifactEvent -> {
                tiers(event, event.title(), null, event.textBody())
            }

            // kind 30621 -- the name (or slug) titles the project; its description is secondary.
            is ProjectEvent -> {
                tiers(event, event.displayName(), event.description(), null)
            }

            is ChannelSummaryEvent -> {
                event.summary()?.let { tiers(event, listOf(it.name), listOf(it.about, it.topic, it.purpose), null) } ?: tiers(event, null, null, null)
            }

            // kind 40008 -- the body is a unified diff; only the summary is indexed.
            is StreamMessageDiffEvent -> {
                tiers(event, null, event.description(), null)
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

            // kind 1010 -- `content` is the proposed replacement text and
            // `summary` describes the edit, so they split the way kind 1063's
            // summary and body do (indexableContent concatenates them in the
            // other order; the roles, not the order, are what a weighted
            // backend reads).
            is TextNoteModificationEvent -> {
                tiers(event, null, event.summary(), event.content)
            }

            // kinds 1068/6969 -- the question is the body, the option labels
            // are short answer-like values a searcher matches whole, so they
            // sit in the secondary tier UNJOINED instead of being appended to
            // the body the way indexableContent() has to.
            is PollEvent -> {
                tiers(event, emptyList(), event.options().map { it.label }, event.content)
            }

            is ZapPollEvent -> {
                tiers(event, emptyList(), event.pollOptionsArray().map { it.descriptor }, event.content)
            }

            // kind 1985 -- the label values are the keywords of the event;
            // the reasoning, if any, is the body.
            is LabelEvent -> {
                tiers(event, emptyList(), event.labels().map { it.label }, event.content)
            }

            // kinds 1111/1311 -- body-only kinds whose indexableContent()
            // concatenates the `t` tags INTO the body. The funnel already
            // carries them in the hashtag role, so passing the body alone
            // stops the same words being indexed twice (the UserAssertion
            // reasoning above, applied to the two chat/comment kinds).
            is CommentEvent -> {
                tiers(event, null, null, event.content)
            }

            is LiveActivitiesChatMessageEvent -> {
                tiers(event, null, null, event.content)
            }

            // kinds 9998/39998/9999/39999 -- a list's or item's names and titles are
            // what it is called, its description and comments what it is about. The
            // `t` values indexableContent() appends are the funnel's hashtag role
            // already, so -- as for 1111/1311/30382 -- they are not passed again, and
            // content is not part of the spec. One branch for all four: an item may
            // carry header tags (the spec's nonstandard method), and the tag helpers
            // read whichever are present. Every other natural-language tag value
            // (author, subject, artist, ...) is the text tier, below both.
            is DecentralizedListEvent -> {
                tiers(event, event.tags.searchableListTitles(), event.tags.searchableListDescriptions(), event.tags.searchableListExtraText())
            }

            // kind 1 LAST among the explicit branches, defensively: a future
            // kind extending the text-note base must hit its own branch first.
            // kind 14 -- a NIP-14 subject names the conversation, the body is the message.
            is ChatMessageEvent -> {
                tiers(event, event.subject(), null, event.content)
            }

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

    /**
     * Single-value convenience over the list funnel — most kinds carry one
     * title, one summary, one body. Cleans each value straight into its role
     * rather than wrapping it in a list first: extraction runs once per stored
     * event, and the wrappers were three throwaway lists on every one of them.
     */
    private fun tiers(
        event: Event,
        primary: String?,
        secondary: String?,
        text: String?,
        website: String? = null,
    ) = build(event, cleanOne(primary), cleanOne(secondary), text, cleanOne(website))

    private fun tiers(
        event: Event,
        primary: List<String?>,
        secondary: List<String?>,
        text: String?,
        websites: List<String?> = emptyList(),
    ) = build(event, cleanAll(primary), cleanAll(secondary), text, cleanAll(websites))

    /**
     * The one funnel BOTH [tiers] overloads end in — [IndexableFields.Tiered.hashtags]
     * and [IndexableFields.Tiered.locations] are filled here, so no branch can
     * forget them. Values stay UNJOINED: separator choices belong to the backend.
     */
    private fun build(
        event: Event,
        primary: List<String>,
        secondary: List<String>,
        text: String?,
        websites: List<String>,
    ) = IndexableFields.Tiered(
        primary = primary,
        secondary = secondary,
        text = clean(text),
        hashtags = hashtagValues(event),
        locations = locationValues(event),
        websites = websites,
    )

    /** Trim and drop empties at the single funnel every derived string passes through. */
    private fun clean(s: String?): String? = s?.trim()?.ifEmpty { null }

    private fun cleanOne(s: String?): List<String> = clean(s)?.let { listOf(it) } ?: emptyList()

    /**
     * Collects lazily: a role whose values are all absent — the common case on
     * most kinds — costs no list at all.
     */
    private fun cleanAll(parts: List<String?>): List<String> {
        var values: MutableList<String>? = null
        for (i in parts.indices) {
            val value = clean(parts[i]) ?: continue
            (values ?: ArrayList<String>(parts.size).also { values = it }).add(value)
        }
        return values ?: emptyList()
    }

    /**
     * The hashtag role. [hashtags] allocates unconditionally, so the scan for
     * a `t` tag comes first — most events carry none. The guard is exactly
     * [HashtagTag.parse]'s own acceptance test, so it can never skip a tag the
     * accessor would have returned.
     */
    private fun hashtagValues(event: Event): List<String> = if (!event.tags.fastAny(HashtagTag::isTagged)) emptyList() else cleanAll(event.tags.hashtags())

    /**
     * Every `location` tag value, on ANY kind. Deliberately a raw scan, not a
     * typed accessor: Quartz's LocationTag classes are per-NIP (calendar,
     * picture, classifieds) and only those kinds expose locations(), while
     * this funnel must also catch location tags on kinds whose class doesn't
     * model them. Collects lazily, like [cleanAll]: an event with no location
     * tag — nearly all of them — allocates nothing here.
     */
    private fun locationValues(event: Event): List<String> {
        var values: MutableList<String>? = null
        event.tags.fastForEach { tag ->
            if (tag.size > 1 && tag[0] == LOCATION_TAG) {
                val value = clean(tag[1])
                if (value != null) {
                    (values ?: ArrayList<String>(2).also { values = it }).add(value)
                }
            }
        }
        return values ?: emptyList()
    }

    private const val LOCATION_TAG = "location"
}
