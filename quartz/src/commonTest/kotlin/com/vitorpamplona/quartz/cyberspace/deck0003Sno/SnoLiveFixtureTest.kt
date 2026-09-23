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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every `kind 33331` object that existed on the network, read back.
 *
 * Collected 2026-09-21 from relay.damus.io and nos.lol (cyberspace.nostr1.com
 * and relay.primal.net held none). Seven objects from six authors.
 *
 * Three of them are the legacy cohort DECK-0003 §5 describes: `v: 2` payloads
 * whose `colors` are still literal `[r, g, b]` triples from before the palette
 * change of 2026-09-16. `decks/sno-reference.py` refuses those three; `sno-core`
 * v0.1.4 accepts them deliberately, and so do we. **All seven must read**, which
 * is the parity target — a strict reader shows an error for nearly half of what
 * exists.
 */
class SnoLiveFixtureTest {
    private data class Fixture(
        val id: String,
        val name: String,
        val vertices: Int,
        val faces: Int,
        val mode: SnoMode,
        val unit: Int,
        val legacyTriples: Boolean,
        val json: String,
    )

    private val fixtures =
        listOf(
            Fixture(
                id = "fbe6a776bde5b90d",
                name = "hello world",
                vertices = 4,
                faces = 4,
                mode = SnoMode.SOLID,
                unit = 0,
                legacyTriples = true,
                json = "{\"v\":2,\"type\":\"shard\",\"name\":\"hello world\",\"unit\":0,\"extent\":8,\"mode\":\"solid\",\"vertices\":[[0,0,0],[3,0,0],[1,0,-3],[1,3,-1]],\"ticks\":[-4],\"colors\":[[1,0.15,0.15],[0.15,1,0.3],[0.2,0.4,1],[1,1,0.6]],\"faces\":[[0,1,2],[0,1,3],[1,2,3],[0,2,3]]}",
            ),
            Fixture(
                id = "4b05663f46353d11",
                name = "second thought",
                vertices = 4,
                faces = 4,
                mode = SnoMode.SOLID,
                unit = 0,
                legacyTriples = true,
                json = "{\"v\":2,\"type\":\"shard\",\"name\":\"second thought\",\"unit\":0,\"extent\":8,\"mode\":\"solid\",\"vertices\":[[0,0,0],[3,0,0],[1,0,-3],[1,3,-1]],\"ticks\":[-4],\"colors\":[[1,0.15,0.15],[0.15,1,0.3],[0.2,0.4,1],[1,1,0.6]],\"faces\":[[0,1,2],[0,1,3],[1,2,3],[0,2,3]]}",
            ),
            Fixture(
                id = "04df8e12623fe071",
                name = "Ship",
                vertices = 18,
                faces = 24,
                mode = SnoMode.LINES,
                unit = 0,
                legacyTriples = true,
                json = "{\"v\":2,\"type\":\"shard\",\"name\":\"Ship\",\"unit\":0,\"extent\":8,\"mode\":\"lines\",\"vertices\":[[0,0,0],[-1,0,0],[0,0,-2],[0,0,1],[-1,0,0],[0,0,0],[0,0,0],[0,-1,0],[-1,-1,0],[1,-1,0],[-1,0,0],[-1,0,1],[-1,0,0],[0,0,1],[0,0,0],[0,0,0],[0,0,1],[-1,0,1]],\"ticks\":[[48,0,0],[72,0,0],[0,0,96],-1,[72,24,72],[48,24,72],[0,48,0],[0,96,0],[0,96,72],[0,96,72],[48,0,96],[96,0,0],[72,24,96],[24,0,0],[72,0,96],[48,24,96],[48,0,24],[72,0,24]],\"colors\":[[0.21176470588235294,0.054901960784313725,0.3607843137254902],[0.21176470588235294,0.054901960784313725,0.3607843137254902],[0.21176470588235294,0.054901960784313725,0.3607843137254902],[0.08627450980392157,0.0784313725490196,0.1411764705882353],[0.5882352941176471,0.14901960784313725,1],[0.5882352941176471,0.14901960784313725,1],[0.21176470588235294,0.054901960784313725,0.3607843137254902],[0.08627450980392157,0.0784313725490196,0.1411764705882353],[0.5882352941176471,0.14901960784313725,1],[0.5882352941176471,0.14901960784313725,1],[1,0.8352941176470589,0],[1,0.8352941176470589,0],[1,0.8352941176470589,0],[1,0.8352941176470589,0],[1,0.8352941176470589,0],[1,0.8352941176470589,0],[1,0.13725490196078433,0.13725490196078433],[1,0.13725490196078433,0.13725490196078433]],\"faces\":[[9,5,0],[4,1,8],[2,0,7],[1,2,7],[2,6,0],[1,6,2],[6,1,3],[3,0,6],[3,0,7],[3,1,7],[3,9,0],[3,1,8],[4,1,3],[5,0,3],[4,8,3],[5,3,9],[15,13,14],[11,12,10],[17,12,10],[12,11,17],[10,11,17],[15,13,16],[14,15,16],[14,16,13]]}",
            ),
            Fixture(
                id = "89f510e1fc40fe85",
                name = "Shard 4 copy",
                vertices = 35,
                faces = 20,
                mode = SnoMode.SOLID,
                unit = 0,
                legacyTriples = false,
                json = "{\"v\":2,\"name\":\"Shard 4 copy\",\"unit\":0,\"extent\":8,\"mode\":\"solid\",\"vertices\":[[0,0,0],[4,0,0],[2,0,-2],[0,0,-2],[0,2,0],[0,5,0],[0,2,-2],[0,2,-2],[4,0,-4],[4,0,-5],[3,0,-5],[3,0,-6],[2,0,-6],[1,0,-6],[1,0,-5],[0,0,-5],[0,0,-4],[0,0,-3],[1,0,-3],[1,0,-2],[2,0,-2],[3,0,-2],[3,0,-3],[4,0,-3],[4,0,-4],[7,0,-4],[5,0,-5],[4,0,-8],[2,0,-6],[0,0,-6],[1,0,-4],[0,0,-2],[2,0,-2],[4,0,0],[5,0,-3]],\"ticks\":[-35],\"colors\":[246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246,246],\"faces\":[[0,1,2],[0,2,3],[4,7,6],[4,6,5],[0,4,5],[0,5,1],[3,2,6],[3,6,7],[0,3,7],[0,7,4],[1,5,6],[1,6,2],[34,25,26],[34,26,27],[34,27,28],[34,28,29],[34,29,30],[34,30,31],[34,31,32],[32,33,34]]}",
            ),
            Fixture(
                id = "47031e0f64e233d6",
                name = "Shard 2",
                vertices = 5,
                faces = 5,
                mode = SnoMode.SOLID,
                unit = 0,
                legacyTriples = false,
                json = "{\"v\":2,\"name\":\"Shard 2\",\"unit\":0,\"extent\":8,\"mode\":\"solid\",\"vertices\":[[-1,0,2],[3,0,2],[3,0,-2],[-1,0,-2],[1,4,0]],\"ticks\":[-5],\"colors\":[226,226,226,226,226],\"faces\":[[1,0,4],[2,1,4],[3,2,4],[0,1,2],[0,2,3]]}",
            ),
            Fixture(
                id = "d12996088aa99127",
                name = "first object",
                vertices = 118,
                faces = 94,
                mode = SnoMode.SOLID,
                unit = 0,
                legacyTriples = false,
                json = "{\"v\":2,\"name\":\"first object\",\"unit\":0,\"extent\":8,\"mode\":\"solid\",\"vertices\":[[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-1,0,1],[1,0,1],[1,0,-1],[-1,0,-1],[-1,2,1],[1,2,1],[1,2,-1],[-1,2,-1],[-2,0,3],[2,0,3],[2,0,-1],[-2,0,-1],[0,4,1],[-2,0,3],[2,0,3],[2,0,-1],[-2,0,-1],[0,4,1],[-2,0,3],[2,0,3],[2,0,-1],[-2,0,-1],[0,4,1],[-1,0,1],[3,0,1],[3,0,-3],[-1,0,-3],[1,4,-1],[-5,0,1],[-1,0,1],[-1,0,-3],[-5,0,-3],[-3,4,-1],[-5,0,5],[-1,0,5],[-1,0,1],[-5,0,1],[-3,4,3],[-1,0,2],[3,0,2],[3,0,-2],[-1,0,-2],[1,4,0],[-3,0,2],[1,0,2],[1,0,-2],[-3,0,-2],[-1,4,0],[-4,0,3],[0,0,3],[0,0,-1],[-4,0,-1],[-2,4,1],[-4,0,-2],[0,0,-2],[0,0,-6],[-4,0,-6],[-2,4,-4],[-8,0,1],[-6,0,1],[-6,0,-1],[-8,0,-1],[-8,2,1],[-8,2,-1],[-8,0,-2],[-6,0,-2],[-6,0,-4],[-8,0,-4],[-8,2,-2],[-8,2,-4],[-8,0,-4],[-6,0,-4],[-6,0,-6],[-8,0,-6],[-8,2,-4],[-8,2,-6],[-8,0,2],[-6,0,2],[-6,0,0],[-8,0,0],[-8,2,2],[-8,2,0],[2,0,7],[4,0,7],[4,0,5],[2,0,5],[2,2,7],[2,2,5],[-2,0,7],[0,0,7],[0,0,5],[-2,0,5],[-2,2,7],[-2,2,5]],\"ticks\":[-118],\"colors\":[226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226,226],\"faces\":[[43,42,46],[44,43,46],[45,44,46],[42,45,46],[42,43,44],[42,44,45],[48,47,51],[49,48,51],[50,49,51],[47,50,51],[47,48,49],[47,49,50],[53,52,56],[54,53,56],[55,54,56],[52,55,56],[52,53,54],[52,54,55],[58,57,61],[59,58,61],[60,59,61],[57,60,61],[57,58,59],[57,59,60],[63,62,66],[64,63,66],[65,64,66],[62,65,66],[62,63,64],[62,64,65],[68,67,71],[69,68,71],[70,69,71],[67,70,71],[67,68,69],[67,69,70],[73,72,76],[74,73,76],[75,74,76],[72,75,76],[72,73,74],[72,74,75],[78,77,81],[79,78,81],[80,79,81],[77,80,81],[77,78,79],[77,79,80],[82,83,84],[82,84,85],[82,85,87],[82,87,86],[83,86,87],[83,87,84],[83,82,86],[85,84,87],[88,89,90],[88,90,91],[88,91,93],[88,93,92],[89,92,93],[89,93,90],[89,88,92],[94,95,96],[94,96,97],[94,97,99],[94,99,98],[95,98,99],[95,99,96],[97,96,99],[100,101,102],[100,102,103],[100,103,105],[100,105,104],[101,104,105],[101,105,102],[101,100,104],[103,102,105],[106,107,108],[106,108,109],[106,109,111],[106,111,110],[107,110,111],[107,111,108],[107,106,110],[109,108,111],[112,113,114],[112,114,115],[112,115,117],[112,117,116],[113,116,117],[113,117,114],[113,112,116],[115,114,117]]}",
            ),
            Fixture(
                id = "8fe392c2fd93d6a7",
                name = "Triforce",
                vertices = 12,
                faces = 18,
                mode = SnoMode.SOLID,
                unit = 12,
                legacyTriples = false,
                json = "{\"v\":2,\"name\":\"Triforce\",\"unit\":12,\"extent\":8,\"mode\":\"solid\",\"vertices\":[[0,2,0],[-1,0,0],[1,0,0],[-2,-1,0],[0,-1,0],[2,-1,0],[0,2,-1],[-1,0,-1],[1,0,-1],[-2,-1,-1],[0,-1,-1],[2,-1,-1]],\"ticks\":[[0,40,0],[0,80,0],[0,80,0],-3,[0,40,80],[0,80,80],[0,80,80],[0,0,80],[0,0,80],[0,0,80]],\"colors\":[249,249,249,249,249,249,249,249,249,249,249,249],\"faces\":[[1,0,2],[3,1,4],[2,4,5],[7,6,8],[9,7,10],[8,10,11],[5,0,6],[6,11,5],[9,11,5],[5,3,9],[9,6,0],[0,3,9],[10,7,1],[1,4,10],[4,2,8],[8,10,4],[1,2,8],[8,7,1]]}",
            ),
        )

    @Test
    fun everyObjectOnTheNetworkReads() {
        fixtures.forEach { fixture ->
            val result = SnoParser.parse(fixture.json)
            assertTrue(result is SnoResult.Invalid == false, "${fixture.id} (${fixture.name}) was refused: $result")
            val payload = (result as SnoResult.Valid).payload
            assertEquals(fixture.name, payload.name, fixture.id)
            assertEquals(fixture.vertices, payload.vertexCount, fixture.id)
            assertEquals(fixture.faces, payload.faceCount, fixture.id)
            assertEquals(fixture.mode, payload.mode, fixture.id)
            assertEquals(fixture.unit, payload.unit, fixture.id)
        }
    }

    @Test
    fun theLegacyCohortIsThreeOfSeven() {
        assertEquals(7, fixtures.size)
        assertEquals(3, fixtures.count { it.legacyTriples })
    }

    @Test
    fun everyColourIsOpaque() {
        fixtures.forEach { fixture ->
            val payload = SnoParser.parse(fixture.json).payloadOrNull()!!
            payload.colors.forEach { argb ->
                assertEquals(0xFF, (argb shr 24) and 0xFF, "${fixture.id} has a transparent colour")
            }
        }
    }

    @Test
    fun everyFaceIndexIsInRange() {
        fixtures.forEach { fixture ->
            val payload = SnoParser.parse(fixture.json).payloadOrNull()!!
            payload.faces.forEach { index ->
                assertTrue(index >= 0 && index < payload.vertexCount, "${fixture.id} has a face index out of range")
            }
        }
    }
}
