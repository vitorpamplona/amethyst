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
import com.vitorpamplona.quartz.mls.tree.Capabilities
import com.vitorpamplona.quartz.mls.tree.Credential
import com.vitorpamplona.quartz.mls.tree.Extension

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
 * ## Why there is no authorization hook
 *
 * cordn has no equivalent of Marmot's MIP-03. `spec/01.md` §5.3 defines
 * admins as *presentation* metadata — who the application shows a settings
 * button to — and neither the spec nor the reference coordinator restricts who
 * may commit. Empty `admin_pubkeys` is egalitarian mode, a deliberate and
 * permanent choice, not a bootstrap window.
 *
 * So [authorizeCommit] is left at the default. That is a real statement about
 * cordn, not an omission: **any member of a cordn group can commit anything
 * MLS itself permits, including removing other members.** A UI that presents a
 * cordn group's admin list as an access-control boundary would be lying. If
 * `spec/01.md` later gives admins enforcement teeth, that rule belongs here,
 * where an admin change can be checked against the post-commit extensions.
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
     * Unused today; kept so the shape of a future rule is obvious.
     *
     * If cordn ever gives `admin_pubkeys` enforcement teeth, the check belongs
     * here — [group] carries the pre-commit extensions and [proposals] the
     * replacement, which is exactly what deciding an admin change needs.
     */
    override fun authorizeCommit(
        group: GroupView,
        proposals: List<PendingProposal>,
        committerLeafIndex: Int,
    ) = Unit
}
