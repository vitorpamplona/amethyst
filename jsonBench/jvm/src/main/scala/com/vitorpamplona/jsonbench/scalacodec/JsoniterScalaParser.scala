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
package com.vitorpamplona.jsonbench.scalacodec

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._

import java.util.function.{Function => JFunction, ToLongFunction}

/** The event as jsoniter-scala users model it; the codec below is generated at compile time. */
final case class SEvent(
    id: String,
    pubkey: String,
    @named("created_at") createdAt: Long,
    kind: Int,
    tags: Array[Array[String]],
    content: String,
    sig: String
)

object SEvent {
  val codec: JsonValueCodec[SEvent] = JsonCodecMaker.make
}

/**
 * jsoniter-scala, driven from the Kotlin harness (JsoniterScala.kt) through JDK interfaces
 * so the two compilers never see each other's classes. The timed loop is here, in Scala,
 * so it is as monomorphic as the Kotlin ones.
 */
final class JsoniterScalaParser extends ToLongFunction[Array[String]] with JFunction[String, Array[AnyRef]] {
  private val codec = SEvent.codec

  override def applyAsLong(inputs: Array[String]): Long = {
    var sink = 0L
    var i = 0
    while (i < inputs.length) {
      val e = readFromString(inputs(i))(codec)
      sink += e.kind + e.tags.length + e.content.length
      i += 1
    }
    sink
  }

  /** One event's fields in Event's constructor order, for the digest. */
  override def apply(input: String): Array[AnyRef] = {
    val e = readFromString(input)(codec)
    Array[AnyRef](e.id, e.pubkey, java.lang.Long.valueOf(e.createdAt), java.lang.Integer.valueOf(e.kind), e.tags, e.content, e.sig)
  }
}
