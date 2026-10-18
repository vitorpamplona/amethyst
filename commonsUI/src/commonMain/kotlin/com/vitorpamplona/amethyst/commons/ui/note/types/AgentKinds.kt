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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunPayload
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_system_prompt
import com.vitorpamplona.amethyst.commons.resources.buzz_workflow_picker_label
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_id
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_moved_out
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_create
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_delete
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_move
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_restore
import com.vitorpamplona.amethyst.commons.resources.kc_agent_artifact_op_update
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_auth_failure
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_auth_success
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_channel_created
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_channel_deleted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_channel_updated
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_event_created
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_event_deleted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_media_uploaded
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_member_added
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_member_removed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_object
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_rate_limit
import com.vitorpamplona.amethyst.commons.resources.kc_agent_audit_unknown
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_head
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_interface
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_operations
import com.vitorpamplona.amethyst.commons.resources.kc_agent_capability_profile
import com.vitorpamplona.amethyst.commons.resources.kc_agent_channel_add_policy
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_encryption
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_ephemeral
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_open_stream
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_oversized
import com.vitorpamplona.amethyst.commons.resources.kc_agent_cvm_tools_count
import com.vitorpamplona.amethyst.commons.resources.kc_agent_dev
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_free
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_hidden
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_package
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_payout
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_price
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_price_sats
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_published
import com.vitorpamplona.amethyst.commons.resources.kc_agent_ext_version
import com.vitorpamplona.amethyst.commons.resources.kc_agent_instructions
import com.vitorpamplona.amethyst.commons.resources.kc_agent_managed_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_model
import com.vitorpamplona.amethyst.commons.resources.kc_agent_parallelism
import com.vitorpamplona.amethyst.commons.resources.kc_agent_persona_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_personas
import com.vitorpamplona.amethyst.commons.resources.kc_agent_policy_anyone
import com.vitorpamplona.amethyst.commons.resources.kc_agent_policy_nobody
import com.vitorpamplona.amethyst.commons.resources.kc_agent_policy_owner_only
import com.vitorpamplona.amethyst.commons.resources.kc_agent_profile_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_project_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_provider
import com.vitorpamplona.amethyst.commons.resources.kc_agent_reminder_due
import com.vitorpamplona.amethyst.commons.resources.kc_agent_repositories
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_allowlist
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_anyone
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_owner_only
import com.vitorpamplona.amethyst.commons.resources.kc_agent_respond_to
import com.vitorpamplona.amethyst.commons.resources.kc_agent_runtime
import com.vitorpamplona.amethyst.commons.resources.kc_agent_shared
import com.vitorpamplona.amethyst.commons.resources.kc_agent_status
import com.vitorpamplona.amethyst.commons.resources.kc_agent_team_catalog_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_team_title
import com.vitorpamplona.amethyst.commons.resources.kc_agent_test
import com.vitorpamplona.amethyst.commons.resources.kc_agent_type
import com.vitorpamplona.amethyst.commons.resources.kc_agent_visibility
import com.vitorpamplona.amethyst.commons.resources.kc_agent_visibility_listed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_visibility_unlisted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_approval_denied
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_approval_granted
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_approval_requested
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_cancelled
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_completed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_def
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_deny
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_error
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_failed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_grant
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_note
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_pr
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_step
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_step_completed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_step_failed
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_step_started
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_trigger
import com.vitorpamplona.amethyst.commons.resources.kc_agent_wf_triggered
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_payload
import com.vitorpamplona.amethyst.commons.resources.members
import com.vitorpamplona.amethyst.commons.resources.website
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.observeAnnouncedServerName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactRemovalEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ArtifactOp
import com.vitorpamplona.quartz.buzz.audit.AuditAction
import com.vitorpamplona.quartz.buzz.audit.AuditEntryEvent
import com.vitorpamplona.quartz.buzz.erReminders.EventReminderEvent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentEvent
import com.vitorpamplona.quartz.buzz.managedAgents.RespondTo
import com.vitorpamplona.quartz.buzz.mpProjects.ProjectEvent
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectVisibility
import com.vitorpamplona.quartz.buzz.teamCatalog.TeamCatalogEvent
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalDeniedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalGrantedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmServerAnnouncementEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmToolsListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.CapabilityDiscoveryEvent
import com.vitorpamplona.quartz.openagents.nipExtExtensions.listing.ExtensionListingEvent
import com.vitorpamplona.quartz.openagents.nipExtExtensions.release.ExtensionReleaseEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource

/*
 * Cards for the AI-agent kinds: Buzz agents, personas, teams, projects, artifacts, workflows and
 * their audit trail, OpenAgents capabilities and extensions, and ContextVM server announcements.
 * Their content is JSON for software or NIP-44 ciphertext, so the text-note fallback printed
 * either raw. Each card shows what the record says instead.
 */

// ---------------------------------------------------------------------------------------------
// Agents, personas, teams, projects
// ---------------------------------------------------------------------------------------------

/** A Buzz agent profile (kind 10100): the agent's name, type, status and who may add it to channels. */
@Composable
fun RenderAgentProfile(baseNote: Note) {
    val event = baseNote.event as? AgentProfileEvent ?: return
    val profile = remember(event) { event.profileOrNull() }
    val name = profile?.displayName?.ifBlank { null } ?: profile?.name?.ifBlank { null }
    val kindTitle = stringRes(Res.string.kc_agent_profile_title)

    KindCard(
        icon = MaterialSymbols.Assistant,
        title = name ?: kindTitle,
        subtitle = if (name != null) kindTitle else null,
    ) {
        KindCardField(stringRes(Res.string.kc_agent_type), profile?.agentType)
        KindCardField(stringRes(Res.string.kc_agent_status), profile?.status)
        KindCardField(stringRes(Res.string.kc_agent_channel_add_policy), profile?.channelAddPolicy?.let { channelAddPolicyLabel(it) })
        KindCardChips(profile?.capabilities.orEmpty())
    }
}

/** A Buzz managed agent (kind 30177): the agent, its model and the persona it runs. */
@Composable
fun RenderManagedAgent(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ManagedAgentEvent ?: return
    val agent = remember(event) { event.agentOrNull() }
    if (agent == null) {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Assistant)
        return
    }
    val agentKey = remember(event) { hex64OrNull(event.agentPubKey()) }
    val personaAddress =
        remember(event) {
            agent.personaId?.ifBlank { null }?.let { Address(PersonaEvent.KIND, event.pubKey, it) }
        }
    val kindTitle = stringRes(Res.string.kc_agent_managed_title)

    KindCard(
        icon = MaterialSymbols.Assistant,
        title = agent.name.ifBlank { kindTitle },
        subtitle = kindTitle,
    ) {
        agentKey?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        KindCardField(stringRes(Res.string.kc_agent_model), agent.model)
        KindCardField(stringRes(Res.string.kc_agent_provider), agent.provider)
        KindCardField(stringRes(Res.string.kc_agent_parallelism), agent.parallelism.toString())
        KindCardField(stringRes(Res.string.kc_agent_respond_to), respondToLabel(agent.respondTo))
        KindCardField(stringRes(Res.string.buzz_persona_system_prompt), agent.systemPrompt, maxLines = 4)
        personaAddress?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** A Buzz agent persona (kind 30175): name, description, model and system prompt. */
@Composable
fun RenderPersona(baseNote: Note) {
    val event = baseNote.event as? PersonaEvent ?: return
    val persona = remember(event) { event.personaOrNull() }
    val shared = remember(event) { event.isShared() }
    val kindTitle = stringRes(Res.string.kc_agent_persona_title)

    KindCard(
        icon = MaterialSymbols.Face,
        title = persona?.displayName?.ifBlank { null } ?: event.slug().ifBlank { kindTitle },
        subtitle = persona?.description?.ifBlank { null } ?: kindTitle,
    ) {
        if (shared) KindCardChips(listOf(stringRes(Res.string.kc_agent_shared)))
        if (persona != null) {
            KindCardField(stringRes(Res.string.kc_agent_model), persona.model)
            KindCardField(stringRes(Res.string.kc_agent_provider), persona.provider)
            KindCardField(stringRes(Res.string.kc_agent_runtime), persona.runtime)
            KindCardField(stringRes(Res.string.kc_agent_parallelism), persona.parallelism?.toString())
            KindCardField(stringRes(Res.string.kc_agent_respond_to), persona.respondTo?.let { respondToLabel(it) })
            KindCardField(stringRes(Res.string.buzz_persona_system_prompt), persona.systemPrompt, maxLines = 4)
        }
    }
}

/** A Buzz agent team (kind 30176): its name, description, instructions and personas. */
@Composable
fun RenderAgentTeam(baseNote: Note) {
    val event = baseNote.event as? TeamEvent ?: return
    val team = remember(event) { event.teamOrNull() }
    val kindTitle = stringRes(Res.string.kc_agent_team_title)

    KindCard(
        icon = MaterialSymbols.Groups,
        title = team?.name?.ifBlank { null } ?: event.teamId().ifBlank { kindTitle },
        subtitle = team?.description?.ifBlank { null } ?: kindTitle,
    ) {
        KindCardField(stringRes(Res.string.kc_agent_instructions), team?.instructions, maxLines = 3)
        val personas = team?.personaIds.orEmpty().filter { it.isNotBlank() }
        if (personas.isNotEmpty()) {
            KindCardField(stringRes(Res.string.kc_agent_personas), personas.size.toString())
            KindCardChips(personas)
        }
    }
}

/** A Buzz team template (kind 30178): the team and the agents it is made of. */
@Composable
fun RenderAgentTeamCatalog(baseNote: Note) {
    val event = baseNote.event as? TeamCatalogEvent ?: return
    val catalog = remember(event) { event.catalogOrNull() }
    val shared = remember(event) { event.isShared() }
    val kindTitle = stringRes(Res.string.kc_agent_team_catalog_title)

    KindCard(
        icon = MaterialSymbols.Groups,
        title = catalog?.name?.ifBlank { null } ?: event.teamId().ifBlank { kindTitle },
        subtitle = catalog?.description?.ifBlank { null } ?: kindTitle,
    ) {
        if (shared) KindCardChips(listOf(stringRes(Res.string.kc_agent_shared)))
        KindCardField(stringRes(Res.string.kc_agent_instructions), catalog?.instructions, maxLines = 3)
        val members =
            catalog
                ?.members
                .orEmpty()
                .map { it.displayName }
                .filter { it.isNotBlank() }
        if (members.isNotEmpty()) {
            KindCardField(stringRes(Res.string.members), members.size.toString())
            KindCardChips(members)
        }
    }
}

/** A Buzz project (kind 30621): a named group of git repositories. */
@Composable
fun RenderAgentProject(baseNote: Note) {
    val event = baseNote.event as? ProjectEvent ?: return
    val repos = remember(event) { event.members().map { it.address.dTag }.filter { it.isNotBlank() } }
    val visibility = remember(event) { event.visibility() }

    KindCard(
        icon = MaterialSymbols.Folder,
        title = event.displayName().ifBlank { stringRes(Res.string.kc_agent_project_title) },
        subtitle = event.description()?.ifBlank { null } ?: stringRes(Res.string.kc_agent_project_title),
    ) {
        KindCardField(
            stringRes(Res.string.kc_agent_visibility),
            when (visibility) {
                ProjectVisibility.LISTED -> stringRes(Res.string.kc_agent_visibility_listed)
                ProjectVisibility.UNLISTED -> stringRes(Res.string.kc_agent_visibility_unlisted)
            },
        )
        if (repos.isNotEmpty()) {
            KindCardField(stringRes(Res.string.kc_agent_repositories), repos.size.toString())
            KindCardChips(repos)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Artifacts and audit
// ---------------------------------------------------------------------------------------------

/** A Buzz artifact revision (kind 45010): its title, what happened to it, and its body. */
@Composable
fun RenderAgentArtifact(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ArtifactEvent ?: return
    val op = remember(event) { event.op() }
    val title = remember(event) { event.title()?.ifBlank { null } }
    val body = remember(event) { event.textBody() }
    val type = remember(event) { event.type() }
    val opLabel = artifactOpLabel(op) ?: kindName(event.kind)

    KindCard(
        icon = if (op == ArtifactOp.DELETE) MaterialSymbols.Delete else MaterialSymbols.Description,
        title = title ?: opLabel,
        subtitle = if (title != null) opLabel else null,
        iconTint = if (op == ArtifactOp.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        KindCardField(stringRes(Res.string.kc_agent_type), type, monospace = true)
        if (body != null) {
            KindCardText(baseNote, body, backgroundColor, accountViewModel, nav)
        } else {
            KindCardCode(event.content, maxLines = 6)
        }
    }
}

/** A Buzz artifact removal marker (kind 45011): the artifact left this channel. */
@Composable
fun RenderAgentArtifactRemoval(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ArtifactRemovalEvent ?: return
    val artifactId = remember(event) { event.artifactId() }
    val replaced = remember(event) { event.replacedRevision() }

    KindCard(
        icon = MaterialSymbols.AutoMirrored.DriveFileMove,
        title = stringRes(Res.string.kc_agent_artifact_moved_out),
    ) {
        KindCardField(stringRes(Res.string.kc_agent_artifact_id), artifactId, monospace = true, maxLines = 1)
        replaced?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** A Buzz audit-log entry (kind 48001): the action, who did it, and on what. */
@Composable
fun RenderAgentAuditEntry(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AuditEntryEvent ?: return
    val action = remember(event) { event.action() }
    val actor = remember(event) { event.actor() }
    val objectId = remember(event) { event.objectId() }

    KindCard(
        icon = MaterialSymbols.History,
        title = auditActionLabel(action),
        subtitle = kindName(event.kind),
        iconTint = if (action == AuditAction.AUTH_FAILURE || action == AuditAction.RATE_LIMIT_EXCEEDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        actor?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        KindCardField(stringRes(Res.string.kc_agent_audit_object), objectId, monospace = true, maxLines = 1)
        KindCardCode(event.detail(), maxLines = 6)
    }
}

// ---------------------------------------------------------------------------------------------
// Workflows
// ---------------------------------------------------------------------------------------------

/** A Buzz workflow definition (kind 30620): its name and YAML recipe. */
@Composable
fun RenderWorkflowDefinition(baseNote: Note) {
    val event = baseNote.event as? WorkflowDefEvent ?: return
    val name = remember(event) { event.name()?.ifBlank { null } }
    val kindTitle = stringRes(Res.string.kc_agent_wf_def)

    KindCard(
        icon = MaterialSymbols.Checklist,
        title = name ?: kindTitle,
        subtitle = if (name != null) kindTitle else null,
    ) {
        if (name == null) KindCardField(stringRes(Res.string.buzz_workflow_picker_label), event.workflowId(), monospace = true, maxLines = 1)
        KindCardCode(event.yaml())
    }
}

/** A manual workflow trigger (kind 46020): the task, and the definition being run. */
@Composable
fun RenderWorkflowTrigger(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? WorkflowTriggerEvent ?: return
    val payload = remember(event) { parseRunPayload(event.content) }
    val definition =
        remember(event) {
            event.workflowId()?.ifBlank { null }?.let { Address(WorkflowDefEvent.KIND, event.pubKey, it) }
        }

    KindCard(
        icon = MaterialSymbols.PlayArrow,
        title = stringRes(Res.string.kc_agent_wf_trigger),
        subtitle = payload?.task?.ifBlank { null },
    ) {
        if (payload == null) KindCardCode(event.content, maxLines = 6)
        definition?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/**
 * The relay-emitted lifecycle of a workflow run (kinds 46001-46012): started, step progress,
 * waiting for approval, done, failed or cancelled. All share one shape, a JSON body naming the
 * run (the id of the trigger event), the task, the step and any error, so one card serves them.
 */
@Composable
fun RenderWorkflowLifecycle(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event ?: return
    val header = workflowLifecycleHeader(event) ?: return
    val payload = remember(event) { parseRunPayload(event.content) }
    val runId = remember(payload) { payload?.run?.let { hex64OrNull(it) } }
    val approver = remember(event) { (event as? WorkflowApprovalRequestedEvent)?.approver() }

    KindCard(
        icon = header.icon,
        title = stringRes(header.title),
        subtitle = payload?.task?.ifBlank { null },
        iconTint = if (header.isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        if (payload != null) {
            KindCardField(stringRes(Res.string.kc_agent_wf_step), payload.step)
            KindCardField(stringRes(Res.string.kc_agent_wf_error), payload.error)
            KindCardField(stringRes(Res.string.kc_agent_wf_note), payload.note)
            KindCardField(stringRes(Res.string.kc_agent_wf_pr), payload.pr, maxLines = 1)
            if (runId == null) KindCardField(stringRes(Res.string.buzz_workflow_picker_label), payload.workflow, monospace = true, maxLines = 1)
        } else {
            KindCardCode(event.content, maxLines = 6)
        }
        approver?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        runId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** An approver's grant (kind 46030) or denial (kind 46031) of a paused workflow run. */
@Composable
fun RenderWorkflowApprovalDecision(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event ?: return
    val granted =
        when (event) {
            is ApprovalGrantEvent -> true
            is ApprovalDenyEvent -> false
            else -> return
        }
    // Amethyst's runner uses the trigger event id as the approval token, so a 64-hex token can be
    // quoted as the run it decides. Anything else is an opaque hash and shows nothing.
    val runId =
        remember(event) {
            val token = (event as? ApprovalGrantEvent)?.tokenHash() ?: (event as? ApprovalDenyEvent)?.tokenHash()
            token?.let { hex64OrNull(it) }
        }

    KindCard(
        icon = if (granted) MaterialSymbols.CheckCircle else MaterialSymbols.Cancel,
        title = stringRes(if (granted) Res.string.kc_agent_wf_grant else Res.string.kc_agent_wf_deny),
        iconTint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    ) {
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
        runId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

// ---------------------------------------------------------------------------------------------
// OpenAgents capabilities and extensions
// ---------------------------------------------------------------------------------------------

/** An OpenAgents capability (kind 30180): what it does, how to reach it, and who serves it. */
@Composable
fun RenderCapabilityDiscovery(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CapabilityDiscoveryEvent ?: return
    val data = remember(event) { CapabilityCardData(event) }

    KindCard(
        icon = MaterialSymbols.Extension,
        title = data.path ?: event.dTag().ifBlank { kindName(event.kind) },
        subtitle = data.summary ?: if (data.isHead) stringRes(Res.string.kc_agent_capability_head) else kindName(event.kind),
    ) {
        KindCardField(stringRes(Res.string.kc_agent_capability_profile), data.profile)
        KindCardField(stringRes(Res.string.kc_agent_capability_interface), data.interfaceId, monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_agent_capability_operations), data.operations.joinToString(", "), monospace = true)
        val flags =
            listOfNotNull(
                if (data.isTest) stringRes(Res.string.kc_agent_test) else null,
                if (data.isDev) stringRes(Res.string.kc_agent_dev) else null,
            )
        KindCardChips(data.transports + flags)
        KindCardUsers(data.workers, accountViewModel, nav, max = 3)
    }
}

private class CapabilityCardData(
    event: CapabilityDiscoveryEvent,
) {
    val path: String? = event.qualifiedId()?.path()
    val summary: String? = event.summary()
    val isHead: Boolean = event.isHead()
    val profile: String? = event.profile()
    val interfaceId: String? = event.interfaceId()
    val operations: List<String> = event.operations()
    val transports: List<String> = event.transports()
    val workers: List<HexKey> = event.linkedPubKeys()
    val isTest: Boolean = event.isTest()
    val isDev: Boolean = event.isDev()
}

/** An OpenAgents extension listing (kind 30184): the extension, its state and current release. */
@Composable
fun RenderExtensionListing(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ExtensionListingEvent ?: return
    val title = remember(event) { event.title() }
    val description = remember(event) { event.description() }
    val published = remember(event) { event.isPublished() }
    val hidden = remember(event) { event.isHidden() }
    val releaseId = remember(event) { event.release()?.id }

    KindCard(
        icon = MaterialSymbols.Extension,
        title = title ?: event.slug().ifBlank { kindName(event.kind) },
        subtitle = description,
    ) {
        if (title != null) KindCardField(stringRes(Res.string.kc_agent_ext_package), event.slug(), monospace = true, maxLines = 1)
        KindCardChips(
            listOfNotNull(
                if (published) stringRes(Res.string.kc_agent_ext_published) else null,
                if (hidden) stringRes(Res.string.kc_agent_ext_hidden) else null,
            ),
        )
        releaseId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** An OpenAgents extension release (kind 3184): package, version and price. */
@Composable
fun RenderExtensionRelease(baseNote: Note) {
    val event = baseNote.event as? ExtensionReleaseEvent ?: return
    val slug = remember(event) { event.packageSlug() }
    val version = remember(event) { event.version() }
    val feeMsat = remember(event) { event.feeMsat() }
    val payout = remember(event) { event.payout() }

    KindCard(
        icon = MaterialSymbols.Download,
        title = slug ?: kindName(event.kind),
        subtitle = version?.let { stringRes(Res.string.kc_agent_ext_version, it) },
    ) {
        KindCardField(
            stringRes(Res.string.kc_agent_ext_price),
            if (feeMsat <= 0L) stringRes(Res.string.kc_agent_ext_free) else stringRes(Res.string.kc_agent_ext_price_sats, (feeMsat / 1000).toString()),
        )
        KindCardField(stringRes(Res.string.kc_agent_ext_payout), payout, monospace = true, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// ContextVM
// ---------------------------------------------------------------------------------------------

/** A ContextVM server announcement (CEP-6): the server's name, blurb and what it supports. */
@Composable
fun RenderCvmServerAnnouncement(baseNote: Note) {
    val event = baseNote.event as? CvmServerAnnouncementEvent ?: return
    val surface = remember(event) { event.discovery() }
    val name = surface.name?.ifBlank { null }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = name ?: kindName(event.kind),
        subtitle = surface.about?.ifBlank { null } ?: if (name != null) kindName(event.kind) else null,
    ) {
        KindCardField(stringRes(Res.string.website), surface.website, maxLines = 1)
        KindCardChips(
            listOfNotNull(
                if (surface.supportsEncryption) stringRes(Res.string.kc_agent_cvm_encryption) else null,
                if (surface.supportsEphemeralEncryption) stringRes(Res.string.kc_agent_cvm_ephemeral) else null,
                if (surface.supportsOversizedTransfer) stringRes(Res.string.kc_agent_cvm_oversized) else null,
                if (surface.supportsOpenStream) stringRes(Res.string.kc_agent_cvm_open_stream) else null,
            ),
        )
    }
}

/** A ContextVM server's `tools/list` announcement (CEP-6): the server and the tools it offers. */
@Composable
fun RenderCvmToolsList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event = baseNote.event as? CvmToolsListEvent ?: return
    val names = remember(event) { event.tools()?.names?.toList() }
    val serverName = observeAnnouncedServerName(event.pubKey, accountViewModel)

    if (names == null) {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Manufacturing)
        return
    }

    KindCard(
        icon = MaterialSymbols.Manufacturing,
        title = serverName ?: kindName(event.kind),
        subtitle = pluralStringRes(Res.plurals.kc_agent_cvm_tools_count, names.size, names.size),
    ) {
        KindCardChips(names.take(MAX_TOOL_CHIPS))
    }
}

private const val MAX_TOOL_CHIPS = 30

// ---------------------------------------------------------------------------------------------
// Encrypted and machine-only kinds
// ---------------------------------------------------------------------------------------------

/** A self-encrypted Buzz reminder (kind 30300): only when it is due shows. */
@Composable
fun RenderEventReminder(baseNote: Note) {
    val event = baseNote.event as? EventReminderEvent ?: return
    val notBefore = remember(event) { event.notBefore() }
    val alt = remember(event) { event.alt() }

    KindCard(
        icon = MaterialSymbols.Notifications,
        title = kindName(event.kind),
        subtitle = alt ?: stringRes(Res.string.kind_card_private_payload),
    ) {
        notBefore?.let { KindCardField(stringRes(Res.string.kc_agent_reminder_due), timeAbsoluteNoDot(it)) }
    }
}

/** An encrypted agent turn metric (kind 44200): token usage and cost for the owner only. */
@Composable
fun RenderAgentTurnMetric(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.AutoMirrored.ShowChart)

/** An encrypted agent memory (kind 30174). */
@Composable
fun RenderEngram(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Lock)

/** An encrypted live telemetry or control frame between an agent and its observer (kind 24200). */
@Composable
fun RenderObserverFrame(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Visibility)

/** An encrypted device-pairing message (kind 24134). */
@Composable
fun RenderAgentPairing(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Link)

/** An encrypted push-notification lease (kind 30350). */
@Composable
fun RenderPushLease(baseNote: Note) = RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

private val workflowJson = Json { ignoreUnknownKeys = true }

/** The run/step JSON of a workflow event, or null when the content is not that object. */
private fun parseRunPayload(content: String): WorkflowRunPayload? {
    if (!content.trimStart().startsWith("{")) return null
    return runCatching { workflowJson.decodeFromString(WorkflowRunPayload.serializer(), content) }.getOrNull()
}

private fun hex64OrNull(value: String): HexKey? = value.takeIf { it.length == 64 && Hex.isHex64(it) }

private class WorkflowLifecycleHeader(
    val title: StringResource,
    val icon: MaterialSymbol,
    val isFailure: Boolean = false,
)

private fun workflowLifecycleHeader(event: Event): WorkflowLifecycleHeader? =
    when (event) {
        is WorkflowTriggeredEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_triggered, MaterialSymbols.PlayArrow)
        is WorkflowStepStartedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_step_started, MaterialSymbols.Bolt)
        is WorkflowStepCompletedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_step_completed, MaterialSymbols.Check)
        is WorkflowStepFailedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_step_failed, MaterialSymbols.Error, isFailure = true)
        is WorkflowCompletedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_completed, MaterialSymbols.CheckCircle)
        is WorkflowFailedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_failed, MaterialSymbols.Error, isFailure = true)
        is WorkflowCancelledEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_cancelled, MaterialSymbols.Cancel, isFailure = true)
        is WorkflowApprovalRequestedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_approval_requested, MaterialSymbols.Gavel)
        is WorkflowApprovalGrantedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_approval_granted, MaterialSymbols.CheckCircle)
        is WorkflowApprovalDeniedEvent -> WorkflowLifecycleHeader(Res.string.kc_agent_wf_approval_denied, MaterialSymbols.Close, isFailure = true)
        else -> null
    }

@Composable
private fun channelAddPolicyLabel(code: String): String =
    when (code) {
        "anyone" -> stringRes(Res.string.kc_agent_policy_anyone)
        "owner_only" -> stringRes(Res.string.kc_agent_policy_owner_only)
        "nobody" -> stringRes(Res.string.kc_agent_policy_nobody)
        else -> code
    }

@Composable
private fun respondToLabel(respondTo: RespondTo): String =
    when (respondTo) {
        RespondTo.OWNER_ONLY -> stringRes(Res.string.kc_agent_respond_owner_only)
        RespondTo.ALLOWLIST -> stringRes(Res.string.kc_agent_respond_allowlist)
        RespondTo.ANYONE -> stringRes(Res.string.kc_agent_respond_anyone)
    }

/** A persona's `respond_to` is a free string; the known values read like the managed-agent enum. */
@Composable
private fun respondToLabel(code: String): String =
    when (code) {
        "owner-only", "owner_only" -> stringRes(Res.string.kc_agent_respond_owner_only)
        "allowlist" -> stringRes(Res.string.kc_agent_respond_allowlist)
        "anyone" -> stringRes(Res.string.kc_agent_respond_anyone)
        else -> code
    }

@Composable
private fun artifactOpLabel(op: ArtifactOp?): String? =
    when (op) {
        ArtifactOp.CREATE -> stringRes(Res.string.kc_agent_artifact_op_create)
        ArtifactOp.UPDATE -> stringRes(Res.string.kc_agent_artifact_op_update)
        ArtifactOp.MOVE -> stringRes(Res.string.kc_agent_artifact_op_move)
        ArtifactOp.DELETE -> stringRes(Res.string.kc_agent_artifact_op_delete)
        ArtifactOp.RESTORE -> stringRes(Res.string.kc_agent_artifact_op_restore)
        null -> null
    }

@Composable
private fun auditActionLabel(action: AuditAction?): String =
    when (action) {
        AuditAction.EVENT_CREATED -> stringRes(Res.string.kc_agent_audit_event_created)
        AuditAction.EVENT_DELETED -> stringRes(Res.string.kc_agent_audit_event_deleted)
        AuditAction.CHANNEL_CREATED -> stringRes(Res.string.kc_agent_audit_channel_created)
        AuditAction.CHANNEL_UPDATED -> stringRes(Res.string.kc_agent_audit_channel_updated)
        AuditAction.CHANNEL_DELETED -> stringRes(Res.string.kc_agent_audit_channel_deleted)
        AuditAction.MEMBER_ADDED -> stringRes(Res.string.kc_agent_audit_member_added)
        AuditAction.MEMBER_REMOVED -> stringRes(Res.string.kc_agent_audit_member_removed)
        AuditAction.AUTH_SUCCESS -> stringRes(Res.string.kc_agent_audit_auth_success)
        AuditAction.AUTH_FAILURE -> stringRes(Res.string.kc_agent_audit_auth_failure)
        AuditAction.RATE_LIMIT_EXCEEDED -> stringRes(Res.string.kc_agent_audit_rate_limit)
        AuditAction.MEDIA_UPLOADED -> stringRes(Res.string.kc_agent_audit_media_uploaded)
        AuditAction.UNKNOWN, null -> stringRes(Res.string.kc_agent_audit_unknown)
    }
