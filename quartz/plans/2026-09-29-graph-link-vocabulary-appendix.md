# Appendix: every Quartz event class, and what its references mean

Companion to [`2026-09-29-graph-link-vocabulary.md`](2026-09-29-graph-link-vocabulary.md).
Generated 2026-09-29 from `utils/EventFactory.kt`: **410 classes**, each read against its tags,
its tag parsers and its NIP (or, for Quartz-only families, its package docs). **340** carry
references; **70** carry none (they get only `AUTHOR`, and `ADDRESS` when addressable).

How to read a row:
- **Links**: `tag[marker/slot] -> RELATION (targets)`, targets **E** event, **A** address,
  **U** user, **T** tag value. `AUTHOR` and `ADDRESS` apply to every class and are not
  repeated.
- **Built from**: the Quartz accessor or tag parser the class's `links()` would call, or
  "new parser needed".
- **Notes**: the NIP or spec, `UNCERTAIN:` where the spec is silent or unmerged, `DRAFT FIX:`
  where the vocabulary tables need correcting, and Quartz bugs found while reading.

Relation names are normalized to one per role (rule 2): `ADDED`→`ADDED_USER`,
`REMOVED`→`REMOVED_USER`, `JOB_REQUEST`→`REQUEST`, `CUSTOMER`→`REQUEST_AUTHOR` (NIP-90's word
is "customer"), `BENEFICIARY`→`ZAP_SPLIT`.

## Relations this review adds

Not in the vocabulary tables yet; each needs the maintainer's review like the tables did.
**Any kind** marks a tag that can appear on every event, better emitted once by the default on
`Event` than per class.

| Relation | Kinds | Justification (from the reviewing pass) |
|---|---|---|
| `ABOUT` | 23903, 30392, 30393, 30394, 30395 | the event a wake-up is about (Quartz builder about(); also the trusted lists' discovery slot) |
| `ABOUT_AUTHOR` | 23903 | its author (rule 3; KDoc: 'p-tags identify the AUTHORS of the referenced events', not recipients) |
| `ACCEPTED` | 30065 | the challenge this event accepts (past participle of the action). OPPONENT: see 30 |
| `ACTOR` | 8002, 8003, 40099, 44100, 44101, 48001 | – |
| `ADDED_USER` | 8000, 9000, 9030, 40099, 41011, 44100 | past participle of NIP-43 'Add User' / NIP-29 put-user; the member an add command/notification adds. Props: role. Should be shared with NIP-43 8000 and NIP-29 9000 |
| `ADMIN` | 39001 | a NIP-29 group's admins (kind 39001 'group admins'; props: roles). It is kept apart from NIP-72 MODERATOR because roles are relay-defined |
| `AGENT` | 24200, 30177, 43001, 44200 | Buzz's `agent` tag word; the AI agent a frame/metric/job/managed-agent record is about |
| `ALLOWED` | 30175, 30177, 34551 | entries of NIP-AP's respond_to_allowlist (who the agent answers), named as the list names them |
| `APP` | 5129, 15128, 15129, 35128, 35129 | NIP-5A `app` tag, 'an addressable event reference to an app descriptor' (NIP-89 31990 / 32267) |
| `APPLICATION` | 30063 | NIP-51's own example names the a the 'Reference to parent software application' (kind 32267): the release belongs to it, it is not a curated item |
| `APPLIED` | 1631 | the patch(es) a 1631 status applied or merged (NIP-34 'applied-or-merged-patch-event-id'; past participle of the status' own name). A q here is not a NIP-18 quote. REPOSITORY_OWNER: see 1617 |
| `APPROVED_AUTHOR` | 4550 | rule 3 - NIP-72 requires 'the p tag of the author of the post (for approval notifications)' |
| `APPROVER` | 46010 | Buzz's word; the person whose approval a paused workflow waits for |
| `ARCHIVED` | 8002, 9035, 13535 | past participle of NIP-IA's action (archive identity); also the entries of the 13535 archived list \| ACTOR: who performed/consented to the action a relay-signed record reports (NIP-IA consent tag's 'actor'); props path=self/owner/admin \| REQUEST: the originating request this relay-signed delta answers (Buzz 'originating request'; also fits NIP-90 results) \| REPLACED_BY: NIP-IA's own word for the successor identity (rotation pointer) |
| `ASSERTION` | 31871, 31872 | the event an attestation or attestation request is about (Quartz assertionEventId/assertionAddrId; UNCERTAIN it is the spec's word, fallback ATTESTED) |
| `ATTESTOR` | 31872 | an attestor asked to attest (the spec family's own word; builder attestorPubKeys). ASSERTION: see 31871 |
| `AUCTION_AUTHOR` | 1021 | the auction's merchant (rule 3, the author of acted-on content, as REACTED_AUTHOR). Quartz writes it via notifyAuthor() |
| `AUDITED` | 48001 | past participle of the audit action; the object an entry records an action on. Props: action |
| `AUTHORED` | 10064 | NIP-F4/NIP-51 'podcasts the user authors' - the counter-claim that verifies a 10154 PODCAST_AUTHOR; past participle of the NIP's action |
| `BADGE_SET` | 10008 | NIP-58 '(Profile badges) may also contain a tags referencing "Badge Set" events' (30008) - the NIP's own noun; not a badge definition |
| `BANNED` | 9040 | past participle of Buzz's ban action. Props: expiration, reason. (Unban 9041, not registered, would be UNBANNED) |
| `BASE_VERSION` | 818 | 'version of the article on which this modification is based' (no marker) |
| `BID_AUTHOR` | 1022 | the bidder (rule 3). Quartz writes it via notifyBidder() |
| `CALENDAR` | 31922, 31923 | NIP-52 'a (repeated) reference tag to kind 31924 calendar event requesting to be included in Calendar' - the NIP's noun for the target |
| `CALENDAR_EVENT_AUTHOR` | 31925 | rule 3 author of the acted-on calendar event; NIP-52 'p (optional) pubkey of the author of the calendar event being responded to' |
| `CHILD` | 9002, 39000 | see 9002 |
| `CLIENT` | **any kind** — seen on 31990 | NIP-89 client tag ('identifying the client that published the note' by its 31990 address) - cross-cutting, any event may carry it |
| `COLLABORATED` | 34238 | the NIP-71 video the signer accepts (or declines) a collaborator credit on; props role, status (accepted\|declined; absent = accepted). COLLABORATED_AUTHOR: that video's author (rule 3) |
| `COLLABORATED_AUTHOR` | 34238 | – |
| `CONCEPT_GRAPH` | 39998 | – |
| `CONFIRMED` | 1316 | the kind-1315 report a Roadstr confirmation confirms or denies (spec: 'report being confirmed or denied'; the kind is named Road Event Confirmation). Props: status (still_there \| no_longer_there) |
| `COPIED` | 15128, 15129, 35128, 35129 | NIP-5A 'a copied site MUST include exactly one lowercase a tag referencing the immediate parent nsite from which it was copied' (past participle of the NIP's action) |
| `CREATED` | 7376 | NIP-60 marker 'created' - the token event this spend created |
| `CREDITED` | 21, 22, 34235, 34236 | divine.video credit markers on p/a/e (inspired-by, audio, collaborator...) that are neither a NIP-71 participant nor a mention; one relation + props.credit instead of one relation per free-text label |
| `CURRENT_SCENE` | 30298 | the scene a reader is at (Quartz currentScene()) |
| `DEFER` | 30818 | the NIP-54 `defer` marker (rule 7, marker wins) - 'considers someone else's entry as a better version of itself'; a WoT-weight transfer, neither a fork nor a mention |
| `DELETED_AUTHOR` | 5 | rule 3 author of the acted-on content; Quartz's builders write a `p` per deleted event's author. For a valid request it always equals AUTHOR, so it may be dropped if the maintainer prefers - but the tag exists and points at a pubkey |
| `DENIED` | 34551 | – |
| `DESTINATION` | 818 | NIP-54 addresses the request to 'destination-pubkey' and its a is '30818:<destination-pubkey>:<d>' (the NIP's only other word is the generic 'target') |
| `DESTINATION_AUTHOR` | 818 | rule 3 author of the acted-on article (NIP-54 'destination pubkey') |
| `DESTROYED` | 7376 | NIP-60 marker 'destroyed' - the token event it consumed |
| `EDITED_AUTHOR` | 1010 | the edited note's author (rule 3). create(notify=) writes it only when editing someone else's note (EditPostViewModel), so it is the author of acted-on content, not a passing mention |
| `ELEMENT_OF` | 39999 | – |
| `EMOJI_SET` | **any kind** — seen on 0, 1, 7, 17, 1111, 10030, 30023, 30030 … (9 kinds) | NIP-30's own name for the optional 4th emoji-tag slot ('the kind 30030 emoji set the emoji belongs to'); an address pointer, so it needs a relation; cross-cutting on every kind NIP-30 allows emoji tags on (0, 1, 1111, 7, 30315) plus 10030/30030 |
| `EXERCISE` | 1301 | a POWR/NIP-101e set's kind-33401 exercise template (the tag's own name; props weight/reps/rpe/set_type) |
| `FAVORITE` | 10012, 10021, 10054, 10090 | NIP-51 names these lists by 'favorite' (10012 'user favorite browsable relays (and relay sets)', 10021 'Favorite follow sets', 10054 'Favorite podcasts'); alternative SUBSCRIBED |
| `FILE_DATA` | 1065 | the kind-1064 storage event holding the bytes this header describes (NIP-95 draft); no existing relation means 'the payload of this metadata' |
| `FINDER` | 7517 | NIP-CC's word for the person the verification attests ('the finder's pubkey') |
| `FOR_USER` | 5300, 5301 | DVM spec kinds/5300 'pubkey of the user to generate recommendations for' (Quartz writes it as ["param","user",hex]); USER alone would collide with the User node label |
| `FOUND` | 7516 | NIP-CC kind 7516 is the 'Found Log' that 'record[s] successful visits'; past participle of the NIP's action |
| `FUNDED` | 9041 | NIP-75 'The goal MAY include an r or a tag linking to a URL or addressable event' - use case 'adding funding goals to events'; past participle of the goal's action |
| `GOAL` | 30311 | the NIP-75 zap goal (kind 9041) a stream raises toward (the tag's own name, 'goal') |
| `GROUP` | 444, 445, 9000, 9001, 9002, 9005, 9007, 9008 … (59 kinds) | NIP-29 `h` group id (Buzz channel UUID) the event is scoped to, target Tag("h", id); NIP-29's word for the slot; `h` is the one reference every channel-scoped Buzz kind carries and it is not E/A/U, so it needs a T relation (propose adding `h` to the allowlisted value tags) |
| `INHERIT_FROM` | 39998, 39999 | the node a b tag claims to inherit from / correspond to (Tapestry draft 'Inherit-From'); props type (pointer\|inherit\|inherit-items). CONCEPT_GRAPH: the concept's Concept Graph core node (tag name) |
| `INPUT` | 5000, 5001, 5002, 5050, 5100, 5200, 5201, 5202 … (38 kinds) | NIP-90 'i' is 'Input data for the job' (props input_type, marker) |
| `INPUT_JOB` | 5000, 5001, 5002, 5050, 5100, 5200, 5201, 5202 … (38 kinds) | NIP-90 input-type 'job' = 'the output of a previous job with the specified event ID' (job chaining), target is that job request |
| `ITEM` | 9999, 39999 | – |
| `KEY_PACKAGE` | 444 | the kind 30443 KeyPackage event this Welcome consumed (MIP-02 names the slot 'KeyPackage'). GROUP: see 9007 |
| `KICKED` | 4312 | the participant a room host ejects (the nostrnests / EGG-07 verb 'kick', as past participle); CHANNEL_MUTED reused for the force-mute verb (room-scoped moderation, not a personal mute) |
| `LINKED` | 30315 | NIP-38 'The status MAY include an r, p, e or a tag linking to a URL, profile, note, or addressable event' - past participle of the NIP's verb; deliberate (the status is about it), so not MENTION |
| `MAINTAINER` | 30617 | the repository's other recognized maintainers (NIP-34 'maintainers' tag; a list named as the list names it) |
| `MERCHANT` | 30019 | the merchants a NIP-15 marketplace groups ('merchants': array of pubkeys). Lists name their entries as the list does (rule 7) |
| `NOTIFICATION_SERVER` | 447, 448, 449 | the push notification server a token record targets (features/push-notifications.md 'notification server'; record key member_id, leaf, platform, server_pubkey) |
| `OBSERVER` | 30392, 30393, 30394, 30395 | the point of view the list was computed under (tag name, NIP-85 vocabulary) |
| `OPEN_TIMESTAMP` | 31 | the kind-1040 NIP-03 proof attesting when the cited page was seen; named after the tag (the spec's word for the slot), not TIMESTAMPED, which is the 1040's own link to its target |
| `OPPONENT` | 30, 30064, 30065, 30066, 30067, 30068 | the other player (Quartz OpponentTag/opponentPubkey(); Jester FLOW 'opponent'), shared with the live chess kinds 30064-30068 |
| `OPTION` | 30296, 30297 | a scene an interactive story branches to (the tag's own name; props: the option text) |
| `ORIGIN` | 5129, 15128, 15129, 35128, 35129 | the uppercase A, 'the origin nsite of the copy lineage' (the NIP's word) |
| `OWNER` | 9035, 9036, 30174, 44200 | NIP-OA's own word; the owner key attesting the event's (agent) author via the `auth` tag, or the owner an agent reports to (NIP-AM/NIP-AE `p`). Props: conditions (attestation only) |
| `PALETTE` | 3330, 11333, 33331 | DECK-0003 §1.3b names the payload field `palette`: an nevent/naddr to a palette published as its own event; a content-borne event/address reference that is not a passing mention |
| `PARENT_LIST` | 9999, 39999 | the list header an item is filed under (spec: 'a pointer to the parent list (the list header)'); T when the z is the bare name of an undeclared list. ITEM: the thing declared as an item on that list (spec: 'declaring a pubkey, event id, string, or naddr as an item on a list') |
| `PERSONA` | 30177 | NIP-AP's word; the 30175 persona a managed agent is defined by (Address 30175:author:persona_id) |
| `PODCAST_AUTHOR` | 10154 | NIP-F4 '["p", <podcast-author-pubkey>, <role>]'; AUTHOR is taken by the signer (here the podcast key itself), so the NIP's own 'podcast author' is the name |
| `POLL_AUTHOR` | 1018 | rule 3 - the author of the acted-on poll; Quartz writes a p for the poll author (notifyAuthor) that NIP-88 does not define |
| `PUBLICATION` | 30041 | the kind-30040 index a section belongs to (Quartz publicationAddress(); the inverse of the index's MEMBER, stated by the section) |
| `RATED_AUTHOR` | 34259 | the rated entity's author (rule 3; the p is 'the rated author', not the rated user, which comes from d when mark=profile) |
| `REDEEMED` | 7376 | NIP-60/61 marker 'redeemed' - the nutzap (9321) this history entry claimed |
| `REDEEMED_AUTHOR` | 7376 | rule 3 - NIP-61 'pubkey of the author of the 9321 event (nutzap sender)' |
| `RELEASE` | 32267 | an application's release (kind 30063 NIP-82 / NIP-51 release artifact set) that the app event points to |
| `REMINDED` | 40007 | past participle of the reminder action; the message a reminder is about (would also serve NIP-ER 30300 targets) |
| `REMOVED_USER` | 8001, 9001, 9031, 40099, 44101 | past participle of NIP-43 'Remove User' / NIP-29 remove-user; should be shared with NIP-43 8001 and NIP-29 9001 |
| `REPLACED_BY` | 8002, 9035 | – |
| `REPOSITORY_OWNER` | 1617, 1618, 1619, 1621, 1630, 1631, 1632, 1633 | NIP-34's 'repository-owner' p on patches, PRs, issues and statuses (rule 3 author-of-acted-on-content, named by the NIP's word). It is the one-hop 'patches to my repos'. |
| `REQUEST` | 819, 6000, 6001, 6002, 6050, 6100, 6200, 6201 … (33 kinds) | the request event a response answers (CLINK: 'a response is distinguished by an e tag referencing the request'); same role as NIP-47/NIP-90 responses and an attestation's request, none classified yet |
| `REQUEST_AUTHOR` | 819, 6000, 6001, 6002, 6050, 6100, 6200, 6201 … (25 kinds) | the requester, i.e. the author of the REQUEST target (rule 3; also fits NIP-90 result/feedback `p`) |
| `RESOLVED` | 9044 | past participle of Buzz's resolve-report action; the kind 1984 report being closed. Props: status (resolved/dismissed), action (delete/kick/ban/timeout/dismiss/escalate), reason |
| `RESULT` | 819, 6300, 6301, 6302, 6303, 6900, 6905, 6970 | NIP-90 'Job result ... providing the output'; the entities a job returns in content (via content) |
| `REVISED` | 1618 | the root patch this PR is a revision of (NIP-34: 'indicate PR is a revision of an existing patch, which should be closed'; past participle of the action) |
| `ROLE_CHANGED` | 9032 | past participle of Buzz's change-role action; the member whose role changes. Props: role. Distinct from ADDED_USER because the target is already a member |
| `SCHEDULED` | 5905 | DVM spec kinds/5905 'Schedule events to be published in the future'; past participle of the action for the signed event the DVM will publish |
| `SEARCH_AUTHOR` | 5302 | DVM spec kinds/5302 param 'users' = 'pubkeys of users to filter notes from' (the authors the search is restricted to) |
| `SITE_MANIFEST` | 31990 | NIP-89 'App descriptor events SHOULD tag or otherwise reference related site manifest events' (latest/next nsite manifests) |
| `SNAPSHOTTED` | 5129 | NIP-5A snapshot (5128) 'MUST include exactly one a tag referencing the source root site or named site' - past participle of the NIP's action; ORIGIN / APP: see 15128 |
| `SOURCE` | 818, 1163, 30040 | the event a reproduced piece of content was taken from (here the post the gallery picture came from; also used for NKBIP-01 derivative works on 30040). Quartz calls the slot fromEvent |
| `SOURCE_TAG` | 30392, 30393, 30394, 30395 | the tag definition the membership was computed from (tag name) |
| `STALL` | 30018, 30020 | the NIP-15 stall a product or auction belongs to ('stall_id: id of the stall to which this product belong to'). The address is derived from the author plus the content stall_id |
| `SUBSET_OF` | 39999 | the superset a set claims to be a subset of ('s'). Named with the draft's words; the draft derives the reversed edges HAS_ELEMENT / IS_A_SUPERSET_OF |
| `TAGGED` | 20 | NIP-68 names its p tags 'Tagged users' and annotate-user 'places a user link in the image' - people shown in the picture, not a passing mention |
| `TEMPLATE` | 1301 | the kind-33402 workout template the session was built from (the tag's own name) |
| `TEXT_TRACK` | 21, 22, 34235, 34236 | NIP-71 `text-track` names the captions/subtitles track (an encoded event or a 39307 coordinate) - the NIP's own tag word |
| `TIMED_OUT` | 9042 | past participle of Buzz's timeout action. Props: expiration, reason |
| `TIMEOUT_CLEARED` | 9043 | Buzz's 'untimeout' = 'clears a timeout'; UNTIMED_OUT is the literal participle but unreadable |
| `TRIGGERED` | 46020 | past participle of Buzz's trigger action; the 30620 workflow definition run |
| `UNARCHIVED` | 8003, 9036 | past participle of NIP-IA unarchive; split from ARCHIVED so 'is X archived' needs no property filter (rule 4) |
| `VERIFIED` | 7517 | past participle - 'the geocache naddr being verified' |
| `VERIFIER` | 37516 | NIP-CC verification tag 'public key for verifying finds' - the key that signs 7517s |
| `VIDEO` | 39307 | the NIP-71 video this caption/subtitle track belongs to (Quartz video()); the target named by its slot, like POLL or COMMUNITY |
| `VIEWER` | 30622 | NIP-DV's own word; the user a relay-signed per-viewer snapshot belongs to |
| `VOTED` | 45002 | past participle of Buzz's vote action; the voted post. Props: direction (+/-) |
| `WIKILINK` | 30041 | a resolved [[double bracket]] reference from the body (NKBIP-01 tag name; T = the target slug when no id is given) |
| `WIKILINK_AUTHOR` | 30041 | the author named in the wikilink's pubkey slot (rule 3) |
| `WINNER` | 30067, 37516 | the winning player (the tag's own name, 'winner'). OPPONENT: see 30 |
| `WOT_ROOT` | 34551 | the wot tag's 'root-pubkey' from which posters must be reachable |
| `ZAP_REQUEST` | 9735 | NIP-57's own name ('zap request') for the event embedded in the `description` tag; a content-borne event reference, so it needs a relation (props could carry the request's comment) |
| `ZAP_SPLIT` | **any kind** — seen on 1, 14, 1111, 9041, 30023 | NIP-57 Appendix G `zap` tag names a pubkey that receives zaps sent to this event; a configuration, not a payment, so it must not be ZAP_RECIPIENT (rule 4); props weight; cross-cutting (Amethyst writes it on 1, 14, 1111, 30023, 30402). EMOJI_SET: see kind 0 |

## The classes, by package

### `buzz` (74)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 8002 | `ArchivedIdentityEvent` | p -> ARCHIVED (U); consent[actor] -> ACTOR (U); e -> REQUEST (E); replaced-by -> REPLACED_BY (U) | ArchivedIdentityEvent.target()/consent()/requestId()/replacedBy() (PTag.parseKey, ConsentTag.parse, ETag.parseId, ReplacedByTag.parse) | Buzz NIP-IA, relay-signed delta. Props on ARCHIVED: reason. BUG: ReplacedByTag.parse and ConsentTag.parse do not check hex64 (a malformed key would become a User link). ACTOR is the 9035 author, so ACTOR duplicates (e)-[:REQUEST]->(r)-[:AUTHOR]; kept because the request is never stored (relay audits, not stores, 9035). |
| 8003 | `UnarchivedIdentityEvent` | p -> UNARCHIVED (U); consent[actor] -> ACTOR (U); e -> REQUEST (E) | UnarchivedIdentityEvent.target()/consent()/requestId() (PTag.parseKey, ConsentTag.parse, ETag.parseId) | Buzz NIP-IA relay-signed delta. Props: reason. ConsentTag actor not hex-validated. REQUEST target (9036) is never stored by the relay. |
| 9030 | `RelayAdminAddMemberEvent` | p -> ADDED_USER (U) | RelayAdminAddMemberEvent.target()/role() (PTag.parseKey, RoleTag.parse) | Buzz 'NIP-43' admin command (not the NIP-43 kind 8000); relay executes and never stores it. No `h` (tenant = connection host). Cross-group consistency: NIP-29/NIP-43 classifier should use the same ADDED/REMOVED. |
| 9031 | `RelayAdminRemoveMemberEvent` | p -> REMOVED_USER (U) | RelayAdminRemoveMemberEvent.target() (PTag.parseKey) | Buzz admin command, never stored. No `h`. |
| 9032 | `RelayAdminChangeRoleEvent` | p -> ROLE_CHANGED (U) | RelayAdminChangeRoleEvent.target()/role() (PTag.parseKey, RoleTag.parse) | UNCERTAIN: NIP-29 folds role assignment into put-user (9000), so ADDED+props role is the alternative. Owner-signed, never stored. |
| 9033 | `SetWorkspaceProfileEvent` | *none* | – | NIP-WP: only an `icon` URL tag (http(s)/data: URL) - not modelled. Only AUTHOR applies. |
| 9035 | `ArchiveRequestEvent` | p -> ARCHIVED (U); replaced-by -> REPLACED_BY (U); auth[owner] -> OWNER (U) | ArchiveRequestEvent.target()/replacedBy()/auth() (PTag.parseKey, ReplacedByTag.parse, AuthTag.parse -> OwnerAttestation.ownerPubKey) | Buzz NIP-IA request, NIP-70 protected; audited but not stored by the relay. Props on ARCHIVED: reason. ReplacedByTag.parse lacks hex64 check. OWNER link is unverified unless OwnerAttestation.verify(author) passes - suggest emitting only verified attestations. |
| 9036 | `UnarchiveRequestEvent` | p -> UNARCHIVED (U); auth[owner] -> OWNER (U) | UnarchiveRequestEvent.target()/auth() (PTag.parseKey, AuthTag.parse) | Buzz NIP-IA request, NIP-70 protected, not stored. Props: reason. |
| 9040 | `ModerationBanEvent` | p -> BANNED (U) | ModerationBanEvent.target()/expiresAt()/reason() (PTag.parseKey, ReasonTag.parse) | Mod-signed command, executed not stored; no `h`. Kind 9041 ModerationUnbanEvent collides with NIP-75 ZapGoalEvent (documented, not in EventFactory). |
| 9042 | `ModerationTimeoutEvent` | p -> TIMED_OUT (U) | ModerationTimeoutEvent.target()/expiresAt()/reason() (PTag.parseKey) | Mod-signed command, executed not stored; no `h`. |
| 9043 | `ModerationUntimeoutEvent` | p -> TIMEOUT_CLEARED (U) | ModerationUntimeoutEvent.target() (PTag.parseKey) | UNCERTAIN naming (alternative UNTIMED_OUT for symmetry with TIMED_OUT). Command, not stored. |
| 9044 | `ModerationResolveReportEvent` | report -> RESOLVED (E) | ModerationResolveReportEvent.report()/status()/action()/reason() (ReportTag.parse) | Custom `report` tag (not `e`) by design. BUG: ReportTag.parse does not check hex64. Command, not stored. No REPORTED_* links derivable without the 1984. |
| 10100 | `AgentProfileEvent` | *none* | – | Replaceable, content-only (loose JSON). Content `channel_ids` are group UUIDs (would be GROUP T via content if content JSON refs are ever modelled) - not modelled; schema flagged conservative. Only AUTHOR/ADDRESS. |
| 13535 | `ArchivedIdentitiesListEvent` | p -> ARCHIVED (U) | ArchivedIdentitiesListEvent.archivedIdentities() (PTag.parseKey) | Relay-signed replaceable snapshot, one bare `p` per archived identity; list entries named as the list names them (archived identities). |
| 20002 | `TypingIndicatorEvent` | h -> GROUP (T); e[root] -> ROOT (E); e[reply] -> PARENT (E) | TypingIndicatorEvent.channelId()/threadRootId()/threadReplyId() (GroupIdTag.parse, MarkedETag.parseAllThreadTags) | Ephemeral, never stored (graph may skip). Builder emits root only when root != reply, so a lone `reply` e is both ROOT and PARENT. parseAllThreadTags does not check hex64 on the id. |
| 24134 | `PairingEvent` | p -> RECIPIENT (U) | PairingEvent.recipientPubKey() (PTag.parseKey) | Buzz NIP-AB, ephemeral; the `p` is an EPHEMERAL session key, not a user identity (graph may want to skip ephemeral kinds). |
| 24200 | `ObserverFrameEvent` | p -> RECIPIENT (U); agent -> AGENT (U) | ObserverFrameEvent.recipientPubKey()/agentPubKey() (PTag.parseKey, AgentTag.parse) | Buzz NIP-AO, ephemeral. Telemetry: AGENT == author (self-link); control: AGENT == RECIPIENT (duplicate). Props: frame (telemetry/control). BUG: AgentTag.parse lacks hex64 check. |
| 24810 | `HuddleReactionEvent` | h -> GROUP (T) | HuddleReactionEvent.channelId() (GroupIdTag.parse) | Ephemeral. `h` is the EPHEMERAL huddle channel UUID, not the parent timeline channel. `reaction`/`sender_name` are values; NIP-30 `emoji` URL not modelled. No reacted target. |
| 30174 | `EngramEvent` | p -> OWNER (U) | EngramEvent.ownerPubKey() (PTag.parseKey) | Buzz NIP-AE. TRAP: `d` is a blinded 64-hex HMAC that looks like an id/pubkey by shape - must never be shape-matched into a link. |
| 30175 | `PersonaEvent` | content{respond_to_allowlist} -> ALLOWED (U) | new parser needed (PersonaEvent.personaOrNull()?.respondToAllowlist) | UNCERTAIN: reference lives in plaintext content JSON, not tags; entries appear to be pubkey hex (test uses a truncated key) - validate hex64. `d` = persona slug (ADDRESS). |
| 30176 | `TeamEvent` | content{persona_ids} -> MEMBER (A) | new parser needed (TeamEvent.teamOrNull()?.personaIds -> Address(30175, author, id)) | UNCERTAIN: content JSON; persona ids assumed to be 30175 `d` slugs under the SAME author (a team groups the owner's personas) - confirm against Buzz team_events.rs. |
| 30177 | `ManagedAgentEvent` | d -> AGENT (U); content{persona_id} -> PERSONA (A); content{respond_to_allowlist} -> ALLOWED (U) | ManagedAgentEvent.agentPubKey() (dTag); new parser needed for agentOrNull()?.personaId / respondToAllowlist | The `d` tag IS the agent pubkey (like NIP-85 d -> SUBJECT); dTag() not hex-validated. UNCERTAIN: persona_id assumed to be the owner's own 30175 slug. |
| 30300 | `EventReminderEvent` | *none* | – | Buzz NIP-ER: public tags are d/not_before/expiration/alt only. The reminder target (id / a) is inside self-encrypted content - private, not modelled (would be REMINDED if ever decrypted). |
| 30350 | `PushLeaseEvent` | *none* | – | Buzz NIP-PL: d = installation id, `exec` = gateway key id (opaque, not a Nostr pubkey), expiration; descriptor encrypted. Only AUTHOR/ADDRESS. |
| 30620 | `WorkflowDefEvent` | h -> GROUP (T) | WorkflowDefEvent.channel() (GroupIdTag via firstTagValue) | d = workflow UUID (ADDRESS). `name` is a value. workflowChannel() uses firstTagValue with no emptiness check. |
| 30622 | `DmVisibilityEvent` | p -> VIEWER (U); h -> HIDDEN (T) | DmVisibilityEvent.viewerFromPTag()/hiddenChannels() (PTag.parseKey, GroupIdTag.parse) | Relay-signed addressable; d == p == viewer pubkey (duplicate, d not validated). HIDDEN extended to a T target (group id): these `h` are hidden DMs, not scope, so they are HIDDEN not GROUP. |
| 39006 | `WindowBoundsEvent` | h -> GROUP (T) | WindowBoundsEvent.channelId() (GroupIdTag.parse) | Relay-synthesized, never stored. d = <channel_id>:<cursor>. content next_cursor.id is a pagination boundary event id - not modelled (not a statement). |
| 40002 | `StreamMessageV2Event` | h -> GROUP (T); p -> MENTION (U); e[root] -> ROOT (E); e[reply] -> PARENT (E) | StreamMessageV2Event.channel()/mentions() (GroupIdTag.parse, PTag.parseKey); buzzThreadRoot()/buzzThreadReply() in buzz/threading (not exposed on the class) | Thread e-tags per Buzz thread_tags (shared with 45003, per buzz/threading KDoc) but the Quartz class/builder neither reads nor writes them - GAP. Lone `reply` marker = direct reply, so it is both ROOT and PARENT. `broadcast` is a flag. Content nostr: URIs not parsed by the class. |
| 40003 | `StreamMessageEditEvent` | h -> GROUP (T); e -> EDITED (E) | StreamMessageEditEvent.channel()/editedMessage() (ETag.parseId) | Buzz build_edit. |
| 40004 | `StreamMessagePinnedEvent` | h -> GROUP (T); e -> PIN (E) | StreamMessagePinnedEvent.channel()/pinnedMessage() (ETag.parseId) | PIN extended to a channel pin. Tag shape inferred (no Buzz builder). |
| 40005 | `StreamMessageBookmarkedEvent` | h -> GROUP (T); e -> BOOKMARK (E) | StreamMessageBookmarkedEvent.channel()/bookmarkedMessage() (ETag.parseId) | Tag shape inferred (no Buzz builder). |
| 40006 | `StreamMessageScheduledEvent` | h -> GROUP (T) | StreamMessageScheduledEvent.channel() (GroupIdTag.parse) | Schema inferred; only `h` modelled. |
| 40007 | `StreamReminderEvent` | h -> GROUP (T); p -> RECIPIENT (U); e -> REMINDED (E) | StreamReminderEvent.channel()/recipients()/targetMessage() (PTag.parseKey, ETag.parseId) | UNCERTAIN: no Buzz builder; `e` target is read but never written by Quartz's builder. RECIPIENT = 'the user the reminder is for'. |
| 40008 | `StreamMessageDiffEvent` | h -> GROUP (T); l -> TAG (T) | StreamMessageDiffEvent.channel()/diffMeta() (GroupIdTag.parse, LanguageTag.parse) | `l` here is the diff's programming language, NOT NIP-32 (target Tag(l, lang)). repo (URL), commit/parent-commit (git SHAs), file, branch, pr are not Nostr entities - not modelled. |
| 40099 | `SystemMessageEvent` | h -> GROUP (T); content{actor} -> ACTOR (U); content{target}[member_joined] -> ADDED_USER (U); content{target}[member_removed\|member_left] -> REMOVED_USER (U); content{target_event_id}[message_deleted] -> DELETED (E); content{participants}[dm_created] -> PARTICIPANT (U) | SystemMessageEvent.channel()/payload() (SystemMessagePayload.actor/target/targetEventId/participants); new parser needed to map by type | Relay-signed. UNCERTAIN: all refs are in content JSON, keyed by payload.type; no hex validation on actor/target/target_event_id/participants. Props: type, reason_code. |
| 40100 | `CanvasEvent` | h -> GROUP (T) | CanvasEvent.channel() (GroupIdTag.parse) | Buzz build_set_canvas; markdown content, no nostr: parsing. |
| 40901 | `ChannelSummaryEvent` | h -> GROUP (T) | ChannelSummaryEvent.channel() (GroupIdTag.parse) | Relay-only sidecar; schema unconfirmed (no Buzz emitter). content channel_id duplicates `h`. |
| 40902 | `PresenceSnapshotEvent` | content{entries[].pubkey} -> SUBJECT (U) | new parser needed (PresenceSnapshotEvent.snapshot()?.entries) | UNCERTAIN: relay-only sidecar, schema unconfirmed (no Buzz emitter; relay answers with 20001s whose `p` is the 'subject'). SUBJECT extended from NIP-85 to a relay's presence statement; props status, last_seen_at. No `h`. |
| 41001 | `DmCreatedEvent` | d -> GROUP (T); p -> PARTICIPANT (U) | DmCreatedEvent.dmId()/participants() (DTag via firstTagValue, PTag.parseKey) | Relay-signed. The DM id rides in `d` on a REGULAR kind (not addressable) - emit as Tag("h", id) so it joins the DM's GROUP links. dmId() returns "" when absent (must not emit). |
| 41010 | `DmOpenEvent` | p -> PARTICIPANT (U) | DmOpenEvent.participants() (PTag.parseKey) | Command (1-8 participants); relay replies with 41001. |
| 41011 | `DmAddMemberEvent` | h -> GROUP (T); p -> ADDED_USER (U) | DmAddMemberEvent.channelId()/member() (GroupIdTag.parse, PTag.parseKey) | – |
| 41012 | `DmHideEvent` | h -> HIDDEN (T) | DmHideEvent.channelId() (GroupIdTag.parse) | HIDDEN (NIP-28 'hide message') extended to a T target: the `h` is the DM being hidden, the object of the action, not just scope. |
| 42000 | `ProductFeedbackEvent` | *none* | – | Only `category` value and optional `imeta` (media URLs, not modelled). Never stored by the relay. |
| 43001 | `JobRequestEvent` | h -> GROUP (T); p -> AGENT (U) | JobRequestEvent.channel()/target() (GroupIdTag.parse, PTag.parseKey) | UNCERTAIN: 43001-43006 reserved in Buzz with no builder; Quartz tag layout is best-effort. |
| 43002 | `JobAcceptedEvent` | e -> REQUEST (E); h -> GROUP (T); p -> REQUEST_AUTHOR (U) | JobAcceptedEvent.jobRequest()/channel()/requester() (ETag.parseId, GroupIdTag.parse, PTag.parseKey) | UNCERTAIN schema (reserved kind). |
| 43003 | `JobProgressEvent` | e -> REQUEST (E); h -> GROUP (T) | JobProgressEvent.jobRequest()/channel() (ETag.parseId, GroupIdTag.parse) | UNCERTAIN schema. Props: status. |
| 43004 | `JobResultEvent` | e -> REQUEST (E); h -> GROUP (T); p -> REQUEST_AUTHOR (U) | JobResultEvent.jobRequest()/channel()/requester() | UNCERTAIN schema. Props: status. |
| 43005 | `JobCancelEvent` | e -> REQUEST (E); h -> GROUP (T) | JobCancelEvent.jobRequest()/channel() | UNCERTAIN schema. |
| 43006 | `JobErrorEvent` | e -> REQUEST (E); h -> GROUP (T); p -> REQUEST_AUTHOR (U) | JobErrorEvent.jobRequest()/channel()/requester() | UNCERTAIN schema. Props: status. |
| 44100 | `MemberAddedNotificationEvent` | p -> ADDED_USER (U); h -> GROUP (T); content{actor} -> ACTOR (U) | MemberAddedNotificationEvent.target()/channel()/actor() (PTag.parseKey, firstTagValue(h), MembershipNotificationContent.parse) | Relay-signed. Self-join reports actor == target (ACTOR == ADDED). |
| 44101 | `MemberRemovedNotificationEvent` | p -> REMOVED_USER (U); h -> GROUP (T); content{actor} -> ACTOR (U) | MemberRemovedNotificationEvent.target()/channel()/actor() | Relay-signed. |
| 44200 | `AgentTurnMetricEvent` | p -> OWNER (U); agent -> AGENT (U) | AgentTurnMetricEvent.ownerPubKey()/agentPubKey() (PTag.parseKey, AgentTag.parse) | Buzz NIP-AM. AGENT normally == author (self-link). AgentTag.parse lacks hex64 check. |
| 45001 | `ForumPostEvent` | h -> GROUP (T); p -> MENTION (U) | ForumPostEvent.channel()/mentions() (firstTagValue(h), PTag.parseKey) | Thread root of a forum thread. |
| 45002 | `ForumVoteEvent` | h -> GROUP (T); e -> VOTED (E) | ForumVoteEvent.channel()/target()/direction() (ETag.parseId) | Alternative: REACTED (a +/- vote is reaction-like); no author `p`, so no VOTED_AUTHOR on the wire. |
| 45003 | `ForumCommentEvent` | h -> GROUP (T); e[root] -> ROOT (E); e[reply] -> PARENT (E); p -> MENTION (U) | ForumCommentEvent.channel()/threadRoot()/replyTo()/mentions() (buzzThreadRoot/buzzThreadReply, PTag.parseKey) | Buzz thread_tags: a direct reply has ONLY a `reply` marker (root == parent), so the method must emit ROOT too when no root marker is present (threadRoot() returns null there). |
| 46001 | `WorkflowTriggeredEvent` | h -> GROUP (T) | WorkflowTriggeredEvent.channel() | Relay-emitted lifecycle; run/step ids not modelled (schema not fixed). |
| 46002 | `WorkflowStepStartedEvent` | h -> GROUP (T) | WorkflowStepStartedEvent.channel() | Same as 46001. |
| 46003 | `WorkflowStepCompletedEvent` | h -> GROUP (T) | WorkflowStepCompletedEvent.channel() | Same as 46001. |
| 46004 | `WorkflowStepFailedEvent` | h -> GROUP (T) | WorkflowStepFailedEvent.channel() | Same as 46001. |
| 46005 | `WorkflowCompletedEvent` | h -> GROUP (T) | WorkflowCompletedEvent.channel() | Same as 46001. |
| 46006 | `WorkflowFailedEvent` | h -> GROUP (T) | WorkflowFailedEvent.channel() | Same as 46001. |
| 46007 | `WorkflowCancelledEvent` | h -> GROUP (T) | WorkflowCancelledEvent.channel() | Same as 46001. |
| 46010 | `WorkflowApprovalRequestedEvent` | h -> GROUP (T); p -> APPROVER (U) | WorkflowApprovalRequestedEvent.channel()/approver() (PTag.parseKey) | Relay-signed needs-action item. |
| 46011 | `WorkflowApprovalGrantedEvent` | h -> GROUP (T) | WorkflowApprovalGrantedEvent.channel() | Relay lifecycle event; no link to the 46010/46030 modelled. |
| 46012 | `WorkflowApprovalDeniedEvent` | h -> GROUP (T) | WorkflowApprovalDeniedEvent.channel() | Same as 46011. |
| 46020 | `WorkflowTriggerEvent` | d -> TRIGGERED (A) | WorkflowTriggerEvent.workflowId() (DTag via firstTagValue) -> Address(30620, author, d) | UNCERTAIN: the `d` (on a REGULAR kind) holds only the workflow UUID; address assumes owner == author, which the relay enforces ('only the workflow owner may trigger'). |
| 46030 | `ApprovalGrantEvent` | *none* | – | `d` (on a regular kind) = approval token hash - opaque, not an event id, not modelled. Only AUTHOR. |
| 46031 | `ApprovalDenyEvent` | *none* | – | Same as 46030. |
| 48001 | `AuditEntryEvent` | p -> ACTOR (U); object -> AUDITED (E\|T) | AuditEntryEvent.actor()/objectId() (PTag.parseKey, ObjectTag.parse) | UNCERTAIN: schema is a Quartz-side projection - Buzz never emits 48001 on the wire. `object` is polymorphic (event id, channel UUID, media sha256): E only when the action targets an event, else T; a 64-hex sha256 must not be shape-matched as an event. |
| 48100 | `HuddleStartedEvent` | h -> GROUP (T) | HuddleStartedEvent.channelId() | Content ephemeral_channel_id (UUID) not modelled. |
| 48101 | `HuddleParticipantJoinedEvent` | h -> GROUP (T); p -> PARTICIPANT (U) | HuddleParticipantJoinedEvent.channelId()/participant() (PTag.parseKey) | Relay-signed; participant is the `p`, not the author. |
| 48102 | `HuddleParticipantLeftEvent` | h -> GROUP (T); p -> PARTICIPANT (U) | HuddleParticipantLeftEvent.channelId()/participant() | Relay-signed. PARTICIPANT for both join and leave: the kind says which. |
| 48103 | `HuddleEndedEvent` | h -> GROUP (T); p -> PARTICIPANT (U) | HuddleEndedEvent.channelId()/participant() | `p` = last participant (optional). |
| 48106 | `HuddleGuidelinesEvent` | h -> GROUP (T) | HuddleGuidelinesEvent.channelId() | Schema uncertain (no Buzz constructor). |

### `experimental` (57)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 31 | `ExternalCitationEvent` | open_timestamp -> OPEN_TIMESTAMP (E); g -> TAG (T) | ExternalCitationEvent.openTimestamp() (CitationTags.OPEN_TIMESTAMP; no 64-hex validation); CitationEvent.geohash() (CitationTags.GEOHASH) | Spec: silberengel/jumble citation vocabulary (no NIP). u/url (the cited URL) is not modelled in v1 (not an allowlisted value tag; would be TAG if u joins the list). Bug: openTimestamp() returns any non-empty string, no id-shape check. Kind 30 (internal citation) deliberately unmodelled: collides with Jester chess. |
| 32 | `HardcopyCitationEvent` | g -> TAG (T) | CitationEvent.geohash() (CitationTags.GEOHASH) | Spec: jumble citations (no NIP). doi, published_in, author are free-text values, not link targets. Only g carries a link, via the shared base. |
| 33 | `PromptCitationEvent` | g -> TAG (T) | CitationEvent.geohash() (CitationTags.GEOHASH) | Spec: jumble citations (no NIP). u (conversation URL) and llm are not modelled. |
| 82 | `FhirResourceEvent` | *none* | – | No spec reference in KDoc; FHIR JSON payload in content, class reads no tags. Anything an initializer adds is unparsed. |
| 1010 | `TextNoteModificationEvent` | e -> EDITED (E); p -> EDITED_AUTHOR (U) | editedNote() = firstTaggedEvent(); p read only via generic taggedUsers (new parser needed for the p slot) | Draft edits NIP (kind 1010). Only the FIRST e is read (firstTaggedEvent). summary is a value tag. Draft-plan correction: the '1010 claimed by two classes' note is wrong: GoodWikiRelayListEvent.KIND is 10102, not 1010, and EventFactory types 1010 only as TextNoteModificationEvent (the text scan matched the 10102 prefix). |
| 1064 | `FileStorageEvent` | *none* | – | NIP-95 draft (base64 file blob in content). Only m (mime) tag. |
| 1065 | `FileStorageHeaderEvent` | e -> FILE_DATA (E) | dataEventIds() / dataEvent() (ETag::parseId / ETag::parse) | NIP-95 draft (never merged). x/url/image/thumb/fallback/service/magnet are hashes and URLs, not modelled. The e tag carries an author slot (EventHintBundle.toETag) that is a hint, not a separate statement. |
| 1163 | `ProfileGalleryEntryEvent` | e -> SOURCE (E) | fromEvent() (ETag::parseId) | Amethyst profile-gallery kind (no NIP). url/x/ox/imeta-style fields are not modelled. No p for the source author; if one is added later it would be SOURCE_AUTHOR (rule 3). |
| 1301 | `WorkoutRecordEvent` | exercise[coordinate] -> EXERCISE (A); template -> TEMPLATE (A); t -> HASHTAG (T) | exerciseSetAddressIds() (ExerciseSetTag::parseAddressId), templateAddressId() (TemplateTag::parseAddressId), addressHints()/linkedAddressIds(); hashtags via generic HashtagTag | NIP-101e (draft) + RUNSTR dialect. RUNSTR exercise tag is a plain verb (not a link); ExerciseSetTag.isCoordinate distinguishes. Oddity: builder writes a d tag on a regular (non-addressable) kind 1301, so d has no replaceability meaning. Implements RootScope (can be a NIP-22 root). |
| 1315 | `RoadEventReportEvent` | t -> HASHTAG (T); g -> TAG (T) | roadEventTypeCode() (RoadEventTypeTag::parseCode), geohashes() | Roadstr draft NIP (jooray/roadstr nips/roadstr.md). t carries the road-event type code (police, accident...), a category rather than a free hashtag; HASHTAG per the t rule. lat/lon/expiration are values. |
| 1316 | `RoadEventConfirmationEvent` | e -> CONFIRMED (E); g -> TAG (T) | reportId() / linkedEventIds() (RoadReportTag::parseId), geohashes() | Roadstr draft NIP. RoadReportTag reads an author at e[3] (Quartz writes the report author there); the spec does not define it, so it stays a hint, not a CONFIRMED_AUTHOR link. UNCERTAIN: if denials must be counted separately, split per rule 4 (CONFIRMED / DENIED). |
| 1808 | `AudioHeaderEvent` | *none* | – | No spec reference. download_url / stream_url / waveform only: URLs and data, not modelled. |
| 2473 | `BirdDetectionEvent` | i -> TAG (T); g -> TAG (T) | speciesReference() (firstTagValue("i"), http(s)-filtered); Event.geohashes() | Birdstar app kind (no NIP). i is a Wikidata species URL (NIP-73 style). n (scientific name) is a value, not modelled. |
| 3063 | `SoftwareAssetEvent` | i -> TAG (T) | appId() (AppIdTag::parse) | NIP-82 (draft; 32267 is listed in the NIPs README but 82.md is not on master). i is the application's d-tag identifier, not a NIP-73 id. UNCERTAIN: it effectively names the address 32267:<author>:<i>; a derived APPLICATION (A) link would be more useful than TAG. url/x/f/apk_certificate_hash are not modelled. |
| 4312 | `AdminCommandEvent` | a -> ROOT (A); p[action=kick] -> KICKED (U); p[action=mute] -> CHANNEL_MUTED (U) | room() and targetPubkey() (raw first a / first p, no validation); action() | nostrnests EGG-07 (ephemeral kind). a names the kind-30312 room; ROOT chosen for consistency with 10312 presence and 1311 chat, but this a carries no root marker: UNCERTAIN (alternative: a new ROOM). Rule 4 split kick vs mute on the same U target. Weak parsing: room()/targetPubkey() take the first a/p without shape checks. |
| 6969 | `ZapPollEvent` | e[root] -> ROOT (E); e[reply] -> PARENT (E); e[mention] -> MENTION (E); q -> QUOTE (E,A); a -> MENTION (A); p -> MENTION (U); content nostr: -> MENTION (E,A,U) | BaseThreadedEvent.root()/reply()/threadTags() (MarkedETag), QTag::parseEventId/parseAddressId, ATag::parseAddressId, PTag::parseKey, citedNIP19() | Zap polls (old NIP-69 draft); a kind-1-shaped threaded note plus poll_option tags, so it should take kind 1's classification verbatim. UNCERTAIN: unmarked a tags treated as MENTION pending kind 1's decision. Known Quartz bug applies: QTag.parseAddressId rejects every address. Votes are zaps (9734/9735) carrying poll_option; not this class. |
| 9998 | `ListHeaderEvent` | *none* | – | Decentralized Lists (Tapestry pre-NIP, nous-clawds4/tapestry protocols/nips/decentralized-lists.md). names/titles/slugs/required/allowed/item-kind/description are values; item-kind names a kind, not an entity. Items point at the header, not vice versa. |
| 9999 | `ListItemEvent` | z -> PARENT_LIST (E,A,T); p -> ITEM (U); e -> ITEM (E); a -> ITEM (A); t -> ITEM (T) | parentLists() (ParentListTag::parse/classify: EventId \| Coordinate \| Name), itemPubKeys() (PTag), itemEvents() (ETag), itemAddresses() (ATag::parse), itemStrings() (HashtagTag); hint providers | Decentralized Lists spec. t here is a list VALUE, case-preserved, not a hashtag (Quartz README says so), hence ITEM (T) instead of HASHTAG. e items may carry the author at e[3] (itemEvent pads the relay slot): a hint. A 9999 may also declare a list (nonstandard method) and then carries header tags (values only). Cross-cutting: Event.dListParents() reads z on ANY kind (Cross-NIP Compatibility), so PARENT_LIST can come from foreign kinds too. |
| 10023 | `EphemeralChatListEvent` | *none* | – | Amethyst ephemeral-chat room list. group tags are [room name, relay URL] pairs: relay-scoped identifiers, not Nostr entities, so not modelled in v1 (would be SUBSCRIBED (T) if room ids become targets). Private rooms live NIP-44 encrypted in content. |
| 11871 | `AttestorProficiencyEvent` | k -> TAG (T) | kinds() (recommendation.tags.KindTag::parse) | Attestations draft NIP (kinds 11871/31871/31872/31873; spec not fetched). The attestor declares the kinds it can attest. |
| 12473 | `BirdexEvent` | i -> TAG (T) | species() (adjacent n/i pairing, asWebReference()) | Birdstar app kind (no NIP). Each i is the Wikidata URL of a species on the life list; n is a value. Arguably list entries (MEMBER (T)), but i -> TAG per the plain-tag rule. |
| 20000 | `GeohashChatEvent` | g -> TAG (T); t[teleport] -> HASHTAG (T) | geohash() (GeoHashTag::parse), isTeleported() (TeleportTag::match) | Bitchat location channels (ephemeral). g is the exact channel cell (single tag, no mip-map). t=teleport is a flag, not a topic; HASHTAG per the t rule (could equally be dropped). n (nickname) is a value. Authors are per-geohash derived keys, unlinkable to the main npub by design. |
| 20001 | `GeohashPresenceEvent` | g -> TAG (T) | geohash() (GeoHashTag::parse) | Bitchat presence (ephemeral). Kind 20001 is also buzz PresenceUpdateEvent; EventFactory disambiguates by the presence of a g tag, so links() must only apply when the class is actually GeohashPresenceEvent. |
| 21001 | `OfferEvent` | p -> RECIPIENT (U); e -> REQUEST (E) | recipientPubKey() (PTag::parseKey), requestId() (ETag::parseId) | CLINK Offers (shocknet/clink specs/clink-offers.md). Ephemeral; content NIP-44 to the p. Same kind for request, response and receipt. |
| 21002 | `DebitEvent` | p -> RECIPIENT (U); e -> REQUEST (E) | recipientPubKey() (PTag::parseKey), requestId() (ETag::parseId) | CLINK Debits (shocknet/clink specs/clink-debits.md). Ephemeral, NIP-44 content. |
| 21003 | `ManageEvent` | p -> RECIPIENT (U); e -> REQUEST (E) | recipientPubKey() (PTag::parseKey), requestId() (ETag::parseId) | CLINK Manage (shocknet/clink specs/clink-manage.md). Ephemeral, NIP-44 content. |
| 23333 | `EphemeralChatEvent` | *none* | – | Ephemeral chat: d = room name and relay = relay URL (RoomTag, RelayTag); neither is a Nostr entity, so not modelled in v1. Note d is used on an ephemeral kind as a room label, not an address. |
| 23903 | `WakeUpEvent` | e -> ABOUT (E); p -> ABOUT_AUTHOR (U); k -> TAG (T) | eventIds() (ETag::parseId), authorKeys() (PTag::parseKey), kinds() (KindTag) | Amethyst experimental push/wake kind (ephemeral, no spec). notifies() returns true for everyone, so p must not be read as RECIPIENT. |
| 30040 | `PublicationIndexEvent` | a -> MEMBER (A); e -> MEMBER (E); p -> MENTION (U); t -> HASHTAG (T); A/E -> SOURCE (A,E) | sections() / PublicationSectionRef.fromTags (a and e, in order), linkedAddressIds() (ATag), linkedPubKeys() (PTag), topics() (hashtags()); A/E: new parser needed | NKBIP-01 (GitCitadel; spec not fetched). MEMBER as in the draft (30040 already listed). Props for MEMBER: order, inline title, level. UNCERTAIN: p semantics unverified (author/contributor pubkey vs mention); author tag is a human name, not a pubkey. EventHintProvider missing: e sections are not in any hint provider. |
| 30041 | `PublicationContentEvent` | T/c -> PUBLICATION (A); wikilink -> WIKILINK (E,T); wikilink[pubkey] -> WIKILINK_AUTHOR (U) | publicationAddress() (T, else c, + own pubkey -> 30040 address), wikilinks() (WikilinkTag::parse) | NKBIP-01. PUBLICATION target is DERIVED (T/c carry only the index d; pubkey assumed = section author), so the link is an inference. AsciiDoc content: no citedNIP19 parsing, so no content MENTIONs today. |
| 30045 | `BookshelfDirectoryEvent` | a -> MEMBER (A); e -> MEMBER (E) | items() (PublicationSectionRef.fromTags), linkedAddressIds() (ATag) | Bookshelf directory (no NIP; already MEMBER in the draft). Hint gap: e entries are not in any EventHintProvider. |
| 30053 | `NNSEvent` | *none* | – | NNS (Nostr name system) record: ip4/ip6/version values only. Bug: neither build() writes a d tag on this addressable kind (every record collapses onto d=""). |
| 30142 | `LearningResourceEvent` | t -> HASHTAG (T) | topics() (hashtags()) | No NIP (edu publishers, schema.org-style flat tags). about:id / learningResourceType:id are vocabulary URIs, encoding:contentUrl a URL: not modelled. |
| 30296 | `InteractiveStoryPrologueEvent` | option -> OPTION (A) | options() (StoryOptionTag::parse) | Interactive stories (no NIP in KDoc). RootScope. StoryOptionTag.parse keeps the relay as a raw string (not normalized). |
| 30297 | `InteractiveStorySceneEvent` | option -> OPTION (A) | options() (StoryOptionTag::parse) | Interactive stories. RootScope. |
| 30298 | `InteractiveStoryReadingStateEvent` | A -> ROOT (A); a -> CURRENT_SCENE (A) | root() (RootSceneTag::parse, uppercase A), currentScene() (ATag::parseAddress) | d = the root story address (a reference in d). BUGS: build() calls rootScene(rootTag), which writes a lowercase a (ATag.toATagArray), and currentScene() then addUnique-replaces it, so Quartz-built events carry no A and root() returns null; build() also swaps storyImage(summary)/storySummary(image). |
| 30392 | `UserTrustedListEvent` | p -> MEMBER (U); a -> ABOUT (A); observer -> OBSERVER (U); source-tag -> SOURCE_TAG (E) | members() (PubKeyMemberTag), aboutAddresses() (ATag), observer() (ObserverTag), sourceTag() (SourceTag) | Tapestry Trusted Lists (+10 of NIP-85 kinds). MEMBER already in the draft; props score (0..100). source-tag also carries the tag's author and slug (hint/provenance; no link proposed). Hint providers ignore observer and source-tag. |
| 30393 | `EventTrustedListEvent` | e -> MEMBER (E); a -> ABOUT (A); p -> ABOUT (U); observer -> OBSERVER (U); source-tag -> SOURCE_TAG (E) | members() (EventMemberTag), aboutAddresses(), aboutPubKeys(), observer(), sourceTag() | Tapestry Trusted Lists. By convention the p is the observer (for #p discovery), duplicating the observer tag; kept as ABOUT (the slot's role). e[3] is a score, not a NIP-10 marker. |
| 30394 | `AddressableTrustedListEvent` | a -> MEMBER (A); p -> ABOUT (U); observer -> OBSERVER (U); source-tag -> SOURCE_TAG (E) | members() (AddressMemberTag), aboutPubKeys(), observer(), sourceTag() | Tapestry Trusted Lists. AddressMemberTag.parseAddressId returns any non-empty a value (no coordinate-shape check). |
| 30395 | `ExternalIdTrustedListEvent` | i -> MEMBER (T); a -> ABOUT (A); p -> ABOUT (U); observer -> OBSERVER (U); source-tag -> SOURCE_TAG (E) | members() (ExternalIdMemberTag), aboutAddresses(), aboutPubKeys(), observer(), sourceTag() | Tapestry Trusted Lists. i members are NIP-73 external ids: MEMBER (T) rather than TAG, since they are list entries (MEMBER needs T added to its targets). |
| 30817 | `NipTextEvent` | a[fork] -> FORK (A); e[fork] -> FORK (E); q -> QUOTE (E,A); a -> MENTION (A); p -> MENTION (U); k -> TAG (T); content nostr: -> MENTION (E,A,U) | forkFromAddress() (ForkTag::parseAddress), forkFromVersion() (MarkedETag.parseForkedEventId), QTag::parseEventId/parseAddressId, ATag::parseAddressId, citedNIP19(), kinds (KindTag) | NIPs-on-Nostr (draft). FORK extends from kind 1 to A targets. BUG: ForkTag.parse / parseValidAddress require kind 34550 (CommunityDefinitionEvent, copy-paste from NIP-72) instead of 30817; forkFromAddress() uses parseAddress, which checks 30817 but not the fork marker, so any a to a 30817 is read as the fork source. Known QTag.parseAddressId bug applies. |
| 31337 | `AudioTrackEvent` | p -> PARTICIPANT (U) | participants() (ParticipantTag::parse) | Zapstr-style audio track (no NIP in KDoc). Zapstr p tags carry a role (Host/Artist) at p[3], which ParticipantTag ignores: should become a prop. c is a type/genre value (not allowlisted); media/cover URLs not modelled. |
| 31871 | `AttestationEvent` | e -> ASSERTION (E); a -> ASSERTION (A); request -> REQUEST (A) | assertionEventId() (ETag), assertionAddrId() (ATag), requestId() / requestAddress() (RequestTag) | Attestations draft (spec not fetched). BUG: RequestTag is copy-pasted from NIP-72 ApprovedAddressTag: parse() returns ApprovedAddressTag and rejects 34550 addresses (meaningless here); linkedAddressIds()/addressHints() ignore the request tag. e carries the attested author at e[3] (hint). |
| 31872 | `AttestationRequestEvent` | e -> ASSERTION (E); a -> ASSERTION (A); p -> ATTESTOR (U) | assertionEventId() (ETag), assertionAddrId() (ATag), linkedPubKeys() (PTag); attestorPubKeys builder | Attestations draft. Naming bug: assertionPubkey()/assertionPTag() read the p, which the builder fills with ATTESTORS, not the assertion's author. cashu_token is a value (payment), not modelled. |
| 31873 | `AttestorRecommendationEvent` | d -> RECOMMENDED (U); k -> TAG (T) | new parser needed (the attestor pubkey is only in dTag(); builder dTag(attestorPubKey)); kinds() (KindTag) | Attestations draft. Reuses RECOMMENDED (NIP-89) with a new U target: the recommended attestor, props kinds. A pubkey in a d tag is invisible to every hint provider and to #p filters; no accessor validates it is 64-hex. |
| 31987 | `RelayReviewEvent` | *none* | – | Relay review (no merged NIP). The reviewed thing is a relay URL (d, or relay tag): relays are not link targets in v1. If relays become nodes, it would be RATED. |
| 32176 | `BlossomPieceIndexEvent` | r -> TAG (T) | url() (firstValue("r")) | No NIP. x (whole-file hash), b (piece hashes) and blossom (servers) are not modelled; note b here means piece hash, unrelated to Tapestry's b (inherit-from). |
| 32267 | `SoftwareApplicationEvent` | a -> RELEASE (A); t -> HASHTAG (T) | appLinks() (ATag::parse), topics() (HashtagTag) | NIP-82 draft. UNCERTAIN: a semantics not documented in Quartz (appLink builder has no KDoc); zapstore apps a-tag their latest 30063 release, hence RELEASE. repository/url/icon/image are URLs, f platform a value. |
| 33401 | `ExerciseTemplateEvent` | *none* | – | NIP-101e draft exercise template (POWR). Referenced by 1301 EXERCISE links; carries only values (title, format, format_units, equipment, difficulty). |
| 33863 | `FundraiserEvent` | t -> HASHTAG (T) | topics() (hashtags()) | Agora app kind (Ditto, no NIP). w = on-chain donation addresses, goal/deadline values: not modelled. Zaps to this event arrive as ZAPPED from 9735s. |
| 34139 | `MusicPlaylistEvent` | a -> CURATED (A); t -> HASHTAG (T) | trackAddresses() (ATag::parseAddress filtered to kind 36787); hashtags | No NIP in KDoc. A playlist is a published curation set of tracks, like 30004-30006, so CURATED (props: order). UNCERTAIN alternative per rule 7: TRACK. Non-track a tags are preserved by edit() but not interpreted. |
| 34238 | `VideoCollaborationEvent` | a -> COLLABORATED (A); p -> COLLABORATED_AUTHOR (U) | video() (ATag::parseAddress), videoAuthor() (PTag::parseKey) | divine-web / divine-mobile convention (no NIP). d may also be the video coordinate (divine-mobile). Listed as REFERENCE-only in the draft; now classified. UNCERTAIN: if declined answers matter to queries, split per rule 4. |
| 34259 | `EntityRatingEvent` | d -> RATED (E,A,U,T); a -> RATED (A); A -> RATED (A); e -> RATED (E); p -> RATED_AUTHOR (U); k -> TAG (T) | targetIdentifier()/mark() (d with mark prefix), targetAddress() (ATag, RootAddressTag), targetEventId() (ETag), targetAuthor() (PTag), targetKind() (ReplyKindTag) | abh3po/nostr-polls XYZ.md. d target type depends on m (event id -> E, profile -> U, coordinate -> A, hashtag/books/movies/relay -> T; relay ones fall under the not-modelled rule). Props: stars/rating, mark. a and A duplicate the same coordinate: emit one link. |
| 36787 | `MusicTrackEvent` | t -> HASHTAG (T) | hashtags (HashtagTag); builder hashtag("music") | No NIP in KDoc. artist/album are free-text names, url/video/image URLs. edit() mentions zap split tags being preserved but no accessor reads them (NIP-57 zap splits would need a relation decided for all kinds). |
| 38192 | `Ps1SaveEvent` | *none* | – | PS1 memory-card blocks (no NIP). m (memory card id), x (hash), block/state/filename/region/title are values. |
| 39998 | `AddressableListHeaderEvent` | b -> INHERIT_FROM (A); concept-graph -> CONCEPT_GRAPH (A) | inheritFrom() (InheritFromTag::parse), conceptGraph() (ConceptGraphTag::parse, else computed) | Tapestry drafts on Decentralized Lists. CONCEPT_GRAPH is computable when the tag is absent (39999:<pubkey>:<d>-concept-graph): only emit it when present. json tag may embed node uuids (addresses) in JSON: not modelled. b-tag-deferred marker is not a link. |
| 39999 | `AddressableListItemEvent` | z -> PARENT_LIST (E,A,T); p -> ITEM (U); e -> ITEM (E); a -> ITEM (A); t -> ITEM (T); b -> INHERIT_FROM (A); n -> ELEMENT_OF (A); s -> SUBSET_OF (A); q -> QUOTE (E,A) | parentLists() (ParentListTag), itemPubKeys()/itemEvents()/itemAddresses()/itemStrings(), inheritFrom() (InheritFromTag), elementOf() (ElementOfTag), subsetOf() (SubsetOfTag); q: new parser needed (CurationCopy only writes it) | Tapestry. q appears on assistant curation copies, pointing back to the original (address and exact version, author at q[3]); QUOTE reused, UNCERTAIN (a COPIED relation would be more precise). Taggings (TagElement, PubKeyTagging, EventTagging, TagPin) overload the item slots (e.g. PubKeyTagging: p = target, a/e = the tag applied) and are only told apart by deployment-configured z namespaces, so links() can only emit the generic ITEM; polarity/curation-method are props. Known QTag.parseAddressId bug breaks the address q. |

### `nip90Dvms` (40)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 5000 | `DvmTextExtractionRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5000: input is a url (audio/video) or event. Quartz build() writes i[url]; reads inputs(), outputMimeType(), range/alignment params. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5001 | `DvmSummarizationRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5001: example mixes i[event] and i[job]. Quartz build() writes i[event] per eventId (inputEvent). NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5002 | `DvmTranslationRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5002: i[event] to translate. Quartz build() writes i[event] (inputEvent); param language. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5050 | `DvmTextGenerationRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5050 uses input-type 'prompt' (not in NIP-90's list; free text, not modelled). Quartz build() writes i[prompt]; indexes prompt/text inputs for search. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5100 | `DvmImageGenerationRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5100: i[text] prompt + optional i[url] source image. Quartz build() writes both (sourceImageUrl -> i[url]). NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5200 | `DvmVideoConversionRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5200: i[url] social media/video link. Quartz build() writes i[url]. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5201 | `DvmVideoTranslationRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5201 example has i[url], i[event] and i[job]. Quartz build() writes i[url] only. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5202 | `DvmImageToVideoRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5202: i[url] image. Quartz build() writes i[url] (imageUrl()). NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5250 | `DvmTextToSpeechRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | Quartz build() writes i[text] (not modelled); i[event] possible per NIP-90. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5300 | `DvmContentDiscoveryRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); param[user] -> FOR_USER (U); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people); dvmPubKey() (first p only) and user() = tags.dvmParam("user"), both on the class | DVM spec kinds/5300 lists the user as a `p` param, which collides with NIP-90's `p` = service provider; Quartz (and Amethyst) put the DVM in `p` and the user in param 'user'. UNCERTAIN: other clients may put the user in `p`. relays tag (RelaysTag) not modelled. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5301 | `DvmUserDiscoveryRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); param[user] -> FOR_USER (U); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people); dvmPubKey() (first p only) and user() = tags.dvmParam("user") | DVM spec kinds/5301 is a copy of 5300 (same p-param ambiguity). build() takes only an initializer: Quartz writes nothing itself. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5302 | `DvmContentSearchRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); param[users] -> SEARCH_AUTHOR (U); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people); users() returns the raw param string: new parser needed to decode its JSON-stringified p tags | Quartz build() writes i[text] query (not modelled) and param users as an opaque string. UNCERTAIN: SEARCH_AUTHOR name; the value is a JSON array of p tags in the spec example. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5303 | `DvmPeopleSearchRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | Quartz build() writes i[text] query (not modelled) and max_results. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5400 | `DvmEventCountRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5400: inputs are tag values (i[text]); content is a NIP-01 filter JSON whose ids/authors/#e/#p are a query, not a statement: not modelled. params relay/group not modelled. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5500 | `DvmMalwareScanRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5500: i[url] file to scan. Quartz build() writes i[url] (fileUrl()). NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5900 | `DvmEventTimestampingRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5900: i[event] = event to stamp (eventIdToStamp()). INPUT, not TIMESTAMPED: the request asks for a stamp; the 1040 proof (TIMESTAMPED) comes back as the 6900's RESULT. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5901 | `DvmOpReturnRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5901: i[text] OP_RETURN payload (not modelled). NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5905 | `DvmEventPublishScheduleRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); i[text] embedded event JSON -> SCHEDULED (E); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people); eventJsons() returns the raw JSON strings: new parser needed to read each embedded event's id | DVM spec kinds/5905: request is normally encrypted (i in NIP-04 content, only p visible), so SCHEDULED is rare in public data. UNCERTAIN: SCHEDULED could be dropped since the 6905 RESULT names the same published id. params relays not modelled. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 5970 | `DvmEventPowDelegationRequestEvent` | i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); p -> SERVICE_PROVIDER (U) | tags.inputs() / InputTag.parse + firstInputByType() (nip90Dvms/tags/TagArrayExt.kt); p: new parser needed (generic tags.taggedUserIds(), nip01Core/tags/people) | DVM spec kinds/5970: i[text] is an UNSIGNED event template (no id until mined): not modelled. param pow not modelled. NIP-90 job request; every request may carry any input type and 'p' (NIP-90: 'Service Providers the customer is interested in'), so one shared DVM-request links() covers them. SERVICE_PROVIDER reused from NIP-85 (NIP-90 calls the actor 'Service providers'); UNCERTAIN: rule 4 may want it split from the NIP-85 10040 meaning. i[text]/i[prompt] are free text: not modelled. param/output/bid/relays: not modelled. With an 'encrypted' tag the i/param tags move into NIP-04 content: the graph sees only p. |
| 6000 | `DvmTextExtractionResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = extracted text (free text; Quartz parses no nostr: URIs). NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6001 | `DvmSummarizationResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = summary text (free text; Quartz parses no nostr: URIs). UNCERTAIN: a summary may cite nostr: URIs; content nostr: -> MENTION would need a new parser. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6002 | `DvmTranslationResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = translated text (free text; Quartz parses no nostr: URIs). NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6050 | `DvmTextGenerationResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = generated text (free text; Quartz parses no nostr: URIs). NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6100 | `DvmImageGenerationResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = image URL: not modelled. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6200 | `DvmVideoConversionResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = video URL: not modelled. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6201 | `DvmVideoTranslationResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = video URL: not modelled. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6202 | `DvmImageToVideoResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = video URL: not modelled. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6250 | `DvmTextToSpeechResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = audio URL: not modelled. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6300 | `DvmContentDiscoveryResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); content e/a tags -> RESULT (E,A) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags); innerTags() (parses content as a tag array, keeps e/a values) | DVM spec kinds/5300 output: content = JSON-stringified list of e/a tags. Quartz wart: innerTags() returns List<HexKey> mixing event ids and address strings (Amethyst re-splits with splitInnerTags) and drops relay hints; a typed parser is needed. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6301 | `DvmUserDiscoveryResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); content p tags -> RESULT (U) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags); innerTags() (content tag array, p values only) | DVM spec kinds/5301 prose says output tags 'SHOULD be a or e' (copy of 5300) but its example returns p; Quartz reads p only. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6302 | `DvmContentSearchResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); content e/a tags -> RESULT (E,A) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags); innerTags() (content tag array, e/a values) | DVM spec kinds/5302 output: content = JSON-stringified e/a tags. Same innerTags() mixed-type wart as 6300. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6303 | `DvmPeopleSearchResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); content p tags -> RESULT (U) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags); innerTags() (content tag array, p values) | DVM spec kinds/5303 output: content = JSON-stringified p tags. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6400 | `DvmEventCountResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = a number or a grouped-count JSON (count()): not a reference. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6500 | `DvmMalwareScanResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = 'CLEAN' or scan report text: not a reference. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6900 | `DvmEventTimestampingResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); content event id -> RESULT (E) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags); otsEventId() (content) | DVM spec kinds/5900 output: content MUST be the id of the kind 1040 OTS event (which itself links TIMESTAMPED to the stamped event). Quartz does not check it is 64-hex. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6901 | `DvmOpReturnResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags) | Content = bitcoin txid (transactionId()): not a Nostr entity, not modelled. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6905 | `DvmEventPublishScheduleResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); content event id -> RESULT (E) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags); publishedEventId() (content) | DVM spec kinds/5905 output: 'Event ID that was published'. Spec example is copy-pasted from 5900 (says 1040 / kind 6900). Quartz does not check 64-hex. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 6970 | `DvmEventPowDelegationResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U); i[event] -> INPUT (E); i[job] -> INPUT_JOB (E); i[url] -> TAG (T); content mined event JSON -> RESULT (E) | new parser needed: the class exposes no e/p accessor; build from generic tags.taggedEventIds() / tags.taggedUserIds() (nip01Core/tags) + tags.inputs() (nip90Dvms/tags); new parser needed: the class exposes no content accessor; parse the JSON and take its id | DVM spec kinds/5970 output: 'Mined event json with nonce and calculated id'. UNCERTAIN: RESULT to an embedded event that may never be published. NIP-90 job result (kind = request + 1000). The 'request' tag embeds the stringified job request (same target as e: not a second link). 'amount' (msats [+bolt11]) could ride as prop msats on JOB_REQUEST. With 'encrypted', i is omitted and content is NIP-04 ciphertext. Amethyst already follows e (FavoriteAlgoFeedsOrchestrator filters #e = requestId). |
| 7000 | `DvmStatusEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U) | new parser needed: the class exposes only status() and firstAmount(); build from generic tags.taggedEventIds() / tags.taggedUserIds() | NIP-90 job feedback. status (code, extra-info) and amount (msats, bolt11) are props, not links: status could ride on JOB_REQUEST (prop status). Content may hold a partial result (free text; for 5300 feeds an e/a tag list could appear: UNCERTAIN). With 'encrypted' the content is NIP-04. Amethyst follows #e = requestId. |
| 11998 | `DvmHeartbeatEvent` | *none* | status(), expiration(); dTag() | Experimental DVM heartbeat (no NIP). Tags d (the DVM's NIP-89 d), status (free text), expiration: none points at another entity. Its d mirrors Address(31990, author, d), a derived link no tag states (not proposed). Replaceable-range kind on BaseAddressableEvent by design (d splits the client-side address). |

### `nip51Lists` (32)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10000 | `MuteListEvent` | p -> MUTE (U); e -> MUTE (E); t -> MUTE (T); word -> MUTE (T) | publicMutes() / MuteTag.parse (UserTag, EventTag, HashtagTag, WordTag in nip51Lists/muteList/tags); tags.mutedUserIds(), mutedThreadIds(), mutedHashtags(), mutedWords() | NIP-51 mute list (p pubkeys, t hashtags, word lowercase strings, e threads). PRIVATE entries: yes (NIP-44 content, privateMutes()); the graph sees public tags only. t targets the same Tag(t) node HASHTAG uses; 'word' is not in the T allowlist (i/r/g/k/l/L/t): needs allowlisting as Tag(word). Quartz: implements PubKeyHintProvider but no EventHintProvider although it holds e threads. |
| 10001 | `PinListEvent` | e -> PIN (E) | pinnedEvents() / EventBookmark.parse; linkedEventIds() (EventHintProvider) | NIP-51 pinned notes (e kind 1). PRIVATE entries: no (BaseReplaceableEvent, never decrypts). Quartz reads e only; an a tag would be ignored (NIP-51 lists e only). |
| 10003 | `BookmarkListEvent` | e -> BOOKMARK (E); a -> BOOKMARK (A) | publicBookmarks() / BookmarkIdTag.parse (EventBookmark, AddressBookmark); linkedEventIds(), linkedAddressIds() | NIP-51 bookmarks (e kind 1, a kind 30023). PRIVATE entries: yes (PrivateReplaceableTagArrayEvent, privateBookmarks()). EventBookmark also reads an author pubkey hint from positions 2-4: a hint, not a link (no BOOKMARK_AUTHOR proposed). Quartz: linkedAddressIds() uses parseAddressId, which returns the raw a value unvalidated (parseValidAddress exists). |
| 10006 | `BlockedRelayListEvent` | *none* | publicRelays() / RelayTag.parse (relay URLs only) | NIP-51 blocked relays (relay tags). Relay URLs are not link targets in v1: not modelled. PRIVATE entries: yes (PrivateTagArrayEvent). Quartz: a replaceable kind on PrivateTagArrayEvent (BaseAddressableEvent) with no dTag() override, so a stray d tag splits its ADDRESS (MuteListEvent/FavoriteFollowSetsListEvent pin it to ""). |
| 10009 | `SimpleGroupListEvent` | group -> SUBSCRIBED (T) | publicGroups() / GroupTag.parse (nip51Lists/simpleGroupList) | NIP-51 simple groups: 'NIP-29 groups the user is in' (group = id + relay URL + name), filed with 10004/10005 as SUBSCRIBED. The group is not an event/address/user (its 39000 metadata is signed by an unknown relay key), so the target is Tag(group, "<host>'<id>" per NIP-29's identifier form): needs 'group' added to the T allowlist. UNCERTAIN: target encoding. The r tags NIP-51 also lists are relay URLs (not modelled; Quartz does not read them). PRIVATE entries: yes. Quartz: no dTag() override (see 10006). |
| 10012 | `FavoriteRelayListEvent` | a -> FAVORITE (A) | publicRelaySets() = tags.relaySetPointers() (AddressBookmark filtered to kind 30002); relay tags via publicRelays() | NIP-51 relay feeds: relay tags (not modelled) and a to kind 30002 relay sets. PRIVATE entries: yes (privateTags; no private relay-set accessor). Quartz: no dTag() override (see 10006). |
| 10015 | `InterestListEvent` | t -> SUBSCRIBED (T); a -> SUBSCRIBED (A) | publicHashtags() (HashtagTag.parse); publicInterestSets() = tags.interestSetPointers() (AddressBookmark filtered to kind 30015) | NIP-51 interests: t hashtags and a to kind 30015 interest sets (draft: SUBSCRIBED). t target is the same Tag(t) node HASHTAG uses. PRIVATE entries: yes (privateTags, privateInterestSets()). Quartz: no dTag() override (see 10006). |
| 10017 | `GitAuthorListEvent` | p -> SUBSCRIBED (U) | publicAuthors() / GitAuthorTag.parse; linkedPubKeys() (PubKeyHintProvider) | NIP-51 git authors: 'code (people who produce NIP-34 events) follow list', p with relay hint + petname like NIP-02. UNCERTAIN: the draft files 10017 under MEMBER; rule 4 (FOLLOW is kind 3, every other follow-like list is SUBSCRIBED) and 10020's identical shape argue for SUBSCRIBED. Petname could ride as a prop. PRIVATE entries: yes. Quartz: no dTag() override (see 10006). |
| 10018 | `GitRepositoryListEvent` | a -> SUBSCRIBED (A) | publicRepositories() / AddressBookmark.parse; linkedAddressIds() (AddressHintProvider) | NIP-51 git repositories: 'NIP-34 followed repositories' (a kind 30617), a follow-like list, so SUBSCRIBED (not in the draft yet; REPOSITORY is a patch's repo). PRIVATE entries: yes. Quartz: linkedAddressIds() returns unvalidated a values; no dTag() override (see 10006). |
| 10020 | `MediaFollowListEvent` | p -> SUBSCRIBED (U) | publicFollows() / UserTag.parse; linkedPubKeys() (PubKeyHintProvider) | NIP-51 media follows (draft: SUBSCRIBED). PRIVATE entries: yes (privateFollows()). Quartz: UserTag drops the NIP-02 petname; no dTag() override (see 10006). |
| 10021 | `FavoriteFollowSetsListEvent` | a -> FAVORITE (A) | publicFavoriteFollowSets() = tags.favoriteFollowSetBookmarks() (AddressBookmark filtered to kind 30000) | NIP-51 kind 10021. Not in the draft. Quartz skips a tags of other kinds. PRIVATE entries: yes (privateFavoriteFollowSets()). dTag() correctly pinned to "". No hint provider implemented. |
| 10081 | `GeohashListEvent` | g -> SUBSCRIBED (T) | publicGeohashes() = tags.geohashList() (GeoHashTag.parse, nip01Core/tags/geohash) | Followed locations (geohashes). Not in the NIP-51 table (Amethyst kind). Target is Tag(g) like TAG. PRIVATE entries: yes (decryptPrivateGeohashes()). QUARTZ BUG (privacy): the non-suspend create(publicGeohashes, privateGeohashes, NostrSignerSync) swaps them (privateTagArray = publicGeohashes, publicTagArray = privateGeohashes), so private geohashes are published in clear tags. No dTag() override (see 10006). |
| 10086 | `IndexerRelayListEvent` | *none* | publicRelays() / RelayTag.parse (relay URLs only) | Indexer relays (relay tags); not in the NIP-51 table. Relay URLs are not link targets in v1: not modelled. PRIVATE entries: yes (PrivateTagArrayEvent). Quartz: a replaceable kind on PrivateTagArrayEvent (BaseAddressableEvent) with no dTag() override, so a stray d tag splits its ADDRESS (MuteListEvent/FavoriteFollowSetsListEvent pin it to ""). |
| 10087 | `ProxyRelayListEvent` | *none* | publicRelays() / RelayTag.parse (relay URLs only) | Proxy relays (relay tags); not in the NIP-51 table. Relay URLs are not link targets in v1: not modelled. PRIVATE entries: yes (PrivateTagArrayEvent). Quartz: a replaceable kind on PrivateTagArrayEvent (BaseAddressableEvent) with no dTag() override, so a stray d tag splits its ADDRESS (MuteListEvent/FavoriteFollowSetsListEvent pin it to ""). |
| 10088 | `BroadcastRelayListEvent` | *none* | publicRelays() / RelayTag.parse (relay URLs only) | Broadcast relays (relay tags); not in the NIP-51 table. Relay URLs are not link targets in v1: not modelled. PRIVATE entries: yes (PrivateTagArrayEvent). Quartz: a replaceable kind on PrivateTagArrayEvent (BaseAddressableEvent) with no dTag() override, so a stray d tag splits its ADDRESS (MuteListEvent/FavoriteFollowSetsListEvent pin it to ""). |
| 10089 | `TrustedRelayListEvent` | *none* | publicRelays() / RelayTag.parse (relay URLs only) | Trusted relays (relay tags); not in the NIP-51 table. Relay URLs are not link targets in v1: not modelled. PRIVATE entries: yes (PrivateTagArrayEvent). Quartz: a replaceable kind on PrivateTagArrayEvent (BaseAddressableEvent) with no dTag() override, so a stray d tag splits its ADDRESS (MuteListEvent/FavoriteFollowSetsListEvent pin it to ""). |
| 10090 | `FavoriteAlgoFeedsListEvent` | a -> FAVORITE (A) | publicFavoriteAlgoFeeds() / AddressBookmark.parse; tags.favoriteAlgoFeedsList() | Not in the NIP-51 table (Amethyst kind). a points at feed DVM announcements (kind 31990); Quartz does not filter by kind. PRIVATE entries: yes (privateFavoriteAlgoFeeds()). No dTag() override (see 10006). |
| 10101 | `GoodWikiAuthorListEvent` | p -> RECOMMENDED (U) | publicAuthors() / UserTag.parse; linkedPubKeys() (PubKeyHintProvider) | NIP-51 good wiki authors: 'NIP-54 user recommended wiki authors'. UNCERTAIN: the draft files 10101 under MEMBER; NIP-51's own word is 'recommended', which reuses RECOMMENDED (extends its targets from A to U). PRIVATE entries: yes. No dTag() override (see 10006). |
| 10102 | `GoodWikiRelayListEvent` | *none* | publicRelays() / RelayTag.parse (relay URLs only) | NIP-51 good wiki relays (relay tags). Kind 10102 in Quartz; the draft's note that it claims kind 1010 does not match the current class (KIND = 10102). Relay URLs are not link targets in v1: not modelled. PRIVATE entries: yes (PrivateTagArrayEvent). Quartz: a replaceable kind on PrivateTagArrayEvent (BaseAddressableEvent) with no dTag() override, so a stray d tag splits its ADDRESS (MuteListEvent/FavoriteFollowSetsListEvent pin it to ""). |
| 30000 | `FollowSetEvent` | p -> MEMBER (U); [d=mute] p/e/t/word -> MUTE (U,E,T) | users() = tags.users() (UserTag.parse); linkedPubKeys(); publicMembers() parses MuteTag (p/e/t/word) | NIP-51 follow sets (draft: MEMBER). The deprecated d='mute' form is a mute list (NIP-51 'use instead kind 10000'), which is why Quartz parses MuteTag here: those entries should be MUTE. PRIVATE entries: yes (privateMembers()). |
| 30001 | `OldBookmarkListEvent` | e -> BOOKMARK (E); a -> BOOKMARK (A); [d=pin] e -> PIN (E); [d=communities] a -> SUBSCRIBED (A) | publicBookmarks() / BookmarkIdTag.parse; linkedEventIds(), linkedAddressIds() | Deprecated NIP-51 kind 30001 (d='bookmark' -> 10003, d='pin' -> 10001, d='communities' -> 10004). Quartz treats every 30001 as bookmarks regardless of d; the semantic method should branch on d. PRIVATE entries: yes. linkedAddressIds() unvalidated (see 10003). |
| 30002 | `RelaySetEvent` | *none* | relays() / RelayTag.parse | NIP-51 relay sets (relay tags only): not modelled. PRIVATE entries: yes. |
| 30003 | `BookmarkSetEvent` | e -> BOOKMARK (E); a -> BOOKMARK (A) | publicBookmarks() / BookmarkIdTag.parse; linkedEventIds(), linkedAddressIds() | NIP-51 bookmark sets (draft: BOOKMARK). PRIVATE entries: yes (PrivateTagArrayEvent; no privateBookmarks() accessor on this class, only privateTags()). linkedAddressIds() unvalidated (see 10003). |
| 30004 | `ArticleCurationSetEvent` | a -> CURATED (A); e -> CURATED (E) | publicItems() / BookmarkIdTag.parse; linkedEventIds(), linkedAddressIds() | NIP-51 curation set (a kind 30023, e kind 1) (draft: CURATED). PRIVATE entries: yes (PrivateTagArrayEvent). linkedAddressIds() unvalidated. |
| 30005 | `VideoCurationSetEvent` | e -> CURATED (E); a -> CURATED (A) | publicItems() / BookmarkIdTag.parse; linkedEventIds(), linkedAddressIds() | NIP-51 lists e (kind 21 videos) only; Quartz also accepts a (addressable videos). PRIVATE entries: yes. |
| 30006 | `PictureCurationSetEvent` | e -> CURATED (E); a -> CURATED (A) | publicItems() / BookmarkIdTag.parse; linkedEventIds() (EventHintProvider only) | NIP-51 lists e (kind 20 pictures) only. Quartz inconsistency: publicItems() accepts a too, but the class implements no AddressHintProvider. PRIVATE entries: yes. |
| 30007 | `KindMuteSetEvent` | p -> MUTE (U) | publicMutedUsers() / UserTag.parse; linkedPubKeys() (PubKeyHintProvider) | NIP-51 kind mute sets: 'mute pubkeys by kinds', d MUST be the kind string: the muted kind should ride as a prop (e.g. muted_kind = d) since kind stays off relation names. PRIVATE entries: yes. |
| 30015 | `InterestSetEvent` | t -> MEMBER (T) | publicHashtags() (HashtagTag.parse) | NIP-51 interest sets: 'interest topics represented by a bunch of hashtags'. A named set, so MEMBER as for follow sets (the draft names 30015 nowhere; 10015's pointer to it is SUBSCRIBED). t target is the Tag(t) node HASHTAG uses. PRIVATE entries: yes (privateHashtags()). |
| 30063 | `ReleaseArtifactSetEvent` | e -> CURATED (E); a -> APPLICATION (A); i -> TAG (T) | items() / BookmarkIdTag.parse; assets() (NIP-82 AssetTag e); appId() (NIP-82 i); linkedEventIds(), linkedAddressIds() | Kind shared by NIP-51 release artifact set and NIP-82 software release (isNip82SoftwareRelease()). e = artifacts (NIP-51 kind 1063; NIP-82 kind 3063 assets). UNCERTAIN: draft says CURATED; a release's files are not a curation, a dedicated ARTIFACT (NIP-51 'release artifact') may read better. NIP-82 i = app id (TAG); c channel / version not modelled. PRIVATE entries: no (BaseAddressableEvent, though NIP-51 content may hold them). |
| 30267 | `AppCurationSetEvent` | a -> CURATED (A) | apps() / AddressBookmark.parse; linkedAddressIds() (AddressHintProvider) | NIP-51 app curation sets (a kind 32267 software applications) (draft: CURATED). PRIVATE entries: no in Quartz (BaseAddressableEvent). |
| 39089 | `StarterPackEvent` | p -> MEMBER (U); t -> HASHTAG (T) | follows() / followIds() (UserTag.parse, nip51Lists/starterPack/TagArrayExt.kt); hashtags() (HashtagTag.parse); linkedPubKeys() | NIP-51 starter packs (draft: MEMBER). Quartz also reads t as topics. PRIVATE entries: no (BaseAddressableEvent). |
| 39092 | `MediaStarterPackEvent` | p -> MEMBER (U) | follows() / followIds() (UserTag.parse); linkedPubKeys() (PubKeyHintProvider) | NIP-51 media starter packs (draft: MEMBER). PRIVATE entries: no (BaseAddressableEvent). |

### `nip29RelayGroups` (16)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 9000 | `GroupPutUserEvent` | h -> GROUP (T\|A); p -> ADDED_USER (U) | groupId() (GroupIdTag), userPubKeys() (PTag::parseKey; roles are p[2..], read via GroupAdminTag::parse) | NIP-29 put-user, with roles riding in the p tag. The Buzz role tag is also written, as a props source. previous holds 8-char event-id prefixes, which cannot resolve to event ids and are not modelled. UNCERTAIN: PUT_USER (literal NIP word) vs ADDED_USER shared with NIP-43. |
| 9001 | `GroupRemoveUserEvent` | h -> GROUP (T\|A); p -> REMOVED_USER (U) | groupId(), userPubKeys() (PTag::parseKey) | NIP-29 remove-user. previous is not modelled (see 9000). |
| 9002 | `GroupEditMetadataEvent` | h -> GROUP (T\|A); parent -> PARENT (T\|A group); child -> CHILD (T\|A group); t -> HASHTAG (T); g -> TAG (T) | groupId(), parent() (ParentTag::parse), children() (ChildTag::parse), hashtags(), geohashes() | NIP-29 edit-metadata. The parent and child values are group ids on the same relay, so they share GROUP's target representation. previous is not modelled. |
| 9005 | `GroupDeleteEventEvent` | h -> GROUP (T\|A); e -> DELETED (E) | groupId(), deletedEventIds() (mapValueTagged('e')) | NIP-29 delete-event: a moderator deletion, which NIP-09's owner-only rule does not govern. DELETED is the NIP's action word. UNCERTAIN: a NIP-09 enforcer querying DELETED must filter on source kind 5, so rule 4 may argue for a separate MODERATOR_DELETED. deletedEventIds() does not validate 64-hex. previous is not modelled. |
| 9007 | `CreateGroupEvent` | h -> GROUP (T\|A) | groupId() (GroupIdTag::parse) | NIP-29 create-group. GROUP target proposal: NIP-29 says a group is referenced by the naddr of its kind 39000 (pubkey = relay NIP-11 self, d = id), so target Address 39000:<relay-self>:<id> when the self key is known (always for a relay-side store; the 39xxx events carry it as author), else Tag('h', id) with the relay in props, or a new LinkTarget.Group(relay, id). A bare Tag('h', id) merges forks and migrations across relays, which NIP-29 says share the same id. The name, about, visibility and channel_type tags (Buzz) are not links. |
| 9008 | `DeleteGroupEvent` | h -> GROUP (T\|A) | groupId() | NIP-29 delete-group. The kind carries the action, so the h stays GROUP rather than DELETED (rule 2). |
| 9009 | `GroupCreateInviteEvent` | h -> GROUP (T\|A) | groupId() | NIP-29 create-invite. The code tag is a secret-ish value and is not modelled. |
| 9010 | `GroupUpdatePinListEvent` | h -> GROUP (T\|A); e -> PIN (E); a -> PIN (A) | groupId(), pins()/pinnedEventIds()/pinnedAddresses() (GroupPin, EventPin, AddressPin) | NIP-29 update-pin-list. It carries the full ordered list, so the order is a prop. The draft's PIN is E-only and must be widened to E, A (both 9010 and 39005 pin addresses). |
| 9021 | `GroupJoinRequestEvent` | h -> GROUP (T\|A) | groupId() | NIP-29 join request. The code (invite) is not modelled. |
| 9022 | `GroupLeaveRequestEvent` | h -> GROUP (T\|A) | groupId() | NIP-29 leave request. |
| 39000 | `GroupMetadataEvent` | parent -> PARENT (A); child -> CHILD (A); t -> HASHTAG (T); g -> TAG (T) | parent() (ParentTag::parse), children() (ChildTag::parse) -> Address 39000:<author>:<value>, hashtags(), geohashes() | NIP-29 group metadata, signed by the relay. The group node IS this event's ADDRESS (39000:<relay-self>:<id>), which is the NIP's own group reference. Subgroup parent and child are on the same relay, so their addresses are exact: 39000:<author>:<value>. t also carries the Buzz channel types (stream/forum/dm/workflow) as values, so those HASHTAGs are not topics (a quirk). |
| 39001 | `GroupAdminsEvent` | d (derived) -> GROUP (A); p -> ADMIN (U) | Address 39000:<author>:dTag() (groupId() = dTag()), admins() (GroupAdminTag::parse) | NIP-29 group admins. Relay-signed with d = group id, so the group is exactly Address 39000:<author>:<d>. It is derived, not tagged (UNCERTAIN whether derived links belong in links()). |
| 39002 | `GroupMembersEvent` | d (derived) -> GROUP (A); p -> MEMBER (U) | Address 39000:<author>:dTag() (groupId() = dTag()), members() (PTag::parseKey) | NIP-29 group members. It is not exhaustive (per the NIP). Relay-signed with d = group id, so the group is exactly Address 39000:<author>:<d>. It is derived, not tagged (UNCERTAIN whether derived links belong in links()). |
| 39003 | `GroupRolesEvent` | d (derived) -> GROUP (A) | Address 39000:<author>:dTag() (groupId() = dTag()), roles() (RoleTag::parse) has no references | NIP-29 group roles. The role names are values. Relay-signed with d = group id, so the group is exactly Address 39000:<author>:<d>. It is derived, not tagged (UNCERTAIN whether derived links belong in links()). |
| 39004 | `GroupParticipantsEvent` | d (derived) -> GROUP (A); participant -> PARTICIPANT (U) | Address 39000:<author>:dTag() (groupId() = dTag()), participants() (mapValueTagged('participant')) | NIP-29 LiveKit participants. participants() does not validate 64-hex. Relay-signed with d = group id, so the group is exactly Address 39000:<author>:<d>. It is derived, not tagged (UNCERTAIN whether derived links belong in links()). |
| 39005 | `GroupPinnedEvent` | d (derived) -> GROUP (A); e -> PIN (E); a -> PIN (A) | Address 39000:<author>:dTag() (groupId() = dTag()), pins()/pinnedEventIds()/pinnedAddresses() | NIP-29 group pinned events, ordered (order as a prop). PIN needs A added (see 9010). Relay-signed with d = group id, so the group is exactly Address 39000:<author>:<d>. It is derived, not tagged (UNCERTAIN whether derived links belong in links()). |

### `nip34Git` (12)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1617 | `GitPatchEvent` | a -> REPOSITORY (A); p[= repo owner] -> REPOSITORY_OWNER (U); p[other] -> MENTION (U); e[reply] -> PARENT (E); e[root] -> ROOT (E); t -> HASHTAG (T); r -> TAG (T) | repositoryAddress()/repository(), PTag::parseKey (+ compare with the a pubkey), MarkedETag parse (linkedEventIds), isRoot()/isRootRevision() (HashtagTag), earliestUniqueCommit() | NIP-34. e[reply] points at the previous patch in the series, or at the original root patch for a revision. UNCERTAIN: NIP-34 text shows only reply, but ngit also writes root markers on series. The p roles are told apart only by comparison with the a pubkey, so this needs a new parser. t values are the markers 'root' and 'root-revision'. r holds the euc and commit ids. commit, parent-commit and committer are git data, not modelled. The content is a patch, so there is no nostr: parsing. |
| 1618 | `GitPullRequestEvent` | a -> REPOSITORY (A); p[= repo owner] -> REPOSITORY_OWNER (U); p[other] -> MENTION (U); e -> REVISED (E); t -> HASHTAG (T); r -> TAG (T) | repositoryAddress(), PTag::parseKey, rootPatchId() (ETag::parseId), labels() (hashtags), earliestUniqueCommit() | NIP-34. UNCERTAIN: the placeholder is <root-patch-event-id>, so ROOT is an alternative, but the PR is not in that patch's thread. It supersedes it. t holds labels. c (tip commit), merge-base, branch-name and clone are not modelled. subject is not a link. |
| 1619 | `GitPullRequestUpdateEvent` | E -> ROOT (E); P -> ROOT_AUTHOR (U); a -> REPOSITORY (A); p[= repo owner] -> REPOSITORY_OWNER (U); p[other] -> MENTION (U); r -> TAG (T) | parentPullRequestId() (RootEventTag::parseKey), parentPullRequestAuthor() (RootAuthorTag::parseKey), repositoryAddress(), PTag::parseKey | NIP-34 PR update uses NIP-22 E/P for the PR, so ROOT and ROOT_AUTHOR follow the draft. c, clone and merge-base are not modelled. Quartz names the getter parentPullRequestId although the tag is the root E. The naming is fine, but note it for the golden test. |
| 1621 | `GitIssueEvent` | a -> REPOSITORY (A); p[= repo owner] -> REPOSITORY_OWNER (U); p[other] -> MENTION (U); q -> QUOTE (E,A); t -> HASHTAG (T); content nostr: -> MENTION (E,A,U) | repositoryAddress()/repository(), PTag::parseKey, QTag::parseEventId/parseAddressId, topics() (hashtags), citedNIP19() | NIP-34 issue. REPOSITORY, MENTION and QUOTE are already listed for 1621 in the draft. subject is not a link. Known QTag.parseAddressId bug. |
| 1622 | `GitReplyEvent` | a -> REPOSITORY (A); e[root] -> ROOT (E); e[reply] -> PARENT (E); p -> MENTION (U); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U) | repository(), rootIssueOrPatch() (MarkedETag::parseRootId), BaseThreadedEvent.reply(), PTag::parseKey, QTag, citedNIP19() | Legacy NIP-34 reply (deprecated in Quartz; NIP-34 now says to use NIP-22 kind 1111). The root is the issue or patch. It is in the draft's ROOT, PARENT and MENTION. |
| 1630 | `GitStatusOpenEvent` | e[root] -> ROOT (E); e[reply] -> PARENT (E); a -> REPOSITORY (A); p[= e[root] author] -> ROOT_AUTHOR (U); p[= repo owner] -> REPOSITORY_OWNER (U); p[= e[reply] author] -> PARENT_AUTHOR (U); p[other] -> MENTION (U); r -> TAG (T) | GitStatusEvent.rootEventId()/replyEventId(), repositoryAddress(), PTag::parseKey (+ comparison with MarkedETag author and the a pubkey), referenceCommits() | NIP-34 status: e root = the issue, PR or root patch, and e reply = the accepted revision root, as in the draft. The NIP-34 p list is repository-owner, root-event-author and revision-author, with no markers. Telling them apart requires comparing each p with the e author field (Quartz writes it at position 4) and with the a pubkey. That is a new parser, and whatever cannot be resolved stays MENTION. |
| 1631 | `GitStatusAppliedEvent` | e[root] -> ROOT (E); e[reply] -> PARENT (E); a -> REPOSITORY (A); p[= e[root] author] -> ROOT_AUTHOR (U); p[= repo owner] -> REPOSITORY_OWNER (U); p[= e[reply] author] -> PARENT_AUTHOR (U); p[other] -> MENTION (U); r -> TAG (T); q -> APPLIED (E) | GitStatusEvent.rootEventId()/replyEventId(), repositoryAddress(), PTag::parseKey (+ comparison with MarkedETag author and the a pubkey), referenceCommits(), appliedPatchIds() (QTag::parseEventId) | q holds the applied or merged patch ids. merge-commit and applied-as-commits are git data, not modelled (their commits also appear as r -> TAG). Quartz gap: GitStatusEvent.linkedEventIds() reads only e, so the q ids are missing from the hint provider. NIP-34 status: e root = the issue, PR or root patch, and e reply = the accepted revision root, as in the draft. The NIP-34 p list is repository-owner, root-event-author and revision-author, with no markers. Telling them apart requires comparing each p with the e author field (Quartz writes it at position 4) and with the a pubkey. That is a new parser, and whatever cannot be resolved stays MENTION. |
| 1632 | `GitStatusClosedEvent` | e[root] -> ROOT (E); e[reply] -> PARENT (E); a -> REPOSITORY (A); p[= e[root] author] -> ROOT_AUTHOR (U); p[= repo owner] -> REPOSITORY_OWNER (U); p[= e[reply] author] -> PARENT_AUTHOR (U); p[other] -> MENTION (U); r -> TAG (T) | GitStatusEvent.rootEventId()/replyEventId(), repositoryAddress(), PTag::parseKey (+ comparison with MarkedETag author and the a pubkey), referenceCommits() | NIP-34 status: e root = the issue, PR or root patch, and e reply = the accepted revision root, as in the draft. The NIP-34 p list is repository-owner, root-event-author and revision-author, with no markers. Telling them apart requires comparing each p with the e author field (Quartz writes it at position 4) and with the a pubkey. That is a new parser, and whatever cannot be resolved stays MENTION. |
| 1633 | `GitStatusDraftEvent` | e[root] -> ROOT (E); e[reply] -> PARENT (E); a -> REPOSITORY (A); p[= e[root] author] -> ROOT_AUTHOR (U); p[= repo owner] -> REPOSITORY_OWNER (U); p[= e[reply] author] -> PARENT_AUTHOR (U); p[other] -> MENTION (U); r -> TAG (T) | GitStatusEvent.rootEventId()/replyEventId(), repositoryAddress(), PTag::parseKey (+ comparison with MarkedETag author and the a pubkey), referenceCommits() | NIP-34 status: e root = the issue, PR or root patch, and e reply = the accepted revision root, as in the draft. The NIP-34 p list is repository-owner, root-event-author and revision-author, with no markers. Telling them apart requires comparing each p with the e author field (Quartz writes it at position 4) and with the a pubkey. That is a new parser, and whatever cannot be resolved stays MENTION. |
| 10317 | `UserGraspListEvent` | *none* | – | NIP-34 grasp list. The g tags here are grasp SERVER URLs, not geohashes, and servers are not modelled. Cross-cutting trap: a generic g -> TAG (geohash) rule would mislabel these, so g must be read per kind. |
| 30617 | `GitRepositoryEvent` | maintainers -> MAINTAINER (U); t -> HASHTAG (T); r[euc] -> TAG (T); u -> FORK (A) | maintainers() (MaintainersTag::parse), hashtags(), earliestUniqueCommit() (EucTag::parse); new parser needed for u | NIP-34. u ('30617:<pubkey>:<id>\|<git-url>', 'indicate repository is a subordinate fork') is not parsed by Quartz. It is FORK (A) when the value is an address, and not modelled when it is a git URL. This extends the draft's FORK from E to E, A. t includes the 'personal-fork' marker. web, clone and relays are not modelled. |
| 30618 | `GitRepositoryStateEvent` | d (derived) -> REPOSITORY (A) | new parser needed (Address 30617:<author>:<d>) | NIP-34: 'd matches the identifier in the corresponding repository announcement', so the repository address is derived from the author plus d, much as the 39xxx GROUP link is. The refs and HEAD are git data, not modelled. UNCERTAIN: whether derived links belong in links() or in the graph layer. |

### `marmot` (8)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 444 | `WelcomeEvent` | e -> KEY_PACKAGE (E); h -> GROUP (T) | keyPackageEventId() (KeyPackageEventTag::parse), nostrGroupId() | Marmot MIP-02. This is an unsigned rumor inside a NIP-59 gift wrap (1059 -> 13 -> 444), so a relay-side graph never sees it. Links apply only after unwrapping. h is the Marmot nostr_group_id (random 32-byte hex, not relay-scoped, unlike NIP-29), so Tag('h', id) is a sound target here. UNCERTAIN: whether Marmot and NIP-29 should share GROUP or get separate relations. relays is not modelled. Quartz reads h inline in nostrGroupId() instead of mip03 GroupIdTag (minor). |
| 445 | `GroupEvent` | h -> GROUP (T) | groupId() (mip03 GroupIdTag::parse) | Marmot MIP-03. The pubkey is ephemeral per event, so AUTHOR is meaningless here: flag it so the graph does not grow one throwaway User per message. The content is encrypted MLS, and the inner kind 9/7 rumors have their own links. The target is Tag('h', nostr_group_id), a global random id. |
| 446 | `NotificationRequestEvent` | *none* | – | Marmot MIP-05 trigger. It has only a v (version) tag and an ephemeral pubkey. The content is encrypted token chunks addressed to a notification server via gift wrap, so there is nothing to link. |
| 447 | `TokenRequestEvent` | content entries[member_id] -> MEMBER (U); content entries[server_pubkey] -> NOTIFICATION_SERVER (U) | new parser needed (entries() = PushGossip.decodeTokens -> PushTokenEntry.memberIdHex/serverPubKeyHex) | Marmot MIP-05. This is an unsigned inner app payload inside kind 445. It is never relay-visible and is readable only by group members. UNCERTAIN whether to model it at all. Otherwise it would be status none in practice. For a self-update, member_id is the sender. Empty content is a request and has no links. |
| 448 | `TokenListEvent` | content entries[member_id] -> MEMBER (U); content entries[server_pubkey] -> NOTIFICATION_SERVER (U) | new parser needed (entries() = PushGossip.decodeTokens) | Marmot MIP-05. This is an unsigned inner payload, never relay-visible (UNCERTAIN, as for 447). The entries include OTHER members' records relayed with their owner_sig, so member_id is not the sender. |
| 449 | `TokenRemovalEvent` | content entries[member_id] -> MEMBER (U); content entries[server_pubkey] -> NOTIFICATION_SERVER (U) | new parser needed (entries() = PushGossip.decodeRemovals) | Marmot MIP-05 removal (tombstones). This is an unsigned inner payload, never relay-visible (UNCERTAIN, as for 447). A dedicated REMOVED relation would be overkill for a payload no graph sees. |
| 10051 | `KeyPackageRelayListEvent` | *none* | – | Marmot MIP-00 KeyPackage relay list. It holds only relay tags, which are not modelled. |
| 30443 | `KeyPackageEvent` | i -> TAG (T) | keyPackageRef() (KeyPackageRefTag, tag 'i') | Marmot MIP-00. i holds the KeyPackageRef hex (a lookup key). The MLS parameter tags, client and the relays list are not modelled. |

### `nip53LiveActivities` (8)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1311 | `LiveActivitiesChatMessageEvent` | a[1st, root marker optional] -> ROOT (A); e -> PARENT (E); p -> MENTION (U); q -> QUOTE (E,A); t -> HASHTAG (T); content nostr: -> MENTION (E,A,U) | activity()/activityAddress() (ATag::parse), BaseThreadedEvent.reply(), PTag::parseKey, QTag::parseEventId/parseAddressId, tags.hashtags(), citedNIP19() | NIP-53: the activity a is the ROOT (the spec example uses the 'root' marker; the text says just 'a'), and e is the direct parent. The activity can be a 30311 or, via roomMessage(), a 30312 space. Bug: unmarkedReplyTos() calls super.markedReplyTos() (copy-paste). Known QTag.parseAddressId bug. |
| 1312 | `LiveActivitiesRaidEvent` | a[root] -> ROOT (A); a[mention] -> RAIDED (A) | fromActivity()/fromAddress(), toActivity()/toAddress() | zap.stream convention, not in NIP-53 master. The root marker is the source stream (the one raiding), so the marker gives ROOT. The mention marker is the target. Rule 7 ('marker wins') would say MENTION, but the draft already chose RAIDED because the target IS the statement. Flag this tension in rule 7. Both are filtered to kind 30311. |
| 1313 | `LiveActivitiesClipEvent` | a -> CLIPPED (A); p -> CLIPPED_AUTHOR (U); r -> TAG (T) | activity()/activityAddress(), host() (PTag::parseKey), videoUrl() (ReferenceTag::parse) | zap.stream convention, not in NIP-53 master. The p is the stream host, which is not necessarily the 30311 signer (a provider may sign), hence CLIPPED_AUTHOR rather than the address AUTHOR. r is the playable video URL. |
| 10112 | `NestsServersEvent` | *none* | – | Nests audio-room server list (server/relay URLs plus auth URLs). The servers are not modelled. |
| 10312 | `MeetingRoomPresenceEvent` | a[root] -> ROOT (A) | interactiveRoom()/linkedAddressIds() (MeetingSpaceTag::parse / parseAddressId) | NIP-53 room presence: ['a', <room>, relay, 'root'], with the ROOT as in the draft. hand, muted, publishing and onstage are flags, not links. Bug: MeetingSpaceTag.assemble writes ['a', addr, relay] WITHOUT the 'root' marker the NIP requires, so the parser must accept an unmarked a. build(root: MeetingRoomEvent) points the presence at a 30313 meeting, while the spec says the room (30312). Both occur. |
| 30311 | `LiveActivitiesEvent` | p -> PARTICIPANT (U); pinned -> PIN (E); goal -> GOAL (E) | participants() (ParticipantTag::parse), pinned() (PinnedEventTag::parse), goalEventId() | NIP-53. Props on PARTICIPANT: role (Host/Speaker/Participant) and proof. UNCERTAIN: rule 4 may justify HOST as its own relation, since the signer is often a provider and the Host p is the actual streamer ('streams by X' queries). t is in the spec but not read by Quartz, so it is not listed. streaming, recording and relays URLs are not modelled. |
| 30312 | `MeetingSpaceEvent` | p -> PARTICIPANT (U) | participants() (ParticipantTag::parse) | NIP-53 space. The p entries are providers with roles (Host/Moderator/Speaker), carried as props. t is in the spec but not read by Quartz. The service, endpoint and relays URLs are not modelled. Style: inline fully-qualified tag names in the class body. |
| 30313 | `MeetingRoomEvent` | a -> PARENT (A); p -> PARTICIPANT (U); pinned -> PIN (E) | interactiveRoom() (MeetingSpaceTag::parse), participants(), pinned() | NIP-53 meeting: the a is the parent space (30312), with PARENT as in the draft. pinned is not in NIP-53 for 30313, but Quartz reads it (the draft lists it). |

### `nip43RelayMembers` (7)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 8000 | `RelayAddMemberEvent` | p -> ADDED_USER (U) | memberPubKeys() (PTag::parseKey) | NIP-43. Signed by the relay NIP-11 self key. UNCERTAIN: whether to unify with NIP-29 9000 or give that its own PUT_USER, since put-user also re-puts roles of existing members. |
| 8001 | `RelayRemoveMemberEvent` | p -> REMOVED_USER (U) | memberPubKeys() (PTag::parseKey) | NIP-43. Signed by the relay self key. |
| 13534 | `RelayMembershipListEvent` | member -> MEMBER (U) | membersWithRoles() (MemberTag::parseMember) | NIP-43 membership list, signed by the relay self key. Props: roles, which are the d-tags of 33534 role events and resolvable to Address 33534:<author>:<role> if roles ever become links. |
| 28934 | `RelayJoinRequestEvent` | *none* | – | NIP-43 join request. Its only data is the claim (invite code), which is not modelled. Ephemeral. |
| 28935 | `RelayInviteRequestEvent` | *none* | – | NIP-43 invite request. It has no tags beyond the initializer. Ephemeral. |
| 28936 | `RelayLeaveRequestEvent` | *none* | – | NIP-43 leave request. It carries only the NIP-70 '-' tag. Ephemeral. |
| 33534 | `RelayRoleEvent` | *none* | – | NIP-43 role definition: d = role id, with label, description, color and order. There are no references. |

### `nip64Chess` (7)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 30 | `JesterEvent` | e[1st, move events] -> ROOT (E); e[2nd, move events] -> PARENT (E); p -> OPPONENT (U) | startEventId(), headEventId(), opponentPubkey() | Jester protocol (jesterui FLOW.md), not NIP-64. ROOT/PARENT: Jester links moves as [startId, headId], which is the game thread's root and the previous move. On START events (content.kind=0) the single e is JesterProtocol.START_POSITION_HASH, a sha256 of the start FEN rather than an event id. It is not modelled, and a shape-based rule would make it a phantom Event node that every Jester game links to. Quartz quirk: startEventId() returns that hash for start events. The link needs the content kind, so it needs a content parse. p is set only for private challenges and moves. |
| 64 | `ChessGameEvent` | *none* | – | NIP-64: PGN in content, and only alt as a tag. The White/Black PGN headers are free-text names, not pubkeys. |
| 30064 | `LiveChessGameChallengeEvent` | p -> OPPONENT (U) | opponentPubkey() (OpponentTag::parseKey) | Amethyst-only live chess kind (docs/live-chess-implementation-status.md), not NIP-64. With no p, it is an open challenge. d = gameId, a value. |
| 30065 | `LiveChessGameAcceptEvent` | e -> ACCEPTED (E); p -> OPPONENT (U) | challengeEventId() (ChallengeEventTag::parse), opponentPubkey() | Amethyst-only kind. The p is the challenger. The game itself (the challenge address 30064:<challenger>:<gameId>) is derivable only from the e, whose tag[3] author Quartz writes. |
| 30066 | `LiveChessMoveEvent` | p -> OPPONENT (U) | opponentPubkey() | Amethyst-only kind. game_id and d (gameId-moveN) are values. The game is 30064:<challenger>:<gameId>, but the challenger may be the author or the opponent, so it is not derivable from the event alone. A GAME (A) relation would need the challenge in hand (UNCERTAIN, not proposed). |
| 30067 | `LiveChessGameEndEvent` | p -> OPPONENT (U); winner -> WINNER (U) | opponentPubkey(), winnerPubkey() (WinnerTag::parse) | Amethyst-only kind. Props: result and termination. Bug-ish: WinnerTag.parse accepts any non-empty string (no 64-hex check), so it needs validation before becoming a User link. |
| 30068 | `LiveChessDrawOfferEvent` | p -> OPPONENT (U) | opponentPubkey() | Amethyst-only kind. d = gameId. |

### `nip15Marketplace` (6)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1021 | `BidEvent` | e -> AUCTION (E); p -> AUCTION_AUTHOR (U) | auctionId() (ETag::parseId), PTag::parseKey | NIP-15: ['e', <auction event id>], with content = amount (props: amount). The p is Quartz's addition, not in NIP-15. The auction is a 30020 addressable event referenced by id, so the target is E. |
| 1022 | `BidConfirmationEvent` | e[1st] -> BID (E); e[2nd] -> AUCTION (E); p -> BID_AUTHOR (U) | new parser needed (positional e; Quartz only has linkedEventIds() = all ETag ids), PTag::parseKey | NIP-15: [['e', <bid id>], ['e', <auction id>]], order-defined. Quartz writes bid then auction but exposes no bidId()/auctionId(). Props: status (accepted/rejected/pending/winner) and duration_extension from content. The p is Quartz's addition. |
| 30017 | `StallEvent` | *none* | – | NIP-15 stall: d plus content JSON (name, currency, shipping). There are no references. |
| 30018 | `ProductEvent` | content stall_id -> STALL (A); t -> HASHTAG (T) | new parser needed for STALL (productData().stallId -> Address 30017:<author>:<stall_id>), categories() (hashtags) | NIP-15. This is a content reference, not a tag. t holds categories. |
| 30019 | `MarketplaceEvent` | content merchants[] -> MERCHANT (U) | new parser needed (marketplaceData().merchants) | NIP-15 marketplace UI/UX. This is a content reference. MERCHANT follows rule 7's list naming; MEMBER is the alternative (UNCERTAIN). |
| 30020 | `AuctionEvent` | content stall_id -> STALL (A); t -> HASHTAG (T) | new parser needed (auctionData().stallId), tags.hashtags() | NIP-15 auction. Bids reference it by EVENT id (1021/1022), not by address. |

### `nip28PublicChat` (6)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 40 | `ChannelCreateEvent` | a -> MENTION (A) | ATag::parseAddressId (via linkedAddressIds) | NIP-28 defines no tags on kind 40 (metadata and relays live in content JSON; relays are not modelled). UNCERTAIN: Quartz reads arbitrary a tags as address hints, and nothing gives them a meaning. They could also be dropped. Known bug: linkedEventIds() returns the event's own id. The status is effectively none, except for the stray a tags. |
| 41 | `ChannelMetadataEvent` | e[root] -> ROOT (E) | BasePublicChatEvent.channel()/channelId() (MarkedETag.parseRoot ?: parseUnmarkedRoot) | NIP-28: ['e', <kind40 id>, relay, 'root']. The channel is the ROOT, as the draft already decides. NIP-28 also allows t (categories) on 41, which Quartz never reads or writes, so it is not listed. If t is later read, t -> HASHTAG (T). |
| 42 | `ChannelMessageEvent` | e[root] -> ROOT (E); e[reply] -> PARENT (E); p -> MENTION (U); q -> QUOTE (E,A); a -> MENTION (A); content nostr: -> MENTION (E,A,U) | channel()/channelId(), BaseThreadedEvent.reply()/markedReply(), PTag::parseKey, QTag::parseEventId/parseAddressId, ATag::parseAddressId, citedNIP19() | NIP-28 root is the channel and reply is the parent message. The NIP-28 reply example has a p for the replied-to author. Quartz writes it via notify() as a plain p, so it is MENTION per the draft. It could be PARENT_AUTHOR, but no marker distinguishes it (UNCERTAIN). markedReplyTos/unmarkedReplyTos already strip the channel id. Known bug: QTag.parseAddressId rejects every address, so q addresses are lost until it is fixed. |
| 43 | `ChannelHideMessageEvent` | e[root] -> ROOT (E); e[unmarked] -> HIDDEN (E) | channel() (MarkedETag.parseRoot), ETag::parseId for the hidden ids (must exclude the root) | NIP-28 kind 43 carries only ['e', <kind42 id>]. Quartz ALSO writes the channel as a root-marked e. Bugs: (1) eventsToHide() = taggedEventIds() includes the channel root id, so the channel is 'hidden' too. (2) On a spec-conformant 43 (no root), channel() falls back to parseUnmarkedRoot and returns the HIDDEN MESSAGE as the channel. The semantic method must split root from unmarked. |
| 44 | `ChannelMuteUserEvent` | e[root] -> ROOT (E); p -> CHANNEL_MUTED (U) | channel() (MarkedETag.parseRoot), usersToMute() (PTag::parseKey) | NIP-28 kind 44 carries only ['p', pubkey]. The channel root e is Quartz's addition (see 43). CHANNEL_MUTED is already in the draft. |
| 10005 | `PublicChatListEvent` | e -> SUBSCRIBED (E) | channels() (ChannelTag::parse), linkedEventIds() (ChannelTag::parseId) | NIP-51 public chats list, pointing at NIP-28 kind 40 channels. This matches the draft (SUBSCRIBED lists 10005). Private entries are NIP-44 encrypted in content, visible only to the owner, and not linked. ChannelTag reads an optional author at position 2-4, a candidate for props. |

### `nipACWebRtcCalls` (6)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 25050 | `CallOfferEvent` | p -> RECIPIENT (U) | recipientPubKeys() (PTag::parseKey) | NIP-AC (in-repo NIP-AC.md): 'p: Hex pubkey of the recipient (group calls: one per member)'. Ephemeral kind, delivered inside an ephemeral gift wrap 21059, so a relay graph never sees it plainly. call-id and call-type are session values, not modelled. |
| 25051 | `CallAnswerEvent` | p -> RECIPIENT (U) | recipientPubKeys() (PTag::parseKey) | NIP-AC (in-repo NIP-AC.md): 'p: Hex pubkey of the recipient (group calls: one per member)'. Ephemeral kind, delivered inside an ephemeral gift wrap 21059, so a relay graph never sees it plainly. call-id and call-type are session values, not modelled. |
| 25052 | `CallIceCandidateEvent` | p -> RECIPIENT (U) | PTag::parseKey (no recipientPubKeys() accessor on this class) | NIP-AC (in-repo NIP-AC.md): 'p: Hex pubkey of the recipient (group calls: one per member)'. Ephemeral kind, delivered inside an ephemeral gift wrap 21059, so a relay graph never sees it plainly. call-id and call-type are session values, not modelled. In group calls ICE candidates carry only the peer. |
| 25053 | `CallHangupEvent` | p -> RECIPIENT (U) | recipientPubKeys() (PTag::parseKey) | NIP-AC (in-repo NIP-AC.md): 'p: Hex pubkey of the recipient (group calls: one per member)'. Ephemeral kind, delivered inside an ephemeral gift wrap 21059, so a relay graph never sees it plainly. call-id and call-type are session values, not modelled. |
| 25054 | `CallRejectEvent` | p -> RECIPIENT (U) | recipientPubKeys() (PTag::parseKey) | NIP-AC (in-repo NIP-AC.md): 'p: Hex pubkey of the recipient (group calls: one per member)'. Ephemeral kind, delivered inside an ephemeral gift wrap 21059, so a relay graph never sees it plainly. call-id and call-type are session values, not modelled. |
| 25055 | `CallRenegotiateEvent` | p -> RECIPIENT (U) | recipientPubKeys() (PTag::parseKey) | NIP-AC (in-repo NIP-AC.md): 'p: Hex pubkey of the recipient (group calls: one per member)'. Ephemeral kind, delivered inside an ephemeral gift wrap 21059, so a relay graph never sees it plainly. call-id and call-type are session values, not modelled. |

### `concord` (5)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 3302 | `ConcordChatEditEvent` | e -> EDITED (E) | editedMessageId() (firstTaggedEvent) | Concord CORD-02 Appendix B edit rumor. channel / epoch / ms binding tags carry Concord-internal channel ids, not Nostr entities: not modelled. Draft row verified. |
| 3308 | `ControlEditionEvent` | *none* | vsk()/eid()/ev()/ep()/vac() (concord/cord04Roles/control/tags) | Concord CORD-02/04 control-plane edition. eid (entity id), ep (prev edition hash), vac (grant id/version/hash) are Concord entity ids/hashes, not Nostr event ids/addresses/pubkeys; content is entity JSON. No Nostr links. |
| 13302 | `ConcordCommunityListEvent` | *none* | decrypt()/decryptDocument() (NIP-44 self-encrypted content, no tags) | Concord CORD-05 joined-communities list; everything (community roots, keys) is in encrypted content; built with emptyArray() tags. |
| 13303 | `ConcordInviteListEvent` | *none* | decrypt() (NIP-44 self-encrypted content, no tags) | Concord CORD-05 invite list; tokens and link-signer keys encrypted; no tags. |
| 33301 | `ConcordInviteBundleEvent` | *none* | versionedSubKind() (VskTag); content NIP-44 encrypted under the link token | Concord CORD-05 invite bundle: d='' and vsk only; no Nostr references visible. |

### `nip71Video` (5)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 21 | `VideoNormalEvent` | p -> PARTICIPANT (U) [props role=label when present]; p[mention]/a[mention]/e[mention] -> MENTION (U,A,E); p[inspired-by]/a[label]/e[label e.g. audio] -> CREDITED (U,A,E) [props credit=label]; text-track[nevent/address ref] -> TEXT_TRACK (E,A); t -> HASHTAG (T) | participants() (PTag::parse), credits() (VideoCredits.parse: p/a/e with marker labels), textTrack() (TextTrackTag::parse; ref untyped, needs Address/NIP-19 detection), hashtags() | NIP-71: p = 'participant in the video'; text-track = 'link to WebVTT file' but example uses an encoded event and divine.video writes a 39307 address; r (web refs) is in the spec but Quartz does not read it. Credits labels are divine.video convention, not NIP-71. UNCERTAIN: whether role labels like 'Collaborator' stay PARTICIPANT(props role) or become CREDITED. Quartz gap: video classes implement no Event/PubKey/Address hint provider although they carry p/a/e. DRAFT FIX: draft lists 34238 (video collaboration) as REFERENCE-only; the collaborator p/credit convention here overlaps it. DRAFT FIX: PARTICIPANT kinds should add 21, 22, 34235, 34236. |
| 22 | `VideoShortEvent` | p -> PARTICIPANT (U) [props role=label when present]; p[mention]/a[mention]/e[mention] -> MENTION (U,A,E); p[inspired-by]/a[label]/e[label e.g. audio] -> CREDITED (U,A,E) [props credit=label]; text-track[nevent/address ref] -> TEXT_TRACK (E,A); t -> HASHTAG (T) | participants() (PTag::parse), credits() (VideoCredits.parse: p/a/e with marker labels), textTrack() (TextTrackTag::parse; ref untyped, needs Address/NIP-19 detection), hashtags() | Same tags as kind 21 (RegularVideoEvent). NIP-71. |
| 34235 | `AddressableNormalVideoEvent` | p -> PARTICIPANT (U) [props role=label when present]; p[mention]/a[mention]/e[mention] -> MENTION (U,A,E); p[inspired-by]/a[label]/e[label e.g. audio] -> CREDITED (U,A,E) [props credit=label]; text-track[nevent/address ref] -> TEXT_TRACK (E,A); t -> HASHTAG (T) | participants() (PTag::parse), credits() (VideoCredits.parse: p/a/e with marker labels), textTrack() (TextTrackTag::parse; ref untyped, needs Address/NIP-19 detection), hashtags() | Same tags as kind 21 (AddressableVideoEvent). NIP-71 addressable video. |
| 34236 | `AddressableShortVideoEvent` | p -> PARTICIPANT (U) [props role=label when present]; p[mention]/a[mention]/e[mention] -> MENTION (U,A,E); p[inspired-by]/a[label]/e[label e.g. audio] -> CREDITED (U,A,E) [props credit=label]; text-track[nevent/address ref] -> TEXT_TRACK (E,A); t -> HASHTAG (T) | participants() (PTag::parse), credits() (VideoCredits.parse: p/a/e with marker labels), textTrack() (TextTrackTag::parse; ref untyped, needs Address/NIP-19 detection), hashtags() | Same tags as kind 21 (AddressableVideoEvent). NIP-71 addressable short video. |
| 39307 | `TextTrackEvent` | a -> VIDEO (A); l -> TAG (T) | video() (ATag::parseAddress), language() (LanguageTag, l) | divine.video convention (not in NIP-71): addressable timed-text track referenced from a video's text-track tag. url is the hosted WebVTT (not modelled). |

### `nip85TrustedAssertions` (5)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10040 | `TrustProviderListEvent` | <kind:tag> slot 1 pubkey -> SERVICE_PROVIDER (U) [props service] | serviceProviders() (ServiceProviderTag::parse) | NIP-85 'Declaring Trusted Service Providers'. Draft SERVICE_PROVIDER row confirmed (one link per entry). Encrypted entries in content are invisible to the graph. |
| 30382 | `UserAssertionEvent` | d -> SUBJECT (U) [props rank, followers, hops, ...]; t -> HASHTAG (T) | aboutUser() (dTag), rank()/followerCount()/hops()/... for props, topics() (TopicTag, t) | NIP-85 kind 30382. Draft SUBJECT row confirmed. p with the same value as d is only a relay hint (no extra link). Encrypted contact-card fields (petname/summary) not modelled. |
| 30383 | `EventAssertionEvent` | d -> SUBJECT (E) [props rank, comment_cnt, ...] | aboutEvent() (dTag), rank()/commentCount()/... for props | NIP-85 kind 30383. Draft SUBJECT row confirmed; e equal to d is a relay hint only. |
| 30384 | `AddressableAssertionEvent` | d -> SUBJECT (A) [props rank, comment_cnt, ...] | aboutAddress() (dTag), rank()/... for props | NIP-85 kind 30384. Draft SUBJECT row confirmed; a equal to d is a relay hint only. |
| 30385 | `ExternalIdAssertionEvent` | d -> SUBJECT (T) [NIP-73 id; props rank, comment_cnt, reaction_cnt]; k -> TAG (T) | aboutExternalId() (dTag), rank()/commentCount()/reactionCount(); k: KindTag (not read by the class) | NIP-85 kind 30385 'NIP-73 identifier' subject; 'NIP-73 k tags should be added'. DRAFT FIX: SUBJECT row lists only 30382-30384 and targets U,E,A; add 30385 and target T (Tag name i). |

### `cyberspace` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 3330 | `SnoShardEvent` | content palette (nevent/naddr) -> PALETTE (E,A) | SnoParser.parse -> SnoPayload.paletteRef (SnoPaletteRef.Event bech32) via readPaletteRef | Cyberspace DECK-0003 §3.2 bag item. Usually sealed (blank content). C coordinate tag is a cyberspace coordinate: not modelled. Pinned to the named event (reader MUST NOT follow forward) - the E target matters for nevent. |
| 11333 | `SnoAvatarEvent` | content palette (nevent/naddr) -> PALETTE (E,A) | SnoParser.parse -> SnoPaletteRef.Event | Cyberspace v2 §8.10 avatar (replaceable). name tag and nonce/PoW: values, not modelled. Blank content = default avatar (no link). |
| 33330 | `CyberspaceBagEvent` | *none* | lookupId() (d), height() (h), hint(), payload() (encrypted tag) | Cyberspace v2 §7.6 bag: items are AES-GCM encrypted inside the `encrypted` tag; d = region lookup id, h = height, version: values. Items once opened are their own events (not links). No Nostr references in cleartext. |
| 33331 | `SnoObjectEvent` | content palette (nevent/naddr) -> PALETTE (E,A) | SnoParser.parse -> SnoPaletteRef.Event | Cyberspace DECK-0003 §3.1 standalone object; name tag is a value. |

### `nip47WalletConnect` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 13194 | `NwcInfoEvent` | *none* | capabilities() (content), EncryptionTag/NotificationsTag/ExtensionsTag | NIP-47 info event: capabilities, encryption schemes, notification types; no references. |
| 23194 | `NwcRequestEvent` | p -> RECIPIENT (U) | walletServicePubKey() (first p) | NIP-47: p = 'the public key of the wallet service'. Chose RECIPIENT (the addressee an encrypted message is p-tagged and encrypted to, rule 2) over the NIP's role word. UNCERTAIN: alt WALLET_SERVICE (U) if wallet-service graphs are wanted. Ephemeral kind - rarely stored. |
| 23195 | `NwcResponseEvent` | e -> REQUEST (E); p -> REQUEST_AUTHOR (U) | requestId() (first e), requestAuthor() (first p) | NIP-47. UNCERTAIN: p could equally be RECIPIENT (it is the encryption addressee); REQUEST_AUTHOR chosen because it is always the request's author and matches rule 3. Ephemeral kind. |
| 23197 | `NwcNotificationEvent` | p -> RECIPIENT (U) | clientPubKey() (first p) | NIP-47 notification (legacy 23196 same shape): p = client pubkey, encrypted to it. Ephemeral. |

### `nip52Calendar` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 31922 | `CalendarDateSlotEvent` | p -> PARTICIPANT (U); a -> CALENDAR (A); t -> HASHTAG (T); g -> TAG (T); r -> TAG (T) | participants() (PTag), hashtags(), geohash(), references(); a -> new parser needed | NIP-52. Props on PARTICIPANT: role (p slot 3). location/start/end values. Draft PARTICIPANT row verified; DRAFT FIX: add CALENDAR for the inclusion-request a. |
| 31923 | `CalendarTimeSlotEvent` | p -> PARTICIPANT (U); a -> CALENDAR (A); t -> HASHTAG (T); g -> TAG (T); r -> TAG (T) | participants() (PTag), hashtags(), geohash(), references(); a -> new parser needed | NIP-52. D day-index, start/end/tzid values. Props role on PARTICIPANT. |
| 31924 | `CalendarCollectionEvent` | a -> MEMBER (A) | calendarEventAddresses() (taggedAddresses) / ATag::parseAddressId | NIP-52 calendar: a = 31922/31923 events it includes. Draft MEMBER row verified. |
| 31925 | `CalendarRSVPEvent` | a -> CALENDAR_EVENT (A); e -> CALENDAR_EVENT (E); p -> CALENDAR_EVENT_AUTHOR (U) | calendarEventAddress() (firstTaggedAddress), calendarEventId() (firstTaggedEvent), calendarEventAuthor() (PTag) | NIP-52 RSVP. Props status (accepted/declined/tentative) and fb. Draft CALENDAR_EVENT row verified; DRAFT FIX: add the author relation. |

### `nip54Wiki` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 818 | `WikiMergeRequestEvent` | a -> DESTINATION (A); p -> DESTINATION_AUTHOR (U); e[source] -> SOURCE (E); unmarked e -> BASE_VERSION (E) | targetArticle() (ATag::parseAddress), destinationAuthor() (PTag::parseKey), mergeSource() (e with source\|fork marker), baseVersion() (unmarked e) | NIP-54 Merge Requests. DRAFT FIX: remove 818 from the REFERENCE-until-classified list. Quartz also accepts `fork` as the source marker. UNCERTAIN: SOURCE is a very generic name in a cross-kind vocabulary (alt: MERGE_SOURCE); BASE_VERSION alt: BASED_ON. |
| 819 | `WikiMergeAcceptanceEvent` | e[result] -> RESULT (E); e[request] -> REQUEST (E); p -> REQUEST_AUTHOR (U) | result()/request() (markedEvent by marker), requester() (PTag::parseKey) | Kind 819 is NOT in NIP-54 on nostr-protocol/nips master (NIP-54 says the destination accepts/rejects via NIP-25 reactions to the 818); Quartz-only / proposal. UNCERTAIN until specified. DRAFT FIX: remove 819 from the REFERENCE list. |
| 30818 | `WikiArticleEvent` | a[fork] -> FORK (A); e[fork] -> FORK (E); a[defer] -> DEFER (A); e[defer] -> DEFER (E); other a/e -> MENTION (E,A); p -> MENTION (U); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U); t -> HASHTAG (T) | forkFromAddress() (ForkTag::parseAddress), forkFromVersion() (MarkedETag::parseForkedEventId), ATag/MarkedETag/PTag/QTag, citedNIP19(); defer -> new parser needed | NIP-54. DRAFT FIX: draft lists 30818 under PARENT, but NIP-54 defines no parent/reply for articles - its a/e are fork (and defer) references; move 30818 to FORK (and FORK needs A and E targets). Content is Asciidoc/Markdown with wikilinks to d-tags ([[...]]), which are slugs, not addresses: not modelled. Known: QTag.parseAddressId rejects every address. |
| 30819 | `WikiRedirectEvent` | a -> REDIRECT (A) | target() (ATag::parseAddress) | NIP-54 redirects; d = normalized from-slug. Draft row verified. |

### `nip58Badges` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 8 | `BadgeAwardEvent` | a -> BADGE_DEFINITION (A); p -> AWARDED (U) | awardDefinition() (taggedAddresses), awardeeIds() (taggedUserIds) | NIP-58: single a (30009) + one p per awardee. Hint providers also read e tags, which NIP-58 does not define for kind 8 (ignore). Draft rows verified. |
| 10008 | `ProfileBadgesEvent` | a (paired) -> BADGE_DEFINITION (A); e (paired) -> BADGE_AWARD (E); a to kind 30008 -> BADGE_SET (A) | acceptedBadges() (AcceptedBadge.parseAll pairs); badgeAwardDefinitions() (taggedAddresses); BADGE_SET split -> new parser needed | NIP-58 (10008 is a NIP-51 standard list). Quartz: badgeAwardDefinitions() returns every a tag, so a 30008 badge-set pointer reads as a badge definition (bug for the relation). Hint providers read p tags NIP-58 does not define here. |
| 30008 | `AcceptedBadgeSetEvent` | a (paired) -> BADGE_DEFINITION (A); e (paired) -> BADGE_AWARD (E) | acceptedBadges() (AcceptedBadge.parseAll), badgeAwardEvents(), badgeAwardDefinitions() | NIP-58 Badge Set (NIP-51 set); d=profile_badges is the legacy profile-badges form (treat as 10008). title/image/description values. Draft rows verified. |
| 30009 | `BadgeDefinitionEvent` | *none* | badgeName/badgeImage/badgeThumbs/badgeDescription | NIP-58 badge definition: name, image and thumb URLs only. (ADDRESS/AUTHOR only.) |

### `nip60Cashu` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 7374 | `CashuMintQuoteEvent` | *none* | – | NIP-60 quote: content is the encrypted quote id; tags expiration and mint URL (not modelled). |
| 7375 | `CashuTokenEvent` | *none* | – | NIP-60 token: everything (mint, proofs, del token ids) is NIP-44 encrypted in content; no public tags. The encrypted del list would be DESTROYED links but is invisible to the graph. |
| 7376 | `CashuSpendingHistoryEvent` | e[redeemed] -> REDEEMED (E); p -> REDEEMED_AUTHOR (U); e[created] -> CREATED (E); e[destroyed] -> DESTROYED (E) | redeemedNutzaps() / redeemedReferences() (TokenReference::parseFromTag), PTag::parseKey; created/destroyed: TokenReference on public tags (usually encrypted) | NIP-60 says created/destroyed e tags SHOULD be encrypted and only redeemed stays public, so CREATED/DESTROYED are rare in the graph (encrypted tags never reach it). UNCERTAIN: p could instead reuse ZAP_SENDER (NIP-61 calls it the 'nutzap sender'), but rule 3 favours REDEEMED_AUTHOR. |
| 17375 | `CashuWalletEvent` | *none* | – | NIP-60 wallet: privkey and mint tags are NIP-44 encrypted in content; no public references. |

### `nip72ModCommunities` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 4550 | `CommunityPostApprovalEvent` | a[34550] -> COMMUNITY (A); e -> APPROVED (E); a[non-34550] -> APPROVED (A); p -> APPROVED_AUTHOR (U); k -> TAG (T) | communityAddresses() (CommunityTag), approvedEvents() (ApprovedEventTag::parseId), approvedAddresses() (ApprovedAddressTag), PTag::parseKey; k: KindTag | NIP-72 approval. Draft COMMUNITY/APPROVED rows confirmed. Content embeds the approved post JSON (containedPost()) - same id as e, not a separate link. |
| 10004 | `CommunityListEvent` | a[34550] -> SUBSCRIBED (A) | publicCommunities() / communityIds() (CommunityTag) | NIP-51 Communities list: 'NIP-72 communities the user belongs to'. Draft SUBSCRIBED row confirmed (MEMBER would match 'belongs to', but the draft chose SUBSCRIBED for follow-like lists; keep). Private (encrypted) entries never reach the graph. |
| 34550 | `CommunityDefinitionEvent` | p[moderator] -> MODERATOR (U); e/q/a -> MENTION (E,A) | moderators()/moderatorKeys() (ModeratorTag), ETag/QTag/ATag hint providers | NIP-72: p with role 'moderator'; relay tags (URLs) not modelled. Draft MODERATOR row confirmed. Quartz: ModeratorTag.parse accepts any p regardless of the role marker. UNCERTAIN: NIP-72 defines no e/q/a on 34550 yet the hint providers read them; MENTION assumed - confirm or drop. |
| 34551 | `CommunityRulesEvent` | a[34550] -> COMMUNITY (A); p[allow] -> ALLOWED (U) [props role]; p[deny] -> DENIED (U) [props role]; wot -> WOT_ROOT (U) [props depth]; k -> TAG (T) | communityAddress() (ATag), pubkeyRules() (PubkeyRuleTag::parse), wotGates() (WotTag::parse), kindRules() (KindRuleTag) | NIP-9B/9A 'Verifiable Community Rules' (unmerged upstream, 404; read from Quartz KDoc). UNCERTAIN: ALLOWED/DENIED could be one relation with props.policy, but deny vs allow is the filter every query applies (rule 4). |

### `nipCCGeocaching` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 7516 | `GeocacheFoundLogEvent` | a[37516] -> FOUND (A) | geocache() / geocacheId() (GeocacheTag = ATag, filtered to kind 37516 or legacy kind) | NIP-CC. Props: verified (hasVerificationAttached()). The verification tag embeds a full 7517 JSON (embeddedVerification()) - not a link by id. image URLs not modelled. Non-found logs are NIP-22 1111s (ROOT/PARENT to the 37516). DRAFT FIX: draft lists geocaching among REFERENCE-only experimental kinds; now classified. |
| 7517 | `GeocacheVerificationEvent` | a[<finder-hex>:<naddr>] finder -> FINDER (U); a[<finder-hex>:<naddr>] cache -> VERIFIED (A) | finder() / verifiedCache() (FinderCacheTag::parseFinder / parseCache) | NIP-CC. The a tag is a non-standard composite '<finder-hex>:<naddr>', so a shape-based ATag parser would misread it. Signed by the cache's verification key, not the finder (so AUTHOR = the key named by 37516's verification tag). |
| 37516 | `GeocacheListingEvent` | F -> WINNER (U); verification -> VERIFIER (U); t -> HASHTAG (T); g -> TAG (T) | firstToFindWinner() (FirstToFindWinnerTag, F), verificationKey() (VerificationKeyTag), cacheType()/isArchived() (t), geohashes() | NIP-CC. t here is the cache type / 'archived', not a free hashtag (HASHTAG per the rule, flagged). r are relay URLs for logs (NOT web refs) - not modelled; must not become TAG r. n, D, T, S, hint, mission, image not modelled. UNCERTAIN: VERIFIER is a dedicated key, not a person's identity. |
| 37517 | `GeocacheCurationListEvent` | a[37516] -> CURATED (A); g -> TAG (T) | curatedGeocaches() / curatedAddresses() (ATag), geohashes() | NIP-CC curation list. Draft CURATED row (37517) confirmed. |

### `nipF4Podcasts` (4)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 54 | `PodcastEpisodeEvent` | *none* | – | NIP-F4 kind 54: title, image, description, audio (URLs). Authored by the podcast's own key, so AUTHOR is the show. |
| 10054 | `FavoritePodcastsListEvent` | p -> FAVORITE (U) | publicFavorites() (UserTag::parse) | NIP-51 / NIP-F4. NIP-51 also allows url (RSS feed URLs) - class ignores them, not modelled. Quartz gap: no PubKeyHintProvider. UNCERTAIN: alternative is BOOKMARK with a U target (NIP-51 literally says 'bookmark') or SUBSCRIBED (NIP-F4: 'publicly advertise to listening to'). |
| 10064 | `AuthoredPodcastsEvent` | p -> AUTHORED (U) | authoredKeys() / linkedPubKeys() (UserTag::parseKey) | NIP-F4 'Authored Podcasts' (spec text says kind 10164 once but the example and NIP-51 say 10064). DRAFT FIX: draft MEMBER row lists 10064; this is not a membership set but an authorship claim, which a query must join with 10154's PODCAST_AUTHOR (both directions must agree). |
| 10154 | `PodcastMetadataEvent` | p -> PODCAST_AUTHOR (U) [props role host/cohost/editor] | claimedAuthors() (AuthorTag::parse) | NIP-F4: the claim 'shouldn't be blindly trusted' until matched by the author's 10064 (AUTHORED). website/image URLs not modelled. Quartz: AuthorTag drops unknown roles (role null) and no PubKeyHintProvider. UNCERTAIN: could reuse PARTICIPANT(props role) as NIP-53 does for Host. |

### `nip17Dm` (3)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 14 | `ChatMessageEvent` | p -> RECIPIENT (U); e -> PARENT (E); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U); zap -> ZAP_SPLIT (U) | recipientsPubKey() (BaseDMGroupEvent, PTag); replyTo() (ETag::parseId); q and content citations -> new parser needed (citedNIP19 lives on BaseNoteEvent, not BaseDMGroupEvent); zapSplitSetup() | NIP-17: p = receivers, e = 'the direct parent message this post is replying to', q MAY cite NIP-21 in content. Draft rows verified; DRAFT FIX: QUOTE and MENTION kinds should include 14. subject tag is a value, not modelled. Rumor kind: normally only reaches a store unwrapped. |
| 15 | `ChatMessageEncryptedFileHeaderEvent` | p -> RECIPIENT (U); e[reply] -> PARENT (E) | recipientsPubKey() (BaseDMGroupEvent); replyTo() (ETag::parseId) | NIP-17 file message. DRAFT FIX: PARENT kinds should list 15. x/ox are blob hashes of an encrypted file, content is the file URL: not modelled. No hint provider for the e tag (Quartz gap, like kind 4). |
| 10050 | `DmRelayListEvent` | *none* | RelayTag::parse (relays()) | NIP-17 DM inbox relays: relay URLs only: not modelled. |

### `nip57Zaps` (3)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 9733 | `PrivateZapEvent` | e -> ZAPPED (E); a -> ZAPPED (A); p -> ZAP_RECIPIENT (U); k -> TAG (T) | ETag::parseId, ATag::parseAddressId, PTag::parseKey (hint providers); KindTag | NIP-57 private zap: the decrypted inner event of an anon tag, built by PrivateZapRequestBuilder from the zap request's tags minus `anon`, so it carries the same e/a/p/k. Draft rows verified. |
| 9734 | `ZapRequestEvent` | e -> ZAPPED (E); a -> ZAPPED (A); p -> ZAP_RECIPIENT (U); k -> TAG (T) | zappedPost() (ETag), ATag::parseAddress, zappedAuthor() (PTag), KindTag; amount tag for props msats | NIP-57 Appendix A/D: exactly one p, 0 or 1 e, optional a, k. Props msats from `amount`. relays/lnurl/anon/poll_option and NIP-29 h: not modelled. Draft rows verified. |
| 9735 | `ZapReceiptEvent` | e -> ZAPPED (E); a -> ZAPPED (A); p -> ZAP_RECIPIENT (U); P -> ZAP_SENDER (U); description (embedded 9734) -> ZAP_REQUEST (E); k -> TAG (T) | zappedPost(), ATag::parseAddress, zappedAuthor(); P -> new parser needed (Quartz reads only zappedRequestAuthor() = zapRequest?.pubKey); zapRequest (containedPost()) | NIP-57 Appendix E. Props msats from bolt11 (amount). ZAP_SENDER should come from P (NIP-57: 'P tag from the pubkey of the zap request (zap sender)'), falling back to the embedded request's pubkey; for anonymous zaps it is a throwaway key. Known upstream: ZapReceiptEvent omits the zap sender from its hint providers. UNCERTAIN: ZAP_REQUEST targets an event that is normally never published to relays. |

### `nip59Giftwrap` (3)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 13 | `SealEvent` | *none* | n/a (content is NIP-44 encrypted rumor; tags normally empty, optional expiration) | NIP-59. The rumor inside is its own event once unsealed; no link from the seal (innerEventId is local runtime state). |
| 1059 | `GiftWrapEvent` | p -> RECIPIENT (U) | recipientPubKey() (firstTagValue p) / PTag::parseKey | NIP-59/NIP-17. Signed by a throwaway key; content encrypted (inner event not linked). Draft row verified. |
| 21059 | `EphemeralGiftWrapEvent` | p -> RECIPIENT (U) | recipientPubKey() (inherited from GiftWrapEvent) | NIP-59 ephemeral gift wrap (CEP-19 uses it too). Draft row verified. |

### `nip5dNapplets` (3)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 5129 | `NappletSnapshotEvent` | a -> SNAPSHOTTED (A); A -> ORIGIN (A); app -> APP (A) | new parser needed (NappletManifest reads only path/server/requires/x/title/description/source/icon) | NIP-5D napplets are not on nostr-protocol/nips master (spec fetch 404); per NappletManifest they 'carry the same NIP-5A tag set', so the 5128 snapshot rules are applied by analogy. UNCERTAIN: whole row; Quartz build() writes none of a/A/app. path hashes, server (blossom) and source URLs: not modelled (source may be a NIP-34 nostr:// git URL - a future REPOSITORY candidate). |
| 15129 | `RootNappletEvent` | a -> COPIED (A); A -> ORIGIN (A); app -> APP (A) | new parser needed (NappletManifest) | NIP-5D root napplet; 'carries the NIP-5A tag set' (NappletManifest). requires = NAP capability domains (values, not modelled). UNCERTAIN: NIP-5D not on nips master; applies NIP-5A rules by analogy; Quartz writes none of these tags. |
| 35129 | `NamedNappletEvent` | a -> COPIED (A); A -> ORIGIN (A); app -> APP (A) | new parser needed (NappletManifest) | NIP-5D named napplet; NIP-5A tag set by analogy. UNCERTAIN: NIP-5D not on nips master. |

### `nip87Ecash` (3)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 38000 | `MintRecommendationEvent` | a[38172/38173] -> RECOMMENDED (A) [props platform cashu/fedimint]; k -> TAG (T) | mintEventAddresses() (raw a values - no Address validation), mintEventKind() (k) | NIP-87 recommendation. DRAFT FIX: RECOMMENDED kinds should add 38000. u values are mint URLs / fedimint invite codes - not modelled. Quartz bug: 38000, 38172, 38173 extend Event, not BaseAddressableEvent, though they are addressable (d-tagged, 3xxxx); mintEventAddresses() returns unvalidated strings. |
| 38172 | `CashuMintEvent` | *none* | – | NIP-87 cashu mint: d is the MINT's pubkey (not a Nostr user), u mint URL, nuts, n. Not modelled. Quartz: extends Event, not BaseAddressableEvent. |
| 38173 | `FedimintEvent` | *none* | – | NIP-87 fedimint: d federation id, u invite codes, modules, n. Quartz: extends Event, not BaseAddressableEvent. |

### `nipB1Bolt12Zaps` (3)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 9736 | `Bolt12ZapEvent` | p -> ZAP_RECIPIENT (U); P -> ZAP_SENDER (U); e -> ZAPPED (E); a -> ZAPPED (A); k -> TAG (T) | recipient() (PTag), payer() (PayerTag, P), zappedEvent() (ETag), zappedAddress() (ATag), zappedKind(), amount() (msats), zapIntent (embedded 9737) | NIP-B1 not merged upstream (404); read from Quartz KDoc. DRAFT FIX: ZAP_SENDER kinds should add 9736 (the P payer tag, NIP-57's word). Props msats = amount(). description embeds the 9737 intent (its id is not a tag; not modelled). Anonymous zaps have no P. |
| 9737 | `Bolt12ZapIntentEvent` | p -> ZAP_RECIPIENT (U); e -> ZAPPED (E); a -> ZAPPED (A); k -> TAG (T) | recipient() (PTag), zappedEvent() (ETag), zappedAddress() (ATag), zappedKind(), amount() | NIP-B1 (unmerged). Draft lists 9737 under ZAPPED. UNCERTAIN: an intent is not a payment ('never counted on its own'), so ZAPPED/ZAP_RECIPIENT counts must filter on source kind 9736 - same situation as 9734 requests; the signer is the would-be sender (AUTHOR). |
| 10058 | `Bolt12OfferListEvent` | *none* | – | NIP-B1 (unmerged): offer tags (BOLT12 offers) only. |

### `contextvm` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 11316 | `CvmServerAnnouncementEvent` | *none* | DiscoverySurface.parse(tags) (name/about/picture/website/support_* flags only) | ContextVM CEP-6 (+CEP-23/35). Discovery tags are self-description values; `p`/`e` are routing tags and CvmTags.ROUTING excludes them from the surface; not expected on an announcement. UNCERTAIN: CEP-17 `r` relay tags if present are relay URLs (not modelled). Kind number from CvmKinds.SERVER_ANNOUNCEMENT = 11316. |
| 11317 | `CvmToolsListEvent` | i -> TAG (T); k -> TAG (T) | CommonToolSchema.parseExternalIds(tags) (i); k written by CommonToolSchema.externalKindTag() | ContextVM CEP-6 + CEP-15 common tool schemas: NIP-73-style `[i, <schema-hash>, <tool>]` + `[k, io.contextvm/common-schema]` on the announcement. Content is the tools JSON (no nostr refs). UNCERTAIN: CEP-15 says 'one per announcement event' without naming 11316 vs 11317; Quartz's builder is not bound to a class, so the same i/k may also appear on 11316. |

### `nip18Reposts` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 6 | `RepostEvent` | e -> REPOSTED (E); a -> REPOSTED (A); p -> REPOSTED_AUTHOR (U); k -> TAG (T) | boostedEventId()/boostedAddress() (last e/a), originalAuthorKeys() (PTag), boostedKind() (KindTag) | NIP-18. Content embeds the reposted event JSON (containedPost()) = the same id as e; not a separate link. Kind 6 per NIP-18 is only for kind 1 but Quartz writes `a` for addressables. Draft rows verified. Note boostedEventId takes the LAST e while linkedEventIds lists all; extra e tags (non-spec) would get no meaning - treat non-last e/p as MENTION. |
| 16 | `GenericRepostEvent` | e -> REPOSTED (E); a -> REPOSTED (A); p -> REPOSTED_AUTHOR (U); k -> TAG (T) | boostedEventId()/boostedAddress(), originalAuthorKeys(), boostedKind() | NIP-18 generic repost; a for replaceables, content JSON when a is absent (same id as e, not a separate link). Draft rows verified. |

### `nip25Reactions` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 7 | `ReactionEvent` | last e -> REACTED (E); a -> REACTED (A); last p -> REACTED_AUTHOR (U); earlier e -> MENTION (E); earlier p -> MENTION (U); k -> TAG (T); emoji[emoji-set-address] -> EMOJI_SET (A) | ETag::parseId, ATag::parseAddress, PTag::parseKey (need last-of, see note), KindTag; EmojiUrlTag (slot 3 new parser) | NIP-25: 'the target event id should be last of the e tags' and 'the target event pubkey should be last of the p tags' (extra e/p are legacy thread copies - MENTION). Quartz bug: originalPost() / originalAuthor() return ALL e ids / p keys, not the last, while the draft cites them for REACTED/REACTED_AUTHOR. DRAFT FIX: cite lastNotNullOfOrNull(ETag::parseId)/(PTag::parseKey), not originalPost()/originalAuthor(). |
| 17 | `ExternalReactionEvent` | i -> REACTED (T); k -> TAG (T); emoji[emoji-set-address] -> EMOJI_SET (A) | externalIds() (ExternalTargetTag::parse), externalKinds() (ReplyKindTag::parse); EmojiUrlTag slot 3 new parser | NIP-25 external content reactions with NIP-73 k+i; several i pairs possible (show + episode), each a REACTED. The i hint (URL) is not a target. Draft row verified. |

### `nip30CustomEmoji` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10030 | `EmojiListEvent` | a -> MEMBER (A); emoji[emoji-set-address] -> EMOJI_SET (A) | emojiPackIds() (ATag::parseAddressId); emoji tags: EmojiUrlTag (slot 3 new parser) | NIP-51 'Emojis: user preferred emojis and pointers to emoji sets' (a = kind 30030). Draft lists 10030 under MEMBER. UNCERTAIN / possible DRAFT FIX: the a entries are sets the user USES (a selection), which reads closer to SUBSCRIBED than to membership; rule 7 would name it after the list ('emoji sets'). Loose emoji tags (URLs) not modelled. |
| 30030 | `EmojiPackEvent` | emoji[emoji-set-address] -> EMOJI_SET (A) | tags.emojis() (EmojiUrlTag; slot 3 new parser); private emojis in NIP-44 content | NIP-51 emoji set / NIP-30. The emoji tags themselves are shortcode+URL (not modelled); only the optional 4th slot (the set an emoji came from, NIP-30) is a link. UNCERTAIN: may point at the pack itself - skip self-links. |

### `nip35Torrents` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 2003 | `TorrentEvent` | i -> TAG (T); t -> HASHTAG (T); q -> QUOTE (E,A); p -> MENTION (U); r -> TAG (T) | HashtagTag/hashtags(); QTag; PTag; i -> new parser needed (TorrentEvent has no i accessor) | NIP-35: i = tcat/newznab/imdb/tmdb/... ids, t = categories. Quartz build() turns nostr: URIs in the description into q (note/nevent/naddr) and p (npub/nprofile via NPub.toQuoteTagArray = PTag) and URLs into r. x/btih info hash, file, tracker: not modelled. Content nostr: is not parsed on read (no citedNIP19 on this class). DRAFT FIX: remove 2003 from the unclassified list. |
| 2004 | `TorrentCommentEvent` | e[root] (or first) -> ROOT (E); e[reply] (or last) -> PARENT (E); middle unmarked e -> MENTION (E); p equal to parent author -> PARENT_AUTHOR (U); other p -> MENTION (U); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U) | torrent() / torrentIds() (MarkedETag::parseRoot, fallback first ETag), BaseThreadedEvent.reply()/markedReply(), PTag, QTag, citedNIP19() | NIP-35: 'works exactly like a kind 1 and should follow NIP-10'; the root is the 2003 torrent. Deprecated in Quartz (replaced by NIP-22). DRAFT FIX: ROOT, QUOTE and MENTION kinds should list 2004 (draft has it only under PARENT). |

### `nip37Drafts` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10013 | `PrivateOutboxRelayListEvent` | *none* | RelayTag::parse (publicRelays()); private relays in NIP-44 content | NIP-37 private-outbox relay list: relay URLs only: not modelled. |
| 31234 | `DraftWrapEvent` | k -> TAG (T); e[root] -> ROOT (E); e[reply] -> PARENT (E); a -> ROOT (A) | KindTag (kind(draft.kind)); exposed tags from ExposeInDraft.exposeInDraft() (ChannelMessageEvent: e root/reply; LiveActivitiesChatMessageEvent: a activity + e reply) - MarkedETag/ATag | NIP-37. The draft itself is NIP-44 encrypted (no content links). Quartz copies the draft's thread anchors (channel, live activity, reply) into public tags so a draft shows in context; they carry the inner kind's meaning (the k tag says which). UNCERTAIN: whether exposed anchors of an unpublished draft should be graph links at all, or get DRAFT_-prefixed relations; kept as ROOT/PARENT per rule 2. |

### `nip5aStaticWebsites` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 15128 | `RootSiteEvent` | a -> COPIED (A); A -> ORIGIN (A); app -> APP (A) | new parser needed (class reads only path/server/title/description/source/icon) | NIP-5A. path (blob sha256), x aggregate hash, server (blossom), source (URL or NIP-34 nostr:// git URL): not modelled. UNCERTAIN: COPIED/ORIGIN could reuse FORK/ROOT (rule 2: a copy is a fork; NIP-22 uses uppercase for the root) - chose the NIP's words per rule 7. |
| 35128 | `NamedSiteEvent` | a -> COPIED (A); A -> ORIGIN (A); app -> APP (A) | new parser needed (class reads only path/server/title/description/source/icon) | NIP-5A named site (d = identifier). Same notes as 15128. |

### `nip61Nutzaps` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 9321 | `NutzapEvent` | e -> ZAPPED (E); p -> ZAP_RECIPIENT (U); k -> TAG (T) | ETag::parseId, PTag::parseKey (no zappedEvent()/recipient() accessors), claimedSatsTotal() for props | NIP-61: 'p is the Nostr identity public key of nutzap recipient', 'e is the event that is being nutzapped'. Draft ZAPPED/ZAP_RECIPIENT rows confirmed; props msats = claimedSatsTotal*1000 (sender-claimed). u = mint URL, not modelled. The sender is the AUTHOR. NIP-61 has no a (addressable) target. |
| 10019 | `NutzapInfoEvent` | *none* | – | NIP-61: relay (URLs), mint (URLs), pubkey = the P2PK key, which 'MUST NOT' be the user's Nostr key - not a User node; not modelled. |

### `nip66RelayMonitor` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10166 | `RelayMonitorEvent` | g -> TAG (T) | geohashes() | NIP-66 monitor announcement: frequency, timeout, c (checks), g. No E/A/U references. |
| 30166 | `RelayDiscoveryEvent` | t -> HASHTAG (T); g -> TAG (T); k -> TAG (T) | topics() (hashtags), geohashes(), acceptedKinds() (AcceptedKindTag, k) | NIP-66: d is the relay URL (not modelled); n, N, R, T, rtt-* are relay attributes. l (language) is in the spec example but not read by the class. |

### `nip78AppData` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 78 | `AppDataEvent` | *none* | – | NIP-78: tags are arbitrary, app-private and non-interoperable; class reads only d. DRAFT FIX: draft Coverage lists 'app data and handlers (78, 30078, 31990)' among kinds carrying references; 78/30078 carry none by spec. |
| 30078 | `AppSpecificDataEvent` | *none* | – | NIP-78: arbitrary app-private tags; class reads only d. DRAFT FIX: see 78 - listed in Coverage as carrying references, but carries none by spec. |

### `nip88Polls` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1018 | `PollResponseEvent` | e -> POLL (E); p -> POLL_AUTHOR (U) | poll() / PollTag::parseId, PTag::parseKey (written by notifyAuthor()) | NIP-88: 'an e tag with the poll event it is referencing, followed by one or more response tags'. Draft POLL row confirmed. Props on POLL: responses (response tag option ids). Quartz: p is an Amethyst convention, not NIP-88. |
| 1068 | `PollEvent` | *none* | – | NIP-88 poll: option, relay (URLs, not modelled), polltype, endsAt. No references. |

### `nip89AppHandlers` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 31989 | `AppRecommendationEvent` | a[31990] -> RECOMMENDED (A) [props platform] | recommendationAddresses() (RecommendationTag::parseAddressId) | NIP-89. Draft RECOMMENDED row confirmed. d is the recommended kind number (value, not in the allowlist). |
| 31990 | `AppDefinitionEvent` | a -> SITE_MANIFEST (A); latest -> SITE_MANIFEST (A) [props release=latest]; next -> SITE_MANIFEST (A) [props release=next]; client -> CLIENT (A); k -> TAG (T); t -> HASHTAG (T) | relatedAddresses() (ATag), client() (ClientTag), supportedKinds() (KindTag), categories() (hashtags); latest/next: new parser needed | NIP-89 handler information. Platform links (web/ios/android URLs) not modelled. UNCERTAIN: whether latest vs next deserve two relations (rule 4) - props chosen. CLIENT applies to every kind (Quartz nip89AppHandlers/clientTag); it belongs in the shared default, not per class. Draft Coverage lists 31990 as unclassified. |

### `nipA0VoiceMessages` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1222 | `VoiceEvent` | *none* | – | NIP-A0: content is an audio URL; t/g MAY be included per other NIPs but the class does not read them (would be HASHTAG/TAG if added). |
| 1244 | `VoiceReplyEvent` | E/A[root scope] -> ROOT (E,A); P -> ROOT_AUTHOR (U); e -> PARENT (E); p -> PARENT_AUTHOR (U); K/k -> TAG (T) | replyingTo()/markedReplyTos() (ReplyEventTag::parseKey, e), replyAuthorKeys() (ReplyAuthorTag::parseKey, p), directKinds() (k); root scope E/A/P: new parser needed (nip22Comments tag parsers exist) | NIP-A0: kind 1244 'MUST follow the structure of NIP-22'. Draft ROOT/PARENT/ROOT_AUTHOR/PARENT_AUTHOR rows for 1244 match the spec. Quartz bug: VoiceReplyEvent.build writes only e/k/p (parent item) and no E/K/P root scope, and the class reads no root; ReplyEventTag reads only e, so a parent given as an a tag is missed; class implements no hint providers. |

### `nipB7Blossom` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10063 | `BlossomServersEvent` | *none* | – | NIP-B7: server URLs - not modelled. |
| 24242 | `BlossomAuthorizationEvent` | *none* | – | NIP-B7/BUD auth: t is the VERB (upload/get/delete/list), not a hashtag, and x a blob hash; server/expiration. Emitting HASHTAG for this t would pollute topics - exclude. Short-lived auth token, normally not stored. |

### `nipXXPodcasting20` (2)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 30054 | `Podcasting20EpisodeEvent` | edit -> EDITED (E); t -> HASHTAG (T) | editsEventId() (EditTag::parse), topics() (hashtags) | Podcasting-2.0 draft (not a NIP; podstr). edit = 'the event id of the original publication when an addressable episode/trailer is updated' - fits draft EDITED. person tags carry names/URLs, not pubkeys (not modelled). Quartz: EditTag.parse does not check 64-hex. |
| 30055 | `Podcasting20TrailerEvent` | *none* | – | Podcasting-2.0 draft trailer: title, url, pubdate, length, type, season - no references. |

### `nip01Core` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 0 | `MetadataEvent` | i -> TAG (T); emoji[emoji-set-address] -> EMOJI_SET (A) | IdentityClaimTag::parse (i); EmojiUrlTag does not read slot 3 -> new parser needed for EMOJI_SET | NIP-01 / NIP-39 (identity claims mirrored as `i` tags, nips PR 1770 tag-names) / NIP-30. Content is JSON; Quartz does not parse nostr: URIs in `about` (no citedNIP19 on this class) so no content MENTION. Other name/picture/... tags are plain values, not modelled. |

### `nip02FollowList` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 3 | `ContactListEvent` | p -> FOLLOW (U) | ContactTag::parseKey / parseValid (petname, relay hint) | NIP-02. Content relay map (legacy) is relay URLs: not modelled. Draft row verified. |

### `nip03Timestamp` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1040 | `OtsEvent` | e -> TIMESTAMPED (E); k -> TAG (T) | TargetEventTag::parseId (digestEventId()); targetKind (KindTag) | NIP-03: e = target event, k = target kind. Draft row verified. |

### `nip04Dm` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 4 | `EncryptedDmEvent` | p -> RECIPIENT (U); e -> PARENT (E) | PTag::parseKey (recipientPubKey()); MarkedETag::parseId (replyTo()) | NIP-04: p = receiver; e = 'the previous message in a conversation or a message we are explicitly replying to'. DRAFT FIX: PARENT kinds should list 4. Content encrypted; NIP-04 says clients should not rewrite nostr refs into tags. EncryptedDmEvent does not implement EventHintProvider for its e tag (only pubkeys) - minor Quartz gap. |

### `nip09Deletions` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 5 | `DeletionRequestEvent` | e -> DELETED (E); a -> DELETED (A); p -> DELETED_AUTHOR (U); k -> TAG (T) | ETag::parseId (deleteEventIds()), ATag::parseAddressId (deleteAddressIds()), PTag::parseKey (new accessor), KindTag (kinds()) | NIP-09 defines only e/a/k (the p is Quartz practice). Draft DELETED row verified. |

### `nip10Notes` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1 | `TextNoteEvent` | e[root] (or first unmarked) -> ROOT (E); e[reply] (or last unmarked) -> PARENT (E); e[mention] or middle unmarked e -> MENTION (E); e[fork] -> FORK (E); a[root] -> ROOT (A); a[reply] -> PARENT (A); a[fork] -> FORK (A); a to kind 34550 -> COMMUNITY (A); other a -> MENTION (A); p equal to the parent's author (e[reply] pubkey slot) -> PARENT_AUTHOR (U); other p -> MENTION (U); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U); t -> HASHTAG (T); r -> TAG (T); g -> TAG (T); zap -> ZAP_SPLIT (U); emoji[emoji-set-address] -> EMOJI_SET (A) | BaseThreadedEvent.markedRoot/unmarkedRoot/markedReply/unmarkedReply, MarkedETag.parseAllThreadTags/parseForkedEventId (MARKER.ROOT/REPLY/MENTION/FORK); ATag::parseAddress; PTag::parseKey; QTag::parseEventId/parseAddressId; citedNIP19(); Event.hashtags(); zapSplitSetup(); a-marker and PARENT_AUTHOR matching -> new parser needed | NIP-10 (+NIP-18 q, NIP-27 content, NIP-72 legacy community a, NIP-57 zap). DRAFT FIX: PARENT_AUTHOR (and ROOT_AUTHOR from e[root] pubkey slot) should include kind 1 - NIP-10 says the replied-to author is added to p; without it 'replies to my notes' needs a 2-hop join. DRAFT FIX: FORK targets E and A (isAFork accepts a or e with fork marker). NIP-10 no longer defines a `mention` marker; Quartz still parses MARKER.MENTION (legacy). Quartz bug: forkFromAddress() = first ATag::parseAddress regardless of fork marker (a community a-tag reads as the fork source); WikiArticleEvent uses ForkTag correctly. Known: QTag.parseAddressId rejects every address. UNCERTAIN: whether a p that is both parent author and notified thread member emits only PARENT_AUTHOR (proposed) or both. |

### `nip22Comments` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1111 | `CommentEvent` | E -> ROOT (E); A -> ROOT (A); I -> ROOT (T); K -> TAG (T); P -> ROOT_AUTHOR (U); e -> PARENT (E); a -> PARENT (A); i -> PARENT (T); k -> TAG (T); p equal to the parent's author -> PARENT_AUTHOR (U); other p -> MENTION (U); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U); t -> HASHTAG (T); zap -> ZAP_SPLIT (U); emoji[emoji-set-address] -> EMOJI_SET (A) | RootEventTag/RootAddressTag/RootIdentifierTag/RootKindTag/RootAuthorTag, ReplyEventTag/ReplyAddressTag/ReplyIdentifierTag/ReplyKindTag/ReplyAuthorTag (nip22Comments/tags), QTag, citedNIP19(), hashtags(), zapSplitSetup() | NIP-22. DRAFT FIX: ROOT and PARENT need target T for the I/i external-identifier scopes (hashtag, geohash, URL comments). NIP-22 also says 'p tags SHOULD be used when mentioning pubkeys in content' so a lowercase p is PARENT_AUTHOR only when it matches the parent (e tag's pubkey slot / replyAuthor()), else MENTION; ReplyAuthorTag currently treats every p as the parent author (Quartz ambiguity). UNCERTAIN: an A root of kind 34550 is a NIP-72 community post - emit COMMUNITY (A) in addition to ROOT? Known: QTag.parseAddressId rejects every address. |

### `nip23LongContent` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 30023 | `LongFormContentEvent` | q -> QUOTE (E,A); e -> MENTION (E); a -> MENTION (A); p -> MENTION (U); content nostr: -> MENTION (E,A,U); t -> HASHTAG (T); zap -> ZAP_SPLIT (U); emoji[emoji-set-address] -> EMOJI_SET (A) | QTag::parseEventId/parseAddressId, PTag::parseKey, citedNIP19(), topics()/hashtags(), zapSplitSetup(); e/a not read by hint providers -> new parser needed | NIP-23: 'references to other notes, articles or profiles must be made according to NIP-27 ... optionally adding tags for these' - so e/a/p are mention tags. Although it extends BaseThreadedEvent it has no reply semantics (root()/reply() must not be used for it). Known: QTag.parseAddressId rejects every address. Draft rows verified. |

### `nip32Labeling` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1985 | `LabelEvent` | e -> LABELED (E); a -> LABELED (A); p -> LABELED (U); t -> LABELED (T); r -> LABELED (T); l -> TAG (T); L -> TAG (T) | labeledEvents() (ETag), labeledAddresses() (ATag), labeledPubKeys() (PTag), labeledHashtags() (HashtagTag), labeledRelayUrls() (r), labels()/namespaces() | NIP-32. Props labels (l values with namespace) on each LABELED. NOTE: on 1985 `t` and `r` are label TARGETS, not the event's own topics - they must NOT fall back to HASHTAG/TAG. r may be a relay URL; kept as a T target because it is what is labeled (UNCERTAIN given 'relay URLs not modelled in v1'). With no target tag, the labels apply to the label event itself (no link). Draft row verified. |

### `nip38UserStatus` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 30315 | `UserStatusEvent` | p -> LINKED (U); e -> LINKED (E); a -> LINKED (A); r -> TAG (T); emoji[emoji-set-address] -> EMOJI_SET (A) | create() writes PTag/ETag/ATag but there are no readers (only firstTaggedUrl() for r) -> new parser needed | NIP-38. d = status type (general/music), expiration: values. DRAFT FIX: remove 30315 from the REFERENCE list. UNCERTAIN: LINKED vs reusing MENTION. |

### `nip39ExtIdentities` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10011 | `ExternalIdentitiesEvent` | i -> TAG (T) | IdentityClaimTag::parse (claims via replaceClaims) | NIP-39: i = platform:identity with proof. Plain value tag. |

### `nip42RelayAuth` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 22242 | `RelayAuthEvent` | *none* | relay() (RelayTag), challenge() (ChallengeTag) | NIP-42: relay URL + challenge string only: not modelled. |

### `nip46RemoteSigner` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 24133 | `NostrConnectEvent` | p -> RECIPIENT (U) | recipientPubKey()/verifiedRecipientPubKey() (first p) | NIP-46: client p-tags remote-signer and vice versa, encrypting to it. DRAFT FIX: RECIPIENT kinds could list 24133, 23194, 23197. Ephemeral. |

### `nip50Search` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10007 | `SearchRelayListEvent` | *none* | tags.relays() (RelayTag) | NIP-50/NIP-51 search relay list: relay URLs (public + NIP-44 private): not modelled. |

### `nip56Reports` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1984 | `ReportEvent` | p (report names no e/a/x) -> REPORTED_USER (U); p (report also names e/a/x) -> REPORTED_AUTHOR (U); e -> REPORTED (E); a -> REPORTED (A); x -> REPORTED (T); l -> TAG (T); L -> TAG (T) | ReportedAuthorTag / ReportedEventTag / ReportedAddressTag (typed, DefaultReportTag fallback), HashSha256Tag (x); reportedAuthorsWithOwnType() | NIP-56. Props report/report_raw on all three. server tag = media server URL: not modelled. Split must be by presence of e/a/x, NOT by whether p carries its own type: Quartz's own build() writes the type on both e and p. Draft rows verified. |

### `nip62RequestToVanish` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 62 | `RequestToVanishEvent` | *none* | – | NIP-62: only relay tags (relay URL or ALL_RELAYS) - not modelled. The vanish effect is about the AUTHOR, already the AUTHOR link. |

### `nip65RelayList` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10002 | `AdvertisedRelayListEvent` | *none* | – | NIP-65: r tags are relay URLs (read/write markers) - not modelled; NOT the TAG r (web url) meaning. |

### `nip68Picture` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 20 | `PictureEvent` | p -> TAGGED (U); imeta annotate-user -> TAGGED (U) [props x,y]; t -> HASHTAG (T); g -> TAG (T) | hashtags(), geohashes(); imetaTags() -> PictureMeta.annotations (UserAnnotationTag); p: new parser needed (class has no p accessor; PTag::parseKey) | NIP-68 also defines m, x (hashes), location, L/l (not read by the class; x/location not modelled). Quartz gap: no PubKeyHintProvider though p tags are spec'd. UNCERTAIN: could merge with PARTICIPANT (NIP-71 video 'participant') if the maintainer prefers one people-in-media relation. |

### `nip69P2pOrderEvents` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 38383 | `P2POrderEvent` | *none* | – | NIP-69: k is the order type (sell/buy), not a kind - do not emit TAG k; f, s, amt, fa, pm, premium, source (URL), network, layer, name, g (spec; not read by the class), bond, y, z. No E/A/U references. |

### `nip75ZapGoals` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 9041 | `ZapGoalEvent` | e -> FUNDED (E); a -> FUNDED (A); zap -> ZAP_SPLIT (U) [props weight]; p -> MENTION (U); r -> TAG (T); t -> HASHTAG (T) | ETag/ATag::parseId (linked()), PTag::parseKey, topics() (hashtags); zap: Event.zapSplitSetup() (ZapSplitSetupParser, pubkey form only); r: new parser needed (builder writes it via reference()) | NIP-75. Draft lists zap goals (9041) as REFERENCE-only; classified here. NIP-75 defines no p or e tag (Quartz writes e as well as a for addressable targets, and reads p): UNCERTAIN p meaning, MENTION chosen. BENEFICIARY is cross-cutting: any event with NIP-57 zap tags. |

### `nip7DThreads` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 11 | `ThreadEvent` | *none* | – | NIP-7D: only title (and nostrord's subject). Replies are NIP-22 1111s pointing at it. NIP-29 h group tag is not an E/A/U target. Class extends Event (not BaseNoteEvent) so no nostr: content parsing; if content citations are wanted later it would be MENTION via content (new parser). |

### `nip84Highlights` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 9802 | `HighlightEvent` | e -> HIGHLIGHTED (E); a -> HIGHLIGHTED (A); i -> HIGHLIGHTED (T); r[source or unmarked] -> HIGHLIGHTED (T); p[author or no role, editor] -> HIGHLIGHTED_AUTHOR (U) [props role]; p[mention] -> MENTION (U); r[mention] -> TAG (T); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U) | inPostVersion()/inPostAddress() (first e/a), inExternalIds() (ReplyIdentifierTag, i), inReference() (r, source/mention markers), author() (p author role), PTag::parseKey, QTag, citedNIP19() | NIP-84: source via a/e, i (NIP-73), r ('may contain a URL or text'); p tags 'the original authors' with optional role (author, editor); in quote highlights p/r 'mention' marker. DRAFT FIX: HIGHLIGHTED targets should include T (i/r sources), as REACTED does for kind 17. Quartz: author() only reads the author role; editor p tags fall through to linkedPubKeys (role lost); q parsing is Amethyst-side (NIP-84 does not define q). |

### `nip94FileMetadata` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1063 | `FileMetadataEvent` | i -> TAG (T) [torrent infohash] | torrentInfoHash() (TorrentInfoHash::parse, tag i) | NIP-94: url, m, x, ox, size, dim, magnet, i, blurhash, thumb, image, summary, alt - no E/A/U references. x/ox are blob hashes (not in the TAG allowlist). DRAFT FIX: draft Coverage lists file metadata (1063) as carrying references; only the i value tag. |

### `nip96FileStorage` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10096 | `FileServersEvent` | *none* | – | NIP-96: server URLs - not modelled. |

### `nip98HttpAuth` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 27235 | `HTTPAuthorizationEvent` | *none* | – | NIP-98: u (URL), method, payload hash - not modelled. |

### `nip99Classifieds` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 30402 | `ClassifiedsEvent` | e -> MENTION (E); a -> MENTION (A); p -> MENTION (U); t -> HASHTAG (T); content nostr: -> MENTION (E,A,U) | ETag/ATag/PTag::parseKey\|parseId (hint providers), categories() (hashtags); content nostr:: new parser needed (class is BaseAddressableEvent, no citedNIP19) | NIP-99: the example's e/a tags are the events the markdown content cites (NIP-27 style), so MENTION. g is in the spec but not read by the class. Draft lists classifieds (30402) as REFERENCE-only; classified here. UNCERTAIN: NIP-99 gives e/a/p no explicit role. |

### `nipA3PaymentTargets` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 10133 | `PaymentTargetsEvent` | *none* | – | NIP-A3: payto payment targets (lightning/bitcoin/etc.) - not Nostr entities. |

### `nipA4PublicMessages` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 24 | `PublicMessageEvent` | p -> RECIPIENT (U); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U) | ReceiverTag::parseKey (p), citedNIP19() (eventIds/addressIds/pubKeys); q: new parser needed in this class (QTag::parseEventId/parseAddressId exist) | NIP-A4: 'p tags identify one or more receivers'; 'e tags must not be used' (Quartz strips them); q MAY cite events used in content. Draft lists 24 under both RECIPIENT and MENTION: correct only if MENTION means the content nostr: URIs - the p tags are RECIPIENT, never MENTION. Quartz gap: q tags not read by hint providers. |

### `nipB0WebBookmarks` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 39701 | `WebBookmarkEvent` | d[url] -> BOOKMARK (T) [Tag name r]; t -> HASHTAG (T) | url() (dTagToUrl(dTag())), hashtags() | NIP-B0: 'The d tag is just their URI'. DRAFT FIX: BOOKMARK targets should include T (a web URL) for 39701. UNCERTAIN: URL values are TAG-shaped; if URLs stay out of the graph in v1, this becomes none apart from HASHTAG. Replies are NIP-22 1111s. |

### `nipBCOnchainZaps` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 8333 | `OnchainZapEvent` | p -> ZAP_RECIPIENT (U); e -> ZAPPED (E); a -> ZAPPED (A); i -> TAG (T) [bitcoin txid]; k -> TAG (T) | recipient() (PTag), zappedEvent() (ETag), zappedAddress() (ATag), txid() (BitcoinTxIdTag, i), claimedAmountInSats() | NIP-BC is not merged upstream (404); read from Quartz KDoc. Draft ZAPPED/ZAP_RECIPIENT rows confirmed. Props msats = claimedAmountInSats*1000, sender-claimed until verified on chain. The sender is the AUTHOR (no P tag). Builder writes both a and e for addressable targets (two ZAPPED links to the same content). |

### `nipC0CodeSnippets` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1337 | `CodeSnippetEvent` | repo[30617 address] -> REPOSITORY (A); l -> TAG (T) | TagArray.repo() (RepoTag::parse returns the raw string; needs Address.parse to tell a 30617 coordinate from a URL - not exposed on the class), language() (l) | NIP-C0: repo 'MUST be either a standard URL or ... the address of a NIP-34 Git repository announcement'. A repo URL is not modelled. REPOSITORY (draft: 1617, 1618, 1621) extends to 1337. Also name, extension, description, runtime, license, dep (no references). |

### `nipC7Chats` (1)

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 9 | `ChatEvent` | q[last, the reply] -> PARENT (E); q[slot 3 pubkey of the parent] -> PARENT_AUTHOR (U); q[other] -> QUOTE (E,A); p -> MENTION (U); content nostr: -> MENTION (E,A,U) | replyingTo() (last q), quotedEvents() (QEventTag::parse incl. author slot), QTag::parseEventId/parseAddressId, PTag::parseKey, citedNIP19() (BaseNoteEvent) | NIP-C7: 'A reply to a kind 9 ... quotes the parent using a q tag'; other kinds MAY be quoted per NIP-18. Draft PARENT row (kind 9 via q) confirmed. UNCERTAIN: by tags alone a kind 9 that only quotes (not replies) is indistinguishable from a reply; Quartz takes the LAST q as parent and returns tag[1] even when it is an address (replyingTo() does not check shape). PARENT_AUTHOR from q[3] follows rule 3; NIP-C7 puts the parent's pubkey in the q tag, so no p is needed. |

## Classes added 2026-10-08 (kind census)

Typed after the 2026-10-08 relay census (`tools/kind-census/`): 27 classes over 24 kinds. Each
class's `linked*` overrides carry a KDoc naming these relations, so its future `links()` is a
transcription. Kinds shared with other apps (38384, 38385, 38386, 31986) are split by tags in
`EventFactory`, as kind 38000 is. Their `UnrecognizedKind…Event` fallbacks carry no links.

### Relations these classes add

| Relation | Kinds | Justification |
|---|---|---|
| `COORDINATOR` | 9840, 9843, 9844, 29846 | the CI coordinator a request, stop, manual trigger or secret update is addressed to (the spec's word for the `p`) |
| `REQUESTER` | 9841, 9842, 39842 | the author of the Service Request or Manual Trigger a run's provenance quote names (slot 3 of the `q`; required by the spec because 9843/9840 are regular events) |
| `SERVICE_REQUEST` | 9842, 39842 | the standing 9843 Service Request a request-gated run was authorized by (`q` with the `service-request` marker) |
| `MANUAL_TRIGGER` | 9841, 9842, 39842 | the 9840 Manual Trigger a run replays (`q` with the `manual-trigger` marker) |
| `JOB_RESULT` | 9842, 39842 | each 9841 Job Result that makes up the run (`q` with a job-id marker; prop: job id) |
| `COMPUTE_PROVIDER` | 9842, 39842 | each quoted Job Result's signer (slot 3 of the `q`; rule 3 for JOB_RESULT) |
| `WORKFLOW_RUN` | 9841 | the 39842 Workflow Progress address of the run a Job Result belongs to (`q`) |
| `READY_FOR` | 19844 | a repository (`a`) or every repository of a maintainer (`p`) the coordinator is ready to accept a Service Request for |
| `SECRET_ORIGIN` | 39844 | the maintainer that provisioned an effective CI secret (slot 2 of `secret`); value and name are props, never the secret itself |
| `SECRETS_KEY` | 29846 | the Coordinator Advertisement whose `secrets-key` the update is encrypted to (`e` with the `secrets-key` marker) |
| `SOLVER` | 38385 | a Mostro instance's dispute solver (`serbero`) |
| `REPUTATION_ISSUER` | 38385 | the key whose reputation attestations the instance publishes |
| `REPUTATION_IMPORT_ISSUER` | 38385 | each issuer whose reputation the instance imports (`reputation_import_issuers`) |
| `ORDER` | 8383 | the 38383 order a Mostro dev fee was paid on, derived as `38383:<signer>:<order-id>` (Mostro publishes each order on `d` = its id) |

`RATED` (34259) gains 38384 (the rated trader's key in `d`, as NIP-85's subject is) and 31986 (the
coordinator in `p`). `EXERCISE` (1301) gains 33402. `SNAPSHOTTED`, `ORIGIN` and `APP` gain 5128,
which this appendix had filed under 5129 by analogy.

### The classes

| Kind | Class | Links | Built from | Notes |
|---|---|---|---|---|
| 1624 | `GitCoverNoteEvent` | e[root] -> ROOT (E); p -> ROOT_AUTHOR (U); q -> QUOTE (E,A); content nostr: -> MENTION (E,A,U) | coverNoteRoot() (MarkedETag, falling back to the first `e`), PTag::parseKey, QTag::parseEventId, QAddressableTag::parse, citedNIP19() | gitworkshop/ngit cover note. k is the root's kind, not a link. Live notes omit the `root` marker. |
| 9840 | `CiManualTriggerEvent` | p -> COORDINATOR (U); a -> REPOSITORY (A); E -> ROOT (E); e -> PARENT (E); P -> ROOT_AUTHOR (U) | ciRunLinkedEventIds(), ciRunLinkedAddressIds(), PTag::parseKey | Nostr CI draft (CI extension to NIP-34). c (commits), w (workflow path + sha256), r (git ref): values. |
| 9841 | `CiJobResultEvent` | a -> REPOSITORY (A); E -> ROOT (E); e -> PARENT (E); P -> ROOT_AUTHOR (U); p -> PARENT_AUTHOR (U); q[39842] -> WORKFLOW_RUN (A); q[manual-trigger] -> MANUAL_TRIGGER (E); q slot 3 -> REQUESTER (U) | ciRun* helpers (RepositoryTag, NIP-22 root/reply tags, QTag/QAddressableTag) | job, name, conclusion, logs/artifact (Blossom URLs), output, exit_code, runs_on: values. |
| 9842 | `CiWorkflowResultEvent` | a -> REPOSITORY (A); E -> ROOT (E); e -> PARENT (E); P -> ROOT_AUTHOR (U); p -> PARENT_AUTHOR (U); q[job id] -> JOB_RESULT (E); q[job id] slot 3 -> COMPUTE_PROVIDER (U); q[service-request] -> SERVICE_REQUEST (E); q[manual-trigger] -> MANUAL_TRIGGER (E); provenance q slot 3 -> REQUESTER (U) | ciRun* helpers | r is either a git ref (`refs/…`) or the run id: values. |
| 9843 | `CiServiceRequestEvent` | a -> REPOSITORY (A); p -> COORDINATOR (U) | RepositoryTag::parseAddressId, PTag::parseKey | exactly one of each, or neither is read. |
| 9844 | `CiServiceStopEvent` | a -> REPOSITORY (A); p -> COORDINATOR (U) | RepositoryTag::parseAddressId, PTag::parseKey | |
| 19843 | `CiCoordinatorAdvertisementEvent` | *none* | – | W/R/M/X/B are capability and policy values; secrets-key is an encryption key, not a user. |
| 19844 | `CiRequestReadinessListEvent` | a -> READY_FOR (A); p -> READY_FOR (U) | readyRepositoryAddressIds(), readyMaintainers() | NIP-51 public list shape; items with a 4th element are dropped (the spec forbids markers). |
| 29846 | `CiSecretUpdateEvent` | a -> REPOSITORY (A); p -> COORDINATOR (U); e[secrets-key] -> SECRETS_KEY (E) | RepositoryTag, PTag, SecretsKeyAdvertisementTag | ephemeral; content is NIP-44 ciphertext. sender/recipient are encryption keys, not users. |
| 39842 | `CiWorkflowProgressEvent` | as 9842 | ciRun* helpers | addressable (d = run id). A queued marker hides the service-request quote, as the spec requires. |
| 39844 | `CiRepositoryStatusEvent` | a -> REPOSITORY (A); secret[slot 2] -> SECRET_ORIGIN (U) | ciRepositoryAddressIds(), secrets() | d repeats the first a: not a second link. |
| 1080 | `PnsEvent` | *none* | – | NIP-PNS draft (#1893). Content is ciphertext; a link would leak what PNS hides. The signer is a derived key unlinkable to the user. |
| 4454 | `EncryptionKeyRequestEvent` | *none* | – | NIP-4E draft. P/pubkey (client key), n (requested encryption key) and relay: key material and delivery hints. |
| 4455 | `EncryptionKeyTransferEvent` | p -> RECIPIENT (U) | recipientKeys() | P (sender client key) is the other half of the NIP-44 conversation key, not a link. |
| 5128 | `SiteSnapshotEvent` | a -> SNAPSHOTTED (A); A -> ORIGIN (A); app -> APP (A) | OriginSiteTag, AppTag (ATag) | NIP-5A. path hashes, x, server/source URLs: values. |
| 30024 | `LongFormDraftEvent` | q -> QUOTE (E,A); p -> MENTION (U); zap -> ZAP_SPLIT (U); content nostr: -> MENTION (E,A,U); t -> HASHTAG (T) | nip23LongContent/TagArrayExt (shared with 30023) | NIP-23 draft; the same references as 30023. Not a NIP-10 thread or NIP-22 root. |
| 30031 | `StickerPackEvent` | *none* | – | DEN Chat / Sonar sticker packs (no spec). Sticker URLs and hashes are values. |
| 33402 | `WorkoutTemplateEvent` | exercise[coordinate] -> EXERCISE (A) | exerciseTemplateAddresses() | NIP-101e draft (POWR); set values are props. Workstr's `workstr:exercise:` ids are not addresses. |
| 8383 | `MostroDevFeePaymentEvent` | order-id (derived) -> ORDER (A) | orderAddressId() | Built only when order-id is a UUID. amount/hash/destination: values. |
| 31986 | `RoboSatsCoordinatorRatingEvent` | p -> RATED (U) | PTag::parseKey | sig is a coordinator token over robot pubkey + order id, not a link; d = alias:orderId. |
| 38384 | `MostroUserRatingEvent` | d -> RATED (U) | ratedPubKey() (64-hex d only) | The instance signs; the d is the rated trade key (the NIP-85 subject pattern). |
| 38385 | `MostroInfoEvent` | serbero -> SOLVER (U); reputation issuer -> REPUTATION_ISSUER (U); reputation_import_issuers -> REPUTATION_IMPORT_ISSUER (U) | serbero(), reputationIssuer(), reputationImportIssuers() | d is the instance's own key (AUTHOR); lnd_node_pubkey is a Lightning key. |
| 38386 | `MostroDisputeEvent` | *none* | – | the dispute id is a UUID, not a Nostr reference. |
| 31986, 38384, 38385, 38386 | `UnrecognizedKind…Event` | *none* | – | other apps' events on these kinds (Paygress, bondtrade, Borkstr): addressable, unread. |
