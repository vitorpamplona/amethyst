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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.messages.Welcome
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

/**
 * Reverse-interop harness: emits a Welcome + PrivateMessages authored by
 * Amethyst, so a foreign MLS backend (MDK / marmot-ts) can try to join
 * and decrypt. Gated on environment variables so it only runs when you
 * are actually driving the cross-impl pipeline:
 *
 *   JOINER_HANDOFF_JSON=/tmp/joiner-handoff.json \
 *   AMETHYST_FIXTURE_JSON=/tmp/amethyst-fixture.json \
 *   ./gradlew :quartz:jvmTest \
 *     --tests com.vitorpamplona.quartz.marmot.mls.AmethystAuthoredVectorGen
 *
 * The handoff file comes from the foreign backend's
 * `emit-joiner-kp` helper (it holds Bob's public KP bytes and Bob's
 * private keys in the foreign backend's format). Amethyst reads only
 * the public `key_package_raw` here and emits the Welcome + three
 * application messages to `AMETHYST_FIXTURE_JSON`. The foreign
 * verifier then consumes the fixture alongside its copy of Bob's
 * private keys to prove the round-trip closes.
 */
class AmethystAuthoredVectorGen {
    @Serializable
    private data class HandoffJson(
        @SerialName("cipher_suite") val cipherSuite: Int,
        val joiner: HandoffJoiner,
    )

    @Serializable
    private data class HandoffJoiner(
        @SerialName("key_package_raw") val keyPackageRaw: String,
        @SerialName("init_priv") val initPriv: String = "",
    )

    @Test
    fun emitAmethystFixture() {
        val handoffPath = System.getenv("JOINER_HANDOFF_JSON") ?: return
        val fixturePath =
            System.getenv("AMETHYST_FIXTURE_JSON")
                ?: error("AMETHYST_FIXTURE_JSON env var must be set when JOINER_HANDOFF_JSON is set")

        val lenientJson = Json { ignoreUnknownKeys = true }
        val handoff =
            lenientJson.decodeFromString<HandoffJson>(File(handoffPath).readText())
        check(handoff.cipherSuite == 1) {
            "only cipher_suite 1 is supported; got ${handoff.cipherSuite}"
        }

        // Drop the raw KeyPackage bytes straight into Amethyst. We use the
        // inner (un-wrapped) form here so this matches MlsGroup.addMember's
        // expectations — that entry point reads an MlsKeyPackage, not an
        // MlsMessage envelope.
        val bobKpBytes = handoff.joiner.keyPackageRaw.hexToByteArray()
        val bobKp = MlsKeyPackage.decodeTls(TlsReader(bobKpBytes))
        check(bobKp.verifySignature()) { "joiner KeyPackage signature did not verify" }

        val alice = MlsGroup.create("alice".encodeToByteArray())
        val addResult = alice.addMember(bobKpBytes)
        val welcomeBytes =
            requireNotNull(addResult.welcomeBytes) { "addMember returned no Welcome" }

        // Also emit the GroupInfo plaintext as a diagnostic so foreign
        // verifiers can inspect our on-wire shape byte-for-byte.
        val groupInfoBytes =
            run {
                val mlsMsg = MlsMessage.decodeTls(TlsReader(welcomeBytes))
                val welcome = Welcome.decodeTls(TlsReader(mlsMsg.payload))
                val myRef = bobKp.reference()
                val mySecrets =
                    welcome.secrets.find { it.newMember.contentEquals(myRef) }
                        ?: error("no secrets for joiner in our own Welcome")
                val initPriv = handoff.joiner.initPriv.hexToByteArray()
                val gsBytes =
                    MlsCryptoProvider.decryptWithLabel(
                        initPriv,
                        "Welcome",
                        welcome.encryptedGroupInfo,
                        mySecrets.encryptedGroupSecrets.kemOutput,
                        mySecrets.encryptedGroupSecrets.ciphertext,
                    )
                val joinerSecret = TlsReader(gsBytes).readOpaqueVarInt()
                val pskSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
                val memberSecret = MlsCryptoProvider.hkdfExtract(joinerSecret, pskSecret)
                val welcomeSecret = MlsCryptoProvider.deriveSecret(memberSecret, "welcome")
                val welcomeKey =
                    MlsCryptoProvider.expandWithLabel(
                        welcomeSecret,
                        "key",
                        ByteArray(0),
                        MlsCryptoProvider.AEAD_KEY_LENGTH,
                    )
                val welcomeNonce =
                    MlsCryptoProvider.expandWithLabel(
                        welcomeSecret,
                        "nonce",
                        ByteArray(0),
                        MlsCryptoProvider.AEAD_NONCE_LENGTH,
                    )
                MlsCryptoProvider.aeadDecrypt(
                    welcomeKey,
                    welcomeNonce,
                    ByteArray(0),
                    welcome.encryptedGroupInfo,
                )
            }

        val plaintexts =
            listOf(
                "Hello from Amethyst",
                "Second message from Amethyst at epoch 1.",
                "Unicode works too: ☕ ❤",
            )
        val appMessages =
            plaintexts.map { pt ->
                val ptBytes = pt.encodeToByteArray()
                val ct = alice.encrypt(ptBytes)
                AppMessageEntry(ptBytes.toHexKey(), ct.toHexKey())
            }

        // Build the joiner-side KeyPackageBundle by re-importing the bytes so
        // that we can drive Amethyst's own processWelcome end-to-end and
        // record a post-join MLS-Exporter KAT. The foreign verifier doesn't
        // need the exporter — but including it keeps the fixture's shape
        // identical to the other cross-impl vectors.
        val exporterContext = "group-event".encodeToByteArray()

        val fixture =
            AmethystFixture(
                cipherSuite = 1,
                description = "Amethyst-authored Welcome and PrivateMessages for reverse interop.",
                welcome = welcomeBytes.toHexKey(),
                groupInfoPlaintext = groupInfoBytes.toHexKey(),
                appMessagesAliceToBob = appMessages,
                exporter =
                    ExporterJson(
                        label = "marmot",
                        context = exporterContext.toHexKey(),
                        length = 32,
                        // The receiver computes its own exporter after
                        // join — foreign verifiers may compare theirs
                        // against ours if they want a cross-check.
                        secret = "",
                    ),
            )

        File(fixturePath).writeText(
            JsonMapper.jsonInstance.encodeToString(
                AmethystFixture.serializer(),
                fixture,
            ),
        )
    }

    @Serializable
    private data class AmethystFixture(
        @SerialName("cipher_suite") val cipherSuite: Int,
        val description: String,
        val welcome: String,
        @SerialName("group_info_plaintext")
        val groupInfoPlaintext: String,
        @SerialName("app_messages_alice_to_bob")
        val appMessagesAliceToBob: List<AppMessageEntry>,
        val exporter: ExporterJson,
    )

    @Serializable
    private data class AppMessageEntry(
        val plaintext: String,
        @SerialName("private_message") val privateMessage: String,
    )

    @Serializable
    private data class ExporterJson(
        val label: String,
        val context: String,
        val length: Int,
        val secret: String,
    )
}
