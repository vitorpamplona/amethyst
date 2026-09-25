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
package com.vitorpamplona.quartz.cordn.groups

import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.mls.codec.TlsWriter
import com.vitorpamplona.quartz.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.mls.components.ComponentData
import com.vitorpamplona.quartz.mls.components.ComponentsList
import com.vitorpamplona.quartz.mls.group.GroupView
import com.vitorpamplona.quartz.mls.group.MlsExporterLabel
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.group.MlsGroupPolicy
import com.vitorpamplona.quartz.mls.group.PendingProposal
import com.vitorpamplona.quartz.mls.messages.Proposal
import com.vitorpamplona.quartz.mls.tree.Capabilities
import com.vitorpamplona.quartz.mls.tree.Credential
import com.vitorpamplona.quartz.mls.tree.Extension
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * cordn's profile, as an [MlsGroupPolicy].
 *
 * Pass it wherever an [MlsGroup] is created, joined or restored and the group
 * gets cordn's capabilities, its `required_capabilities`, its extension
 * registry and its payload exporter in one argument:
 *
 * ```kotlin
 * val group = MlsGroup.create(
 *     CordnCredential.of(myPubKeyHex).identity,
 *     policy = CordnGroupPolicy,
 *     initialExtensions = listOf(metadata.toExtension()),
 * )
 * ```
 *
 * ## Admin authorization
 *
 * `spec/01.md` §5.3 leaves the meaning of `admin_pubkeys` to the application
 * and says only that an EMPTY list is egalitarian mode. The reference client
 * fills that in, and because MLS has no server that can police membership,
 * it does so on both sides of every commit: it refuses to build an add, a
 * remove or a metadata change unless the local member is an admin, and it
 * rejects an inbound commit carrying one of those from a member who is not.
 *
 * [authorizeCommit] implements exactly that rule, and the engine calls it in
 * both directions, so one check covers both. Matching it is not cosmetic —
 * accepting a commit the rest of the group rejects forks the epoch, and MLS
 * does not recover from a fork. Being *stricter* than the reference would fork
 * it the other way, which is why the gate is the reference's three proposal
 * types and nothing else: an Update, a SelfRemove or a PSK from any member
 * stays allowed, so nobody can be trapped in a group they may not leave.
 *
 * Egalitarian mode is a permanent choice, not a bootstrap window: an empty
 * list means every member administers, so the gate opens rather than closes.
 */
object CordnGroupPolicy : MlsGroupPolicy {
    /**
     * `spec/01.md` §7: a client claiming support for `cordn_group_metadata`
     * MUST advertise `0xC04D`, and a group using the extension must not add a
     * member who does not.
     *
     * Nothing else is listed. RFC 9420 §7.2 forbids advertising DEFAULT types,
     * and cordn requires no non-default proposal — in particular no
     * `self_remove`, which Marmot requires and cordn does not.
     */
    override val defaultLeafCapabilities: Capabilities
        get() = Capabilities(extensions = listOf(CordnGroupMetadata.EXTENSION_TYPE))

    /**
     * None, deliberately.
     *
     * `spec/01.md` §6 requires a member of a group using `0xC04D` to advertise
     * it, and the reference client does — so RFC 9420 §13.4 can be enforced
     * here from capabilities alone, with no exemption to weaken it. cordn has
     * no deployed groups predating that rule to be kind to.
     */
    override val knownExtensionTypes: Set<Int> = emptySet()

    /**
     * None.
     *
     * `spec/01.md` §6 says a group MAY omit the metadata extension entirely
     * and stay valid, so requiring it at epoch 0 would refuse groups the spec
     * allows. The reference client agrees: it advertises `0xC04D` in
     * capabilities and installs no `required_capabilities`
     * (`packages/cli/src/utils/mlsIdentity.ts:createKeyPackageCapabilities`).
     *
     * A caller that wants the stricter group passes
     * [requiredCapabilitiesExtension] explicitly.
     */
    override val defaultRequiredCapabilities: Extension? get() = null

    /**
     * `MLS-Exporter("cordn", "group-payload", 32)` — `spec/03.md` §4.
     *
     * Identical machinery to Marmot's seal and a different key by one label,
     * which is the whole reason the two never decrypt each other's traffic.
     */
    override val commitExporter: MlsExporterLabel get() = PAYLOAD_EXPORTER

    /** `MLS-Exporter("cordn", "group-payload", 32)`, for application messages too. */
    val PAYLOAD_EXPORTER = MlsExporterLabel("cordn", "group-payload".encodeToByteArray(), 32)

    /**
     * `MLS-Exporter("cordn", "encrypted-media", 32)`.
     *
     * `spec/applications/encrypted-media.md` §3.1: the media key is derived
     * from the epoch's exporter secret and is never transmitted. A separate
     * context from [PAYLOAD_EXPORTER] because the two layers are independent —
     * the spec is explicit that they "use distinct exporter contexts and do not
     * interact".
     */
    val MEDIA_EXPORTER = MlsExporterLabel("cordn", "encrypted-media".encodeToByteArray(), 32)

    /**
     * An explicit `required_capabilities` naming `0xC04D`, for a group that
     * wants every member to be able to read its metadata.
     *
     * Optional by design (see [defaultRequiredCapabilities]). Installing it
     * makes the group refuse any leaf that does not advertise `0xC04D` —
     * including, today, every Marmot KeyPackage.
     */
    fun requiredCapabilitiesExtension(): Extension {
        val writer = TlsWriter()
        val exts = TlsWriter()
        exts.putUint16(CordnGroupMetadata.EXTENSION_TYPE)
        writer.putOpaqueVarInt(exts.toByteArray())
        writer.putOpaqueVarInt(ByteArray(0))
        val creds = TlsWriter()
        creds.putUint16(Credential.CREDENTIAL_TYPE_BASIC)
        writer.putOpaqueVarInt(creds.toByteArray())
        return Extension(MlsGroup.REQUIRED_CAPABILITIES_EXTENSION_TYPE, writer.toByteArray())
    }

    /**
     * The KeyPackage-level `app_data_dictionary` marking a last-resort
     * KeyPackage.
     *
     * `spec/00.md` §11 defers to "the last-resort extension defined by MLS",
     * and the reference implementation reads that as the extensions-draft
     * component `0x0004` inside `app_data_dictionary` (`0x0006`), with empty
     * data (`packages/core/src/lastResortKeyPackage.ts`). Marmot's MIP-era
     * profile instead sets bare extension `0x000A`, so the two disagree about
     * how a last-resort KeyPackage is even recognised — our
     * `MlsKeyPackage.isLastResort()` accepts both carriers, which is why it
     * reads a cordn KeyPackage correctly.
     */
    fun lastResortExtension(): Extension = AppDataDictionary(listOf(ComponentData(ComponentsList.LAST_RESORT_KEY_PACKAGE_ID, ByteArray(0)))).toExtension()

    /**
     * Leaf capabilities for a last-resort KeyPackage: [defaultLeafCapabilities]
     * plus `app_data_dictionary`, since the leaf must say it understands the
     * carrier it is using.
     */
    fun lastResortLeafCapabilities(): Capabilities =
        Capabilities(
            extensions = listOf(CordnGroupMetadata.EXTENSION_TYPE, AppDataDictionary.EXTENSION_TYPE),
        )

    /**
     * Refuses a commit that adds, removes or rewrites metadata on behalf of a
     * member `admin_pubkeys` does not name. See the class KDoc for why this
     * matches the reference client exactly rather than approximately.
     *
     * [committerLeafIndex] is the committer, which is who the reference checks
     * for a commit. A proposal one member sent by reference and an admin then
     * committed is therefore allowed — on both implementations, the admin who
     * committed it is the one answering for it.
     */
    override fun authorizeCommit(
        group: GroupView,
        proposals: List<PendingProposal>,
        committerLeafIndex: Int,
    ) {
        if (proposals.isEmpty()) return

        if (adminIdentitiesIn(group.extensions).isEmpty()) return
        if (proposals.none { it.proposal.needsAdmin() }) return

        check(isAdminLeaf(group, committerLeafIndex)) {
            "cordn: only admin_pubkeys may add, remove or rewrite group metadata; leaf " +
                "$committerLeafIndex is not an admin"
        }
    }

    /**
     * The admin set [extensions] names, or empty for egalitarian.
     *
     * A metadata extension too malformed to decode reads as egalitarian rather
     * than taking the group down with it — the same call Marmot's policy makes
     * about its own components. It is not a way in: installing metadata takes a
     * GroupContextExtensions commit, which this gate already covers, so nobody
     * outside the admin set can put a broken extension there in the first
     * place. A group whose metadata was malformed from creation was never
     * administrable by anyone.
     */
    fun adminIdentitiesIn(extensions: List<Extension>): Set<HexKey> =
        runCatching { CordnGroupMetadata.fromExtensions(extensions)?.adminPubkeys }
            .getOrNull()
            .orEmpty()
            .toSet()

    /** True if the account [pubKey] may add, remove or rewrite metadata here. */
    fun isAdmin(
        group: GroupView,
        pubKey: HexKey,
    ): Boolean {
        val admins = adminIdentitiesIn(group.extensions)
        return admins.isEmpty() || pubKey in admins
    }

    /**
     * True if the member at [leafIndex] may.
     *
     * The comparison happens in credential-bytes space, not account space, and
     * deliberately: [GroupView.memberIdentityHex] hexes whatever the credential
     * holds, and a cordn credential holds the account key as its 64 ASCII hex
     * characters (see [CordnCredential]), so that accessor returns 128
     * characters which are never a pubkey. Marmot stores raw bytes and can
     * compare directly; comparing an account pubkey against this without
     * converting would match nothing and reject every commit in a group that
     * names admins. Mapping the admin list forwards rather than decoding the
     * leaf backwards keeps untrusted bytes out of the decoder entirely.
     */
    fun isAdminLeaf(
        group: GroupView,
        leafIndex: Int,
    ): Boolean {
        val admins = adminIdentitiesIn(group.extensions)
        if (admins.isEmpty()) return true

        val credentialHex = group.memberIdentityHex(leafIndex) ?: return false
        return credentialHex in admins.mapTo(mutableSetOf()) { it.encodeToByteArray().toHexKey() }
    }

    /** True if the local member may. */
    fun isLocalAdmin(group: GroupView): Boolean = isAdminLeaf(group, group.myLeafIndex)

    /**
     * The three proposal types the reference client gates, and only those:
     * `add`, `remove` and `group_context_extensions`, matching its
     * `addMember` / `removeMember` / `updateGroupMetadata`.
     */
    private fun Proposal.needsAdmin(): Boolean = this is Proposal.Add || this is Proposal.Remove || this is Proposal.GroupContextExtensions
}
