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
package com.vitorpamplona.amethyst.commons.model.accountLifecycle

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val NAMECOIN_MAX_VALUE_BYTES = 520
const val ACCOUNT_LIFECYCLE_MAX_SINCE = 9007199254740991L

@Serializable
enum class LifecycleSource {
    @SerialName("namecoin")
    NAMECOIN,
}

@Serializable
enum class AccountLifecycleState(
    val wire: String,
) {
    @SerialName("retired")
    RETIRED("retired"),

    @SerialName("compromised")
    COMPROMISED("compromised"),
}

@Serializable
enum class AccountLifecycleStatus {
    @SerialName("current")
    CURRENT,

    @SerialName("provisional")
    PROVISIONAL,

    @SerialName("no_declaration")
    NO_DECLARATION,

    @SerialName("invalid")
    INVALID,

    @SerialName("unsupported_state")
    UNSUPPORTED_STATE,

    @SerialName("unsupported_traversal")
    UNSUPPORTED_TRAVERSAL,

    @SerialName("unavailable")
    UNAVAILABLE,

    @SerialName("stale")
    STALE,
}

@Serializable
enum class AccountLifecycleScope {
    @SerialName("account")
    ACCOUNT,

    @SerialName("direct_record")
    DIRECT_RECORD,

    @SerialName("conflicting")
    CONFLICTING,

    @SerialName("none")
    NONE,
}

@Serializable
enum class SuccessorConfirmation {
    @SerialName("absent")
    ABSENT,

    @SerialName("unconfirmed")
    UNCONFIRMED,

    @SerialName("reciprocal_alias_confirmed")
    RECIPROCAL_ALIAS_CONFIRMED,

    @SerialName("withdrawn")
    WITHDRAWN,

    @SerialName("invalid")
    INVALID,

    @SerialName("conflicting")
    CONFLICTING,

    @SerialName("stale")
    STALE,

    @SerialName("not_applicable")
    NOT_APPLICABLE,
}

@Serializable
enum class BaseNip05Status {
    @SerialName("verified")
    VERIFIED,

    @SerialName("unverified")
    UNVERIFIED,

    @SerialName("unavailable")
    UNAVAILABLE,

    @SerialName("unsupported")
    UNSUPPORTED,

    @SerialName("not_applicable")
    NOT_APPLICABLE,
}

@Serializable
enum class LifecycleObservation {
    CURRENT,
    PROVISIONAL,
    REREGISTERED,
}

@Serializable
data class LifecycleProvenance(
    @SerialName("selected_name") val selectedName: String,
    val dependencies: List<String>,
    val txid: String? = null,
    val height: Int? = null,
    val expiresIn: Int? = null,
    val configuredBackend: String? = null,
)

@Serializable
data class AccountLifecycleResult(
    val status: AccountLifecycleStatus,
    val scope: AccountLifecycleScope = AccountLifecycleScope.NONE,
    val state: AccountLifecycleState? = null,
    val since: Long? = null,
    @SerialName("successor") val successorPubkey: String? = null,
    @SerialName("successor_confirmation") val successorConfirmation: SuccessorConfirmation = SuccessorConfirmation.NOT_APPLICABLE,
    @SerialName("confirmation_aliases") val confirmationAliases: List<String> = emptyList(),
    @SerialName("base_nip05") val baseNip05: BaseNip05Status = BaseNip05Status.UNAVAILABLE,
    val diagnostics: List<String> = emptyList(),
    @SerialName("automatic_actions") val automaticActions: List<String> = emptyList(),
    val provenance: LifecycleProvenance? = null,
) {
    val isAccountWarning: Boolean
        get() = state != null && (scope == AccountLifecycleScope.ACCOUNT || scope == AccountLifecycleScope.CONFLICTING)

    val isCompromiseWarning: Boolean
        get() = state == AccountLifecycleState.COMPROMISED && isAccountWarning
}

@Serializable
data class SignedLifecycleEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int = MetadataEvent.KIND,
    val tags: List<List<String>> = emptyList(),
    val content: String,
    val sig: String,
) {
    fun toEvent(): MetadataEvent =
        MetadataEvent(
            id = id,
            pubKey = pubkey,
            createdAt = createdAt,
            tags = tags.map { it.toTypedArray() }.toTypedArray(),
            content = content,
            sig = sig,
        )

    companion object {
        fun fromEvent(event: Event): SignedLifecycleEvent =
            SignedLifecycleEvent(
                id = event.id,
                pubkey = event.pubKey,
                createdAt = event.createdAt,
                kind = event.kind,
                tags = event.tags.map { it.toList() },
                content = event.content,
                sig = event.sig,
            )
    }
}

@Serializable
data class LifecycleBacklink(
    val localPart: String,
    val event: SignedLifecycleEvent,
    val current: Boolean,
)

@Serializable
enum class LifecycleNameNamespace {
    D,
    ID,
}

@Serializable
data class NamecoinNameContext(
    val namespace: LifecycleNameNamespace,
    val name: String,
    val host: String?,
    val localPart: String?,
    val mapPath: List<String> = emptyList(),
)

data class AccountLifecycleEvaluation(
    val result: AccountLifecycleResult,
    val context: NamecoinNameContext,
    val subjectPubkey: String,
    val selectedRecord: String?,
    val backlinks: List<LifecycleBacklink> = emptyList(),
)

@Serializable
data class LifecycleDiscoveryResult(
    val candidateName: String? = null,
    val signedAssociation: Boolean = false,
    val warning: String,
)

enum class LifecycleEventClassification {
    UNCLASSIFIED,
}

fun accountLifecycleEventClassification(
    @Suppress("UNUSED_PARAMETER") eventCreatedAt: Long,
    @Suppress("UNUSED_PARAMETER") since: Long?,
): LifecycleEventClassification = LifecycleEventClassification.UNCLASSIFIED

fun publisherAcceptsValueSize(value: String): Boolean = value.encodeToByteArray().size <= NAMECOIN_MAX_VALUE_BYTES
