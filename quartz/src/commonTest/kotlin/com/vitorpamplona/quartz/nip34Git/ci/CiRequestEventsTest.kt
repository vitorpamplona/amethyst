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
package com.vitorpamplona.quartz.nip34Git.ci

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.ci.manualTrigger.CiManualTriggerEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceRequest.CiServiceRequestEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceStop.CiServiceStopEvent
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowFile
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The maintainer-signed requests: Manual Trigger (9840), Service Request (9843) and Stop (9844). */
class CiRequestEventsTest {
    private val manualTrigger =
        """{"id":"5b5d2b1638bf714b38d90850ee6171ca1fdaefa00362d10b85565989b1c345ed","pubkey":"9cd14d9acdbab7ca162b772c91fa514da8e3248f61f924bf3441e6e72c5f0dee","created_at":1791335307,"kind":9840,"tags":[["p","765cd47badcbbc4a38c7d0c57d5607663b484c20cd59773f9f7064487f9431e8"],["a","30617:9cd14d9acdbab7ca162b772c91fa514da8e3248f61f924bf3441e6e72c5f0dee:contextvm-services"],["c","a59d1187bee7375ef721b7f6c184a4da36fd7670"],["w",".ngit/act/workflows/ci.yml","3d06582b13bbb359b38b226d35f78318488c8903c27fc086de1d12f13f3c0d93"],["r","refs/heads/ci/ngit-ci-workflow"]],"content":"","sig":"92eee8dbaaccac0b5e64c57c7918c2c11f01a28cf78eff34dee7260bbef177efa0b14205fab4bc85f4fa0874c3e7f3db2174034d4c27c6b8f2c7327cf496f76e"}"""

    private val serviceRequest =
        """{"id":"c0996e3a34bfe2605d07e6782fc5f61e18df725d286f9645e5e292f7c584e8e6","pubkey":"9cd14d9acdbab7ca162b772c91fa514da8e3248f61f924bf3441e6e72c5f0dee","created_at":1791335253,"kind":9843,"tags":[["a","30617:9cd14d9acdbab7ca162b772c91fa514da8e3248f61f924bf3441e6e72c5f0dee:contextvm-services"],["p","765cd47badcbbc4a38c7d0c57d5607663b484c20cd59773f9f7064487f9431e8"]],"content":"","sig":"85626a2a3d0290d020c44774c1c73511c90f3ec269ff13144eaddeb5dccbf3f1ead9ff81ca8700c9179f89bd4fff2a444a1cc36bf8a6f0d1a3f274c1f7b3811e"}"""

    private val maintainer = "9cd14d9acdbab7ca162b772c91fa514da8e3248f61f924bf3441e6e72c5f0dee"
    private val coordinator = "765cd47badcbbc4a38c7d0c57d5607663b484c20cd59773f9f7064487f9431e8"
    private val repo = "30617:$maintainer:contextvm-services"

    @Test
    fun factoryBuildsAllThree() {
        assertIs<CiManualTriggerEvent>(Event.fromJson(manualTrigger))
        assertIs<CiServiceRequestEvent>(Event.fromJson(serviceRequest))
        val stop = EventFactory.create<Event>("1".repeat(64), maintainer, 1L, CiServiceStopEvent.KIND, arrayOf(arrayOf("a", repo), arrayOf("p", coordinator)), "", "0".repeat(128))
        assertIs<CiServiceStopEvent>(stop)
        assertTrue(EventFactory.isKnownKind(CiManualTriggerEvent.KIND))
        assertTrue(EventFactory.isKnownKind(CiServiceRequestEvent.KIND))
        assertTrue(EventFactory.isKnownKind(CiServiceStopEvent.KIND))
        assertFalse(Event.fromJson(manualTrigger) is SearchableEvent)
    }

    @Test
    fun readsTheManualTrigger() {
        val trigger = assertIs<CiManualTriggerEvent>(Event.fromJson(manualTrigger))
        assertEquals(coordinator, trigger.coordinator())
        assertEquals(listOf(repo), trigger.repositories().map { it.toTag() })
        assertEquals("a59d1187bee7375ef721b7f6c184a4da36fd7670", trigger.commit())
        assertEquals(".ngit/act/workflows/ci.yml", trigger.workflow()?.path)
        assertEquals("refs/heads/ci/ngit-ci-workflow", trigger.gitRef())
        assertNull(trigger.pullRequest())

        assertEquals(listOf(coordinator), trigger.linkedPubKeys())
        assertEquals(listOf(repo), trigger.linkedAddressIds())
        assertEquals(emptyList(), trigger.linkedEventIds())
    }

    @Test
    fun aManualTriggerAddressesExactlyOneCoordinator() {
        // On a 9840 the only `p` is the coordinator; a second one makes the request ambiguous.
        val tags = arrayOf(arrayOf("p", coordinator), arrayOf("p", "f".repeat(64)))
        val trigger = assertIs<CiManualTriggerEvent>(EventFactory.create<Event>("1".repeat(64), maintainer, 1L, CiManualTriggerEvent.KIND, tags, "", "0".repeat(128)))
        assertNull(trigger.coordinator())
    }

    @Test
    fun readsTheServiceRequest() {
        val request = assertIs<CiServiceRequestEvent>(Event.fromJson(serviceRequest))
        assertEquals(repo, request.repository()?.toTag())
        assertEquals(coordinator, request.coordinator())
        assertEquals(listOf(coordinator), request.linkedPubKeys())
        assertEquals(listOf(repo), request.linkedAddressIds())
    }

    @Test
    fun serviceControlsNeedExactlyOneRepositoryAndCoordinator() {
        val tags = arrayOf(arrayOf("a", repo), arrayOf("a", "30617:$coordinator:other"), arrayOf("p", coordinator), arrayOf("p", maintainer))
        val request = assertIs<CiServiceRequestEvent>(EventFactory.create<Event>("1".repeat(64), maintainer, 1L, CiServiceRequestEvent.KIND, tags, "", "0".repeat(128)))
        assertNull(request.repository())
        assertNull(request.coordinator())
        val stop = assertIs<CiServiceStopEvent>(EventFactory.create<Event>("1".repeat(64), maintainer, 1L, CiServiceStopEvent.KIND, tags, "", "0".repeat(128)))
        assertNull(stop.repository())
        assertNull(stop.coordinator())
    }

    @Test
    fun buildersRoundTrip() {
        val repository = ATag(30617, maintainer, "contextvm-services", null)

        val requestTemplate = CiServiceRequestEvent.build(repository, coordinator, createdAt = 1L)
        assertEquals(listOf(listOf("a", repo), listOf("p", coordinator)), requestTemplate.tags.map { it.toList() })
        assertEquals("", requestTemplate.content)

        val stopTemplate = CiServiceStopEvent.build(repository, coordinator, createdAt = 2L)
        val stop = assertIs<CiServiceStopEvent>(EventFactory.create<Event>("1".repeat(64), maintainer, stopTemplate.createdAt, stopTemplate.kind, stopTemplate.tags, stopTemplate.content, "0".repeat(128)))
        assertEquals(repo, stop.repository()?.toTag())
        assertEquals(coordinator, stop.coordinator())

        val pr = CiPullRequestContext("a".repeat(64), "b".repeat(64), 1618, "c".repeat(64), 1619, sourceAuthor = "d".repeat(64))
        val context = CiRunContext(listOf(repository), listOf("e".repeat(40)), CiWorkflowFile(".ngit/act/workflows/ci.yml", "f".repeat(64)), pullRequest = pr)
        val triggerTemplate = CiManualTriggerEvent.build(coordinator, context, createdAt = 3L)
        // The PR context omits the lowercase `p`: the coordinator is the only one, and `o` is never written.
        assertEquals(listOf(coordinator), triggerTemplate.tags.filter { it[0] == "p" }.map { it[1] })
        assertTrue(triggerTemplate.tags.none { it[0] == "o" })

        val trigger = assertIs<CiManualTriggerEvent>(EventFactory.create<Event>("1".repeat(64), maintainer, triggerTemplate.createdAt, triggerTemplate.kind, triggerTemplate.tags, triggerTemplate.content, "0".repeat(128)))
        assertEquals(coordinator, trigger.coordinator())
        assertEquals(pr.copy(sourceAuthor = null), trigger.pullRequest())
        assertEquals(context.commits, trigger.commits())
        assertEquals(context.workflow, trigger.workflow())
        assertEquals(listOf("a".repeat(64), "c".repeat(64)), trigger.linkedEventIds())
        assertEquals(listOf(coordinator, "b".repeat(64)), trigger.linkedPubKeys())
    }
}
