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
package com.vitorpamplona.amethyst.desktop.nowPlaying

import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSource
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Linux/BSD: asks every MPRIS player on the session bus (Spotify, VLC, Rhythmbox, browsers, ...)
 * what it is playing, through the `dbus-send` tool that ships with D-Bus itself, so no D-Bus
 * library is linked in.
 */
class MprisNowPlayingReader(
    private val run: suspend (List<String>) -> String? = { runCommand(it) },
    private val nowSeconds: () -> Long = TimeUtils::now,
) : OsNowPlayingReader {
    // A player's name never changes while its bus name lives; asking once saves a process per poll.
    private val identities = mutableMapOf<String, String?>()

    override suspend fun read(): NowPlaying? {
        val names = run(LIST_NAMES)?.let(MprisParser::playerNames).orEmpty()
        identities.keys.retainAll(names.toSet())

        for (busName in names) {
            val properties = run(getAll(busName))?.let(MprisParser::properties) ?: continue
            if (properties["PlaybackStatus"]?.firstOrNull() != "Playing") continue

            val identity =
                identities.getOrPut(busName) {
                    run(getIdentity(busName))?.let(MprisParser::properties)?.get("Identity")?.firstOrNull()
                }
            return MprisParser.toNowPlaying(busName, identity, properties, nowSeconds())
        }

        return null
    }

    companion object {
        private val LIST_NAMES =
            listOf(
                "dbus-send",
                "--session",
                "--print-reply",
                "--dest=org.freedesktop.DBus",
                "/org/freedesktop/DBus",
                "org.freedesktop.DBus.ListNames",
            )

        private fun getAll(busName: String) =
            listOf(
                "dbus-send",
                "--session",
                "--print-reply",
                "--dest=$busName",
                "/org/mpris/MediaPlayer2",
                "org.freedesktop.DBus.Properties.GetAll",
                "string:org.mpris.MediaPlayer2.Player",
            )

        private fun getIdentity(busName: String) =
            listOf(
                "dbus-send",
                "--session",
                "--print-reply",
                "--dest=$busName",
                "/org/mpris/MediaPlayer2",
                "org.freedesktop.DBus.Properties.GetAll",
                "string:org.mpris.MediaPlayer2",
            )
    }
}

/** Parses the text `dbus-send --print-reply` prints for the MPRIS calls above. */
object MprisParser {
    private const val PREFIX = "org.mpris.MediaPlayer2."
    private val INSTANCE_SUFFIX = Regex("""\.instance_?\d+(_\d+)?$""")

    fun playerNames(output: String): List<String> =
        output
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("string \"$PREFIX") }
            .map { unquote(it.removePrefix("string ")) }
            .toList()

    /** The player id the block list keys on: `org.mpris.MediaPlayer2.vlc.instance123` is `vlc`. */
    fun playerId(busName: String): String = busName.removePrefix(PREFIX).replace(INSTANCE_SUFFIX, "")

    /**
     * Flattens every `dict entry` of a GetAll reply, nested ones included (the track metadata is
     * a dictionary inside the `Metadata` entry), into key -> values. Scalars have one value;
     * string arrays such as `xesam:artist` keep all of them.
     */
    fun properties(output: String): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        var expectKey = false
        var key: String? = null
        var arrayKey: String? = null

        output.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("dict entry(") -> {
                    expectKey = true
                }

                expectKey && line.startsWith("string ") -> {
                    key = unquote(line.removePrefix("string "))
                    expectKey = false
                }

                line.startsWith("variant") -> {
                    val currentKey = key ?: return@forEach
                    val value = line.removePrefix("variant").trim()
                    if (value.startsWith("array [")) {
                        // Either a string list (artists) or a nested dictionary (Metadata);
                        // a nested dictionary's entries are picked up by the branches above.
                        arrayKey = currentKey
                    } else {
                        scalar(value)?.let { result.getOrPut(currentKey) { mutableListOf() }.add(it) }
                    }
                    key = null
                }

                line == "]" -> {
                    arrayKey = null
                }

                arrayKey != null && line.startsWith("string ") -> {
                    result.getOrPut(arrayKey) { mutableListOf() }.add(unquote(line.removePrefix("string ")))
                }
            }
        }

        return result
    }

    fun toNowPlaying(
        busName: String,
        identity: String?,
        properties: Map<String, List<String>>,
        nowSeconds: Long,
    ): NowPlaying? {
        val title = properties["xesam:title"]?.firstOrNull()?.ifBlank { null } ?: return null
        val artist =
            properties["xesam:artist"]?.filter { it.isNotBlank() }?.joinToString(", ")?.ifBlank { null }
                ?: properties["xesam:albumArtist"]?.firstOrNull()?.ifBlank { null }

        // MPRIS times are microseconds.
        val durationMs = properties["mpris:length"]?.firstOrNull()?.toLongOrNull()?.div(1000)
        val positionMs = properties["Position"]?.firstOrNull()?.toLongOrNull()?.div(1000) ?: 0L
        val rate = properties["Rate"]?.firstOrNull()?.toFloatOrNull() ?: 1f

        val id = playerId(busName)
        val label = identity?.ifBlank { null } ?: id.replaceFirstChar { it.uppercase() }

        return NowPlaying(
            title = title,
            artist = artist,
            source = NowPlayingSource.OtherApp(id, label),
            endsAt = NowPlaying.endsAt(nowSeconds, durationMs, positionMs, rate),
            url = properties["xesam:url"]?.firstOrNull()?.let(::shareableUrl),
        )
    }

    /**
     * Only links that name a track are shared. Browsers fill `xesam:url` with the page of whatever
     * tab is playing, and a local player with a file path: neither belongs in a public status.
     */
    fun shareableUrl(url: String): String? = url.takeIf { SHAREABLE_URL.matches(it) }

    private val SHAREABLE_URL = Regex("""^https://open\.spotify\.com/(intl-[a-z-]+/)?(track|episode)/[A-Za-z0-9]+(\?.*)?$""")

    /** `string "x"`, `object path "/x"`, `int64 5`, `double 1`, `boolean true` -> the value. */
    private fun scalar(value: String): String? =
        when {
            value.startsWith("string ") -> unquote(value.removePrefix("string "))
            value.startsWith("object path ") -> unquote(value.removePrefix("object path "))
            else -> value.substringAfter(' ', "").ifBlank { null }
        }

    private fun unquote(value: String): String {
        val start = value.indexOf('"')
        val end = value.lastIndexOf('"')
        return if (start >= 0 && end > start) value.substring(start + 1, end) else value
    }
}
