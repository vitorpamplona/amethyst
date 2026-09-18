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
package com.vitorpamplona.cordn.interop

import com.vitorpamplona.cordn.groups.CordnCredential
import com.vitorpamplona.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.cordn.spec02Envelopes.CordnApplicationMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec03Payloads.SealedPayload
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Produces the artifacts Staircase's `conformance/fixtures-gen/verify.ts` reads,
 * so **ts-mls** can check our output rather than the other way round.
 *
 * [CordnLifecycleInteropTest] proves we can read what ts-mls writes. That is
 * only half of interoperating: a client can parse everything correctly and
 * still emit something nobody else accepts — and the failure mode is worse,
 * because our own tests stay green while every peer silently drops us.
 *
 * This test writes files; `cordn/interop/verify-with-ts-mls.sh` feeds them to
 * ts-mls. The split is deliberate: producing the artifacts needs no toolchain
 * beyond Gradle and runs in CI unconditionally, while running ts-mls needs bun
 * and a cordn checkout. A green run here means the artifacts exist and our own
 * engine round-trips them; it does **not** by itself mean ts-mls accepted them.
 *
 * Output goes to `-Dcordn.interop.out`, defaulting to `build/interop/kotlin`.
 */
@OptIn(ExperimentalEncodingApi::class)
class KotlinArtifactProducerTest {
    private val alice = "cc".repeat(32)

    private val out =
        File(System.getProperty("cordn.interop.out") ?: "build/interop/kotlin").also { it.mkdirs() }

    private fun write(
        name: String,
        value: String,
    ) = File(out, name).writeText(value)

    private val metadata =
        CordnGroupMetadata(
            name = "Kotlin conformance",
            description = "quartz MLS via :cordn",
            adminPubkeys = listOf(alice),
            icon = "💎",
        )

    private val updatedMetadata = metadata.copy(name = "Kotlin conformance (renamed)")

    private fun exporterHex(group: MlsGroup): String = CordnGroupPolicy.PAYLOAD_EXPORTER.let { group.exporterSecret(it.label, it.context, it.length) }.toHexKey()

    @Test
    fun `produce the artifacts ts-mls verifies`() {
        // --- epoch 0: alice creates a cordn group carrying metadata ---------
        val group =
            MlsGroup.create(
                identity = CordnCredential.of(alice).identity,
                policy = CordnGroupPolicy,
                initialExtensions = listOf(metadata.toExtension()),
            )
        write("k-alice.pk", alice)
        write("k-meta.json", metadataJson(metadata))

        // --- epoch 1: add THEIR key package ---------------------------------
        // bob2 is a real ts-mls KeyPackage. Using ours would make this a test
        // of our own encoder talking to itself.
        val add = group.addMember(TsMlsFixtures.bytes("bob2-kp.bin"))
        val welcome = assertNotNull(add.welcomeBytes, "adding a member must produce a Welcome")
        assertEquals(1L, group.epoch)

        write("k-welcome.b64", Base64.encode(welcome))
        write("k-exporter-e1.hex", exporterHex(group))

        // --- an application message at epoch 1 -------------------------------
        val envelope =
            CordnEnvelope.build(
                pubKey = alice,
                createdAt = 1_700_000_100L,
                kind = 9,
                content = "hello from quartz 💎",
            )
        write("k-app-sealed.b64", CordnApplicationMessage.seal(group, alice, envelope))
        write("k-envelope.json", envelopeJson(envelope))

        // --- epoch 2: a metadata change -------------------------------------
        // Sealed with the PRE-commit key the commit itself reports. Sealing
        // under the post-commit epoch would lock out every member still at
        // epoch 1 — which is everyone this commit is addressed to.
        group.proposeGroupContextExtensions(
            group.extensions.filterNot { it.extensionType == CordnGroupMetadata.EXTENSION_TYPE } +
                updatedMetadata.toExtension(),
        )
        val commit = group.commit()
        assertEquals(2L, group.epoch)
        assertTrue(commit.preCommitExporterSecret.isNotEmpty(), "the policy must supply a commit exporter")

        write("k-commit-meta-sealed.b64", SealedPayload.seal(commit.framedCommitBytes, commit.preCommitExporterSecret))
        write("k-meta-2.json", metadataJson(updatedMetadata))
        write("k-exporter-e2.hex", exporterHex(group))

        // Our own round trip, so a broken artifact fails here rather than only
        // in a script someone may not run.
        assertEquals(
            updatedMetadata.name,
            CordnGroupMetadata.fromExtensions(group.extensions)?.name,
        )
        assertEquals(
            9,
            listOf(
                "k-alice.pk",
                "k-meta.json",
                "k-welcome.b64",
                "k-exporter-e1.hex",
                "k-app-sealed.b64",
                "k-envelope.json",
                "k-commit-meta-sealed.b64",
                "k-meta-2.json",
                "k-exporter-e2.hex",
            ).count { File(out, it).exists() },
            "every artifact verify.ts reads must be written",
        )
    }

    /** verify.ts compares name, description and adminPubkeys against the extension. */
    private fun metadataJson(meta: CordnGroupMetadata) =
        buildString {
            append("{\"name\":").append(jsonString(meta.name))
            append(",\"description\":").append(jsonString(meta.description))
            append(",\"adminPubkeys\":[").append(meta.adminPubkeys.joinToString(",") { jsonString(it) }).append("]")
            append(",\"icon\":").append(jsonString(meta.icon))
            append("}")
        }

    /** verify.ts compares id, content and pubkey. */
    private fun envelopeJson(envelope: CordnEnvelope) =
        buildString {
            append("{\"id\":").append(jsonString(envelope.id))
            append(",\"pubkey\":").append(jsonString(envelope.pubKey))
            append(",\"created_at\":").append(envelope.createdAt)
            append(",\"kind\":").append(envelope.kind)
            append(",\"content\":").append(jsonString(envelope.content))
            append("}")
        }

    private fun jsonString(value: String) =
        buildString {
            append('"')
            value.forEach {
                when {
                    it == '"' -> append("\\\"")
                    it == '\\' -> append("\\\\")
                    it.code < 0x20 -> append("\\u%04x".format(it.code))
                    else -> append(it)
                }
            }
            append('"')
        }
}
