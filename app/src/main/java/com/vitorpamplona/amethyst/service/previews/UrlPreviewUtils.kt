package com.vitorpamplona.amethyst.service.previews

import com.vitorpamplona.amethyst.service.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val ELEMENT_TAG_META = "meta"
private const val ATTRIBUTE_VALUE_PROPERTY = "property"
private const val ATTRIBUTE_VALUE_NAME = "name"
private const val ATTRIBUTE_VALUE_ITEMPROP = "itemprop"

/*for <meta itemprop=... to get title */
private val META_X_TITLE = arrayOf(
    "og:title", "\"og:title\"", "'og:title'", "name", "\"name\"", "'name'",
    "twitter:title",
    "\"twitter:title\"",
    "'twitter:title'",
    "title",
    "\"title\"",
    "'title'"
)

/*for <meta itemprop=... to get description */
private val META_X_DESCRIPTION = arrayOf(
    "og:description", "\"og:description\"", "'og:description'",
    "description", "\"description\"", "'description'",
    "twitter:description",
    "\"twitter:description\"",
    "'twitter:description'",
    "description",
    "\"description\"",
    "'description'"
)

/*for <meta itemprop=... to get image */
private val META_X_IMAGE = arrayOf(
    "og:image", "\"og:image\"", "'og:image'",
    "image", "\"image\"", "'image'",
    "twitter:image",
    "\"twitter:image\"",
    "'twitter:image'"
)

private const val CONTENT = "content"

suspend fun getDocument(url: String, timeOut: Int = 30000): Document =
    withContext(Dispatchers.IO) {
        val request: Request = Request.Builder().url(url).get().build()
        val html = HttpClient.getHttpClient().newCall(request).execute().use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                throw IllegalArgumentException("Website returned: " + it.code)
            }
        }

        Jsoup.parse(html)
    }

suspend fun parseHtml(url: String, document: Document): UrlInfoItem =
    withContext(Dispatchers.IO) {
        val metaTags = document.getElementsByTag(ELEMENT_TAG_META)

        var title: String = ""
        var description: String = ""
        var image: String = ""

        metaTags.forEach {
            when (it.attr(ATTRIBUTE_VALUE_PROPERTY)) {
                in META_X_TITLE -> if (title.isEmpty()) {
                    title = it.attr(CONTENT)
                }
                in META_X_DESCRIPTION -> if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }
                in META_X_IMAGE -> if (image.isEmpty()) {
                    image = it.attr(CONTENT)
                }
            }

            when (it.attr(ATTRIBUTE_VALUE_NAME)) {
                in META_X_TITLE -> if (title.isEmpty()) {
                    title = it.attr(CONTENT)
                }
                in META_X_DESCRIPTION -> if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }
                in META_X_IMAGE -> if (image.isEmpty()) {
                    image = it.attr(CONTENT)
                }
            }

            when (it.attr(ATTRIBUTE_VALUE_ITEMPROP)) {
                in META_X_TITLE -> if (title.isEmpty()) {
                    title = it.attr(CONTENT)
                }
                in META_X_DESCRIPTION -> if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }
                in META_X_IMAGE -> if (image.isEmpty()) {
                    image = it.attr(CONTENT)
                }
            }

            if (title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()) {
                return@withContext UrlInfoItem(url, title, description, image)
            }
        }
        return@withContext UrlInfoItem(url, title, description, image)
    }
