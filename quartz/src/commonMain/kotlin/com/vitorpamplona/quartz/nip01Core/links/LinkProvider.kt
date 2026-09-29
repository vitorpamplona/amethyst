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
package com.vitorpamplona.quartz.nip01Core.links

/**
 * An event class that states what its references mean.
 *
 * Every class `EventFactory` builds implements this, directly or through a base class that
 * decides for its whole family, or declares [LinkFree]: there is no fallback that guesses a
 * meaning from a tag's shape (the same letter means different things by kind: a 64-hex `e` in a
 * chess start is a board hash, `r` in 10002 holds relay URLs). `LinkCoverageTest` enforces it,
 * so a new kind cannot land without that decision.
 *
 * [links] returns only this class's own statements. [allLinks] adds what every event states:
 * its `AUTHOR`, its `ADDRESS` when it has one, and the tags any kind may carry (`client`, `zap`,
 * the emoji tag's set).
 */
interface LinkProvider {
    fun links(): List<Link<*>>
}

/**
 * A class whose tags and content reference nothing beyond what every event states (settings,
 * metadata, relay and server lists, key material): it links only through [allLinks].
 */
interface LinkFree : LinkProvider {
    override fun links(): List<Link<*>> = emptyList()
}
