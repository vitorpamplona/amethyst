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

import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519

// The elliptic-curve primitives on their own.
//
// These exist because a JFR CPU profile of create_group is not trustworthy
// here: JFR's execution sampler is safepoint-biased, and the tight counted
// loops in the field arithmetic carry no safepoint polls, so samples pile up
// on whichever method happens to follow the poll rather than the one burning
// the time. It put 75% of samples in car25519; peeling the modulo and the
// branch out of car25519 then changed nothing measurable, which is the profile
// telling on itself.
//
// Timing each primitive end to end needs no profiler to be believed, and
// multiplying by how many of them an operation performs says how much of that
// operation is curve work and how much is everything else.

private val ED_KEYS = Ed25519.generateKeyPair()
private val X_KEYS = X25519.generateKeyPair()
private val X_PEER = X25519.generateKeyPair()
private val MESSAGE = ByteArray(256) { it.toByte() }
private val SIGNATURE = Ed25519.sign(MESSAGE, ED_KEYS.privateKey)

/** One X25519 scalar multiplication against a supplied point — the ladder. */
fun benchX25519Dh(): BenchResult =
    measure(name = "x25519_dh", iterations = 500, warmup = 200, setup = { Unit }) {
        X25519.dh(X_KEYS.privateKey, X_PEER.publicKey)
    }

/** X25519 scalar multiplication against the base point. */
fun benchX25519Base(): BenchResult =
    measure(name = "x25519_base", iterations = 500, warmup = 200, setup = { Unit }) {
        X25519.publicFromPrivate(X_KEYS.privateKey)
    }

/** Ed25519 signing — one base-point scalar multiplication plus hashing. */
fun benchEd25519Sign(): BenchResult =
    measure(name = "ed25519_sign", iterations = 500, warmup = 200, setup = { Unit }) {
        Ed25519.sign(MESSAGE, ED_KEYS.privateKey)
    }

/** Ed25519 verification — two scalar multiplications plus a decompression. */
fun benchEd25519Verify(): BenchResult =
    measure(name = "ed25519_verify", iterations = 500, warmup = 200, setup = { Unit }) {
        Ed25519.verify(MESSAGE, SIGNATURE, ED_KEYS.publicKey)
    }

fun primitiveBenchmarks(): List<Pair<String, () -> BenchResult>> =
    listOf(
        "x25519_dh" to { benchX25519Dh() },
        "x25519_base" to { benchX25519Base() },
        "ed25519_sign" to { benchEd25519Sign() },
        "ed25519_verify" to { benchEd25519Verify() },
    )
