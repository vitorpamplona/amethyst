# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# For the Secp256k1 library
-keepattributes InnerClasses

-keep class fr.acinq.secp256k1.jni.** { *; }
# For the NostrPostr library
-keep class nostr.postr.** { *; }
-keep class com.vitorpamplona.amethyst.service.model.** { *; }
# Json parsing
-keep class com.google.gson.reflect.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

-keep class com.vitorpamplona.amethyst.lnurl.** { *; }
-keep class com.vitorpamplona.amethyst.model.** { *; }
-keep class com.vitorpamplona.amethyst.service.** { *; }
-keep class com.vitorpamplona.amethyst.ui.** { *; }
