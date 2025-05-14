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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.Height100Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.SquaredQuoteBorderModifier
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import kotlinx.collections.immutable.toImmutableList

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
fun SellProductPreview() {
    val accountViewModel = mockAccountViewModel()
    val postViewModel = NewProductViewModel()
    postViewModel.init(accountViewModel)

    ThemeComparisonColumn {
        SellProduct(postViewModel)
    }
}

@Composable
fun SellProduct(postViewModel: NewProductViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!postViewModel.productImages.isEmpty()) {
            LazyRow(Height100Modifier, horizontalArrangement = spacedBy(Size5dp)) {
                items(postViewModel.productImages) {
                    Box(SquaredQuoteBorderModifier) {
                        AsyncImage(
                            model = it.url,
                            contentDescription = it.alt ?: it.url,
                            contentScale = ContentScale.FillHeight,
                            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_title),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                value = postViewModel.title,
                onValueChange = {
                    postViewModel.updateTitle(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(R.string.classifieds_title_placeholder),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                visualTransformation =
                    UrlUserTagTransformation(
                        MaterialTheme.colorScheme.primary,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_price),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                modifier = Modifier.fillMaxWidth(),
                value = postViewModel.price,
                onValueChange = {
                    postViewModel.updatePrice(it)
                },
                placeholder = {
                    Text(
                        text = "1000",
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                    ),
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_condition),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            val conditionTypes =
                listOf(
                    Triple(
                        ConditionTag.CONDITION.NEW,
                        stringRes(id = R.string.classifieds_condition_new),
                        stringRes(id = R.string.classifieds_condition_new_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_LIKE_NEW,
                        stringRes(id = R.string.classifieds_condition_like_new),
                        stringRes(id = R.string.classifieds_condition_like_new_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_GOOD,
                        stringRes(id = R.string.classifieds_condition_good),
                        stringRes(id = R.string.classifieds_condition_good_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_FAIR,
                        stringRes(id = R.string.classifieds_condition_fair),
                        stringRes(id = R.string.classifieds_condition_fair_explainer),
                    ),
                )

            val conditionOptions =
                remember {
                    conditionTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
                }

            TextSpinner(
                placeholder = conditionTypes.filter { it.first == postViewModel.condition }.first().second,
                options = conditionOptions,
                onSelect = {
                    postViewModel.updateCondition(conditionTypes[it].first)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 5.dp, bottom = 1.dp),
            ) { currentOption, modifier ->
                ThinPaddingTextField(
                    value = TextFieldValue(currentOption),
                    onValueChange = {},
                    readOnly = true,
                    modifier = modifier,
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                )
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_category),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            val categoryList =
                listOf(
                    R.string.classifieds_category_clothing,
                    R.string.classifieds_category_accessories,
                    R.string.classifieds_category_electronics,
                    R.string.classifieds_category_furniture,
                    R.string.classifieds_category_collectibles,
                    R.string.classifieds_category_books,
                    R.string.classifieds_category_pets,
                    R.string.classifieds_category_sports,
                    R.string.classifieds_category_fitness,
                    R.string.classifieds_category_art,
                    R.string.classifieds_category_crafts,
                    R.string.classifieds_category_home,
                    R.string.classifieds_category_office,
                    R.string.classifieds_category_food,
                    R.string.classifieds_category_misc,
                    R.string.classifieds_category_other,
                )

            val categoryTypes = categoryList.map { Triple(it, stringRes(id = it), null) }

            val categoryOptions =
                remember {
                    categoryTypes.map { TitleExplainer(it.second, null) }.toImmutableList()
                }
            TextSpinner(
                placeholder = categoryTypes.firstOrNull { it.second == postViewModel.category.text }?.second ?: "",
                options = categoryOptions,
                onSelect = {
                    postViewModel.updateCategory(TextFieldValue(categoryTypes[it].second))
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 5.dp, bottom = 1.dp),
            ) { currentOption, modifier ->
                ThinPaddingTextField(
                    value = TextFieldValue(currentOption),
                    onValueChange = {},
                    readOnly = true,
                    modifier = modifier,
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                )
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_location),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                value = postViewModel.locationText,
                onValueChange = {
                    postViewModel.updateLocation(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(R.string.classifieds_location_placeholder),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                visualTransformation =
                    UrlUserTagTransformation(
                        MaterialTheme.colorScheme.primary,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}
