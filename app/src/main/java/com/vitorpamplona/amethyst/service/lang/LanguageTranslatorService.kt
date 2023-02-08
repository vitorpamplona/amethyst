package com.vitorpamplona.amethyst.service.lang

import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.ArrayList

class ResultOrError(
  var result: String?,
  var sourceLang: String?,
  var targetLang: String?,
  var error: Exception?
)

object LanguageTranslatorService {
  private val languageIdentification = LanguageIdentification.getClient()

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
      for (paragraph in text.split("\n")) {
        tasks.add(translator.translate(paragraph))
      }

      Tasks.whenAll(tasks).continueWith {
        val results: MutableList<String> = ArrayList()
        for (task in tasks) {
          results.add(task.result)
        }
        ResultOrError(results.joinToString("\n"), source, target, null)
      }
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