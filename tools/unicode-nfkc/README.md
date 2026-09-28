# unicode-nfkc

Generates `NfkcData.kt`, the tables behind Quartz's pure-Kotlin NFKC normalizer
(`quartz/.../utils/unicode/NfkcNormalizer.kt`). Linux native uses it because it
has no platform normalizer. JVM/Android use `java.text.Normalizer` and Apple
uses `NSString`. NIP-49 NFKC-normalizes passwords before scrypt, so a wrong
normalizer silently breaks key decryption.

```bash
V=17.0.0
mkdir -p /tmp/ucd && for f in UnicodeData.txt DerivedNormalizationProps.txt; do
  curl -sSfo /tmp/ucd/$f https://www.unicode.org/Public/$V/ucd/$f
done
tools/unicode-nfkc/generate.py /tmp/ucd \
  quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/unicode/NfkcData.kt
```

Commit the regenerated file. `NfkcNormalizerJdkParityTest` (jvmTest) checks the
result against the JDK for every code point the JDK knows. To check a new
Unicode version fully, run the normalizer over that version's
`NormalizationTest.txt` (column 4 is the NFKC of columns 1-5).

The data is derived from the Unicode Character Database, distributed under the
permissive [Unicode License v3](https://www.unicode.org/license.txt).
