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
package com.vitorpamplona.quartz.nip64Chess.end

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.end.tags.ResultTag
import com.vitorpamplona.quartz.nip64Chess.end.tags.TerminationTag
import com.vitorpamplona.quartz.nip64Chess.end.tags.WinnerTag

fun TagArrayBuilder<LiveChessGameEndEvent>.result(result: GameResult) = addUnique(ResultTag.assemble(result))

fun TagArrayBuilder<LiveChessGameEndEvent>.termination(termination: GameTermination) = addUnique(TerminationTag.assemble(termination))

fun TagArrayBuilder<LiveChessGameEndEvent>.winner(winnerPubkey: HexKey) = addUnique(WinnerTag.assemble(winnerPubkey))
