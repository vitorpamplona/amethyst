/*
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
package com.vitorpamplona.quartz.utils.urldetector.detection

import com.vitorpamplona.quartz.utils.urldetector.Url
import kotlin.test.Test
import kotlin.test.assertEquals

class UriDetectionTest {
    @Test
    fun testBasicString() {
        runTest("hello world")
    }

    @Test
    fun testBasicDetect() {
        runTest("this is a link: www.google.com", "www.google.com")
    }

    @Test
    fun testSimple() {
        runTest(
            "http://www.linkedin.com/vshlos",
            "http://www.linkedin.com/vshlos",
        )
    }

    @Test
    fun testEmailAndNormalUrl() {
        runTest(
            "my email is vshlosbe@linkedin.com and my site is http://www.linkedin.com/vshlos",
            "vshlosbe@linkedin.com",
            "http://www.linkedin.com/vshlos",
        )
    }

    @Test
    fun testTwoBasicUrls() {
        runTest(
            "the url google.com is a lot better then www.google.com.",
            "google.com",
            "www.google.com",
        )
    }

    @Test
    fun testLongUrl() {
        runTest(
            "google.com.google.com is kind of a valid url",
            "google.com.google.com",
        )
    }

    @Test
    fun testInternationalUrls() {
        runTest(
            "this is an international domain: http://\u043F\u0440\u0438\u043c\u0435\u0440.\u0438\u0441\u043f\u044b" +
                "\u0442\u0430\u043d\u0438\u0435 so is this: \u4e94\u7926\u767c\u5c55.\u4e2d\u570b.",
            "http://\u043F\u0440\u0438\u043c\u0435\u0440.\u0438\u0441\u043f\u044b\u0442\u0430\u043d\u0438\u0435",
            "\u4e94\u7926\u767c\u5c55.\u4e2d\u570b",
        )
    }

    @Test
    fun testDomainWithUsernameAndPassword() {
        runTest(
            "domain with username is http://username:password@www.google.com/site/1/2",
            "http://username:password@www.google.com/site/1/2",
        )
    }

    @Test
    fun testFTPWithUsernameAndPassword() {
        runTest(
            "ftp with username is ftp://username:password@www.google.com",
            "ftp://username:password@www.google.com",
        )
    }

    @Test
    fun testUncommonFormatUsernameAndPassword() {
        runTest(
            "weird url with username is username:password@www.google.com",
            "username:password@www.google.com",
        )
    }

    @Test
    fun testEmailAndLinkWithUserPass() {
        runTest(
            "email and username is hello@test.google.com or hello@www.google.com hello:password@www.google.com",
            "hello@test.google.com",
            "hello@www.google.com",
            "hello:password@www.google.com",
        )
    }

    @Test
    fun testWrongSpacingInSentence() {
        runTest(
            "I would not like to work at salesforce.com, it looks like a crap company.and not cool!",
            "salesforce.com",
            "company.and",
        )
    }

    @Test
    fun testNumbersAreNotDetected() {
        // make sure pure numbers don't work, but domains with numbers do.
        runTest("Do numbers work? such as 3.1415 or 4.com", "4.com")
    }

    @Test
    fun testNewLinesAndTabsAreDelimiters() {
        runTest(
            "Do newlines and tabs break? google.com/hello/\nworld www.yahoo.com\t/stuff/ yahoo.com/\thello news.ycombinator.com\u0000/hello world",
            "google.com/hello/",
            "www.yahoo.com",
            "stuff/",
            "yahoo.com/",
            "news.ycombinator.com",
        )
    }

    @Test
    fun testIpAddressFormat() {
        runTest(
            "How about IP addresses? fake: 1.1.1 1.1.1.1.1 0.0.0.256 255.255.255.256 real: 1.1.1.1 192.168.10.1 1.1.1.1.com 255.255.255.255",
            "1.1.1.1",
            "192.168.10.1",
            "1.1.1.1.com",
            "255.255.255.255",
        )
    }

    @Test
    fun testNumericIpAddress() {
        runTest(
            "http://3232235521/helloworld",
            "http://3232235521/helloworld",
        )
    }

    @Test
    fun testNumericIpAddressWithPort() {
        runTest(
            "http://3232235521:8080/helloworld",
            "http://3232235521:8080/helloworld",
        )
    }

    @Test
    fun testDomainAndLabelSizeConstraints() {
        // Really long addresses testing rules about total length of domain name and number of labels in a domain and size of each label.
        runTest(
            (
                "This will work: 1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.a.b.c.d.e.ly " +
                    "This will work:  1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.a.b.c.d.e.f.ly " +
                    "This should as well: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.dddddddddddddddddddddddddddddddddddddddddddddddddddddd.bit.ly " +
                    "But should as well: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.dddddddddddddddddddddddddddddddddddddddddddddddddddddd.bit.ly.dbl.spamhaus.org"
            ),
            "1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.a.b.c.d.e.ly",
            "1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0.a.b.c.d.e.f.ly",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.dddddddddddddddddddddddddddddddddddddddddddddddddddddd.bit.ly",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.dddddddddddddddddddddddddddddddddddddddddddddddddddddd.bit.ly.dbl.spamhaus.org",
        )
    }

    @Test
    fun testIncorrectParsingHtmlWithBadOptions() {
        runTest(
            "<a href=\"http://www.google.com/\">google.com</a>",
            "http://www.google.com/\">google.com</a>",
        )
    }

    @Test
    fun testNonStandardDots() {
        runTest(
            "www\u3002google\u3002com username:password@www\uFF0Eyahoo\uFF0Ecom http://www\uFF61facebook\uFF61com http://192\u3002168\uFF0E0\uFF611/",
            "www\u3002google\u3002com",
            "username:password@www\uFF0Eyahoo\uFF0Ecom",
            "http://www\uFF61facebook\uFF61com",
            "http://192\u3002168\uFF0E0\uFF611/",
        )
    }

    @Test
    fun testNonStandardDotsBacktracking() {
        runTest("\u9053 \u83dc\u3002\u3002\u3002\u3002")
    }

    @Test
    fun testBacktrackingStrangeFormats() {
        runTest(
            "http:http:http://www.google.com www.www:yahoo.com yahoo.com.br hello.hello..hello.com",
            "http://www.google.com",
            "www.www",
            "yahoo.com",
            "yahoo.com.br",
            "hello.hello",
            "hello.com",
        )
    }

    @Test
    fun testBacktrackingUsernamePassword() {
        runTest("check out my url:www.google.com", "www.google.com")
        runTest("check out my url:www.google.com ", "www.google.com")
    }

    @Test
    fun testBacktrackingEmptyDomainName() {
        runTest("check out my http:///hello")
        runTest("check out my http://./hello")
    }

    @Test
    fun testDoubleScheme() {
        runTest("http://http://")
        runTest("hello http://http://")
    }

    @Test
    fun testMultipleSchemes() {
        runTest("http://http://www.google.com", "http://www.google.com")
        runTest(
            "make sure it's right here http://http://www.google.com",
            "http://www.google.com",
        )
        runTest(
            "http://http://http://www.google.com",
            "http://www.google.com",
        )
        runTest(
            "make sure it's right here http://http://http://www.google.com",
            "http://www.google.com",
        )
        runTest(
            "http://ftp://https://www.google.com",
            "https://www.google.com",
        )
        runTest(
            "make sure its right here http://ftp://https://www.google.com",
            "https://www.google.com",
        )
    }

    @Test
    fun testDottedHexIpAddress() {
        runTest(
            "http://0xc0.0x00.0xb2.0xEB",
            "http://0xc0.0x00.0xb2.0xEB",
        )
        runTest(
            "http://0xc0.0x0.0xb2.0xEB",
            "http://0xc0.0x0.0xb2.0xEB",
        )
        runTest(
            "http://0x000c0.0x00000.0xb2.0xEB",
            "http://0x000c0.0x00000.0xb2.0xEB",
        )
        runTest(
            "http://0xc0.0x00.0xb2.0xEB/bobo",
            "http://0xc0.0x00.0xb2.0xEB/bobo",
        )
        runTest(
            "ooh look i can find it in text http://0xc0.0x00.0xb2.0xEB/bobo like this",
            "http://0xc0.0x00.0xb2.0xEB/bobo",
        )
        runTest(
            "noscheme look 0xc0.0x00.0xb2.0xEB/bobo",
            "0xc0.0x00.0xb2.0xEB/bobo",
        )
        runTest(
            "no scheme 0xc0.0x00.0xb2.0xEB or path",
            "0xc0.0x00.0xb2.0xEB",
        )
    }

    @Test
    fun testDottedOctalIpAddress() {
        runTest(
            "http://0301.0250.0002.0353",
            "http://0301.0250.0002.0353",
        )
        runTest(
            "http://0301.0250.0002.0353/bobo",
            "http://0301.0250.0002.0353/bobo",
        )
        runTest("http://192.168.017.015/", "http://192.168.017.015/")
        runTest(
            "ooh look i can find it in text http://0301.0250.0002.0353/bobo like this",
            "http://0301.0250.0002.0353/bobo",
        )
        runTest(
            "noscheme look 0301.0250.0002.0353/bobo",
            "0301.0250.0002.0353/bobo",
        )
        runTest(
            "no scheme 0301.0250.0002.0353 or path",
            "0301.0250.0002.0353",
        )
    }

    @Test
    fun testHexIpAddress() {
        runTest("http://0xC00002EB/hello", "http://0xC00002EB/hello")
        runTest(
            "http://0xC00002EB.com/hello",
            "http://0xC00002EB.com/hello",
        )
        runTest(
            "still look it up as a normal url http://0xC00002EXsB.com/hello",
            "http://0xC00002EXsB.com/hello",
        )
        runTest(
            "ooh look i can find it in text http://0xC00002EB/bobo like this",
            "http://0xC00002EB/bobo",
        )
        runTest(
            "browsers dont support this without a scheme look 0xC00002EB/bobo",
            "0xC00002EB/bobo",
        )
        runTest(
            "browsers dont support this without a scheme look test/bobo",
        )
    }

    @Test
    fun testOctalIpAddress() {
        runTest(
            "http://030000001353/bobobo",
            "http://030000001353/bobobo",
        )
        runTest(
            "ooh look i can find it in text http://030000001353/bobo like this",
            "http://030000001353/bobo",
        )
        runTest(
            "browsers dont support this without a scheme look 030000001353/bobo",
            "030000001353/bobo",
        )
        runTest(
            "browsers dont support this without a scheme look 1727123/bobo",
        )
    }

    @Test
    fun testUrlWithEmptyPort() {
        runTest(
            "http://wtfismyip.com:/foo.html",
            "http://wtfismyip.com:/foo.html",
        )
        runTest(
            "http://wtfismyip.com://foo.html",
            "http://wtfismyip.com://foo.html",
        )
        runTest(
            "make sure its right here http://wtfismyip.com://foo.html",
            "http://wtfismyip.com://foo.html",
        )
    }

    @Test
    fun testUrlEncodedDot() {
        runTest("hello www%2ewtfismyip%2ecom", "www%2ewtfismyip%2ecom")
        runTest("hello wtfismyip%2ecom", "wtfismyip%2ecom")
        runTest("http://wtfismyip%2ecom", "http://wtfismyip%2ecom")
        runTest(
            "make sure its right here http://wtfismyip%2ecom",
            "http://wtfismyip%2ecom",
        )
    }

    @Test
    fun testUrlEncodedBadPath() {
        runTest("%2ewtfismyip")
        runTest("wtfismyip%2e")
        runTest("wtfismyip%2ecom%2e", "wtfismyip%2ecom%2e")
        runTest("wtfismyip%2ecom.", "wtfismyip%2ecom")
        runTest("%2ewtfismyip%2ecom", "wtfismyip%2ecom")
    }

    @Test
    fun testDetectUrlEncoded() {
        runTest(
            "%77%77%77%2e%67%75%6d%62%6c%61%72%2e%63%6e",
            "%77%77%77%2e%67%75%6d%62%6c%61%72%2e%63%6e",
        )
        runTest(
            " asdf  %77%77%77%2e%67%75%6d%62%6c%61%72%2e%63%6e",
            "%77%77%77%2e%67%75%6d%62%6c%61%72%2e%63%6e",
        )
        runTest(
            "%77%77%77%2e%67%75%6d%62%6c%61%72%2e%63%6e%2e",
            "%77%77%77%2e%67%75%6d%62%6c%61%72%2e%63%6e%2e",
        )
    }

    @Test
    fun testIncompleteIpAddresses() {
        runTest("hello 10...")
        runTest("hello 10...1")
        runTest("hello 10..1.")
        runTest("hello 10..1.1")
        runTest("hello 10.1..1")
        runTest("hello 10.1.1.")
        runTest("hello .192..")
        runTest("hello .192..1")
        runTest("hello .192.1.")
        runTest("hello .192.1.1")
        runTest("hello ..3.")
        runTest("hello ..3.1")
        runTest("hello ...1")
    }

    @Test
    fun testIPv4EncodedDot() {
        runTest("hello 192%2e168%2e1%2e1", "192%2e168%2e1%2e1")
        runTest(
            "hello 192.168%2e1%2e1/lalala",
            "192.168%2e1%2e1/lalala",
        )
    }

    @Test
    fun testIPv4HexEncodedDot() {
        runTest(
            "hello 0xee%2e0xbb%2e0x1%2e0x1",
            "0xee%2e0xbb%2e0x1%2e0x1",
        )
        runTest(
            "hello 0xee%2e0xbb.0x1%2e0x1/lalala",
            "0xee%2e0xbb.0x1%2e0x1/lalala",
        )
    }

    @Test
    fun testIpv6BadWithGoodUrls() {
        runTest("[:::] [::] [bacd::]", "[::]", "[bacd::]")
        runTest("[:0][::]", "[::]")
        runTest("[:0:][::afaf]", "[::afaf]")
        runTest(
            "::] [fe80:aaaa:aaaa:aaaa::]",
            "[fe80:aaaa:aaaa:aaaa::]",
        )
        runTest(
            "fe80:22:]3123:[adf] [fe80:aaaa:aaaa:aaaa::]",
            "[fe80:aaaa:aaaa:aaaa::]",
        )
        runTest("[][123[][ae][fae][de][:a][d]aef:E][f")
        runTest("[][][]2[d][]][]]]:d][[[:d[e][aee:]af:")
    }

    @Test
    fun testIpv6BadWithGoodUrlsEmbedded() {
        runTest("[b[::7f8e]:55]akjef[::]", "[::7f8e]:55", "[::]")
        runTest(
            "[bcad::kkkk:aaaa:3dd0[::7f8e]:57b7:34d5]akjef[::]",
            "aaaa:3",
            "[::7f8e]:57",
            "b7:34",
            "[::]",
        )
    }

    @Test
    fun testIpv6BadWithGoodUrlsWeirder() {
        runTest("[:[::]", "[::]")
        runTest("[:] [feed::]", "[feed::]")
        runTest(":[::feee]:]", "[::feee]")
        runTest(":[::feee]:]]", "[::feee]")
        runTest("[[:[::feee]:]", "[::feee]")
    }

    @Test
    fun testIpv6ConsecutiveGoodUrls() {
        runTest("[::afaf][eaea::][::]", "[::afaf]", "[eaea::]", "[::]")
        runTest("[::afaf]www.google.com", "[::afaf]", "www.google.com")
        runTest("[lalala:we][::]", "[::]")
        runTest("[::fe][::]", "[::fe]", "[::]")
        runTest("[aaaa::][:0:][::afaf]", "[aaaa::]", "[::afaf]")
    }

    @Test
    fun testIpv6BacktrackingUsernamePassword() {
        runTest("check out my url:google.com", "google.com")
        runTest(
            "check out my url:[::BAD:DEAD:BEEF:2e80:0:0]",
            "[::BAD:DEAD:BEEF:2e80:0:0]",
        )
        runTest(
            "check out my url:[::BAD:DEAD:BEEF:2e80:0:0] ",
            "[::BAD:DEAD:BEEF:2e80:0:0]",
        )
    }

    @Test
    fun testIpv6BacktrackingEmptyDomainName() {
        runTest("check out my http:///[::2e80:0:0]", "[::2e80:0:0]")
        runTest("check out my http://./[::2e80:0:0]", "[::2e80:0:0]")
    }

    @Test
    fun testIpv6DoubleSchemeWithDomain() {
        runTest("http://http://[::2e80:0:0]", "http://[::2e80:0:0]")
        runTest(
            "make sure its right here http://http://[::2e80:0:0]",
            "http://[::2e80:0:0]",
        )
    }

    @Test
    fun testIpv6MultipleSchemes() {
        runTest(
            "http://http://http://[::2e80:0:0]",
            "http://[::2e80:0:0]",
        )
        runTest(
            "make sure its right here http://http://[::2e80:0:0]",
            "http://[::2e80:0:0]",
        )
        runTest(
            "http://ftp://https://[::2e80:0:0]",
            "https://[::2e80:0:0]",
        )
        runTest(
            "make sure its right here http://ftp://https://[::2e80:0:0]",
            "https://[::2e80:0:0]",
        )
    }

    @Test
    fun testIpv6FtpWithUsernameAndPassword() {
        runTest(
            "ftp with username is ftp://username:password@[::2e80:0:0]",
            "ftp://username:password@[::2e80:0:0]",
        )
    }

    @Test
    fun testIpv6NewLinesAndTabsAreDelimiters() {
        runTest(
            "Do newlines and tabs break? [::2e80:0:0]/hello/\nworld [::BEEF:ADD:BEEF]\t/stuff/ [AAbb:AAbb:AAbb::]/\thello [::2e80:0:0\u0000]/hello world",
            "[::2e80:0:0]/hello/",
            "[::BEEF:ADD:BEEF]",
            "stuff/",
            "[AAbb:AAbb:AAbb::]/",
        )
    }

    @Test
    fun testIpv6WithPort() {
        runTest(
            "http://[AAbb:AAbb:AAbb::]:8080/helloworld",
            "http://[AAbb:AAbb:AAbb::]:8080/helloworld",
        )
    }

    @Test
    fun testIpv6IncorrectParsingHtmlWithBadOptions() {
        runTest(
            "<a href=\"http://[::AAbb:]/\">google.com</a>",
            "http://[::AAbb:]/\">google.com</a>",
        )
    }

    @Test
    fun testIpv6EmptyPort() {
        runTest(
            "http://[::AAbb:]://foo.html",
            "http://[::AAbb:]://foo.html",
        )
        runTest(
            "make sure its right here http://[::AAbb:]://foo.html",
            "http://[::AAbb:]://foo.html",
        )
    }

    @Test
    fun testBacktrackInvalidUsernamePassword() {
        runTest("http://hello:asdf.com", "asdf.com")
    }

    /*
     * https://github.com/linkedin/URL-Detector/issues/12
     */
    @Test
    fun testIssue12() {
        runTest(
            "http://user:pass@host.com host.com",
            "http://user:pass@host.com",
            "host.com",
        )
    }

    /*
     * https://github.com/linkedin/URL-Detector/issues/15
     */
    @Test
    fun testIssue15() {
        runTest(
            ".............:::::::::::;;;;;;;;;;;;;;;::...............................................:::::::::::::::::::::::::::::....................",
        )
    }

    /*
     * https://github.com/linkedin/URL-Detector/issues/16
     */
    @Test
    fun testIssue16() {
        runTest("://VIVE MARINE LE PEN//:@.")
    }

    @Test
    fun testColonWithoutSlashesFail() {
        val parser = UrlDetector("ftp:example.com")
        val found: List<Url> = parser.detect()
        for (url in found) {
            assertEquals(url.scheme, "ftp")
            // Should be detected as a username now and set to default http://
            assertEquals(url.host, "example.com")
        }
    }

    @Test
    fun testSingleLevelDomain() {
        runTest("http://localhost:9000/lalala hehe", "http://localhost:9000/lalala")
        runTest("localhost:9000/lalala hehe", "localhost:9000/lalala")
        runTest("http://localhost lasdf", "http://localhost")
        runTest("localhost:9000/lalala", "localhost:9000/lalala")
        runTest("192.168.1.1/lalala", "192.168.1.1/lalala")
        runTest("http://localhost", "http://localhost")
        runTest("//localhost", "//localhost")
        runTest("asf//localhost")
        runTest("hello/", "hello/")
        runTest("hello/ ", "hello/")
        runTest("hello")
        runTest("go/", "go/")
        runTest("hello:password@go12//", "hello:password@go12//")
        runTest("hello:password@go12", "hello:password@go12")
        runTest("hello:password@go12 lala", "hello:password@go12")
        runTest("hello.com..", "hello.com")
        runTest("a/")
        runTest("4/5")
        runTest("concerns/worries")
        runTest("asdflocalhost aksdjfhads")
        runTest("/")
        runTest("////")
        runTest("hi:")
        runTest("hi: ")
        runTest("hi:\n")
        runTest("testing normal phrase")
        runTest("testing normal/something phrase")
        runTest("testing normal: phrase")
    }

    @Test
    fun testLongSingleLabelDomain() {
        runTest("user:password@localhost", "user:password@localhost")
    }

    @Test
    fun testShortSingleLabelDomain() {
        runTest("user:password@go12", "user:password@go12")
    }

    @Test
    fun testIssueUnderscore() {
        runTest("Neomobius_at_mstdn.jp@mostr.pub", "Neomobius_at_mstdn.jp@mostr.pub")
    }

    @Test
    fun testNostr() {
        runTest("Check this post nostr:npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf . I think it is really cool", "nostr:npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf")
    }

    @Test
    fun testBlossom() {
        runTest("Check this image blossom:somethingsomething . I think it is really cool", "blossom:somethingsomething")
    }

    @Test
    fun testNostrSlashes() {
        runTest("Check this post nostr://npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf . I think it is really cool", "nostr://npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf")
    }

    @Test
    fun testBlossomWithSlashes() {
        runTest("Check this image blossom://somethingsomething . I think it is really cool", "blossom://somethingsomething")
    }

    @Test
    fun testNostr2() {
        runTest("I saw this on nostr: somethingsomething. I think it is really cool")
    }

    @Test
    fun testBlossom2() {
        runTest("I saw this on blossom: somethingsomething. I think it is really cool")
    }

    @Test
    fun testUnsupportedSchema() {
        runTest("I saw this on hxxp://test.com I think it is really cool")
    }

    @Test
    fun testBlossomSchema() {
        runTest("blossom:b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf?xs=cdn.satellite.earth", "blossom:b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf?xs=cdn.satellite.earth")
    }

    @Test
    fun testBlossomShema2() {
        runTest("blossom:9584b6d64e43747364b10276f4b821e5df09f46477b3b8c60cced3e8c647fbef.jpg?xs=blossom.primal.net", "blossom:9584b6d64e43747364b10276f4b821e5df09f46477b3b8c60cced3e8c647fbef.jpg?xs=blossom.primal.net")
    }

    @Test
    fun testLocalHost() {
        runTest("wss://localhost:3030", "wss://localhost:3030")
    }

    @Test
    fun testBrokenCaseInProduction() {
        runTest("今北産業")
        runTest("http://test.com今北産業", "http://test.com")
        runTest("ftp://test.com今北産業", "ftp://test.com")
        runTest("test.com今北産業", "test.com")
        runTest("wss://test.com今北産業", "wss://test.com")
        runTest("blossom:test.com今北産業", "blossom:test.com")
        runTest("nostr:test.com今北産業", "nostr:test.com")
        runTest("nostr:test今北産業", "nostr:test")
        runTest("nostr:nprofile1qqsv0agl52pt4e5pe586fz9vsd5phqz7je49yrcg532h2u5nejsc8gcpzamhxue69uhhxetpwf3kstnwdaejuar0v3shjtcv8453m今北産業", "nostr:nprofile1qqsv0agl52pt4e5pe586fz9vsd5phqz7je49yrcg532h2u5nejsc8gcpzamhxue69uhhxetpwf3kstnwdaejuar0v3shjtcv8453m")

        runTest("今北産業http://test.com", "http://test.com")
        runTest("今北産業ftp://test.com", "ftp://test.com")
        runTest("今北産業test.com", "test.com")
        runTest("今北産業wss://test.com", "wss://test.com")
        runTest("今北産業blossom:test.com", "blossom:test.com")
        runTest("今北産業nostr:test.com", "nostr:test.com")
        runTest("今北産業nostr:test", "nostr:test")
        runTest("今北産業nostr:nprofile1qqsv0agl52pt4e5pe586fz9vsd5phqz7je49yrcg532h2u5nejsc8gcpzamhxue69uhhxetpwf3kstnwdaejuar0v3shjtcv8453m今北産業", "nostr:nprofile1qqsv0agl52pt4e5pe586fz9vsd5phqz7je49yrcg532h2u5nejsc8gcpzamhxue69uhhxetpwf3kstnwdaejuar0v3shjtcv8453m")

        runTest("今北産業http://test.com今北産業", "http://test.com")
        runTest("今北産業ftp://test.com今北産業", "ftp://test.com")
        runTest("今北産業test.com今北産業", "test.com")
        runTest("今北産業wss://test.com今北産業", "wss://test.com")
        runTest("今北産業blossom:test.com今北産業", "blossom:test.com")
        runTest("今北産業nostr:test.com今北産業", "nostr:test.com")
        runTest("今北産業nostr:test今北産業", "nostr:test")
        runTest("今北産業nostr:nprofile1qqsv0agl52pt4e5pe586fz9vsd5phqz7je49yrcg532h2u5nejsc8gcpzamhxue69uhhxetpwf3kstnwdaejuar0v3shjtcv8453m今北産業", "nostr:nprofile1qqsv0agl52pt4e5pe586fz9vsd5phqz7je49yrcg532h2u5nejsc8gcpzamhxue69uhhxetpwf3kstnwdaejuar0v3shjtcv8453m")
    }

    @Test
    fun testFullText() {
        val text =
            """
            Did you know you can embed #Nostr live streams into #Nostr long-form posts? Sounds like an obvious thing, but it's only supported by nostr:npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf at the moment.

            See how it can be done here: https://lnshort.it/live-stream-embeds/

            https://nostr.build/i/fd53fcf5ad950fbe45127e4bcee1b59e8301d41de6beee211f45e344db214e8a.jpg
            """.trimIndent()

        runTest(text, "nostr:npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf", "https://lnshort.it/live-stream-embeds/", "https://nostr.build/i/fd53fcf5ad950fbe45127e4bcee1b59e8301d41de6beee211f45e344db214e8a.jpg")
    }

    @Test
    fun testBasicIPv6() {
        runTest("I saw this on http://[2001:db8:1f70:0:999:de8:7648:6e8] I think it is really cool", "http://[2001:db8:1f70:0:999:de8:7648:6e8]")
        runTest("I saw this on http://[2001:db8::1]:80 I think it is really cool", "http://[2001:db8::1]:80")
        runTest("I saw this on http://[2a01:5cc0:1:2::4] I think it is really cool", "http://[2a01:5cc0:1:2::4]")
        runTest("I saw this on http://[::1]:3000 I think it is really cool", "http://[::1]:3000")
    }

    @Test
    fun testNoSchemaUrlMultibyteAscii() {
        runTest("ほtest.com", "test.com")
        runTest("test.comほ", "test.com")
        runTest("ほtest.comほ", "test.com")
    }

    @Test
    fun testBeginsAndEndsWithPunctuation() {
        UrlDetector.CANNOT_END_URLS_WITH.forEach { punctuation ->
            runTest("${punctuation}http://test.com?s=dd", "http://test.com?s=dd")
            runTest("http://test.com?s=dd$punctuation", "http://test.com?s=dd")
            runTest("${punctuation}http://test.com?s=dd$punctuation", "http://test.com?s=dd")

            runTest("${punctuation}http://test.com/", "http://test.com/")
            runTest("http://test.com/.", "http://test.com/")
            runTest("${punctuation}http://test.com/$punctuation", "http://test.com/")

            runTest("${punctuation}http://test.com", "http://test.com")
            runTest("http://test.com$punctuation", "http://test.com")
            runTest("${punctuation}http://test.com$punctuation", "http://test.com")

            runTest("${punctuation}test.com", "test.com")
            runTest("test.com$punctuation", "test.com")
            runTest("${punctuation}test.com$punctuation", "test.com")
        }

        UrlDetector.CANNOT_BEGIN_URLS_WITH.forEach { punctuation ->
            runTest("${punctuation}http://test.com?s=dd", "http://test.com?s=dd")
            runTest("http://test.com?s=dd$punctuation", "http://test.com?s=dd")
            runTest("${punctuation}http://test.com?s=dd$punctuation", "http://test.com?s=dd")

            runTest("${punctuation}http://test.com/", "http://test.com/")
            runTest("http://test.com/.", "http://test.com/")
            runTest("${punctuation}http://test.com/$punctuation", "http://test.com/")

            runTest("${punctuation}http://test.com", "http://test.com")
            runTest("http://test.com$punctuation", "http://test.com")
            runTest("${punctuation}http://test.com$punctuation", "http://test.com")

            runTest("${punctuation}test.com", "test.com")
            runTest("test.com$punctuation", "test.com")
            runTest("${punctuation}test.com$punctuation", "test.com")
        }
    }

    private fun runTest(
        text: String,
        vararg expected: String?,
    ) = assertEquals(
        expected.toList(),
        UrlDetector(text).detect().map { it.originalUrl },
    )
}
