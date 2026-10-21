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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [NostrJsonReader] against Quartz's Jackson deserializers for everything besides a bare event:
 * every relay message type, every client command type with its filters, rumors and event lists.
 * Each type gets hand-written frames covering its optional elements and odd values, real events
 * from the startup capture, and random edits of all of them.
 *
 * Whenever both accept a frame they must build the same thing; the reader must never accept a
 * frame Jackson rejects. The reader may reject what Jackson accepts only where Jackson builds
 * garbage or reads on inside a value of the wrong shape; every such case is printed.
 */
class NostrJsonReaderFrameParityTest {
    private fun describe(e: Event): String = "${e::class.simpleName}(${e.id},${e.pubKey},${e.createdAt},${e.kind},${e.tags.joinToString("|") { it.joinToString(",") }},${e.content},${e.sig})"

    private fun describe(f: Filter): String = "Filter(${f.ids},${f.authors},${f.kinds},${f.tags},${f.tagsAll},${f.since},${f.until},${f.limit},${f.search})"

    private fun describe(m: Message): String =
        when (m) {
            is EventMessage -> "EVENT ${m.subId} ${describe(m.event)}"
            is EoseMessage -> "EOSE ${m.subId} ${m.hints}"
            is NoticeMessage -> "NOTICE ${m.message}"
            is OkMessage -> "OK ${m.eventId} ${m.success} ${m.message} ${m.params}"
            is AuthMessage -> "AUTH ${m.challenge}"
            is NotifyMessage -> "NOTIFY ${m.message}"
            is ClosedMessage -> "CLOSED ${m.subId} ${m.message} ${m.params}"
            is CountMessage -> "COUNT ${m.queryId} ${m.result.count} ${m.result.approximate} ${m.result.hll?.contentToString()}"
            is LimitsMessage -> "LIMITS $m"
            is NegMsgMessage -> "NEG-MSG ${m.subId} ${m.message}"
            is NegErrMessage -> "NEG-ERR ${m.subId} ${m.reason} ${m.cap}"
            else -> "?? ${m::class}"
        }

    private fun describe(c: Command): String =
        when (c) {
            is ReqCmd -> "REQ ${c.subId} ${c.filters.map { describe(it) }}"
            is CountCmd -> "COUNT ${c.queryId} ${c.filters.map { describe(it) }}"
            is EventCmd -> "EVENT ${describe(c.event)} ${c.eventJson}"
            is CloseCmd -> "CLOSE ${c.subId}"
            is AuthCmd -> "AUTH ${describe(c.event)}"
            is NegOpenCmd -> "NEG-OPEN ${c.subId} ${describe(c.filter)} ${c.initialMessage}"
            is NegMsgCmd -> "NEG-MSG ${c.subId} ${c.message}"
            is NegCloseCmd -> "NEG-CLOSE ${c.subId}"
            else -> "?? ${c::class}"
        }

    private fun describe(r: Rumor): String = "Rumor(${r.id},${r.pubKey},${r.createdAt},${r.kind},${r.tags?.joinToString("|") { it.joinToString(",") }},${r.content})"

    private class Tally(
        val name: String,
    ) {
        var same = 0
        var bothReject = 0
        val onlyJackson = ArrayList<String>()
        val readerOnly = ArrayList<String>()
        val different = ArrayList<String>()

        fun report() {
            println("$name: same $same, both reject $bothReject, only Jackson ${onlyJackson.size}, only reader ${readerOnly.size}, different ${different.size}")
            onlyJackson.take(12).forEach { println("    only Jackson: ${it.take(220)}") }
        }
    }

    private fun <T> run(block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            null
        }

    private fun compare(
        input: String,
        t: Tally,
        jackson: (String) -> String?,
        reader: (String) -> String?,
    ) {
        val j = jackson(input)
        val r = reader(input)
        when {
            j != null && r != null -> if (j == r) t.same++ else t.different += "$input\n      jackson: $j\n      reader:  $r"
            j == null && r == null -> t.bothReject++
            j != null -> t.onlyJackson += input
            else -> t.readerOnly += "$input\n      reader: $r"
        }
    }

    private fun assertParity(t: Tally) {
        t.report()
        if (t.different.isNotEmpty()) fail("${t.name}: ${t.different.size} frames build different results, e.g.\n${t.different.take(5).joinToString("\n")}")
        if (t.readerOnly.isNotEmpty()) fail("${t.name}: ${t.readerOnly.size} frames only the reader accepts, e.g.\n${t.readerOnly.take(5).joinToString("\n")}")
    }

    private fun mutate(
        inputs: List<String>,
        perInput: Int,
        seed: Int,
    ): List<String> {
        val rnd = Random(seed)
        val out = ArrayList<String>()
        for (json in inputs) {
            repeat(perInput) {
                val sb = StringBuilder(json)
                repeat(1 + rnd.nextInt(3)) {
                    if (sb.isEmpty()) return@repeat
                    val p = rnd.nextInt(sb.length)
                    when (rnd.nextInt(4)) {
                        0 -> sb.deleteCharAt(p)
                        1 -> sb.insert(p, MUTATION_CHARS[rnd.nextInt(MUTATION_CHARS.length)])
                        2 -> sb.setCharAt(p, MUTATION_CHARS[rnd.nextInt(MUTATION_CHARS.length)])
                        else -> {
                            val q = rnd.nextInt(sb.length)
                            val c = sb[p]
                            sb.setCharAt(p, sb[q])
                            sb.setCharAt(q, c)
                        }
                    }
                }
                out += sb.toString()
            }
        }
        return out
    }

    private fun capturedEvents(count: Int): List<String> {
        val out = ArrayList<String>(count)
        val stream = javaClass.classLoader?.getResourceAsStream(CORPUS) ?: throw IllegalStateException("corpus $CORPUS missing")
        GZIPInputStream(stream).bufferedReader().use { reader ->
            val buf = CharArray(1 shl 16)
            val sb = StringBuilder()
            var depth = 0
            var inStr = false
            var esc = false
            var n = reader.read(buf)
            while (n > 0 && out.size < count) {
                for (i in 0 until n) {
                    val c = buf[i]
                    if (depth > 0) sb.append(c)
                    when {
                        esc -> esc = false
                        c == '\\' && inStr -> esc = true
                        c == '"' -> inStr = !inStr
                        inStr -> {}
                        c == '{' -> {
                            if (depth == 0) sb.append(c)
                            depth++
                        }
                        c == '}' -> {
                            depth--
                            if (depth == 0) {
                                if (sb.length < 3000 && out.size < count) out += sb.toString()
                                sb.setLength(0)
                            }
                        }
                    }
                }
                n = reader.read(buf)
            }
        }
        return out
    }

    // ---- messages

    private fun messageFrames(events: List<String>): List<String> {
        val ev = events.first()
        val hll = (0 until 256).joinToString("") { (it % 16).toString(16) + ((it * 7) % 16).toString(16) }
        val f = ArrayList<String>()
        events.take(150).forEach { f += """["EVENT","sub-${it.length}",$it]""" }
        f +=
            listOf(
                """["EVENT","s",$ev,"extra",{"a":[1]}]""",
                """ [ "EVENT" , "s\"x" , $ev ] """,
                """["EVENT","s",$ev] trailing""",
                """["EOSE","s"]""",
                """["EOSE","s",["complete","partial"]]""",
                """["EOSE","s",[]]""",
                """["EOSE","s",["a",1,null,true,["x"],{"b":"c"},"d"]]""",
                """["EOSE","s",{"a":[1,2]},"x"]""",
                """["EOSE","s","notalist"]""",
                """["EOSE","s",null]""",
                """["EOSE","sA",["h\n"]]""",
                """["NOTICE","hello"]""",
                """["NOTICE","esc\"aped é 😀"]""",
                """["NOTICE",""]""",
                """["NOTICE","x","extra"]""",
                """["NOTICE",1]""",
                """["NOTICE"]""",
                """["OK","abc",true,""]""",
                """["OK","abc",false,"blocked: no"]""",
                """["OK","abc",true]""",
                """["OK","abc",true,null]""",
                """["OK","abc",true,5]""",
                """["OK","abc",true,false]""",
                """["OK","abc",false,"rate-limited: slow down","1500"]""",
                """["OK","abc",false,"rate-limited: slow down",1500]""",
                """["OK","abc",false,"rate-limited: slow down",-0]""",
                """["OK","abc",false,"rate-limited",1.5]""",
                """["OK","abc",false,"rate-limited",1e3]""",
                """["OK","abc",false,"rate-limited",null]""",
                """["OK","abc",false,"rate-limited",true]""",
                """["OK","abc",false,"rate-limited",{"a":1}]""",
                """["OK","abc",false,"rate-limited",[1,2]]""",
                """["OK","abc",false,"x","p","extra",[]]""",
                """["OK","abc","true",""]""",
                """["OK","abc",1,""]""",
                """["OK","abc"]""",
                """["OK",5,true,""]""",
                """["OK","abc",true,{"a":1}]""",
                """["OK","abc",true,["x"]]""",
                """["AUTH","challenge-123"]""",
                """["AUTH",""]""",
                """["AUTH",null]""",
                """["NOTIFY","you have mail"]""",
                """["CLOSED","s","error: shutting down"]""",
                """["CLOSED","s"]""",
                """["CLOSED","s",null]""",
                """["CLOSED","s","rate-limited: wait","5000"]""",
                """["CLOSED","s","rate-limited: wait",5000]""",
                """["CLOSED","s","x",2.5]""",
                """["COUNT","q",{"count":5}]""",
                """["COUNT","q",{"count":5,"approximate":true,"hll":"$hll"}]""",
                """["COUNT","q",{"count":5,"approximate":false,"hll":"abc"}]""",
                """["COUNT","q",{"count":7.9}]""",
                """["COUNT","q",{"count":-3}]""",
                """["COUNT","q",{"count":"12"}]""",
                """["COUNT","q",{"count":" 13 "}]""",
                """["COUNT","q",{"count":"+14"}]""",
                """["COUNT","q",{"count":"1.5"}]""",
                """["COUNT","q",{"count":"abc"}]""",
                """["COUNT","q",{"count":true}]""",
                """["COUNT","q",{"count":false}]""",
                """["COUNT","q",{"count":null}]""",
                """["COUNT","q",{"count":[1]}]""",
                """["COUNT","q",{"count":{"a":1}}]""",
                """["COUNT","q",{"count":3000000000}]""",
                """["COUNT","q",{"count":1,"approximate":1}]""",
                """["COUNT","q",{"count":1,"approximate":0}]""",
                """["COUNT","q",{"count":1,"approximate":"true"}]""",
                """["COUNT","q",{"count":1,"approximate":" false "}]""",
                """["COUNT","q",{"count":1,"approximate":"yes"}]""",
                """["COUNT","q",{"count":1,"approximate":2.5}]""",
                """["COUNT","q",{"count":1,"approximate":null}]""",
                """["COUNT","q",{"count":1,"approximate":99999999999999999999}]""",
                """["COUNT","q",{"count":1,"hll":5}]""",
                """["COUNT","q",{"count":1,"hll":null}]""",
                """["COUNT","q",{"count":1,"hll":["x"]}]""",
                """["COUNT","q",{"count":1,"other":{"x":[1,{"y":2}]}}]""",
                """["COUNT","q",{"count":1,"count":2}]""",
                """["COUNT","q",{}]""",
                """["COUNT","q",5]""",
                """["COUNT","q","x"]""",
                """["COUNT","q",null]""",
                """["COUNT","q",[1,2]]""",
                """["COUNT","q"]""",
                """["LIMITS",{"can_write":true,"can_read":false,"auth_for_read":true,"auth_for_write":false,"accepted_event_kinds":[1,7,30023],"blocked_event_kinds":[4],"min_pow_difficulty":16,"max_message_length":65536,"max_subscriptions":20,"max_filters":10,"max_limit":500,"max_event_tags":2000,"max_content_length":8196,"created_at_msecs_ago":94608000000,"created_at_msecs_ahead":900000,"filter_rate_limit":10,"publishing_rate_limit":5,"required_tags":[["t","nostr"],["client"]]}]""",
                """["LIMITS",{}]""",
                """["LIMITS"]""",
                """["LIMITS","x"]""",
                """["LIMITS",null]""",
                """["LIMITS",[1]]""",
                """["LIMITS",{"can_write":"true","can_read":1,"max_limit":"500","max_filters":null,"min_pow_difficulty":[1]}]""",
                """["LIMITS",{"max_limit":12.9,"filter_rate_limit":3.7,"created_at_msecs_ago":-5}]""",
                """["LIMITS",{"max_limit":3000000000}]""",
                """["LIMITS",{"filter_rate_limit":99999999999999999999}]""",
                """["LIMITS",{"accepted_event_kinds":[1,"7",null,2.5,[3],{"a":4},5]}]""",
                """["LIMITS",{"accepted_event_kinds":"1,7"}]""",
                """["LIMITS",{"accepted_event_kinds":[3000000000]}]""",
                """["LIMITS",{"required_tags":[["t",1,true,null,"x"],"notatag",5,[],[["nested"]],[{"o":1}]]}]""",
                """["LIMITS",{"required_tags":[["-0","007x",99999999999999999999]]}]""",
                """["LIMITS",{"required_tags":[[1.5]]}]""",
                """["LIMITS",{"required_tags":{}}]""",
                """["LIMITS",{"unknown":{"deep":[[[1]]]},"can_write":true,"can_write":"no"}]""",
                """["NEG-MSG","s","6100000200"]""",
                """["NEG-MSG","s"]""",
                """["NEG-MSG","s",5]""",
                """["NEG-ERR","s","blocked: too many"]""",
                """["NEG-ERR","s","blocked: too many",500000]""",
                """["NEG-ERR","s","blocked",5.5]""",
                """["NEG-ERR","s","blocked","500"]""",
                """["NEG-ERR","s","blocked",99999999999999999999]""",
                """["NEG-ERR","s","blocked",null]""",
                """["NEG-ERR","s","blocked",{"a":1}]""",
                """["NEG-ERR","s"]""",
                """["NEG-ERR","s",null,5]""",
                """["NEG-ERR","s",7,5]""",
                """["UNKNOWN","x"]""",
                """[5,"x"]""",
                """[]""",
                """{}""",
                """"EVENT"""",
                """["EVENT","s",$ev]""",
                """["NOTICE","x"]""",
            )
        return f
    }

    @Test
    fun everyMessageTypeParsesTheSame() {
        val events = capturedEvents(400)
        val frames = messageFrames(events)
        val jackson = { s: String -> run { describe(JacksonMapper.fromJsonToMessage(s)) } }
        val reader = { s: String -> run { describe(NostrJsonReader.readMessage(s)) } }

        val base = Tally("message frames")
        frames.forEach { compare(it, base, jackson, reader) }
        assertParity(base)
        assertTrue(base.same > 200)

        val edits = Tally("message frame edits")
        mutate(frames.drop(140), 150, 1).forEach { compare(it, edits, jackson, reader) }
        mutate(frames.take(140), 10, 2).forEach { compare(it, edits, jackson, reader) }
        assertParity(edits)
    }

    // ---- commands

    private fun commandFrames(events: List<String>): List<String> {
        val ev = events.first()
        val auth = """{"id":"ab","pubkey":"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798","created_at":1700000000,"kind":22242,"tags":[["relay","wss://r.example"],["challenge","c1"]],"content":"","sig":"cd"}"""
        val parsed = events.take(60).map { JacksonMapper.fromJson(it) }
        val f = ArrayList<String>()
        parsed.forEach { e ->
            val p = e.tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1) ?: e.pubKey
            f += """["REQ","s1",{"ids":["${e.id}"]},{"authors":["${e.pubKey}"],"kinds":[${e.kind}],"since":${e.createdAt - 100},"until":${e.createdAt},"limit":20},{"#p":["$p"],"#e":["${e.id}"]}]"""
        }
        events.take(60).forEach { f += """["EVENT",$it]""" }
        f +=
            listOf(
                """["REQ","s",{"kinds":[1,6,7],"limit":100}]""",
                """["REQ","s"]""",
                """["REQ","s",{}]""",
                """["REQ","s",{"search":"bitcoin lang:en"}]""",
                """["REQ","s",{"search":5}]""",
                """["REQ","s",{"search":1.50}]""",
                """["REQ","s",{"search":true}]""",
                """["REQ","s",{"search":null}]""",
                """["REQ","s",{"search":{"a":1}}]""",
                """["REQ","s",{"search":["a"]}]""",
                """["REQ","s",{"ids":null,"authors":"x","kinds":{"a":1}}]""",
                """["REQ","s",{"ids":["a",1,null,true,["b"],{"c":"d"},"e"]}]""",
                """["REQ","s",{"kinds":[1.9,"x",null,[2],{"k":3},4,-5]}]""",
                """["REQ","s",{"kinds":[3000000000]}]""",
                """["REQ","s",{"kinds":[1e3]}]""",
                """["REQ","s",{"kinds":[1e20]}]""",
                """["REQ","s",{"kinds":[]}]""",
                """["REQ","s",{"since":1.7e9,"until":1700000000.9}]""",
                """["REQ","s",{"since":null,"until":null,"limit":null}]""",
                """["REQ","s",{"since":5,"since":null}]""",
                """["REQ","s",{"since":"5"}]""",
                """["REQ","s",{"since":true}]""",
                """["REQ","s",{"since":[5]}]""",
                """["REQ","s",{"since":99999999999999999999}]""",
                """["REQ","s",{"limit":10.9}]""",
                """["REQ","s",{"limit":3000000000}]""",
                """["REQ","s",{"limit":"10"}]""",
                """["REQ","s",{"#t":["nostr","bitcoin"],"&t":["a","b"],"#e":[]}]""",
                """["REQ","s",{"#":["x"],"&":["y"],"#t":null,"#p":"x"}]""",
                """["REQ","s",{"#t":["a"],"#t":["b"],"#u":["c"]}]""",
                """["REQ","s",{"#t":["x"],"ids":["y"]}]""",
                """["REQ","s",{"unknown":{"deep":[1,{"x":[2]}]},"kinds":[1]}]""",
                """["REQ","s",{"kinds":[1]},{"kinds":[2]},{"kinds":[3]}]""",
                """["REQ","s","notafilter"]""",
                """["REQ","s",{"kinds":[1]},5]""",
                """["REQ","s",null]""",
                """["REQ",5,{}]""",
                """["REQ"]""",
                """["COUNT","q",{"kinds":[1],"#p":["abc"]}]""",
                """["COUNT","q"]""",
                """["COUNT",null,{}]""",
                """["EVENT",$ev,"extra"]""",
                """["EVENT","notanevent"]""",
                """["EVENT"]""",
                """["EVENT",{}]""",
                """["CLOSE","s"]""",
                """["CLOSE","s","extra",[1]]""",
                """["CLOSE",5]""",
                """["CLOSE"]""",
                """["AUTH",$auth]""",
                """["AUTH",$ev]""",
                """["AUTH","x"]""",
                """["NEG-OPEN","n",{"kinds":[1]},"6100000200"]""",
                """["NEG-OPEN","n",{"kinds":[1]}]""",
                """["NEG-OPEN","n","x","6100"]""",
                """["NEG-OPEN","n",{"kinds":[1]},5]""",
                """["NEG-MSG","n","6100"]""",
                """["NEG-MSG","n"]""",
                """["NEG-CLOSE","n"]""",
                """["NEG-CLOSE",null]""",
                """["NOTREAL","x"]""",
                """[null]""",
                """[]""",
            )
        return f
    }

    @Test
    fun everyCommandTypeParsesTheSame() {
        val events = capturedEvents(400)
        val frames = commandFrames(events)
        val jackson = { s: String -> run { describe(JacksonMapper.fromJsonToCommand(s)) } }
        val reader = { s: String -> run { describe(NostrJsonReader.readCommand(s)) } }

        val base = Tally("command frames")
        frames.forEach { compare(it, base, jackson, reader) }
        assertParity(base)
        assertTrue(base.same > 100)

        val edits = Tally("command frame edits")
        mutate(frames.drop(120), 150, 3).forEach { compare(it, edits, jackson, reader) }
        mutate(frames.take(120), 10, 4).forEach { compare(it, edits, jackson, reader) }
        assertParity(edits)
    }

    // ---- rumors and event lists

    @Test
    fun rumorsParseTheSame() {
        val events = capturedEvents(600)
        val rumors = ArrayList<String>()
        events.forEach { rumors += it.replace(Regex(""","sig":"[0-9a-f]*""""), "") }
        rumors += listOf("{}", """{"content":"x"}""", """{"kind":1,"tags":[]}""", """{"sig":"abc","kind":"x"}""", """{"pubkey":null}""", "[]", "null")

        val jackson = { s: String -> run { describe(JacksonMapper.fromJsonToRumor(s)) } }
        val reader = { s: String -> run { describe(NostrJsonReader.readRumor(s)) } }
        val base = Tally("rumors")
        rumors.forEach { compare(it, base, jackson, reader) }
        assertParity(base)
        assertTrue(base.same > 500)

        val edits = Tally("rumor edits")
        mutate(rumors.take(300), 30, 5).forEach { compare(it, edits, jackson, reader) }
        assertParity(edits)
    }

    @Test
    fun eventListsParseTheSame() {
        val events = capturedEvents(300)
        val lists = ArrayList<String>()
        events.chunked(10).forEach { lists += it.joinToString(",", "[", "]") }
        lists += listOf("[]", " [ ] ", events.first(), "[${events.first()}] trailing", "[null]", "[1]", "[${events.first()},]", "{}", "5")

        val jackson = { s: String -> run { JacksonMapper.fromJsonToEventList(s).joinToString("\n") { describe(it) } } }
        val reader = { s: String -> run { NostrJsonReader.readEventList(s).joinToString("\n") { describe(it) } } }
        val base = Tally("event lists")
        lists.forEach { compare(it, base, jackson, reader) }
        assertParity(base)

        val edits = Tally("event list edits")
        mutate(lists, 100, 6).forEach { compare(it, edits, jackson, reader) }
        assertParity(edits)
    }

    companion object {
        const val CORPUS = "nostr_vitor_startup_data.json.gz"
        const val MUTATION_CHARS = "\"\\{}[],: \n\tau0-9.eE+nlftré\uD83D❤"
    }
}
