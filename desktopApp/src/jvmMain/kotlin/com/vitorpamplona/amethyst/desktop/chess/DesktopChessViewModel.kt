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
package com.vitorpamplona.amethyst.desktop.chess

import com.vitorpamplona.amethyst.commons.chess.ChessPollingDefaults
import com.vitorpamplona.amethyst.commons.chess.ChessPollingDelegate
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessSubscriptionController
import com.vitorpamplona.amethyst.commons.data.UserMetadataCache
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.PieceType
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Desktop ViewModel for managing chess game state and event publishing.
 * Adapts the Android ChessViewModel patterns for Desktop's relay architecture.
 */
class DesktopChessViewModel(
    private val account: AccountState.LoggedIn,
    private val relayManager: DesktopRelayConnectionManager,
    private val scope: CoroutineScope,
) {
    companion object {
        const val CHALLENGE_EXPIRY_SECONDS = 24 * 60 * 60L
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1000L
    }

    // Active games being played
    private val _activeGames = MutableStateFlow<Map<String, LiveChessGameState>>(emptyMap())
    val activeGames: StateFlow<Map<String, LiveChessGameState>> = _activeGames.asStateFlow()

    // Pending challenges
    private val _challenges = MutableStateFlow<List<LiveChessGameChallengeEvent>>(emptyList())
    val challenges: StateFlow<List<LiveChessGameChallengeEvent>> = _challenges.asStateFlow()

    // Badge count
    private val _badgeCount = MutableStateFlow(0)
    val badgeCount: StateFlow<Int> = _badgeCount.asStateFlow()

    // Currently selected game
    private val _selectedGameId = MutableStateFlow<String?>(null)
    val selectedGameId: StateFlow<String?> = _selectedGameId.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Refresh key - incrementing this triggers re-subscription
    private val _refreshKey = MutableStateFlow(0)
    val refreshKey: StateFlow<Int> = _refreshKey.asStateFlow()

    // Completed games history
    private val _completedGames = MutableStateFlow<List<CompletedGame>>(emptyList())
    val completedGames: StateFlow<List<CompletedGame>> = _completedGames.asStateFlow()

    // Shared user metadata cache
    val userMetadataCache = UserMetadataCache()

    // Pending moves waiting for game to be created (gameId -> list of (san, fen, moveNumber))
    private val pendingMoves = mutableMapOf<String, MutableList<Triple<String, String, Int?>>>()

    // Challenge events indexed by gameId for lookup during accept processing
    private val challengesByGameId = mutableMapOf<String, LiveChessGameChallengeEvent>()

    // Pending accept events waiting for challenge to arrive (gameId -> accept event)
    private val pendingAccepts = mutableMapOf<String, LiveChessGameAcceptEvent>()

    // Track event IDs we've already processed to avoid duplicates
    // Uses ConcurrentHashMap for thread-safe check-and-add
    private val processedEventIds =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()

    // Track games currently being created to prevent race conditions
    private val gamesBeingCreated = mutableSetOf<String>()

    // Subscription controller for dynamic filter updates
    private var subscriptionController: ChessSubscriptionController? = null

    // Polling delegate for periodic refresh (fallback when relay events are missed)
    private val pollingDelegate =
        ChessPollingDelegate(
            config = ChessPollingDefaults.desktop,
            scope = scope,
            onRefreshGames = { gameIds -> refreshGamesFromRelay(gameIds) },
            onRefreshChallenges = { /* Subscriptions handle challenges */ },
            onCleanup = { cleanupExpiredChallenges() },
        )

    init {
        // Start polling for move updates
        pollingDelegate.start()
    }

    /**
     * Set the subscription controller for dynamic filter updates.
     * Should be called after creating the ViewModel.
     */
    fun setSubscriptionController(controller: ChessSubscriptionController) {
        subscriptionController = controller
    }

    /**
     * Notify subscription controller and polling delegate of active game changes.
     * Call this whenever _activeGames is modified.
     */
    private fun notifyActiveGamesChanged() {
        val gameIds = _activeGames.value.keys
        subscriptionController?.updateActiveGames(
            activeGameIds = gameIds,
            spectatingGameIds = emptySet(),
        )
        pollingDelegate.setActiveGameIds(gameIds)
    }

    /**
     * Process incoming chess event from relay subscription
     */
    fun handleIncomingEvent(event: Event) {
        // Skip already processed events (atomic check-and-add)
        if (!processedEventIds.add(event.id)) return

        // Check by kind since events may not be cast to specific types
        when (event.kind) {
            MetadataEvent.KIND -> {
                handleMetadataEvent(event)
                return
            }
            LiveChessGameChallengeEvent.KIND -> {
                if (event is LiveChessGameChallengeEvent) {
                    handleNewChallenge(event)
                } else {
                    // Manually parse if not already the right type
                    val challenge =
                        LiveChessGameChallengeEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        )
                    handleNewChallenge(challenge)
                }
            }
            LiveChessGameAcceptEvent.KIND -> {
                if (event is LiveChessGameAcceptEvent) {
                    handleGameAccepted(event)
                } else {
                    val accept =
                        LiveChessGameAcceptEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        )
                    handleGameAccepted(accept)
                }
            }
            LiveChessMoveEvent.KIND -> {
                if (event is LiveChessMoveEvent) {
                    handleIncomingMove(event)
                } else {
                    val move =
                        LiveChessMoveEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        )
                    handleIncomingMove(move)
                }
            }
            LiveChessGameEndEvent.KIND -> {
                if (event is LiveChessGameEndEvent) {
                    handleGameEnded(event)
                } else {
                    val end =
                        LiveChessGameEndEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        )
                    handleGameEnded(end)
                }
            }
            LiveChessDrawOfferEvent.KIND -> {
                if (event is LiveChessDrawOfferEvent) {
                    handleDrawOffer(event)
                } else {
                    val drawOffer =
                        LiveChessDrawOfferEvent(
                            event.id,
                            event.pubKey,
                            event.createdAt,
                            event.tags,
                            event.content,
                            event.sig,
                        )
                    handleDrawOffer(drawOffer)
                }
            }
        }
    }

    private fun handleMetadataEvent(event: Event) {
        try {
            val metadataEvent =
                MetadataEvent(
                    event.id,
                    event.pubKey,
                    event.createdAt,
                    event.tags,
                    event.content,
                    event.sig,
                )
            val metadata = metadataEvent.contactMetaData()
            if (metadata != null) {
                userMetadataCache.put(event.pubKey, metadata)
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }

    private fun handleIncomingMove(event: LiveChessMoveEvent) {
        println("[Chess] Received move event: gameId=${event.gameId()}, san=${event.san()}, moveNum=${event.moveNumber()}, from=${event.pubKey.take(8)}")

        val gameId = event.gameId() ?: return
        val san = event.san() ?: return
        val fen = event.fen() ?: return
        val moveNumber = event.moveNumber()

        val gameState = _activeGames.value[gameId]

        if (gameState == null) {
            // Game doesn't exist yet - buffer the move for later (including our own for FEN sync)
            println("[Chess] Game $gameId not found, buffering move $san (fen for sync)")
            val moveList = pendingMoves.getOrPut(gameId) { mutableListOf() }
            moveList.add(Triple(san, fen, moveNumber))
            return
        }

        // Don't process our own moves for active games (already applied locally)
        if (event.pubKey == account.pubKeyHex) {
            println("[Chess] Skipping own move (already applied locally)")
            return
        }

        if (event.pubKey != gameState.opponentPubkey) {
            println("[Chess] Move not from opponent (expected ${gameState.opponentPubkey.take(8)}, got ${event.pubKey.take(8)})")
            return
        }

        println("[Chess] Applying opponent move: $san (move #$moveNumber)")
        gameState.applyOpponentMove(san, fen, moveNumber)
        updateBadgeCount()
    }

    private fun handleGameAccepted(event: LiveChessGameAcceptEvent) {
        val gameId = event.gameId() ?: return

        // Skip if game already exists
        if (_activeGames.value.containsKey(gameId)) return

        val challengerPubkey = event.challengerPubkey() ?: return
        val accepterPubkey = event.pubKey

        // Check if we have the challenge event for color info
        val challengeEvent = challengesByGameId[gameId]
        if (challengeEvent == null) {
            // Challenge hasn't arrived yet - buffer this accept for later
            if (challengerPubkey == account.pubKeyHex || accepterPubkey == account.pubKeyHex) {
                pendingAccepts[gameId] = event
            }
            return
        }

        // Handle if user is the challenger or accepter
        if (challengerPubkey == account.pubKeyHex) {
            startGameFromAcceptance(event, isChallenger = true)
        } else if (accepterPubkey == account.pubKeyHex) {
            startGameFromAcceptance(event, isChallenger = false)
        }
    }

    private fun handleGameEnded(event: LiveChessGameEndEvent) {
        val gameId = event.gameId() ?: return

        // Store game in history before removing (skip if already in completed list)
        val alreadyCompleted = _completedGames.value.any { it.gameId == gameId }
        val gameState = _activeGames.value[gameId]
        if (gameState != null && !alreadyCompleted) {
            val completedGame =
                CompletedGame(
                    gameId = gameId,
                    opponentPubkey = gameState.opponentPubkey,
                    playerColor = gameState.playerColor,
                    result = event.result() ?: "?",
                    termination = event.termination() ?: "unknown",
                    winnerPubkey = event.winnerPubkey(),
                    completedAt = event.createdAt,
                    moveCount = gameState.moveHistory.value.size,
                )
            _completedGames.value = listOf(completedGame) + _completedGames.value
        }

        // Remove from active games
        if (_activeGames.value.containsKey(gameId)) {
            _activeGames.value = _activeGames.value - gameId
            notifyActiveGamesChanged()
        }

        // Clean up challenge references
        _challenges.value = _challenges.value.filter { it.gameId() != gameId }
        challengesByGameId.remove(gameId)
        pendingAccepts.remove(gameId)
        pendingMoves.remove(gameId)

        updateBadgeCount()
    }

    private fun handleDrawOffer(event: LiveChessDrawOfferEvent) {
        // Only process draw offers from opponent
        if (event.pubKey == account.pubKeyHex) return

        val gameId = event.gameId() ?: return
        val gameState = _activeGames.value[gameId] ?: return

        // Verify this is from our opponent in this game
        if (event.pubKey != gameState.opponentPubkey) return

        // Pass to game state to track
        gameState.receiveDrawOffer(event.pubKey)
        updateBadgeCount()
    }

    private fun handleNewChallenge(event: LiveChessGameChallengeEvent) {
        val gameId = event.gameId() ?: return
        val opponentPubkey = event.opponentPubkey()
        val isUserChallenge = event.pubKey == account.pubKeyHex
        val isOpenChallenge = opponentPubkey == null
        val isDirectedAtUser = opponentPubkey == account.pubKeyHex

        // Request metadata for challenger
        userMetadataCache.request(event.pubKey)
        opponentPubkey?.let { userMetadataCache.request(it) }

        // Always store in lookup map for accept event processing (even if game exists)
        challengesByGameId[gameId] = event

        // Skip if game already exists (challenge was already accepted)
        if (_activeGames.value.containsKey(gameId)) return

        // Check if there's a pending accept waiting for this challenge
        val pendingAccept = pendingAccepts.remove(gameId)
        if (pendingAccept != null) {
            // Now we can properly process the accept
            val isChallenger = event.pubKey == account.pubKeyHex
            val isAccepter = pendingAccept.pubKey == account.pubKeyHex
            if (isChallenger || isAccepter) {
                startGameFromAcceptance(pendingAccept, isChallenger = isChallenger)
                return // Don't add to challenges list since game is starting
            }
        }

        // Show challenges that are:
        // - Created by user (their outgoing challenges)
        // - Open challenges (anyone can accept)
        // - Directed at user (incoming challenges)
        if (isUserChallenge || isOpenChallenge || isDirectedAtUser) {
            val currentChallenges = _challenges.value.toMutableList()
            // Deduplicate by both event ID and game ID to prevent duplicates from relay broadcasts
            val isDuplicateEvent = currentChallenges.any { it.id == event.id }
            val isDuplicateGame = currentChallenges.any { it.gameId() == gameId }
            if (!isDuplicateEvent && !isDuplicateGame) {
                currentChallenges.add(event)
                _challenges.value = currentChallenges
                updateBadgeCount()
            }
        }
    }

    /**
     * Create a new chess challenge
     */
    fun createChallenge(
        opponentPubkey: String? = null,
        playerColor: Color = Color.WHITE,
        timeControl: String? = null,
    ) {
        val gameId = generateGameId()

        scope.launch(Dispatchers.IO) {
            val success =
                retryWithBackoff {
                    val template =
                        LiveChessGameChallengeEvent.build(
                            gameId = gameId,
                            playerColor = playerColor,
                            opponentPubkey = opponentPubkey,
                            timeControl = timeControl,
                        )

                    val signedEvent = account.signer.sign(template)
                    relayManager.broadcastToAll(signedEvent)
                }

            if (success) {
                _error.value = null
            } else {
                _error.value = "Failed to create challenge after $MAX_RETRIES attempts"
            }
        }
    }

    /**
     * Accept a chess challenge
     */
    fun acceptChallenge(challengeEvent: LiveChessGameChallengeEvent) {
        val gameId = challengeEvent.gameId() ?: return
        val challengerPubkey = challengeEvent.pubKey
        val challengerColor = challengeEvent.playerColor() ?: Color.WHITE
        val playerColor = challengerColor.opposite()

        // Store in lookup map for consistent access
        challengesByGameId[gameId] = challengeEvent

        // Mark as being created to prevent duplicate from relay echo
        if (!gamesBeingCreated.add(gameId)) return // Already being created

        scope.launch(Dispatchers.IO) {
            val success =
                retryWithBackoff {
                    val template =
                        LiveChessGameAcceptEvent.build(
                            gameId = gameId,
                            challengeEventId = challengeEvent.id,
                            challengerPubkey = challengerPubkey,
                        )

                    val signedEvent = account.signer.sign(template)
                    relayManager.broadcastToAll(signedEvent)
                }

            if (success) {
                // Check if game was already created by relay echo while we were broadcasting
                if (_activeGames.value.containsKey(gameId)) {
                    gamesBeingCreated.remove(gameId)
                    _selectedGameId.value = gameId
                    _challenges.value = _challenges.value.filter { it.id != challengeEvent.id }
                    _error.value = null
                    return@launch
                }

                val engine = ChessEngine()
                engine.reset()

                val gameState =
                    LiveChessGameState(
                        gameId = gameId,
                        playerPubkey = account.pubKeyHex,
                        opponentPubkey = challengerPubkey,
                        playerColor = playerColor,
                        engine = engine,
                    )

                _activeGames.value = _activeGames.value + (gameId to gameState)
                notifyActiveGamesChanged()
                gamesBeingCreated.remove(gameId)
                _selectedGameId.value = gameId
                _challenges.value = _challenges.value.filter { it.id != challengeEvent.id }
                _error.value = null
            } else {
                gamesBeingCreated.remove(gameId)
                _error.value = "Failed to accept challenge"
            }
        }
    }

    private fun startGameFromAcceptance(
        acceptEvent: LiveChessGameAcceptEvent,
        isChallenger: Boolean,
    ) {
        scope.launch(Dispatchers.IO) {
            val gameId = acceptEvent.gameId() ?: return@launch
            val challengerPubkey = acceptEvent.challengerPubkey() ?: return@launch
            val accepterPubkey = acceptEvent.pubKey

            println("[Chess] startGameFromAcceptance: gameId=$gameId, isChallenger=$isChallenger")
            println("[Chess] Pending moves in buffer: ${pendingMoves.keys}")

            // Skip if game already exists or is being created
            if (_activeGames.value.containsKey(gameId)) {
                println("[Chess] Game $gameId already exists, skipping")
                return@launch
            }
            if (!gamesBeingCreated.add(gameId)) {
                println("[Chess] Game $gameId already being created, skipping")
                return@launch // Returns false if already present
            }

            val opponentPubkey: String
            val playerColor: Color

            // Use challengesByGameId for reliable lookup (not affected by UI filtering)
            val challengeEvent = challengesByGameId[gameId]

            if (isChallenger) {
                // User is challenger, opponent is accepter
                opponentPubkey = accepterPubkey
                playerColor = challengeEvent?.playerColor() ?: Color.WHITE
            } else {
                // User is accepter, opponent is challenger
                opponentPubkey = challengerPubkey
                // Accepter gets opposite color of challenger
                val challengerColor = challengeEvent?.playerColor() ?: Color.WHITE
                playerColor = challengerColor.opposite()
            }

            val engine = ChessEngine()
            engine.reset()

            val gameState =
                LiveChessGameState(
                    gameId = gameId,
                    playerPubkey = account.pubKeyHex,
                    opponentPubkey = opponentPubkey,
                    playerColor = playerColor,
                    engine = engine,
                )

            println("[Chess] Adding game $gameId to active games, opponent=$opponentPubkey")
            _activeGames.value = _activeGames.value + (gameId to gameState)
            notifyActiveGamesChanged()
            _challenges.value = _challenges.value.filter { it.gameId() != gameId }
            gamesBeingCreated.remove(gameId)

            // Apply any pending moves that arrived before the game was created
            println("[Chess] About to apply pending moves for $gameId, buffer has: ${pendingMoves[gameId]?.size ?: 0} moves")
            applyPendingMoves(gameId, gameState)
        }
    }

    private fun applyPendingMoves(
        gameId: String,
        gameState: LiveChessGameState,
    ) {
        val moves = pendingMoves.remove(gameId)
        if (moves == null) {
            println("[Chess] No pending moves for game $gameId")
            return
        }

        println("[Chess] Processing ${moves.size} pending moves for game $gameId")

        // Sort by move number to find the latest move
        val sortedMoves = moves.sortedBy { it.third ?: Int.MAX_VALUE }

        if (sortedMoves.isEmpty()) return

        // Find the move with highest move number - its FEN has the current board state
        val latestMove = sortedMoves.maxByOrNull { it.third ?: 0 }
        if (latestMove != null) {
            val (san, fen, moveNumber) = latestMove
            println("[Chess] Syncing to latest position from move #$moveNumber: $san")
            println("[Chess] FEN: $fen")

            // Use forceResync to set the board to the correct position
            gameState.forceResync(fen)

            // Mark all received move numbers as processed to avoid duplicates
            sortedMoves.forEach { (_, _, num) ->
                if (num != null) {
                    gameState.markMovesAsReceived(setOf(num))
                }
            }

            println("[Chess] Board synced to position after ${sortedMoves.size} moves")
        }

        updateBadgeCount()
    }

    /**
     * Make and publish a move
     */
    fun publishMove(
        gameId: String,
        from: String,
        to: String,
    ) {
        val gameState = _activeGames.value[gameId] ?: return

        // Parse promotion from 'to' if present (e.g., "e8q" -> square="e8", promotion=QUEEN)
        val (targetSquare, promotion) = parsePromotionFromTarget(to)

        val moveResult = gameState.makeMove(from, targetSquare, promotion)
        if (moveResult != null) {
            publishMoveEvent(gameId, moveResult)
        }
    }

    /**
     * Parse promotion piece from target square string.
     * e.g., "e8q" -> ("e8", QUEEN), "e4" -> ("e4", null)
     */
    private fun parsePromotionFromTarget(to: String): Pair<String, PieceType?> {
        if (to.length == 3) {
            val square = to.substring(0, 2)
            val promotion =
                when (to[2].lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> null
                }
            return square to promotion
        }
        return to to null
    }

    private fun publishMoveEvent(
        gameId: String,
        moveEvent: ChessMoveEvent,
    ) {
        scope.launch(Dispatchers.IO) {
            val success =
                retryWithBackoff {
                    val template =
                        LiveChessMoveEvent.build(
                            gameId = moveEvent.gameId,
                            moveNumber = moveEvent.moveNumber,
                            san = moveEvent.san,
                            fen = moveEvent.fen,
                            opponentPubkey = moveEvent.opponentPubkey,
                        )

                    val signedEvent = account.signer.sign(template)
                    relayManager.broadcastToAll(signedEvent)
                }

            if (!success) {
                _error.value = "Failed to publish move"
            }
        }
    }

    /**
     * Resign from a game
     */
    fun resign(gameId: String) {
        val gameState = _activeGames.value[gameId] ?: return

        scope.launch(Dispatchers.IO) {
            val endData = gameState.resign()

            val success =
                retryWithBackoff {
                    val template =
                        LiveChessGameEndEvent.build(
                            gameId = endData.gameId,
                            result = endData.result,
                            termination = endData.termination,
                            winnerPubkey = endData.winnerPubkey,
                            opponentPubkey = endData.opponentPubkey,
                            pgn = endData.pgn ?: "",
                        )

                    val signedEvent = account.signer.sign(template)
                    relayManager.broadcastToAll(signedEvent)
                }

            if (success) {
                // Store in history (skip if already completed)
                val alreadyCompleted = _completedGames.value.any { it.gameId == gameId }
                if (!alreadyCompleted) {
                    val completedGame =
                        CompletedGame(
                            gameId = gameId,
                            opponentPubkey = gameState.opponentPubkey,
                            playerColor = gameState.playerColor,
                            result = endData.result.notation,
                            termination = endData.termination.name.lowercase(),
                            winnerPubkey = endData.winnerPubkey,
                            completedAt = TimeUtils.now(),
                            moveCount = gameState.moveHistory.value.size,
                        )
                    _completedGames.value = listOf(completedGame) + _completedGames.value
                }
                _activeGames.value = _activeGames.value - gameId
                notifyActiveGamesChanged()
                _error.value = null
            } else {
                _error.value = "Failed to resign"
            }
        }
    }

    /**
     * Offer draw - sends a draw offer event that opponent can accept or decline
     */
    fun offerDraw(gameId: String) {
        val gameState = _activeGames.value[gameId] ?: return

        scope.launch(Dispatchers.IO) {
            val drawOffer = gameState.offerDraw()

            val success =
                retryWithBackoff {
                    val template =
                        LiveChessDrawOfferEvent.build(
                            gameId = drawOffer.gameId,
                            opponentPubkey = drawOffer.opponentPubkey,
                            message = drawOffer.message ?: "",
                        )

                    val signedEvent = account.signer.sign(template)
                    relayManager.broadcastToAll(signedEvent)
                }

            if (!success) {
                _error.value = "Failed to offer draw"
            }
        }
    }

    /**
     * Accept opponent's draw offer
     */
    fun acceptDraw(gameId: String) {
        val gameState = _activeGames.value[gameId] ?: return

        scope.launch(Dispatchers.IO) {
            val endData = gameState.acceptDraw()
            if (endData == null) {
                _error.value = "No draw offer to accept"
                return@launch
            }

            val success =
                retryWithBackoff {
                    val template =
                        LiveChessGameEndEvent.build(
                            gameId = endData.gameId,
                            result = endData.result,
                            termination = endData.termination,
                            winnerPubkey = endData.winnerPubkey,
                            opponentPubkey = endData.opponentPubkey,
                            pgn = endData.pgn ?: "",
                        )

                    val signedEvent = account.signer.sign(template)
                    relayManager.broadcastToAll(signedEvent)
                }

            if (success) {
                // Store in history and remove from active (skip if already completed)
                val alreadyCompleted = _completedGames.value.any { it.gameId == gameId }
                if (!alreadyCompleted) {
                    val completedGame =
                        CompletedGame(
                            gameId = gameId,
                            opponentPubkey = gameState.opponentPubkey,
                            playerColor = gameState.playerColor,
                            result = GameResult.DRAW.notation,
                            termination = GameTermination.DRAW_AGREEMENT.name.lowercase(),
                            winnerPubkey = null,
                            completedAt = TimeUtils.now(),
                            moveCount = gameState.moveHistory.value.size,
                        )
                    _completedGames.value = listOf(completedGame) + _completedGames.value
                }
                _activeGames.value = _activeGames.value - gameId
                notifyActiveGamesChanged()
                _error.value = null
            } else {
                _error.value = "Failed to accept draw"
            }
        }
    }

    /**
     * Decline opponent's draw offer
     */
    fun declineDraw(gameId: String) {
        val gameState = _activeGames.value[gameId] ?: return
        gameState.declineDraw()
    }

    fun selectGame(gameId: String?) {
        _selectedGameId.value = gameId
    }

    fun getGameState(gameId: String): LiveChessGameState? = _activeGames.value[gameId]

    fun clearError() {
        _error.value = null
    }

    /**
     * Trigger a refresh of chess data from relays.
     * Preserves existing game state (including local moves) while refreshing challenges.
     */
    fun refresh() {
        _isLoading.value = true

        // Clear challenges - they'll be rebuilt from relay events
        _challenges.value = emptyList()

        // Clear event tracking caches to allow re-processing
        processedEventIds.clear()
        challengesByGameId.clear()
        pendingAccepts.clear()
        pendingMoves.clear()
        gamesBeingCreated.clear()

        // DON'T clear _activeGames - preserve existing game state with local moves
        // Incoming events will be deduplicated by LiveChessGameState.receivedMoveNumbers

        _refreshKey.value++
    }

    /**
     * Called when EOSE is received from relay, indicating initial load complete
     */
    fun onLoadComplete() {
        _isLoading.value = false
    }

    /**
     * Refresh game states - polling callback.
     * Triggers a subscription refresh to catch any missed events.
     * The fixed filter (with opponent authors) will fetch opponent moves.
     */
    private suspend fun refreshGamesFromRelay(gameIds: Set<String>) {
        if (gameIds.isEmpty()) return

        // Trigger subscription controller refresh to re-fetch with current filters
        // The filter now includes opponent pubkeys as authors, so it will catch their moves
        subscriptionController?.forceRefresh()
    }

    /**
     * Clean up expired challenges (older than 24 hours)
     */
    private fun cleanupExpiredChallenges() {
        val now = TimeUtils.now()
        _challenges.value =
            _challenges.value.filter { challenge ->
                (now - challenge.createdAt) < CHALLENGE_EXPIRY_SECONDS
            }
    }

    private fun updateBadgeCount() {
        val incomingChallenges = _challenges.value.count { it.opponentPubkey() == account.pubKeyHex }
        val yourTurnGames = _activeGames.value.values.count { it.isPlayerTurn() }
        _badgeCount.value = incomingChallenges + yourTurnGames
    }

    private suspend fun retryWithBackoff(operation: suspend () -> Unit): Boolean {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                operation()
                return true
            } catch (e: Exception) {
                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        return false
    }

    private fun generateGameId(): String = ChessGameNameGenerator.generateGameId(TimeUtils.now())
}

/**
 * Represents a completed chess game for history display
 */
data class CompletedGame(
    val gameId: String,
    val opponentPubkey: String,
    val playerColor: Color,
    val result: String,
    val termination: String,
    val winnerPubkey: String?,
    val completedAt: Long,
    val moveCount: Int,
) {
    fun didWin(playerPubkey: String): Boolean? =
        when {
            result == "1/2-1/2" -> null // Draw
            winnerPubkey == playerPubkey -> true
            winnerPubkey != null -> false
            else -> null
        }

    fun resultText(playerPubkey: String): String =
        when (didWin(playerPubkey)) {
            true -> "Won"
            false -> "Lost"
            null -> "Draw"
        }
}
