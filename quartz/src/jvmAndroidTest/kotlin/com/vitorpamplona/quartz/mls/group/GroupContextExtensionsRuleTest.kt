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
package com.vitorpamplona.quartz.mls.group

import com.vitorpamplona.quartz.mls.codec.TlsWriter
import com.vitorpamplona.quartz.mls.tree.Capabilities
import com.vitorpamplona.quartz.mls.tree.Credential
import com.vitorpamplona.quartz.mls.tree.Extension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * RFC 9420 §12.1.7, which the engine used to get wrong in both directions.
 *
 * It rejected any GroupContextExtensions proposal carrying an extension type
 * outside a hardcoded list — a rule the RFC does not have, and one that made
 * a whole class of valid group un-joinable. Meanwhile the rule the RFC DOES
 * state, that the resulting group must not require capabilities some member
 * lacks, was not checked at all.
 *
 * Found while building the cordn binding: cordn's `0xC04D` metadata extension
 * is exactly the kind of application extension the old check refused.
 */
class GroupContextExtensionsRuleTest {
    private val alice = "alice".encodeToByteArray()

    /** An arbitrary application extension type, in the private-use range. */
    private val appExtension = 0xC04D

    private fun requiredCapabilities(extensions: List<Int>): Extension {
        val writer = TlsWriter()
        val exts = TlsWriter()
        extensions.forEach { exts.putUint16(it) }
        writer.putOpaqueVarInt(exts.toByteArray())
        writer.putOpaqueVarInt(ByteArray(0))
        val creds = TlsWriter()
        creds.putUint16(Credential.CREDENTIAL_TYPE_BASIC)
        writer.putOpaqueVarInt(creds.toByteArray())
        return Extension(MlsGroup.REQUIRED_CAPABILITIES_EXTENSION_TYPE, writer.toByteArray())
    }

    @Test
    fun anUnknownExtensionTypeIsInstalledWhenEveryMemberAdvertisesIt() {
        // "We recognise the type" is not the rule. RFC 9420 §13.4 makes the
        // test membership: "an extension in use by the group MUST be supported
        // by all members of the group", read off leaf capabilities. So a type
        // this code has never heard of installs fine, provided the leaves say
        // they support it.
        //
        // An earlier version of this test asserted the opposite - that an
        // unknown type installs unconditionally - on the reading that §12.1.7
        // states the only rule. §12.1.7 does state the only rule *it* has;
        // §13.4 is where the membership requirement lives.
        val group = MlsGroup.create(alice, capabilities = Capabilities(extensions = listOf(appExtension)))
        group.proposeGroupContextExtensions(listOf(Extension(appExtension, byteArrayOf(1, 2, 3))))
        group.commit()

        assertTrue(
            group.extensions.any { it.extensionType == appExtension },
            "the extension must be installed, not rejected",
        )
    }

    @Test
    fun anExtensionThisMemberDoesNotAdvertiseIsRejected() {
        // The same proposal, from a leaf that never claimed the capability.
        // Accepting it would put the group in a state §13.4 forbids.
        val group = MlsGroup.create(alice)
        group.proposeGroupContextExtensions(listOf(Extension(appExtension, byteArrayOf(1, 2, 3))))

        val error = assertFailsWith<IllegalArgumentException> { group.commit() }
        assertTrue(
            error.message?.contains("Unsupported extension type") == true,
            "unexpected message: ${error.message}",
        )
    }

    @Test
    fun aRequiredCapabilityNoMemberAdvertisesIsRejected() {
        // The rule the RFC actually states. Accepting this splits the group:
        // every peer that checks refuses the commit, every peer that does not
        // applies it, and the two halves diverge at the next epoch.
        val group = MlsGroup.create(alice, capabilities = Capabilities())
        group.proposeGroupContextExtensions(listOf(requiredCapabilities(listOf(appExtension))))

        val error = assertFailsWith<IllegalStateException> { group.commit() }
        assertTrue(
            error.message?.contains("required_capabilities") == true,
            "must fail on the capability rule specifically: got '${error.message}'",
        )
    }

    @Test
    fun aRequiredCapabilityEveryMemberAdvertisesIsAccepted() {
        val group = MlsGroup.create(alice, capabilities = Capabilities(extensions = listOf(appExtension)))
        val epochBefore = group.epoch

        group.proposeGroupContextExtensions(listOf(requiredCapabilities(listOf(appExtension))))
        group.commit()

        assertEquals(epochBefore + 1, group.epoch)
    }

    @Test
    fun theReplacementIsWholesaleNotAMerge() {
        // §12.1.7: "This is a wholesale replacement, not a merge. An extension
        // is only carried over if the sender of the proposal includes it."
        //
        // Both types are advertised so that §13.4 is satisfied throughout and
        // this test fails only on the replacement rule it is about.
        val group = MlsGroup.create(alice, capabilities = Capabilities(extensions = listOf(appExtension, 0xC04E)))
        group.proposeGroupContextExtensions(listOf(Extension(appExtension, byteArrayOf(1))))
        group.commit()

        group.proposeGroupContextExtensions(listOf(Extension(0xC04E, byteArrayOf(2))))
        group.commit()

        assertTrue(group.extensions.none { it.extensionType == appExtension }, "the old extension is gone")
        assertTrue(group.extensions.any { it.extensionType == 0xC04E })
    }
}
