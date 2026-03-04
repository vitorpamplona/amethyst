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
package com.vitorpamplona.quartz.nip64Chess

import kotlin.random.Random

/**
 * Generates human-readable chess game names.
 *
 * Produces memorable names like "Bold Knight", "Swift Rook", etc.
 */
object ChessGameNameGenerator {
    // Chess-themed adjectives
    private val adjectives =
        listOf(
            "Bold",
            "Swift",
            "Cunning",
            "Royal",
            "Noble",
            "Shadow",
            "Silent",
            "Golden",
            "Silver",
            "Iron",
            "Fierce",
            "Wise",
            "Dark",
            "Bright",
            "Ancient",
            "Brave",
            "Mystic",
            "Grand",
            "Crimson",
            "Azure",
            "Ivory",
            "Obsidian",
            "Emerald",
            "Sapphire",
            "Ruby",
            "Phantom",
            "Eternal",
            "Secret",
            "Hidden",
            "Blazing",
        )

    // Chess pieces and related nouns
    private val chessNouns =
        listOf(
            "King",
            "Queen",
            "Rook",
            "Bishop",
            "Knight",
            "Pawn",
            "Castle",
            "Crown",
            "Throne",
            "Gambit",
            "Defense",
            "Attack",
            "Checkmate",
            "Fortress",
            "Tower",
            "Endgame",
            "Opening",
            "Sacrifice",
            "Pin",
            "Fork",
            "Strategy",
            "Victory",
            "Battle",
            "Duel",
            "Match",
            "Challenge",
        )

    // Famous chess-related animals/themes
    private val animals =
        listOf(
            "Dragon",
            "Phoenix",
            "Griffin",
            "Eagle",
            "Lion",
            "Wolf",
            "Tiger",
            "Falcon",
            "Hawk",
            "Raven",
            "Bear",
            "Stallion",
            "Serpent",
            "Panther",
            "Cobra",
        )

    /**
     * Generate a random chess game name.
     *
     * @param includeNumber Whether to append a random number (1-99)
     * @return A name like "Bold Knight" or "Swift Dragon 42"
     */
    fun generate(includeNumber: Boolean = false): String {
        val adjective = adjectives.random()
        val noun = if (Random.nextBoolean()) chessNouns.random() else animals.random()
        val base = "$adjective $noun"

        return if (includeNumber) {
            val num = Random.nextInt(1, 100)
            "$base $num"
        } else {
            base
        }
    }

    /**
     * Generate a name based on two player display names.
     *
     * Creates a name that incorporates elements from both players
     * for a more personalized game name.
     *
     * @param playerName First player's display name (can be npub/pubkey)
     * @param opponentName Second player's display name (can be npub/pubkey, or null for open challenge)
     * @return A personalized game name
     */
    fun generateFromPlayers(
        playerName: String?,
        opponentName: String?,
    ): String {
        // Extract first letter or use adjective if name is a pubkey/npub
        val p1Initial = extractInitial(playerName)
        val p2Initial = opponentName?.let { extractInitial(it) }

        // If we have good initials, use them in the name
        val adjective =
            if (p1Initial != null) {
                // Try to find an adjective starting with that letter
                adjectives.find { it.startsWith(p1Initial, ignoreCase = true) }
                    ?: adjectives.random()
            } else {
                adjectives.random()
            }

        val noun =
            if (p2Initial != null) {
                // Try to find a noun starting with opponent's initial
                (chessNouns + animals).find { it.startsWith(p2Initial, ignoreCase = true) }
                    ?: if (Random.nextBoolean()) chessNouns.random() else animals.random()
            } else {
                // Open challenge - use "Challenge" or similar
                listOf("Challenge", "Gambit", "Opening", "Battle", "Duel").random()
            }

        return "$adjective $noun"
    }

    /**
     * Extract a usable initial from a name.
     * Returns null if the name looks like a pubkey/npub.
     */
    private fun extractInitial(name: String?): Char? {
        if (name.isNullOrBlank()) return null

        // Skip if it looks like a hex pubkey or npub
        if (name.length > 20 && name.all { it.isLetterOrDigit() }) return null
        if (name.startsWith("npub") || name.startsWith("nprofile")) return null

        // Get first letter of the display name
        val firstChar = name.firstOrNull { it.isLetter() }
        return firstChar?.uppercaseChar()
    }

    /**
     * Generate a game ID that includes a readable component.
     *
     * @param timestamp Unix timestamp
     * @return Game ID like "chess-1234567890-bold-knight"
     */
    fun generateGameId(timestamp: Long): String {
        val name =
            generate(includeNumber = false)
                .lowercase()
                .replace(" ", "-")
        return "chess-$timestamp-$name"
    }

    /**
     * Extract the human-readable name from a game ID.
     *
     * @param gameId Game ID like "chess-1234567890-bold-knight"
     * @return Display name like "Bold Knight", or null if not a named game ID
     */
    fun extractDisplayName(gameId: String): String? {
        // Pattern: chess-timestamp-name-parts
        if (!gameId.startsWith("chess-")) return null

        val parts = gameId.removePrefix("chess-").split("-")
        if (parts.size < 2) return null

        // First part is timestamp, rest is the name
        val nameParts = parts.drop(1)
        if (nameParts.isEmpty()) return null

        // Check if it looks like a UUID (8 hex chars) - old format
        if (nameParts.size == 1 && nameParts[0].length == 8 && nameParts[0].all { it.isLetterOrDigit() }) {
            return null
        }

        // Convert to title case
        return nameParts.joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
