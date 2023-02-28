package com.vitorpamplona.amethyst.ui.components

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.ResultOrError
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.util.Locale

@Composable
fun TranslateableRichTextViewer(
  content: String,
  canPreview: Boolean,
  modifier: Modifier = Modifier,
  tags: List<List<String>>?,
  accountViewModel: AccountViewModel,
  navController: NavController
) {
  val translatedTextState = remember {
    mutableStateOf(ResultOrError(content, null, null, null))
  }

  var showOriginal by remember { mutableStateOf(false) }
  var langSettingsPopupExpanded by remember { mutableStateOf(false) }

  val context = LocalContext.current

  val accountState by accountViewModel.accountLanguagesLiveData.observeAsState()
  val account = accountState?.account ?: return

  LaunchedEffect(accountState) {
    LanguageTranslatorService.autoTranslate(
      content,
      account.dontTranslateFrom,
      account.translateTo
    ).addOnCompleteListener { task ->
      if (task.isSuccessful) {
        if (task.result.sourceLang != null && task.result.targetLang != null) {
          val preference = account.preferenceBetween(task.result.sourceLang!!, task.result.targetLang!!)
          showOriginal = preference == task.result.sourceLang
        }
        translatedTextState.value = task.result
      } else {
        translatedTextState.value = ResultOrError(content, null, null, null)
      }
    }
  }

  val toBeViewed = if (showOriginal) content else translatedTextState.value.result ?: content

  Column(modifier = Modifier.padding(top = 5.dp)) {
    ExpandableRichTextViewer(
      toBeViewed,
      canPreview,
      modifier,
      tags,
      accountViewModel,
      navController
    )

    val target = translatedTextState.value.targetLang
    val source = translatedTextState.value.sourceLang

    if (source != null && target != null) {
      if (source != target) {
        Row(modifier = Modifier
          .fillMaxWidth()
          .padding(top = 5.dp)) {
          val clickableTextStyle =
            SpanStyle(color = MaterialTheme.colors.primary.copy(alpha = 0.52f))

          val annotatedTranslationString = buildAnnotatedString {
            withStyle(clickableTextStyle) {
              pushStringAnnotation("langSettings", true.toString())
              append(stringResource(R.string.auto))
            }

            append("-${stringResource(R.string.translated_from)} ")

            withStyle(clickableTextStyle) {
              pushStringAnnotation("showOriginal", true.toString())
              append(Locale(source).displayName)
            }

            append(" ${stringResource(R.string.to)} ")

            withStyle(clickableTextStyle) {
              pushStringAnnotation("showOriginal", false.toString())
              append(Locale(target).displayName)
            }
          }

          ClickableText(
            text = annotatedTranslationString,
            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)),
            overflow = TextOverflow.Visible,
            maxLines = 3
          ) { spanOffset ->
            annotatedTranslationString.getStringAnnotations(spanOffset, spanOffset)
              .firstOrNull()
              ?.also { span ->
                if (span.tag == "showOriginal")
                  showOriginal = span.item.toBoolean()
                else
                  langSettingsPopupExpanded = !langSettingsPopupExpanded
              }
          }

          DropdownMenu(
            expanded = langSettingsPopupExpanded,
            onDismissRequest = { langSettingsPopupExpanded = false }
          ) {
            DropdownMenuItem(onClick = {
              accountViewModel.dontTranslateFrom(source, context)
              langSettingsPopupExpanded = false
            }) {
              if (source in account.dontTranslateFrom)
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
              else
                Spacer(modifier = Modifier.size(24.dp))

              Spacer(modifier = Modifier.size(10.dp))

              Text(stringResource(R.string.never_translate_from) + "${Locale(source).displayName}")
            }
            Divider()
            DropdownMenuItem(onClick = {
              accountViewModel.prefer(source, target, source)
              langSettingsPopupExpanded = false
            }) {
              if (account.preferenceBetween(source, target) == source)
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
              else
                Spacer(modifier = Modifier.size(24.dp))

              Spacer(modifier = Modifier.size(10.dp))

              // TODO : Rashed translate this
              Text(
                "${stringResource(R.string.show_in)} ${Locale(source).displayName} ${
                  stringResource(
                    R.string.first
                  )
                }"
              )
            }
            DropdownMenuItem(onClick = {
              accountViewModel.prefer(source, target, target)
              langSettingsPopupExpanded = false
            }) {
              if (account.preferenceBetween(source, target) == target)
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
              else
                Spacer(modifier = Modifier.size(24.dp))

              Spacer(modifier = Modifier.size(10.dp))

              Text("${stringResource(id = R.string.show_in)} ${Locale(target).displayName} ${R.string.first}")
            }
            Divider()

            val languageList =
              ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
            for (i in 0 until languageList.size()) {
              languageList.get(i)?.let { lang ->
                DropdownMenuItem(onClick = {
                  accountViewModel.translateTo(lang, context)
                  langSettingsPopupExpanded = false
                }) {
                  if (lang.language in account.translateTo)
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  else
                    Spacer(modifier = Modifier.size(24.dp))

                  Spacer(modifier = Modifier.size(10.dp))
                  
                  Text("${stringResource(R.string.always_translate_to)}${lang.displayName}")
                }
              }
            }
          }
        }
      }
    }
  }
}