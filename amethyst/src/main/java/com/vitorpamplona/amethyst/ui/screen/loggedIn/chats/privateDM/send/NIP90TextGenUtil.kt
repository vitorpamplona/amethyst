/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import android.util.Log
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.DiscoverNIP89FeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.dal.NIP90TextGenDVMFeedFilter
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility class for NIP90 text generation operations.
 * Handles discovery and interaction with Text Generation DVMs.
 */
object NIP90TextGenUtil {
    // Cache for DVMs to avoid redundant discoveries
    private var cachedDvmList: List<DvmInfo>? = null
    private var lastDiscoveryTime: Long = 0
    private const val CACHE_TTL = 5 * 60 * 1000 // 5 minutes in milliseconds

    // Kind for text generation DVMs
    const val KIND_TEXT_GENERATION = DiscoverNIP89FeedFilter.TEXT_GENERATION_KIND

    // Kind for App Definition
    const val KIND_APP_DEFINITION = DiscoverNIP89FeedFilter.APP_DEFINITION_KIND

    /**
     * Retrieves text generation DVMs using NIP90TextGenDVMFeedFilter.
     * Uses cached results if available and not expired.
     */
    suspend fun getTextGenerationDVMs(account: Account): List<DvmInfo> =
        withContext(Dispatchers.IO) {
            // Check for valid cached list first
            val currentTime = System.currentTimeMillis()
            if (cachedDvmList != null && (currentTime - lastDiscoveryTime < CACHE_TTL)) {
                Log.d("DVM_DEBUG", "Using cached DVM list (${cachedDvmList!!.size} DVMs)")
                return@withContext cachedDvmList!!
            }

            Log.d("DVM_DEBUG", "Starting NIP90 DVM discovery process")

            // First, request DVM events from relays to ensure our cache has the latest
            DiscoverNIP89FeedFilter.requestDVMEvents(account)

            // Allow time for relay responses and processing
            delay(1000)

            try {
                // Get DVMs using NIP90TextGenDVMFeedFilter
                val feed = NIP90TextGenDVMFeedFilter(account)
                val dvmNotes = feed.feed()

                Log.d("DVM_DEBUG", "Raw DVM notes found: ${dvmNotes.size}")

                if (dvmNotes.isEmpty()) {
                    Log.d("DVM_DEBUG", "No text generation DVMs found")
                    cachedDvmList = emptyList()
                    lastDiscoveryTime = currentTime
                    return@withContext emptyList()
                }

                // Process the discovered DVMs
                val result = processDiscoveredDVMs(dvmNotes)

                // Cache the results
                cachedDvmList = result
                lastDiscoveryTime = currentTime

                Log.d("DVM_DEBUG", "NIP90 DVM discovery complete, found ${result.size} DVMs")
                return@withContext result
            } catch (e: Exception) {
                Log.e("DVM_DEBUG", "Error discovering NIP90 DVMs: ${e.message}", e)
                // If error occurs and we have no cache, return empty list
                if (cachedDvmList == null) {
                    cachedDvmList = emptyList()
                    lastDiscoveryTime = currentTime
                }
                return@withContext cachedDvmList ?: emptyList()
            }
        }

    /**
     * Process discovered DVM notes into a clean list of DvmInfo objects.
     * Filters for kind 5050 (text generation) and active DVMs.
     */
    private fun processDiscoveredDVMs(dvmNotes: List<Note>): List<DvmInfo> {
        // Extract DVMs that match text generation criteria
        val dvmInfo =
            dvmNotes.mapNotNull { note ->
                try {
                    val appDef = note.event as? AppDefinitionEvent ?: return@mapNotNull null
                    val metadata = appDef.appMetaData() ?: return@mapNotNull null
                    val supportedKinds = appDef.supportedKinds()
                    val pubkey = note.author?.pubkeyHex ?: return@mapNotNull null

                    Log.d("DVM_DEBUG", "Processing DVM: ${metadata.name ?: "unnamed"}, pubkey: ${pubkey.take(8)}, kinds: $supportedKinds")

                    // Only include DVMs that support kind 5050 (text generation)
                    if (!supportedKinds.contains(KIND_TEXT_GENERATION)) {
                        Log.d("DVM_DEBUG", "Skipping DVM - doesn't support kind 5050: ${metadata.name ?: "unnamed"}")
                        return@mapNotNull null
                    }

                    // Only include DVMs active within the past month
                    if ((note.createdAt() ?: 0L) < TimeUtils.oneMonthAgo()) {
                        Log.d("DVM_DEBUG", "Skipping DVM - not recently active: ${metadata.name ?: "unnamed"}")
                        return@mapNotNull null
                    }

                    DvmInfo(
                        pubkey = pubkey,
                        name = metadata.name,
                        supportedKinds = supportedKinds.toSet(),
                        description = metadata.about,
                    )
                } catch (e: Exception) {
                    Log.e("DVM_DEBUG", "Error processing DVM note: ${e.message}")
                    null
                }
            }

        // Deduplicate by pubkey
        val uniqueDvms = mutableMapOf<String, DvmInfo>()
        dvmInfo.forEach { dvm ->
            uniqueDvms[dvm.pubkey] = dvm
            Log.d("DVM_DEBUG", "Keeping DVM: ${dvm.name ?: "unnamed"}, pubkey: ${dvm.pubkey.take(8)}")
        }

        return uniqueDvms.values.toList()
    }

    /**
     * Formats a message as a NIP-90 text generation request.
     * If not talking to a DVM, returns the original message.
     */
    fun formatTextGenerationRequest(
        message: String,
        isDvmConversation: Boolean,
    ): String {
        // If not talking to a DVM, return the original message
        if (!isDvmConversation) return message

        try {
            // Create a NIP-90 kind:5050 formatted request
            val requestObject = JSONObject()

            // Add input data as text
            val inputTags = JSONArray()
            val inputTag = JSONArray()
            inputTag.put("i")
            inputTag.put(message)
            inputTag.put("text")
            inputTags.put(inputTag)

            // Add basic params
            val paramTags = JSONArray()

            // Add model param if you want to specify a model
            val modelParam = JSONArray()
            modelParam.put("param")
            modelParam.put("model")
            modelParam.put("default") // You could let user customize this
            paramTags.put(modelParam)

            // Create the request object
            requestObject.put("kind", KIND_TEXT_GENERATION)
            requestObject.put("tags", inputTags)
            requestObject.put("content", "")

            return requestObject.toString(2)
        } catch (e: Exception) {
            Log.e("DVM_DEBUG", "Error formatting NIP90 text generation request", e)
            return message
        }
    }

    /**
     * Parses a text response from a NIP-90 message.
     * This function handles both request (kind 5050) and response (kind 6050) formats.
     *
     * If the content is valid JSON in NIP-90 format, it extracts the actual text content.
     * If not a valid NIP-90 JSON or not JSON at all, returns the original message.
     */
    fun parseTextFromNIP90Message(message: String): String {
        if (message.isBlank()) return message

        // Check if it looks like JSON
        if (!message.trim().startsWith("{")) return message

        try {
            val jsonObject = JSONObject(message)

            // Check if this is a NIP-90 request message (kind 5050)
            if (jsonObject.optInt("kind") == KIND_TEXT_GENERATION) {
                // Extract input text from request tags
                val tagsArr = jsonObject.optJSONArray("tags")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val tag = tagsArr.optJSONArray(i)
                        if (tag != null &&
                            tag.length() >= 3 &&
                            tag.optString(0) == "i" &&
                            tag.optString(2) == "text"
                        ) {
                            return tag.optString(1, message)
                        }
                    }
                }
            }

            // Check if this is a NIP-90 response (has content field)
            val content = jsonObject.optString("content", "")
            if (content.isNotBlank()) {
                return content
            }

            // If it's JSON but not in expected format, just return original
            return message
        } catch (e: Exception) {
            Log.d("DVM_DEBUG", "Not a valid NIP90 JSON: ${e.message}")
            return message
        }
    }
} 
