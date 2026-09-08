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
package com.vitorpamplona.quartz.marmot.appComponents

/**
 * `marmot.group.lifecycle.v1`, component `0x800c` — whether the group has been
 * terminated.
 *
 * ```text
 * enum { active(0), disbanded(1) } MarmotGroupLifecycleV1;
 * ```
 *
 * Exactly one byte. Every other value, and any encoding with trailing bytes, is
 * invalid.
 *
 * `disbanded` is absorbing: once a disband Commit is selected, clients do not
 * process later group traffic, do not select a later branch, and cannot rejoin
 * the same group id. A replacement conversation is a new MLS group.
 *
 * A group predating this component may omit it and remains valid — it simply
 * cannot be disbanded. An admin enables disbanding with one Commit that adds
 * the `active` state and adds `0x800c` to the required component list; every
 * resulting member leaf must advertise support for it. Once required, it stays
 * required for the group's lifetime.
 *
 * The convergence handling of a disband Commit is deliberately NOT here: a
 * valid disband candidate forces a bounded convergence pass even when it is a
 * linear edge, so terminalization can only happen after branch selection. That
 * belongs to the convergence engine (Stage 6).
 */
enum class GroupLifecycleV1(
    val code: Int,
) {
    ACTIVE(0),
    DISBANDED(1),
    ;

    fun encode(): ByteArray = byteArrayOf(code.toByte())

    companion object {
        const val COMPONENT_ID = AppComponentIds.GROUP_LIFECYCLE_V1

        fun decode(bytes: ByteArray): GroupLifecycleV1 {
            require(bytes.size == 1) {
                "group lifecycle component must be exactly 1 byte, was ${bytes.size}"
            }
            return when (bytes[0].toInt()) {
                ACTIVE.code -> ACTIVE
                DISBANDED.code -> DISBANDED
                else -> throw IllegalArgumentException("unknown group lifecycle state ${bytes[0]}")
            }
        }
    }
}
