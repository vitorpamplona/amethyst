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

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel

private fun micros(nanos: Long) = nanos / 1000.0

private fun kb(bytes: Long) = bytes / 1024.0

fun main(args: Array<String>) {
    val json = args.contains("--json")

    // `--only=<substring>` narrows the run to matching rows. Mostly for
    // profiling, where mixing every benchmark's samples into one recording
    // hides the operation you are actually asking about.
    val only = args.firstOrNull { it.startsWith("--only=") }?.substringAfter("=")

    // Quartz logs at DEBUG by default, and those lines land INSIDE the measured
    // window: they cost time, and the string building they do is charged to the
    // benchmark thread's allocation counter. Measuring the logger instead of
    // the engine would make every number here fiction.
    Log.minLevel = LogLevel.ERROR

    if (args.contains("--epoch-probe")) {
        epochProbe()
        return
    }

    val results = allBenchmarks(only)

    if (json) {
        println("[")
        results.forEachIndexed { i, r ->
            val comma = if (i == results.size - 1) "" else ","
            println(
                """  {"name":"${r.name}","p50_us":${"%.1f".format(micros(r.p50))},""" +
                    """"p90_us":${"%.1f".format(micros(r.p90))},"p99_us":${"%.1f".format(micros(r.p99))},""" +
                    """"mean_us":${"%.1f".format(micros(r.mean))},"bytes_per_op":${r.bytesPerOp},""" +
                    """"iterations":${r.iterations}}$comma""",
            )
        }
        println("]")
        return
    }

    println("quartz Marmot — latency and allocation per operation")
    println("(alloc is bytes the operation allocated on the calling thread — GC pressure, not heap footprint)")
    println()
    println("%-28s %10s %10s %10s %12s".format("benchmark", "p50", "p90", "p99", "alloc/op"))
    println("-".repeat(74))
    results.forEach { r ->
        println(
            "%-28s %9.1fµs %9.1fµs %9.1fµs %10.1f KB".format(
                r.name,
                micros(r.p50),
                micros(r.p90),
                micros(r.p99),
                kb(r.bytesPerOp),
            ),
        )
    }
}
