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
package com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.openagents.core.ArtifactRef
import com.vitorpamplona.quartz.openagents.core.DefinitionRef
import com.vitorpamplona.quartz.openagents.core.OaJson
import com.vitorpamplona.quartz.openagents.core.QualifiedId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The body of a kind-30180 capability head, which is exactly one of two shapes:
 *
 * - a **definition**: `id`, `profile`, `summary`, `input`, `output`, `effects`, `minimum`,
 *   `support` and `binding_contract`, published inline;
 * - a **head**: only `definition`, a [DefinitionRef] to bytes published elsewhere (a release).
 *
 * A definition describes operations and grants nothing: no field here authorizes a call, and
 * every one of them is the publisher's claim.
 */
@Immutable
@Serializable
data class CapabilityDefinition(
    val v: Int? = OaJson.VERSION,
    val requires: List<String>? = emptyList(),
    val id: String? = null,
    val profile: String? = null,
    val summary: String? = null,
    val input: ArtifactRef? = null,
    val output: ArtifactRef? = null,
    val effects: CapabilityEffects? = null,
    val minimum: JsonObject? = null,
    val support: JsonObject? = null,
    @SerialName("binding_contract")
    val bindingContract: BindingContract? = null,
    val definition: DefinitionRef? = null,
    val meta: JsonObject? = null,
) {
    /** A head names its definition instead of carrying it. */
    fun isHead() = definition != null && id == null

    /** The qualified id this capability is known by, inline or referenced. */
    fun qualifiedId(): QualifiedId? = QualifiedId.parse(id ?: definition?.id)

    /** Every worker key this capability says serves it, each with the relays it names for that worker. */
    fun workers(): List<Pair<String, List<String>>> {
        val contract = bindingContract ?: return emptyList()
        val direct =
            contract.remote
                ?.worker
                ?.let { listOf(it to contract.remote.relays.orEmpty()) }
                .orEmpty()
        val lanes =
            contract.service
                ?.lanes
                .orEmpty()
                .mapNotNull { lane -> lane.worker?.let { it to lane.relays.orEmpty() } }
        return direct + lanes
    }

    /**
     * The transports the `oa:transport:` tags must name: one per distinct lane for a service; the
     * profile itself for `native` and `plugin`, which run in the host; the contract's for the rest.
     */
    fun transports(): List<String> =
        when (profile) {
            PROFILE_SERVICE ->
                bindingContract
                    ?.service
                    ?.lanes
                    .orEmpty()
                    .mapNotNull { it.transport }
                    .distinct()
                    .sorted()
            PROFILE_NATIVE, PROFILE_PLUGIN -> listOfNotNull(profile)
            else -> listOfNotNull(bindingContract?.transport)
        }

    fun toJson(): String = OaJson.json.encodeToString(serializer(), this)

    companion object {
        const val PROFILE_NATIVE = "native"
        const val PROFILE_EXECUTOR = "executor"
        const val PROFILE_PLUGIN = "plugin"
        const val PROFILE_ADAPTER = "adapter"
        const val PROFILE_SERVICE = "service"

        fun parse(content: String): CapabilityDefinition = OaJson.json.decodeFromString(serializer(), content)
    }
}

/** The shared contracts' effects object: scopes for reads/writes/network, flags for the rest. */
@Immutable
@Serializable
data class CapabilityEffects(
    val reads: List<String> = emptyList(),
    val writes: List<String> = emptyList(),
    val network: List<String> = emptyList(),
    val process: Boolean = false,
    val delegates: Boolean = false,
    val spend: Boolean = false,
)

/**
 * How a host binds the definition. Which fields appear depends on the profile: `native` names an
 * `operation`; `plugin` a `plugin` id and its `abi`s; `executor` and `adapter` a `transport`, an
 * optional `remote`, and the `operations` (adapter) or `task`/`context`/`isolation` (executor);
 * `service` its `service` contract. A profile-specific extension (`x402`, …) stays raw JSON.
 */
@Immutable
@Serializable
data class BindingContract(
    @SerialName("interface")
    val interfaceId: String? = null,
    val transport: String? = null,
    val operation: String? = null,
    val operations: List<String>? = null,
    val remote: RemoteContract? = null,
    val plugin: String? = null,
    val abi: List<String>? = null,
    val task: ArtifactRef? = null,
    val context: ArtifactRef? = null,
    val isolation: List<String>? = null,
    val service: ServiceContract? = null,
    val x402: JsonObject? = null,
)

/** Where an executor or adapter runs: a Nostr worker and its relays, or a public endpoint. */
@Immutable
@Serializable
data class RemoteContract(
    val worker: String? = null,
    val relays: List<String>? = null,
    val endpoint: String? = null,
    val identity: ArtifactRef? = null,
)

/** A decision service's contract: the lanes it is reached on and the doors it serves. */
@Immutable
@Serializable
data class ServiceContract(
    val lanes: List<ServiceLane>? = null,
    val doors: List<ServiceDoor>? = null,
    val limits: JsonObject? = null,
    val versions: JsonObject? = null,
)

/** One way to reach a service: an `http` endpoint, or a `nostr-cj` worker on its relays. */
@Immutable
@Serializable
data class ServiceLane(
    val transport: String? = null,
    val endpoint: String? = null,
    val call: String? = null,
    val models: String? = null,
    val worker: String? = null,
    val relays: List<String>? = null,
    @SerialName("request_kind")
    val requestKind: Int? = null,
    @SerialName("result_kind")
    val resultKind: Int? = null,
    @SerialName("feedback_kind")
    val feedbackKind: Int? = null,
)

/** A named model an unauthenticated caller may ask a service for. */
@Immutable
@Serializable
data class ServiceDoor(
    val name: String? = null,
    val model: String? = null,
    @SerialName("artifact_signature")
    val artifactSignature: String? = null,
    val version: String? = null,
)
