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

    fun translateTo(text: String, translateTo: String): String? {
        val task = LanguageTranslatorService.autoTranslate(text, emptySet(), translateTo)
        return Tasks.await(task).result
    }

    fun assertTranslate(expected: String, input: String, translateTo: String) {
        assertEquals(null, expected, translateTo(input, translateTo))
    }

    fun assertTranslateContains(expected: String, input: String, translateTo: String) {
        val translated = translateTo(input, translateTo)!!
        assertTrue("'$translated' does not contain '$expected'", translated.contains(expected))
    }

    @Test
    fun testTranslation() {
        assertTranslate("Olá mundo", "Hello World", "pt")
    }

    @Test
    fun testTranslationName() {
        assertTranslate("Olá Vitor, como você está?", "Hello Vitor, how are you doing?", "pt")
    }

    @Test
    fun testTranslationTag() {
        assertTranslate("Você já viu isso, #[0]", "Have you seen this, #[0]", "pt")
    }

    @Test
    fun testTranslationUrl() {
        assertTranslateContains("https://t.me/mygroup", "Have you seen this https://t.me/mygroup", "pt")
        assertTranslateContains("http://bananas.com", "Have you seen this http://bananas.com", "pt")
        assertTranslateContains("http://bananas.com/myimage.jpg", "Have you seen this http://bananas.com/myimage.jpg", "pt")
        assertTranslateContains("http://bananas.com?search=true&image=myimage.jpg", "Have you seen this http://bananas.com?search=true&image=myimage.jpg", "pt")
        assertTranslate("https://i.imgur.com/EZ3QPsw.jpg", "https://i.imgur.com/EZ3QPsw.jpg", "pt")
        assertTranslate("https://HaveYouSeenThis.com", "https://HaveYouSeenThis.com", "pt")
        assertTranslate("https://haveyouseenthis.com", "https://haveyouseenthis.com", "pt")
        assertTranslate("https://i.imgur.com/asdEZ3QPsw.jpg", "https://i.imgur.com/asdEZ3QPsw.jpg", "pt")
        assertTranslateContains("https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg", "Hi there! \n How are you doing? \n https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg", "pt")
    }

    @Test
    fun testChineseWithUrlDetector() {
        assertTranslate("I entered your home page is very carton, perhaps your attention or other data is too much, and the homepage of others is not so carton. From aMethyst client", "我进入你的主页很卡顿，也许是你的关注人数或者其他数据太多了，其他人主页没有这么卡顿。来自amethyst客户端", "en")
    }

    @Test
    fun testTranslationEmail() {
        assertTranslateContains("vitor@amethyst.social", "Have you seen this vitor@amethyst.social", "pt")
    }

    @Test
    fun testTranslationLnInvoice() {
        assertTranslateContains(
            "lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjscqzpgxqyz5vqsp5zuzyetf33aphetf0e80w7tztw6dfsjs4lmvya4cyk8umfsx00qts9qyyssqke9hphcr36zvcav8wr502g0mhfhxpy8m9tt36zttg8vldm2qxw039ulccr8nwy3hjg2sw5vk65e99lwuhrhw0nuya2u57qszltvx7egp74jydn",
            "Have you seen this: lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjscqzpgxqyz5vqsp5zuzyetf33aphetf0e80w7tztw6dfsjs4lmvya4cyk8umfsx00qts9qyyssqke9hphcr36zvcav8wr502g0mhfhxpy8m9tt36zttg8vldm2qxw039ulccr8nwy3hjg2sw5vk65e99lwuhrhw0nuya2u57qszltvx7egp74jydn I think I have to pay",
            "pt"
        )

        assertTranslateContains(
            "lnbc10u1p3l0wg0pp5y5y3vxt3429m28uuq56uqhwxadftn67yaarq06h3y9nqapz72n6sdqqxqyjw5q9q7sqqqqqqqqqqqqqqqqqqqqqqqqq9qsqsp5y2tazp42xde3c0tdsz30zqcekrt0lzrneszdtagy2qn7vs0d3p5qrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rwr3vxeje0glcll7jdvcln4lhw5qqqqlgqqqqqeqqjqdau9jzseecmvmh03h88xyf5f980xx45fmn0cej654v5jr79ye36pww90jwdda38damlmgt54v8rn6q9kywtw057rh4v3wwrmn8fajagqnssr7v",
            "Test lnbc10u1p3l0wg0pp5y5y3vxt3429m28uuq56uqhwxadftn67yaarq06h3y9nqapz72n6sdqqxqyjw5q9q7sqqqqqqqqqqqqqqqqqqqqqqqqq9qsqsp5y2tazp42xde3c0tdsz30zqcekrt0lzrneszdtagy2qn7vs0d3p5qrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rwr3vxeje0glcll7jdvcln4lhw5qqqqlgqqqqqeqqjqdau9jzseecmvmh03h88xyf5f980xx45fmn0cej654v5jr79ye36pww90jwdda38damlmgt54v8rn6q9kywtw057rh4v3wwrmn8fajagqnssr7v",
            "pt"
        )
    }

    @Test
    fun testNostrEvents() {
        assertTranslateContains(
            "nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy",
            "sure, nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy",
            "en"
        )
    }

    @Test
    fun testJapaneseTranslationsOfUrl() {
        assertTranslateContains(
            "https://youtu.be/wMYFmCDy_Eg",
            "うちの会社の小さい先輩の話 第1話「うちの会社の先輩は小さくて可愛い」\n" +
                "\n" +
                "https://youtu.be/wMYFmCDy_Eg\n" +
                "\n" +
                "先輩がうざい後輩の話と似たような話かと思ったけど、もっとオタクの妄想あるある的なものを詰め込んだやつだ。ワードとかシチュエーションとか、ヒロインのサイズ感とか。知らんけど",
            "en"
        )
    }

    @Test
    fun testEmoji() {
        assertTranslateContains(
            "https://cdn.nostr.build/i/df3783dcdf7dd289ba02ba538dc039c8fe1d4db055e580b81604ed88c6af4ee0.jpg",
            "\uD83E\uDD23 https://cdn.nostr.build/i/df3783dcdf7dd289ba02ba538dc039c8fe1d4db055e580b81604ed88c6af4ee0.jpg ",
            "pt"
        )
    }
}
