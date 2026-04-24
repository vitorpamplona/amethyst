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
import com.vitorpamplona.amethyst.cli.DataDir

/**
 * Tiny dispatcher over the per-verb command groups. Each top-level subcommand
 * (`init`, `relay`, `group`, `message`, …) gets its own file so no single
 * file is too big to edit safely.
 */
object Commands {
    suspend fun init(
        dataDir: DataDir,
        args: Args,
    ): Int = InitCommands.init(dataDir, args)

    suspend fun create(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = CreateCommand.run(dataDir, tail)

    suspend fun login(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = LoginCommand.run(dataDir, tail)

    suspend fun whoami(dataDir: DataDir): Int = InitCommands.whoami(dataDir)

    suspend fun relay(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = RelayCommands.dispatch(dataDir, tail)

    suspend fun keyPackage(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = KeyPackageCommands.dispatch(dataDir, tail)

    suspend fun group(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = GroupCommands.dispatch(dataDir, tail)

    suspend fun message(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = MessageCommands.dispatch(dataDir, tail)

    suspend fun await(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = AwaitCommands.dispatch(dataDir, tail)

    suspend fun reset(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = MarmotResetCommand.run(dataDir, tail)

    suspend fun dm(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = DmCommands.dispatch(dataDir, tail)

    suspend fun profile(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = ProfileCommands.dispatch(dataDir, tail)

    suspend fun post(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = PostCommand.run(dataDir, tail)

    suspend fun feed(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = FeedCommand.run(dataDir, tail)
}
