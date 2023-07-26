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

/* for <meta property="og:" to get title */
private val META_OG_TITLE = arrayOf("og:title", "\"og:title\"", "'og:title'")

/* for <meta property="og:" to get description */
private val META_OG_DESCRIPTION =
    arrayOf("og:description", "\"og:description\"", "'og:description'")

/* for <meta property="og:" to get image */
private val META_OG_IMAGE = arrayOf("og:image", "\"og:image\"", "'og:image'")

/*for <meta name=... to get title */
private val META_NAME_TITLE = arrayOf(
    "twitter:title",
    "\"twitter:title\"",
    "'twitter:title'",
    "title",
    "\"title\"",
    "'title'"
)

/*for <meta name=... to get description */
private val META_NAME_DESCRIPTION = arrayOf(
    "twitter:description",
    "\"twitter:description\"",
    "'twitter:description'",
    "description",
    "\"description\"",
    "'description'"
)

/*for <meta name=... to get image */
private val META_NAME_IMAGE = arrayOf(
    "twitter:image",
    "\"twitter:image\"",
    "'twitter:image'"
)

/*for <meta itemprop=... to get title */
private val META_ITEMPROP_TITLE = arrayOf("name", "\"name\"", "'name'")

/*for <meta itemprop=... to get description */
private val META_ITEMPROP_DESCRIPTION = arrayOf("description", "\"description\"", "'description'")

/*for <meta itemprop=... to get image */
private val META_ITEMPROP_IMAGE = arrayOf("image", "\"image\"", "'image'")

private const val CONTENT = "content"

suspend fun getDocument(url: String, timeOut: Int = 30000): Document =
    withContext(Dispatchers.IO) {
        val request: Request = Request.Builder().url(url).get().build()
        val html = HttpClient.getHttpClient().newCall(request).execute().use {
            it.body.string()
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
            val propertyTag = it.attr(ATTRIBUTE_VALUE_PROPERTY)
            when (propertyTag) {
                in META_OG_TITLE -> if (title.isEmpty()) title = it.attr(CONTENT)
                in META_OG_DESCRIPTION -> if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }
                in META_OG_IMAGE -> if (image.isEmpty()) image = it.attr(CONTENT)
            }

            when (it.attr(ATTRIBUTE_VALUE_NAME)) {
                in META_NAME_TITLE -> if (title.isEmpty()) title = it.attr(CONTENT)
                in META_NAME_DESCRIPTION -> if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }
                in META_OG_IMAGE -> if (image.isEmpty()) image = it.attr(CONTENT)
            }

            when (it.attr(ATTRIBUTE_VALUE_ITEMPROP)) {
                in META_ITEMPROP_TITLE -> if (title.isEmpty()) {
                    title = it.attr(CONTENT)
                }
                in META_ITEMPROP_DESCRIPTION -> if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }
                in META_ITEMPROP_IMAGE -> if (image.isEmpty()) {
                    image = it.attr(CONTENT)
                }
            }

            if (title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()) {
                return@withContext UrlInfoItem(url, title, description, image)
            }
        }
        return@withContext UrlInfoItem(url, title, description, image)
    }
