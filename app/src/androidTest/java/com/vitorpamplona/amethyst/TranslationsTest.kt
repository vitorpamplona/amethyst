package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationsTest {

  fun translate(text: String): String? {
    val task = LanguageTranslatorService.autoTranslate(text, emptySet(), "pt")
    return Tasks.await(task).result
  }

  fun assertTranslate(expected: String, input: String) {
    assertEquals(null, expected, translate(input))
  }

  fun assertTranslateContains(expected: String, input: String) {
    assertTrue(null, translate(input)!!.contains(expected))
  }

  @Test
  fun testTranslation() {
    assertTranslate("Olá mundo", "Hello World")
  }

  @Test
  fun testTranslationName() {
    assertTranslate("Olá Vitor, como você está?", "Hello Vitor, how are you doing?")
  }

  @Test
  fun testTranslationTag() {
    assertTranslate("Você já viu isso, #[0]", "Have you seen this, #[0]")
  }

  @Test
  fun testTranslationUrl() {
    assertTranslateContains("https://t.me/mygroup", "Have you seen this https://t.me/mygroup")
    assertTranslateContains("http://bananas.com", "Have you seen this http://bananas.com")
    assertTranslateContains("http://bananas.com/myimage.jpg", "Have you seen this http://bananas.com/myimage.jpg")
    assertTranslateContains("http://bananas.com?search=true&image=myimage.jpg", "Have you seen this http://bananas.com?search=true&image=myimage.jpg")
    assertTranslate("https://i.imgur.com/EZ3QPsw.jpg", "https://i.imgur.com/EZ3QPsw.jpg")
    assertTranslate("https://HaveYouSeenThis.com", "https://HaveYouSeenThis.com")
    assertTranslate("https://haveyouseenthis.com", "https://haveyouseenthis.com")
    assertTranslate("https://i.imgur.com/asdEZ3QPsw.jpg", "https://i.imgur.com/asdEZ3QPsw.jpg")
    assertTranslateContains("https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg", "Hi there! \n How are you doing? \n https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg")
  }

  @Test
  fun testTranslationEmail() {
    assertTranslateContains("vitor@amethyst.social", "Have you seen this vitor@amethyst.social")
  }

  @Test
  fun testTranslationLnInvoice() {
    assertTranslateContains(
      "lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjscqzpgxqyz5vqsp5zuzyetf33aphetf0e80w7tztw6dfsjs4lmvya4cyk8umfsx00qts9qyyssqke9hphcr36zvcav8wr502g0mhfhxpy8m9tt36zttg8vldm2qxw039ulccr8nwy3hjg2sw5vk65e99lwuhrhw0nuya2u57qszltvx7egp74jydn",
      "Have you seen this: lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjscqzpgxqyz5vqsp5zuzyetf33aphetf0e80w7tztw6dfsjs4lmvya4cyk8umfsx00qts9qyyssqke9hphcr36zvcav8wr502g0mhfhxpy8m9tt36zttg8vldm2qxw039ulccr8nwy3hjg2sw5vk65e99lwuhrhw0nuya2u57qszltvx7egp74jydn I think I have to pay"
    )

    assertTranslateContains(
      "lnbc10u1p3l0wg0pp5y5y3vxt3429m28uuq56uqhwxadftn67yaarq06h3y9nqapz72n6sdqqxqyjw5q9q7sqqqqqqqqqqqqqqqqqqqqqqqqq9qsqsp5y2tazp42xde3c0tdsz30zqcekrt0lzrneszdtagy2qn7vs0d3p5qrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rwr3vxeje0glcll7jdvcln4lhw5qqqqlgqqqqqeqqjqdau9jzseecmvmh03h88xyf5f980xx45fmn0cej654v5jr79ye36pww90jwdda38damlmgt54v8rn6q9kywtw057rh4v3wwrmn8fajagqnssr7v",
      "Test lnbc10u1p3l0wg0pp5y5y3vxt3429m28uuq56uqhwxadftn67yaarq06h3y9nqapz72n6sdqqxqyjw5q9q7sqqqqqqqqqqqqqqqqqqqqqqqqq9qsqsp5y2tazp42xde3c0tdsz30zqcekrt0lzrneszdtagy2qn7vs0d3p5qrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rwr3vxeje0glcll7jdvcln4lhw5qqqqlgqqqqqeqqjqdau9jzseecmvmh03h88xyf5f980xx45fmn0cej654v5jr79ye36pww90jwdda38damlmgt54v8rn6q9kywtw057rh4v3wwrmn8fajagqnssr7v"
    )
  }
}