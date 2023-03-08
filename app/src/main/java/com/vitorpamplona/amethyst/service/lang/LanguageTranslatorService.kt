package com.vitorpamplona.amethyst.service.lang

import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import java.util.regex.Pattern

class ResultOrError(
    var result: String?,
    var sourceLang: String?,
    var targetLang: String?,
    var error: Exception?
)

object LanguageTranslatorService {
    private val languageIdentification = LanguageIdentification.getClient()
    val lnRegex = Pattern.compile("\\blnbc[a-z0-9]+\\b")

    private val translators =
        object : LruCache<TranslatorOptions, Translator>(10) {
            override fun create(options: TranslatorOptions): Translator {
                return Translation.getClient(options)
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: TranslatorOptions,
                oldValue: Translator,
                newValue: Translator?
            ) {
                oldValue.close()
            }
        }

    fun identifyLanguage(text: String): Task<String> {
        return languageIdentification.identifyLanguage(text)
    }

    fun translate(text: String, source: String, target: String): Task<ResultOrError> {
        val sourceLangCode = TranslateLanguage.fromLanguageTag(source)
        val targetLangCode = TranslateLanguage.fromLanguageTag(target)

        if (sourceLangCode == null || targetLangCode == null) {
            return Tasks.forCanceled()
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build()

        val translator = translators[options]

        return translator.downloadModelIfNeeded().onSuccessTask {
            val tasks = mutableListOf<Task<String>>()

            val dict = lnDictionary(text) + urlDictionary(text)

            for (paragraph in encodeDictionary(text, dict).split("\n")) {
                tasks.add(translator.translate(paragraph))
            }

            Tasks.whenAll(tasks).continueWith {
                val results: MutableList<String> = ArrayList()
                for (task in tasks) {
                    var fixedText = task.result.replace("# [", "#[") // fixes tags that always return with a space
                    results.add(decodeDictionary(fixedText, dict))
                }
                ResultOrError(results.joinToString("\n"), source, target, null)
            }
        }
    }

    private fun encodeDictionary(text: String, dict: Map<String, String>): String {
        var newText = text
        for (pair in dict) {
            newText = newText.replace(pair.value, pair.key, true)
        }
        return newText
    }

    private fun decodeDictionary(text: String, dict: Map<String, String>): String {
        var newText = text
        for (pair in dict) {
            newText = newText.replace(pair.key, pair.value, true)
        }
        return newText
    }

    private fun lnDictionary(text: String): Map<String, String> {
        val matcher = lnRegex.matcher(text)
        val returningList = mutableMapOf<String, String>()
        val counter = 0
        while (matcher.find()) {
            try {
                val lnInvoice = matcher.group()
                val short = "Amethystlnindexer$counter"
                returningList.put(short, lnInvoice)
            } catch (e: Exception) {
            }
        }
        return returningList
    }

    private fun urlDictionary(text: String): Map<String, String> {
        val parser = UrlDetector(text, UrlDetectorOptions.Default)
        val urlsInText = parser.detect()

        val counter = 0

        return urlsInText.filter { !it.originalUrl.contains("，") || !it.originalUrl.contains("。") }.associate {
            "Amethysturlindexer$counter" to it.originalUrl
        }
    }

    fun autoTranslate(text: String, dontTranslateFrom: Set<String>, translateTo: String): Task<ResultOrError> {
        return identifyLanguage(text).onSuccessTask {
            if (it == translateTo) {
                Tasks.forCanceled()
            } else if (it != "und" && !dontTranslateFrom.contains(it)) {
                translate(text, it, translateTo)
            } else {
                Tasks.forCanceled()
            }
        }
    }
}
