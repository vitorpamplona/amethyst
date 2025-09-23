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
package com.vitorpamplona.quartz.nip96FileStorage

import com.vitorpamplona.quartz.nip96FileStorage.info.ServerInfoParser
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip96Test {
    val relativeUrlTest =
        """
{
  "api_url": "/n96",
  "download_url": "/",
  "content_types": [
    "image/*",
    "video/*",
    "audio/*"
  ],
  "plans": {
    "free": {
      "name": "",
      "is_nip98_required": true,
      "max_byte_size": 5000000000
    }
  }
}
"""

    val json =
        """
        {
          "api_url": "https://nostr.build/api/v2/nip96/upload",
          "download_url": "https://media.nostr.build",
          "supported_nips": [
            94,
            96,
            98
          ],
          "tos_url": "https://nostr.build/tos/",
          "content_types": [
            "image/*",
            "video/*",
            "audio/*"
          ],
          "plans": {
            "free": {
              "name": "Free",
              "is_nip98_required": true,
              "url": "https://nostr.build",
              "max_byte_size": 26214400,
              "file_expiration": [
                0,
                0
              ],
              "media_transformations": {
                "image": [
                  "resizing",
                  "format_conversion",
                  "compression",
                  "metadata_stripping"
                ],
                "video": [
                  "resizing",
                  "format_conversion",
                  "compression"
                ]
              }
            },
            "professional": {
              "name": "Professional",
              "is_nip98_required": true,
              "url": "https://nostr.build/signup/new/",
              "max_byte_size": 10737418240,
              "file_expiration": [
                0,
                0
              ],
              "media_transformations": {
                "image": [
                  "resizing",
                  "format_conversion",
                  "compression",
                  "metadata_stripping"
                ],
                "video": [
                  "resizing",
                  "format_conversion",
                  "compression"
                ]
              }
            },
            "creator": {
              "name": "Creator",
              "is_nip98_required": true,
              "url": "https://nostr.build/signup/new/",
              "max_byte_size": 26843545600,
              "file_expiration": [
                0,
                0
              ],
              "media_transformations": {
                "image": [
                  "resizing",
                  "format_conversion",
                  "compression",
                  "metadata_stripping"
                ],
                "video": [
                  "resizing",
                  "format_conversion",
                  "compression"
                ]
              }
            }
          }
        }
        """.trimIndent()

    @Test()
    fun parseNostrBuild() {
        val info = ServerInfoParser().parse("https://nostr.build", json)

        assertEquals("https://nostr.build/api/v2/nip96/upload", info.apiUrl)
        assertEquals("https://media.nostr.build", info.downloadUrl)
        assertEquals(listOf(94, 96, 98), info.supportedNips)
        assertEquals("https://nostr.build/tos/", info.tosUrl)
        assertEquals(listOf("image/*", "video/*", "audio/*"), info.contentTypes)

        assertEquals(listOf("creator", "free", "professional"), info.plans.keys.sorted())

        assertEquals("Free", info.plans["free"]?.name)
        assertEquals(true, info.plans["free"]?.isNip98Required)
        assertEquals("https://nostr.build", info.plans["free"]?.url)
        assertEquals(26214400L, info.plans["free"]?.maxByteSize)
        assertEquals(listOf(0, 0), info.plans["free"]?.fileExpiration?.toList())
        assertEquals(
            listOf("image", "video"),
            info.plans["free"]
                ?.mediaTransformations
                ?.keys
                ?.sorted(),
        )

        assertEquals(26843545600L, info.plans["creator"]?.maxByteSize)
        assertEquals(10737418240L, info.plans["professional"]?.maxByteSize)
    }

    @Test()
    fun parseRelativeUrls() {
        val info = ServerInfoParser().parse("https://test.com", relativeUrlTest)

        assertEquals("https://test.com/n96", info.apiUrl)
        assertEquals("https://test.com/", info.downloadUrl)
        assertEquals(null, info.tosUrl)
        assertEquals(null, info.delegatedToUrl)
    }
}
