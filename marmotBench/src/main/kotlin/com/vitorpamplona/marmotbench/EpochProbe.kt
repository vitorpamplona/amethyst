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
package com.vitorpamplona.marmotbench

import com.vitorpamplona.amethyst.commons.marmot.ingest
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.runBlocking

// Counts the commits `create_group/N` actually publishes, so the README's
// claim about the shape difference against MDK is checked rather than assumed.
fun epochProbe() =
    runBlocking {
        val alice = Client("alice")
        val bob = Client("bob")
        val groupId = RandomInstance.bytes(32).toHexKey()

        alice.manager.createCurrentProfileGroup(groupId, listOf("wss://bench.invalid"), GroupProfileV1("probe", ""))
        println("after createCurrentProfileGroup: epoch=${alice.manager.groupEpoch(groupId)}")

        val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
        val (commit, welcome) = alice.manager.addMember(groupId, kp, emptyList())
        println("after addMember:                 epoch=${alice.manager.groupEpoch(groupId)}")
        println("commits published by addMember:  1 (kind ${commit.signedEvent.kind})")
        println("welcome produced:                ${welcome != null}")

        bob.manager.ingest(welcome!!.giftWrapEvent)
        println("bob after joining:               epoch=${bob.manager.groupEpoch(groupId)}")
    }
