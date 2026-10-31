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
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessEngine
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.GameStatus
import com.vitorpamplona.amethyst.commons.nip64Chess.acceptDrawTemplate
import com.vitorpamplona.amethyst.commons.nip64Chess.bot.ChessBots
import com.vitorpamplona.amethyst.commons.nip64Chess.chessGameFilters
import com.vitorpamplona.amethyst.commons.nip64Chess.claimAbandonmentTemplate
import com.vitorpamplona.amethyst.commons.nip64Chess.jesterGameEvents
import com.vitorpamplona.amethyst.commons.nip64Chess.moveTemplate
import com.vitorpamplona.amethyst.commons.nip64Chess.resignTemplate
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * `amy chess …`: Jester (kind 30) chess games, played through the same commons code as the app's
 * board (`ChessGameSnapshot`, `moveTemplate`), and a bot that plays a game to the end, for
 * testing the app and relays against.
 */
object ChessCommands {
    val USAGE: String =
        """
        |amy chess challenge [--opponent USER] [--color white|black] [--relay URL …]
        |amy chess show GAME_ID [--relay URL …]
        |amy chess move GAME_ID FROM TO [--offer-draw]   # squares, e.g. e2 e4; promote with e7 e8q
        |amy chess resign GAME_ID
        |amy chess accept-draw GAME_ID              # accept the opponent's standing draw offer
        |amy chess claim GAME_ID                    # claim a game whose opponent stopped moving
        |amy chess play GAME_ID [--engine random|minimax] [--depth N] [--seed N]
        |               [--max-plies N] [--think-ms MS] [--timeout SECS] [--poll-ms MS] [--relay URL …]
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "chess",
            tail = tail,
            usage = USAGE,
            help = USAGE,
            routes =
                mapOf(
                    "challenge" to { rest -> challenge(dataDir, rest) },
                    "show" to { rest -> show(dataDir, rest) },
                    "move" to { rest -> move(dataDir, rest) },
                    "resign" to { rest -> resign(dataDir, rest) },
                    "accept-draw" to { rest -> acceptDraw(dataDir, rest) },
                    "claim" to { rest -> claim(dataDir, rest) },
                    "play" to { rest -> play(dataDir, rest) },
                ),
        )

    private suspend fun challenge(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val opponentInput = args.flag("opponent")
        val color =
            when (args.flag("color", "white")!!.lowercase()) {
                "white" -> Color.WHITE
                "black" -> Color.BLACK
                else -> return Output.error("bad_args", "--color must be white or black")
            }
        val extraRelays = RawEventSupport.relayFlag(args)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val opponent = opponentInput?.let { ctx.requireUserHex(it) }
            val signed = ctx.signer.sign(JesterEvent.buildChallenge(color, opponent))
            val relays = ctx.outboxRelays() + extraRelays + (opponent?.let { inboxOf(ctx, it) } ?: emptySet())
            if (relays.isEmpty()) return Output.error("no_relays", "no outbox relays configured; pass --relay or run `amy relay add`")
            val ack = ctx.publish(signed, relays)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "game_id" to signed.id,
                    "color" to color.name.lowercase(),
                    "opponent" to opponent,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gameId = args.positional(0, "GAME_ID")
        val extraRelays = RawEventSupport.relayFlag(args)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val loaded = load(ctx, gameId, extraRelays) ?: return Output.error("not_found", "game $gameId not found")
            Output.emit(summary(loaded.game))
            return 0
        }
    }

    private suspend fun move(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gameId = args.positional(0, "GAME_ID")
        val from = args.positional(1, "FROM").lowercase()
        val to = args.positional(2, "TO").lowercase()
        val offerDraw = args.bool("offer-draw")
        val extraRelays = RawEventSupport.relayFlag(args)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val loaded = load(ctx, gameId, extraRelays) ?: return Output.error("not_found", "game $gameId not found")
            if (!loaded.game.isMyTurn) return Output.error("not_your_turn", "it is not your move in $gameId")
            val template = loaded.game.moveTemplate(from, to, offerDraw) ?: return Output.error("illegal_move", "$from$to is not a legal move")
            return publish(ctx, template, loaded, extraRelays)
        }
    }

    private suspend fun resign(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gameId = args.positional(0, "GAME_ID")
        val extraRelays = RawEventSupport.relayFlag(args)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val loaded = load(ctx, gameId, extraRelays) ?: return Output.error("not_found", "game $gameId not found")
            val template = loaded.game.resignTemplate() ?: return Output.error("not_a_player", "you hold no seat in an ongoing $gameId")
            return publish(ctx, template, loaded, extraRelays)
        }
    }

    private suspend fun acceptDraw(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gameId = args.positional(0, "GAME_ID")
        val extraRelays = RawEventSupport.relayFlag(args)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val loaded = load(ctx, gameId, extraRelays) ?: return Output.error("not_found", "game $gameId not found")
            val template = loaded.game.acceptDrawTemplate() ?: return Output.error("no_draw_offer", "your opponent has no standing draw offer in $gameId")
            return publish(ctx, template, loaded, extraRelays)
        }
    }

    private suspend fun claim(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gameId = args.positional(0, "GAME_ID")
        val extraRelays = RawEventSupport.relayFlag(args)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val loaded = load(ctx, gameId, extraRelays) ?: return Output.error("not_found", "game $gameId not found")
            val at = loaded.game.abandonmentClaimableAt ?: return Output.error("not_waiting", "you are not waiting on an opponent in $gameId")
            val template = loaded.game.claimAbandonmentTemplate() ?: return Output.error("too_early", "your opponent can still move until $at")
            return publish(ctx, template, loaded, extraRelays)
        }
    }

    /**
     * Plays [GAME_ID] with a bot until it ends, [--max-plies] half-moves have been played, or
     * [--timeout] passes (exit 124). Takes the open seat of an open challenge by moving in it.
     * Waits for each of its own moves to come back from the relays before thinking again, so a
     * slow relay can't make it play the same turn twice.
     */
    private suspend fun play(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gameId = args.positional(0, "GAME_ID")
        val engineName = args.flag("engine", ChessBots.MINIMAX)!!
        val depth = args.intFlag("depth", 2)
        val seed = args.flag("seed")?.toLongOrNull()
        val maxPlies = args.intFlag("max-plies", 200)
        val timeoutMs = args.timeoutMs(600)
        val pollMs = args.longFlag("poll-ms", 500)
        // A pause before each move, so a person watching the game in the app can follow it.
        val thinkMs = args.longFlag("think-ms", 0)
        val extraRelays = RawEventSupport.relayFlag(args)
        args.rejectUnknown()

        val bot =
            ChessBots.byName(engineName, depth, seed?.let { Random(it) } ?: Random.Default)
                ?: return Output.error("bad_args", "--engine must be ${ChessBots.RANDOM} or ${ChessBots.MINIMAX}")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val deadline = TimeUtils.now() * 1000 + timeoutMs
            var played = 0
            var waitingForPly = -1
            while (true) {
                val loaded = load(ctx, gameId, extraRelays, idleTimeoutMs = 2_000)
                val game = loaded?.game
                when {
                    game == null -> {}
                    game.isFinished || game.moveCount >= maxPlies -> {
                        Output.emit(summary(game) + mapOf("bot_moves" to played))
                        return 0
                    }
                    game.myColor == null -> return Output.error("not_a_player", "this account holds no seat in $gameId")
                    game.isMyTurn && game.moveCount > waitingForPly -> {
                        if (thinkMs > 0) delay(thinkMs)
                        val engine = ChessEngine()
                        engine.loadFen(game.engine.getFen())
                        val choice = bot.choose(engine) ?: return Output.error("runtime", "the bot found no move in ${game.engine.getFen()}")
                        val template = game.moveTemplate(choice.from, choice.to) ?: return Output.error("runtime", "the bot chose an illegal move: $choice")
                        val code = publish(ctx, template, loaded, extraRelays, emit = false)
                        if (code != 0) return code
                        System.err.println("played ${choice.from}${choice.to} (ply ${game.moveCount + 1})")
                        played++
                        waitingForPly = game.moveCount + 1
                        continue
                    }
                }
                if (TimeUtils.now() * 1000 > deadline) {
                    return Output.error("timeout", "game $gameId still going after ${timeoutMs / 1000}s")
                }
                delay(pollMs)
            }
        }
    }

    private class LoadedGame(
        val game: ChessGameSnapshot,
        val seenOn: Set<NormalizedRelayUrl>,
    )

    /** The game as the relays have it now, from the user's point of view; null until its start event is found. */
    private suspend fun load(
        ctx: Context,
        gameId: HexKey,
        extraRelays: Set<NormalizedRelayUrl>,
        idleTimeoutMs: Long = 8_000,
    ): LoadedGame? {
        val relays = ctx.anyRelays() + extraRelays
        val found = ctx.drain(relays.associateWith { chessGameFilters(gameId) }, idleTimeoutMs = idleTimeoutMs)
        val events = jesterGameEvents(gameId, found.map { it.second }) ?: return null
        val game = ChessGameSnapshot.build(events, ctx.signer.pubKey) ?: return null
        return LoadedGame(game, found.filter { it.second.id == gameId }.map { it.first }.toSet())
    }

    private suspend fun publish(
        ctx: Context,
        template: EventTemplate<*>,
        loaded: LoadedGame,
        extraRelays: Set<NormalizedRelayUrl>,
        emit: Boolean = true,
    ): Int {
        val signed: Event = ctx.signer.sign(template)
        // Where the game lives and where the opponent reads: what the app's broadcast planner adds for a move.
        val relays = ctx.outboxRelays() + loaded.seenOn + extraRelays + (loaded.game.opponentPubkey?.let { inboxOf(ctx, it) } ?: emptySet())
        val ack = ctx.publish(signed, relays)
        RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
        if (emit) Output.emit(mapOf("event_id" to signed.id, "game_id" to loaded.game.gameId) + RawEventSupport.ackFields(ack))
        return 0
    }

    private suspend fun inboxOf(
        ctx: Context,
        pubkey: HexKey,
    ): Set<NormalizedRelayUrl> = ctx.relaysOf(pubkey)?.readRelaysNorm()?.toSet() ?: emptySet()

    private fun summary(game: ChessGameSnapshot): Map<String, Any?> {
        val status = game.state.gameStatus
        return mapOf(
            "game_id" to game.gameId,
            "white" to game.whitePubkey,
            "black" to game.blackPubkey,
            "my_color" to game.myColor?.name?.lowercase(),
            "status" to
                when {
                    status is GameStatus.Finished -> "finished"
                    game.isOpenSeat -> "open"
                    else -> "in_progress"
                },
            "result" to (status as? GameStatus.Finished)?.result?.notation,
            "termination" to
                game.state.termination
                    ?.name
                    ?.lowercase(),
            "draw_offered_by" to game.drawOfferedBy?.name?.lowercase(),
            "claimable_at" to game.abandonmentClaimableAt,
            "to_move" to
                game.state.currentPosition.activeColor.name
                    .lowercase(),
            "your_turn" to game.isMyTurn,
            "plies" to game.moveCount,
            "moves" to game.state.moveHistory,
            "fen" to game.engine.getFen(),
        )
    }
}
