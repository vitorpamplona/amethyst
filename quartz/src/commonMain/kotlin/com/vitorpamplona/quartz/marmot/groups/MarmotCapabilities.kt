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
package com.vitorpamplona.quartz.marmot.groups

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRoles
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.mls.codec.TlsWriter
import com.vitorpamplona.quartz.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.mls.tree.Capabilities
import com.vitorpamplona.quartz.mls.tree.Credential
import com.vitorpamplona.quartz.mls.tree.Extension

/**
 * The MLS capability sets that identify a group as Marmot's.
 *
 * These used to be defaults baked into `MlsGroup.create`, which is why a
 * plain RFC 9420 group could not be created at all: every group came out
 * requiring `marmot_group_data`. They are unchanged, only relocated, and the
 * engine now reaches them through [MarmotGroupPolicy].
 *
 * Marmot has two profiles and a client must be able to read either, so both
 * sets live here:
 *
 * - **MIP-era** — `0xF2EE` carries all group state, `self_remove` is required.
 * - **current** — `app_data_dictionary` (`0x0006`) carries it as components,
 *   with `app_data_update` (`0x0008`) to change them.
 */
object MarmotCapabilities {
    /** Marmot Group Data Extension type (MIP-01). */
    const val MARMOT_GROUP_DATA_EXTENSION_TYPE = 0xF2EE

    /**
     * Default MLS leaf Capabilities that advertise support for Marmot's
     * required extensions and proposals so new members can join a group
     * whose `required_capabilities` lists them.
     */
    fun mipLeaf(): Capabilities =
        Capabilities(
            extensions = listOf(MARMOT_GROUP_DATA_EXTENSION_TYPE),
            proposals = listOf(MlsGroup.SELF_REMOVE_PROPOSAL_TYPE),
        )

    /**
     * The MIP-era leaf set as a published KeyPackage advertises it.
     *
     * Same as [mipLeaf] plus `0x000A` as an EXTENSION, which OpenMLS validation
     * requires on a last-resort KeyPackage. Note `0x000A` appears in both lists
     * meaning different things: as an extension it is `last_resort`, as a
     * proposal it is `self_remove`.
     *
     * The order is load-bearing and must not be tidied: these bytes go into
     * published KeyPackages, and `KeyPackageBundleStore`'s v4 snapshot format
     * is defined by them.
     */
    fun mipKeyPackageLeaf(): Capabilities =
        Capabilities(
            extensions = listOf(MlsKeyPackage.LAST_RESORT_EXTENSION_TYPE, MARMOT_GROUP_DATA_EXTENSION_TYPE),
            proposals = listOf(MlsGroup.SELF_REMOVE_PROPOSAL_TYPE),
        )

    /**
     * Build an MLS `required_capabilities` extension that marks Marmot's
     * mandatory interop set as required for all members (RFC 9420 §7.2):
     *   extensions  = [marmot_group_data (0xF2EE)]
     *   proposals   = [self_remove (0x000A)]
     *   credentials = [Basic (0x0001)]
     */
    fun mipRequired(): Extension =
        requiredCapabilities(
            extensions = listOf(MARMOT_GROUP_DATA_EXTENSION_TYPE),
            proposals = listOf(MlsGroup.SELF_REMOVE_PROPOSAL_TYPE),
        )

    /**
     * Leaf capabilities for the current profile.
     *
     * RFC 9420 §7.2 forbids advertising DEFAULT extension types, so only
     * the draft `app_data_dictionary` extension and the `app_data_update`
     * proposal appear — `required_capabilities` support is implicit.
     *
     * The legacy `0xF2EE` group-data extension is advertised alongside
     * them, and that is not a hedge. A capability says "this client can
     * handle it", not "this group uses it", and a group that REQUIRES
     * `0xF2EE` refuses to add a leaf that does not advertise it. Without
     * this line a current-profile KeyPackage would be un-addable to every
     * legacy group that already exists — the exact mirror of the interop
     * failure the current profile was adopted to fix.
     *
     * `0xF2D1` is the agent-text-stream RECEIVE role, for the same reason:
     * a group carrying component `0x8006` with `required_member_roles`
     * naming `receive` refuses a leaf that does not advertise it. The
     * reference client puts exactly that policy into EVERY group it
     * creates, so without this line an Amethyst KeyPackage cannot be
     * invited into one at all.
     *
     * We stop at receive. `send` and `fanout` are not here because we do
     * not originate previews from the app, and a capability is a standing
     * promise rather than a hedge.
     */
    fun currentProfileLeaf(): Capabilities =
        Capabilities(
            extensions =
                listOf(
                    AppDataDictionary.EXTENSION_TYPE,
                    MarmotGroupData.EXTENSION_ID_INT,
                    AgentTextStreamRoles.RECEIVE_CAPABILITY,
                ),
            proposals = listOf(MlsGroup.APP_DATA_UPDATE_PROPOSAL_TYPE, MlsGroup.SELF_REMOVE_PROPOSAL_TYPE),
        )

    /**
     * `required_capabilities` for a new current-profile group: extension
     * `0x0006` and proposal `0x0008`.
     *
     * The Marmot components a group requires are negotiated in the
     * upstream `app_components` component INSIDE the dictionary, not here —
     * MLS `RequiredCapabilities` carries only MLS-level primitives.
     */
    fun currentProfileRequired(): Extension =
        requiredCapabilities(
            extensions = listOf(AppDataDictionary.EXTENSION_TYPE),
            proposals = listOf(MlsGroup.APP_DATA_UPDATE_PROPOSAL_TYPE),
        )

    /** Encodes an RFC 9420 §7.2 `required_capabilities` extension over Basic credentials. */
    private fun requiredCapabilities(
        extensions: List<Int>,
        proposals: List<Int>,
    ): Extension {
        val writer = TlsWriter()
        // extensions<V>: uint16 each
        val exts = TlsWriter()
        extensions.forEach { exts.putUint16(it) }
        writer.putOpaqueVarInt(exts.toByteArray())
        // proposals<V>: uint16 each
        val props = TlsWriter()
        proposals.forEach { props.putUint16(it) }
        writer.putOpaqueVarInt(props.toByteArray())
        // credentials<V>: uint16 each
        val creds = TlsWriter()
        creds.putUint16(Credential.CREDENTIAL_TYPE_BASIC)
        writer.putOpaqueVarInt(creds.toByteArray())
        return Extension(MlsGroup.REQUIRED_CAPABILITIES_EXTENSION_TYPE, writer.toByteArray())
    }
}
