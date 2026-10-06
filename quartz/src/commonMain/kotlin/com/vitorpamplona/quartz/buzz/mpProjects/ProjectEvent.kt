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
package com.vitorpamplona.quartz.buzz.mpProjects

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMember
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMemberTag
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectVisibility
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Buzz NIP-MP project (`kind:30621`): an addressable, signed, named grouping of NIP-34
 * repository announcements (`kind:30617`,
 * [com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent]) referenced by
 * coordinate, so one project can span repositories owned by different pubkeys. It is metadata
 * only — its signer gains no authority over any member.
 *
 * Tags: exactly one non-empty `d` (the project slug), optional `name` (≤256 bytes; clients
 * fall back to `d`), optional `description` (≤2048 bytes), 0–64 member `a` tags
 * ([ProjectMember]; no two with the same coordinate), optional `buzz-channel` and
 * `buzz-visibility` (`listed`|`unlisted`, anything else reads as listed). `content` is `""`
 * and carries no meaning; unknown tags are ignored. Global-only: a stray `h` never scopes it.
 *
 * Ground truth: `build_project` / `validate_project_envelope` in Buzz's
 * `buzz-sdk/src/builders.rs`, `validate_project_envelope` in
 * `buzz-relay/src/handlers/ingest.rs` (mirrored by [ProjectValidator]), and
 * `docs/nips/NIP-MP.md` with its `NIP-MP.fixtures.json` oracle.
 */
@Immutable
class ProjectEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent,
    AddressHintProvider {
    override fun indexableContent() = listOfNotNull(displayName(), description()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, in the same order.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(displayName())) return
        visitor.visit(description())
    }

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ProjectMemberTag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ProjectMemberTag::parseAddressId)

    /** The project slug — the `d` tag. */
    fun slug() = dTag()

    /** The `name` tag, if any. */
    fun name() = tags.projectName()

    /** What clients display: the `name`, falling back to the slug. */
    fun displayName() = name() ?: slug()

    fun description() = tags.projectDescription()

    /** The well-formed member repositories. */
    fun members(): List<ProjectMember> = tags.projectMembers()

    /** The member repositories' `30617` addresses. */
    fun memberAddresses(): List<Address> = members().map { it.address }

    /** The discussion channel reference — metadata, not routing. */
    fun channelId() = tags.projectChannel()

    fun visibility(): ProjectVisibility = tags.projectVisibility()

    fun isListed() = visibility() == ProjectVisibility.LISTED

    /** The relay's ingest verdict: null when accepted, else the rule that rejects it. */
    fun validate(): ProjectRejection? = ProjectValidator.validate(tags)

    fun isWellFormed() = validate() == null

    companion object {
        const val KIND = 30621

        /**
         * Builds a project with Buzz's writer policy (`build_project`): a non-empty slug of at
         * most 1024 bytes, [channelId] a UUID, empty content, and the whole envelope checked
         * by [ProjectValidator] (so duplicate or oversized members and metadata are refused
         * here instead of by the relay).
         */
        @OptIn(ExperimentalUuidApi::class)
        fun build(
            slug: String,
            name: String? = null,
            description: String? = null,
            members: List<ProjectMember> = emptyList(),
            channelId: String? = null,
            visibility: ProjectVisibility? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProjectEvent>.() -> Unit = {},
        ): EventTemplate<ProjectEvent> {
            require(slug.isNotEmpty()) { "project slug must not be empty" }
            require(slug.encodeToByteArray().size <= ProjectValidator.D_MAX_BYTES) { "project slug must not exceed ${ProjectValidator.D_MAX_BYTES} bytes" }
            if (channelId != null) {
                require(runCatching { Uuid.parse(channelId) }.isSuccess) { "buzz-channel must be a valid UUID (got $channelId)" }
            }

            val template =
                eventTemplate<ProjectEvent>(KIND, "", createdAt) {
                    dTag(slug)
                    name?.let { projectName(it) }
                    description?.let { projectDescription(it) }
                    projectMembers(members)
                    channelId?.let { projectChannel(it) }
                    visibility?.let { projectVisibility(it) }
                    initializer()
                }

            ProjectValidator.validate(template.tags)?.let { throw IllegalArgumentException("[${it.rule.id}] ${it.message}") }
            return template
        }
    }
}
