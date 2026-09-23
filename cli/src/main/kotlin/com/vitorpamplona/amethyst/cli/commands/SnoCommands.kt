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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoAvatarEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoAvatarWork
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteRef
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoParser
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoResult
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.math.abs

/**
 * `amy sno …` — read Simple Nostr Objects (DECK-0003), local and accountless.
 *
 * Thin assembly only: every rule lives in quartz's `cyberspace/deck0003Sno/`.
 * These verbs exist so the Kotlin reader can be diffed against the deck's own
 * `sno-reference.py` and against cyberspace-cli without a device or a relay —
 * see `cli/tests/sno/`.
 */
object SnoCommands {
    val USAGE: String =
        """
        |amy sno — Simple Nostr Objects, DECK-0003 (local, accountless)
        |
        |  sno parse [PAYLOAD|-]        validate a payload against §1.9
        |  sno work [PAYLOAD|-]         the proof of work an avatar owes for it (§8.10)
        |  sno verify [EVENT|-]         whether a kind 11333 avatar event has paid
        |
        |Each reads its argument as JSON, or from stdin when it is omitted or `-`.
        """.trimMargin()

    suspend fun dispatch(tail: Array<String>): Int =
        route(
            "sno",
            tail,
            "sno <parse|work|verify>",
            mapOf(
                "parse" to { rest -> parse(rest) },
                "work" to { rest -> work(rest) },
                "verify" to { rest -> verify(rest) },
            ),
            USAGE,
        )

    /**
     * Validate a payload. `valid` is the answer; on a refusal `rule` names the
     * numbered rule of §1.9 that failed, which is what makes a verdict
     * comparable with `sno-reference.py`'s.
     */
    private fun parse(rest: Array<String>): Int {
        val args = Args(rest)
        val json = RawEventSupport.readArgOrStdin(args)
        args.rejectUnknown()

        return when (val result = SnoParser.parse(json)) {
            is SnoResult.Invalid -> {
                Output.emit(mapOf("valid" to false, "rule" to result.rule, "reason" to result.reason))
                0
            }
            is SnoResult.Valid -> {
                Output.emit(mapOf("valid" to true) + facts(result.payload))
                0
            }
        }
    }

    private fun work(rest: Array<String>): Int {
        val args = Args(rest)
        val json = RawEventSupport.readArgOrStdin(args)
        args.rejectUnknown()

        val payload =
            SnoParser.parse(json).payloadOrNull()
                ?: return Output.error("bad_payload", "not a valid SNO payload")

        Output.emit(
            mapOf(
                "required" to SnoAvatarWork.required(payload),
                "reach_ticks" to reachTicks(payload),
                "unit" to payload.unit,
                "detail" to maxOf(SnoAvatarWork.DETAIL_FREE, payload.vertexCount + payload.faceCount),
            ),
        )
        return 0
    }

    /**
     * Whether an avatar event may be drawn. The keys mirror
     * `verify_avatar_work` in the reference implementations so the two can be
     * diffed directly.
     */
    private fun verify(rest: Array<String>): Int {
        val args = Args(rest)
        val json = RawEventSupport.readArgOrStdin(args)
        args.rejectUnknown()

        val event =
            try {
                Event.fromJson(json)
            } catch (_: Exception) {
                return Output.error("bad_event", "not a nostr event")
            }

        val avatar =
            event as? SnoAvatarEvent
                ?: return Output
                    .emit(
                        mapOf("ok" to false, "required" to 0, "committed" to null, "zeros" to 0, "reason" to "not-an-avatar"),
                    ).let { 0 }

        val payment = avatar.payment()
        Output.emit(
            mapOf(
                "ok" to payment.ok,
                "required" to payment.required,
                "committed" to payment.committed,
                "zeros" to payment.zeros,
                "reason" to payment.reason.code,
            ),
        )
        return 0
    }

    /** The facts a conformance diff cares about, and nothing else. */
    private fun facts(payload: SnoPayload): Map<String, Any?> =
        mapOf(
            "version" to payload.version,
            "name" to payload.name,
            "unit" to payload.unit,
            "extent" to payload.extent,
            "mode" to payload.mode.code,
            "vertices" to payload.vertexCount,
            "faces" to payload.faceCount,
            "has_face_colors" to (payload.faceColors != null),
            "palette" to
                when (payload.paletteRef) {
                    SnoPaletteRef.BuiltIn -> "built-in"
                    is SnoPaletteRef.Inline -> "inline"
                    is SnoPaletteRef.Event -> "event"
                },
            "up" to payload.up,
            "spin" to payload.spin,
            "reach_ticks" to reachTicks(payload),
        )

    /**
     * The farthest any vertex lies from the build origin, in ticks — the
     * integer half of §8.10's reach, before the scale exponent is applied.
     * Reported as an integer so a diff never argues about float formatting.
     */
    private fun reachTicks(payload: SnoPayload): Int {
        var farthest = 0
        for (tick in payload.positions) {
            val magnitude = abs(tick)
            if (magnitude > farthest) farthest = magnitude
        }
        return farthest
    }
}
