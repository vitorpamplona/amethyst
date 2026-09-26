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

import com.vitorpamplona.quartz.mls.tree.Capabilities
import com.vitorpamplona.quartz.mls.tree.Extension
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * RFC 9420 §13.4: an extension in use by the group MUST be supported by all members. A GroupContextExtensions
 * proposal can install a type this implementation doesn't know, as long as every member's leaf advertises it.
 */
class GroupContextExtensionsSupportTest {
    // RFC 9420 §17.3 reserves 0xF000-0xFFFF for private use.
    private val customType = 0xF0A1
    private val customExtension = Extension(customType, byteArrayOf(1, 2, 3))

    // The Marmot leaf capabilities, with and without the custom type.
    private val plain = Capabilities(extensions = listOf(0xF2EE), proposals = listOf(0x000A))
    private val withCustom = plain.copy(extensions = plain.extensions + customType)

    @Test
    fun anExtensionEveryMemberSupportsIsInstalled() {
        val (alice, bob) = twoMemberGroup(bobCapabilities = withCustom)

        alice.proposeGroupContextExtensions(alice.groupContextExtensionsSnapshot() + customExtension)
        val commit = alice.commit()
        bob.processFramedCommit(commit.framedCommitBytes)

        assertEquals(alice.epoch, bob.epoch)
        for (group in listOf(alice, bob)) {
            val installed = group.groupContextExtensionsSnapshot().single { it.extensionType == customType }
            assertContentEquals(customExtension.extensionData, installed.extensionData)
        }
    }

    @Test
    fun anExtensionSomeMemberDoesNotSupportIsRejected() {
        val (alice, _) = twoMemberGroup(bobCapabilities = plain)

        alice.proposeGroupContextExtensions(alice.groupContextExtensionsSnapshot() + customExtension)
        assertFails { alice.commit() }
    }

    private fun twoMemberGroup(bobCapabilities: Capabilities): Pair<MlsGroup, MlsGroup> {
        val alice = MlsGroup.create("11".repeat(32).hexToByteArray(), capabilities = withCustom)
        val bobBundle = alice.createKeyPackage("22".repeat(32).hexToByteArray(), ByteArray(0), capabilities = bobCapabilities)
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)
        return alice to bob
    }
}
