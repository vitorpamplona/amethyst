/**
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
package com.vitorpamplona.quartz.nip19Bech32

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.utils.Hex
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NIP19EmbedTests {
    @Test
    fun testEmbedKind1Event() {
        val signer =
            NostrSignerInternal(
                KeyPair(Hex.decode("e8e7197ccc53c9ed4cf9b1c8dce085475fa1ffdd71f2c14e44fe23d0bdf77598")),
            )

        val textNote: TextNoteEvent =
            runBlocking {
                signer.sign(
                    TextNoteEvent.build("I like this. It could solve the ninvite problem in #1062, and it seems like it could be applied very broadly to limit the spread of events that shouldn't stand on their own or need to be private. The one question I have is how long are these embeds? If it's 50 lines of text, that breaks the human readable (or at least parseable) requirement of kind 1s. Also, encoding json in a tlv is silly, we should at least use the tlv to reduce the payload size."),
                )
            }

        assertNotNull(textNote)

        val bech32 = NEmbed.create(textNote!!)

        println(bech32)

        val decodedNote = (Nip19Parser.uriToRoute(bech32)?.entity as NEmbed).event

        assertTrue(decodedNote.verify())

        assertEquals(textNote!!.toJson(), decodedNote.toJson())
    }

    @Test
    fun testVisionPrescriptionEmbedEvent() {
        val signer =
            NostrSignerInternal(
                KeyPair(Hex.decode("e8e7197ccc53c9ed4cf9b1c8dce085475fa1ffdd71f2c14e44fe23d0bdf77598")),
            )

        val eyeglassesPrescriptionEvent =
            runBlocking {
                signer.sign(FhirResourceEvent.build(visionPrescriptionFhir))
            }

        assertNotNull(eyeglassesPrescriptionEvent)

        val bech32 = NEmbed.create(eyeglassesPrescriptionEvent)

        println(eyeglassesPrescriptionEvent.toJson())
        println(bech32)

        val decodedNote = (Nip19Parser.uriToRoute(bech32)?.entity as NEmbed).event

        assertTrue(decodedNote.verify())

        assertEquals(eyeglassesPrescriptionEvent.toJson(), decodedNote.toJson())
    }

    @Test
    fun testVisionPrescriptionBundleEmbedEvent() {
        val signer =
            NostrSignerInternal(
                KeyPair(Hex.decode("e8e7197ccc53c9ed4cf9b1c8dce085475fa1ffdd71f2c14e44fe23d0bdf77598")),
            )

        val countDownLatch = CountDownLatch(1)

        val eyeglassesPrescriptionEvent =
            runBlocking {
                signer.sign(FhirResourceEvent.build(visionPrescriptionBundle))
            }

        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        assertNotNull(eyeglassesPrescriptionEvent)

        val bech32 = NEmbed.create(eyeglassesPrescriptionEvent)

        println(eyeglassesPrescriptionEvent.toJson())
        println(bech32)

        val decodedNote = (Nip19Parser.uriToRoute(bech32)?.entity as NEmbed).event

        assertTrue(decodedNote.verify())

        assertEquals(eyeglassesPrescriptionEvent.toJson(), decodedNote.toJson())
    }

    @Test
    fun testVisionPrescriptionBundle2EmbedEvent() {
        val signer =
            NostrSignerInternal(
                KeyPair(decodePrivateKeyAsHexOrNull("nsec1arn3jlxv20y76n8ek8ydecy9ga06rl7aw8evznjylc3ap00hwkvqx4vvy6")!!.hexToByteArray()),
            )

        val eyeglassesPrescriptionEvent =
            runBlocking {
                signer.sign(FhirResourceEvent.build(visionPrescriptionBundle2))
            }

        assertNotNull(eyeglassesPrescriptionEvent)

        val bech32 = NEmbed.create(eyeglassesPrescriptionEvent)

        println(eyeglassesPrescriptionEvent.toJson())
        println(bech32)

        val decodedNote = (Nip19Parser.uriToRoute(bech32)?.entity as NEmbed).event

        assertTrue(decodedNote.verify())

        assertEquals(eyeglassesPrescriptionEvent.toJson(), decodedNote.toJson())
    }

    @Test
    fun testTimsNembed() {
        val uri = "nembed1r79ssq9446hkwqhl642ukmku8qg0c92pu7w3j0jyfte8tc7tvg85vmrys8x3sqgle5vjy7jpjswqhphl0kd6yf4sz0n3peyjq5rp3zkat4w6c6j3f7um0724jmfu5456xxgg2yxkn8dp23j64xsn9npcggzafyh2effyntqrqxzja8dp52kpcvc9zqxlj86e8mx05vevzxkeprjkfs4wmppxm3p96vj6yvu2mqgf5l4v99492r2qsggquxuv93uzx244652h2kkj8xseg9xkq0afpygknjtty9j4ju5v0nm9mezux9wyl6s5wr7lzce7cj397mnu0u04ha7aq3w7exelrhe3zs3l3urwa9sp36u80npllrs0hmsxqdn0fsuyav3nv0azjs5suzuurg2uymncjxez8p9xksc2j6gw992enjflgrdd7n5uq2xrpvfrd3rckw624ey0elvm6grr27tyzlf4vaswgm5vc3hdyczsl983g2j8e67r6z5zt30lat84ma4wclkwwxxrcflvdsuwd7346h7zqav4vdwe3gkt9lr87sfk4aqd2aey03tt4eyspldrqcmkx9pqe2pn63rv7grwwalr86akuldnvjm6m87wrw9sdwns8wq0rnsmj57vqwtc3g7hkwum3vl2dda78dwkycgfzw6qna3ufhpatcvq5a4hm4ehl45an8umwt0clf7rn77ctke475qglwu86hhfwhn7dkca4pkfpyc4y75rll6nvr5qc8nlhf8mk22celn5mecvyuzxd830drhdck9tcdpcafymk8wajwu2w8ha8gatggjfvq0a4jlf2sdamzj0ysqks9dk8me3q7a0qpmf6vykurkrcls4pug3u4pn4u26ezx3h8e482n07x2nsmu80dpufxqc0ttcyzhnppguxma4d8aumdawnlsyy7yzcuxl7lw5y9p4nv5h8fn6u8anpm2tsze3p6mgxy9j9uuqfxg2jvlmtjpakna5m4hln0msmw804hnun96h66fh62270yhhljnmmdl7jln07ll5vft7e870hemcld34a09n943ed6629fgtctsftma9q6tf4jfm2p0ukd2j2n2dpz53fqrkk4ctdcy2j5jar095g5jntf6u807ggkzauzt6uqkwk4tg5w7w55kskspc9663zx5dzzzfwpg3q546g2ve4kukr70n0a46eyce2crsqqq247ql5"

        val decodedNote = (Nip19Parser.uriToRoute(uri)?.entity as NEmbed).event

        assertTrue(decodedNote.verify())

        assertEquals(timsPrescription, decodedNote.toJson())
    }

    val visionPrescriptionFhir = "{\"resourceType\":\"VisionPrescription\",\"status\":\"active\",\"created\":\"2014-06-15\",\"patient\":{\"reference\":\"Patient/Donald Duck\"},\"dateWritten\":\"2014-06-15\",\"prescriber\":{\"reference\":\"Practitioner/Adam Careful\"},\"lensSpecification\":[{\"eye\":\"right\",\"sphere\":-2,\"prism\":[{\"amount\":0.5,\"base\":\"down\"}],\"add\":2},{\"eye\":\"left\",\"sphere\":-1,\"cylinder\":-0.5,\"axis\":180,\"prism\":[{\"amount\":0.5,\"base\":\"up\"}],\"add\":2}]}"
    val visionPrescriptionBundle = "{\"resourceType\":\"Bundle\",\"id\":\"bundle-vision-test\",\"type\":\"document\",\"entry\":[{\"resourceType\":\"Practitioner\",\"id\":\"2\",\"active\":true,\"name\":[{\"use\":\"official\",\"family\":\"Careful\",\"given\":[\"Adam\"]}],\"gender\":\"male\"},{\"resourceType\":\"Patient\",\"id\":\"1\",\"active\":true,\"name\":[{\"use\":\"official\",\"family\":\"Duck\",\"given\":[\"Donald\"]}],\"gender\":\"male\"},{\"resourceType\":\"VisionPrescription\",\"status\":\"active\",\"created\":\"2014-06-15\",\"patient\":{\"reference\":\"#1\"},\"dateWritten\":\"2014-06-15\",\"prescriber\":{\"reference\":\"#2\"},\"lensSpecification\":[{\"eye\":\"right\",\"sphere\":-2,\"prism\":[{\"amount\":0.5,\"base\":\"down\"}],\"add\":2},{\"eye\":\"left\",\"sphere\":-1,\"cylinder\":-0.5,\"axis\":180,\"prism\":[{\"amount\":0.5,\"base\":\"up\"}],\"add\":2}]}]}"

    val visionPrescriptionBundle2 = "{\"resourceType\":\"Bundle\",\"id\":\"bundle-vision-test\",\"type\":\"document\",\"entry\":[{\"resourceType\":\"Practitioner\",\"id\":\"2\",\"active\":true,\"name\":[{\"use\":\"official\",\"family\":\"Smith\",\"given\":[\"Dr. Joe\"]}],\"gender\":\"male\"},{\"resourceType\":\"Patient\",\"id\":\"1\",\"active\":true,\"name\":[{\"use\":\"official\",\"family\":\"Doe\",\"given\":[\"Jane\"]}],\"gender\":\"male\"},{\"resourceType\":\"VisionPrescription\",\"status\":\"active\",\"created\":\"2014-06-15\",\"patient\":{\"reference\":\"#1\"},\"dateWritten\":\"2014-06-15\",\"lensSpecification\":[{\"eye\":\"right\",\"sphere\":-2,\"prism\":[{\"amount\":0.5,\"base\":\"down\"}],\"add\":2},{\"eye\":\"left\",\"sphere\":-1,\"cylinder\":-0.5,\"axis\":180,\"prism\":[{\"amount\":0.5,\"base\":\"up\"}],\"add\":2}]}]}"
    val timsPrescription = "{\"id\":\"18d8b22e6455dfc9f4c6d6be8c2cf015e961b8d160dfe5e4b7fc1578f2c4e0be\",\"pubkey\":\"46f1826abf5b03de972192e619e25fa94d775a1c555efe53a775412dbf49889b\",\"created_at\":1739566773,\"kind\":82,\"tags\":[[\"p\",\"46f1826abf5b03de972192e619e25fa94d775a1c555efe53a775412dbf49889b\"]],\"content\":\"{\\\"resourceType\\\": \\\"VisionPrescription\\\", \\\"id\\\": \\\"eyeglass-prescription-001\\\", \\\"status\\\": \\\"active\\\", \\\"created\\\": \\\"2025-02-14T10:00:00Z\\\", \\\"patient\\\": {\\\"reference\\\": \\\"Patient/12345\\\", \\\"display\\\": \\\"John Doe\\\"}, \\\"encounter\\\": {\\\"reference\\\": \\\"Encounter/67890\\\"}, \\\"dateWritten\\\": \\\"2025-02-10T15:00:00Z\\\", \\\"prescriber\\\": {\\\"reference\\\": \\\"Practitioner/56789\\\", \\\"display\\\": \\\"Dr. Emily Smith\\\"}, \\\"lensSpecification\\\": [{\\\"product\\\": {\\\"coding\\\": [{\\\"system\\\": \\\"http://terminology.hl7.org/CodeSystem/ex-visionprescriptionproduct\\\", \\\"code\\\": \\\"lens\\\", \\\"display\\\": \\\"Eyeglasses\\\"}]}, \\\"eye\\\": \\\"right\\\", \\\"sphere\\\": -2.5, \\\"cylinder\\\": -1.0, \\\"axis\\\": 180, \\\"prism\\\": [{\\\"amount\\\": 0.5, \\\"base\\\": \\\"up\\\"}], \\\"add\\\": 2.0, \\\"duration\\\": {\\\"value\\\": 24, \\\"unit\\\": \\\"months\\\", \\\"system\\\": \\\"http://unitsofmeasure.org\\\", \\\"code\\\": \\\"mo\\\"}, \\\"note\\\": [{\\\"text\\\": \\\"Right eye prescription for near-sightedness with astigmatism.\\\"}]}, {\\\"product\\\": {\\\"coding\\\": [{\\\"system\\\": \\\"http://terminology.hl7.org/CodeSystem/ex-visionprescriptionproduct\\\", \\\"code\\\": \\\"lens\\\", \\\"display\\\": \\\"Eyeglasses\\\"}]}, \\\"eye\\\": \\\"left\\\", \\\"sphere\\\": -3.0, \\\"cylinder\\\": -0.75, \\\"axis\\\": 160, \\\"prism\\\": [{\\\"amount\\\": 0.5, \\\"base\\\": \\\"down\\\"}], \\\"add\\\": 2.0, \\\"duration\\\": {\\\"value\\\": 24, \\\"unit\\\": \\\"months\\\", \\\"system\\\": \\\"http://unitsofmeasure.org\\\", \\\"code\\\": \\\"mo\\\"}, \\\"note\\\": [{\\\"text\\\": \\\"Left eye prescription for near-sightedness with astigmatism.\\\"}]}]}\",\"sig\":\"d22d3b86aea397094de8b6cdf69decdfd886c90008aeebf95fd43a2770b37d486b313bff5bdb44b78e33bbaa3f336d74ee8b36bc5b16050374054246c72d93c2\"}"
}
