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
package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.richtext.ExpandableTextCutOffCalculator
import com.vitorpamplona.amethyst.commons.richtext.nthIndexOf
import junit.framework.TestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpandableTextCutOffCalculatorBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun computeTestCase1() {
        benchmarkRule.measureRepeated {
            TestCase.assertEquals(
                293,
                testCase1.nthIndexOf('\n', 10),
            )
        }
    }

    @Test
    fun computeTestCase2() {
        benchmarkRule.measureRepeated {
            TestCase.assertEquals(
                423,
                testCase2.nthIndexOf('\n', 10),
            )
        }
    }

    @Test
    fun computeTestCase1All() {
        benchmarkRule.measureRepeated {
            TestCase.assertEquals(
                293,
                ExpandableTextCutOffCalculator.indexToCutOff(testCase1),
            )
        }
    }

    @Test
    fun computeTestCase2All() {
        benchmarkRule.measureRepeated {
            TestCase.assertEquals(
                355,
                ExpandableTextCutOffCalculator.indexToCutOff(testCase2),
            )
        }
    }

    @Test
    fun computeTestCase3All() {
        benchmarkRule.measureRepeated {
            TestCase.assertEquals(
                65,
                ExpandableTextCutOffCalculator.indexToCutOff(testCase3),
            )
        }
    }

    val testCase1 = """
#Amethyst v0.83.10

تحديث جديد لـ Amethyst بإصدار 0.83.10 مع تعديلات وإضافات جديدة

: NIP-92 إصلاحات الأخطاء

 الإضافات الجديدة:
 - يتضمن رابط المنتج في الرسالة الأولى من المشتري في السوق
 - يضيف دعمًا لـ NIP-92 في الرسائل العامة والرسائل المباشرة الجديدة (NIP-17).  يبقى NIP-54 في NIP-04 DMs
 - إضافة التمرير الأفقي إلى أزرار الإجراءات في شاشة النشر الجديد لإصلاح الأزرار المخفية جزئيًا في الشاشات الصغيرة/الرفيعة.

 اصلاحات الشوائب:
 - إصلاحات التعطل مع مبلغ Zap مخصص غير صالح
 - يعمل على إصلاح مشكلات إعادة اتصال التتابع عندما يقوم المرحل بإغلاق الاتصال
 - إصلاح الحشو العلوي للملاحظة المقتبسة في المنشور
 - تحسين استخدام الذاكرة للمستخدم المرئي وعلامة URL في المشاركات الجديدة

 الترجمات المحدثة:
 - الفارسية بواسطة
 - الفرنسية والإنجليزية، المملكة المتحدة بواسطة
 - الأوكرانية
 - الإسبانية والإسبانية والمكسيك والإسبانية والولايات المتحدة بواسطة
 - العربية

 تحسينات جودة الكود:
 - تحديثات لنظام Android Studio 2023.1.1 Patch 2




nostr:nevent1qqszq7kl888sw0c5rpvepn8w373zt0jrw8864x8lkauxxw335s66rzgppemhxue69uhkummn9ekx7mp0qgsyvrp9u6p0mfur9dfdru3d853tx9mdjuhkphxuxgfwmryja7zsvhqrqsqqqqqpaax7m2
"""

    val testCase2 = """
#Amethyst v0.83.10: NIP-92 and Bug Fixes

New Additions:
- Includes a link to the product in the first message from the buyer in the marketplace
- Adds support for NIP-92 in public messages and new DMs (NIP-17). NIP-54 stays in NIP-04 DMs
- Adds Horizontal Scroll to the action buttons in the New Post screen to partially fix hidden buttons in small/thin screens.

Bugfixes:
- Fixes crash with an invalid custom Zap Amount
- Fixes relay re-connection issues when the relay closes a connection
- Fixes the top padding of the quoted note in a post
- Optimizes memory use of the visual user and url tagger in new posts

Updated translations:
- Persian by nostr:npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk
- French and English, United Kingdom by nostr:npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t
- Ukrainian by crowdin.com/profile/liizzzz
- Spanish, Spanish, Mexico and Spanish, United States by nostr:npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903
- Arabic by nostr:npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t

Code Quality Improvements:
- Updates to Android Studio 2023.1.1 Patch 2

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.10/amethyst-googleplay-universal-v0.83.10.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.10/amethyst-fdroid-universal-v0.83.10.apk )
"""

    val testCase3 = """#100aDayUntil100k
Day 5 ✔️

Seems like they may be getting easier"""
}
