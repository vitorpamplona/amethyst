# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepdirectories libs

# Keep all names
-keepnames class ** { *; }

# Keep All enums
-keep enum ** { *; }

# preserve access to native classses
-keep class fr.acinq.secp256k1.** { *; }

# libscrypt
-keep class com.lambdaworks.codec.** { *; }
-keep class com.lambdaworks.crypto.** { *; }
-keep class com.lambdaworks.jni.** { *; }

# JSON parsing
-keep class com.vitorpamplona.quartz.** { *; }