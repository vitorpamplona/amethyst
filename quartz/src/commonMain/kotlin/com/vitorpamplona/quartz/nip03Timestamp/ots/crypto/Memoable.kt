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
package com.vitorpamplona.quartz.nip03Timestamp.ots.crypto

/**
 * Interface for Memoable objects. Memoable objects allow the taking of a snapshot of their internal state
 * via the [copy()][.copy] method and then resetting the object back to that state later using the
 * [reset()][.reset] method.
 */
interface Memoable {
    /**
     * Produce a copy of this object with its configuration and in its current state.
     *
     *
     * The returned object may be used simply to store the state, or may be used as a similar object
     * starting from the copied state.
     *
     * @return Memoable object
     */
    fun copy(): Memoable

    /**
     * Restore a copied object state into this object.
     *
     *
     * Implementations of this method *should* try to avoid or minimise memory allocation to perform the reset.
     *
     * @param other an object originally [copied][.copy] from an object of the same type as this instance.
     * @throws ClassCastException if the provided object is not of the correct type.
     */
    fun reset(other: Memoable)
}
