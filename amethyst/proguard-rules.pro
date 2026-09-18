# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# preserve the line number information for debugging stack traces.
-dontobfuscate
-keepattributes LocalVariableTable
-keepattributes LocalVariableTypeTable
-keepattributes *Annotation*
-keepattributes SourceFile
-keepattributes LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes MethodParameters
-keepparameternames

-keepdirectories libs

# Keep all names
-keepnames class ** { *; }

# Keep All enums
-keep enum ** { *; }

# preserve access to native classses
-keep class fr.acinq.secp256k1.** { *; }

# JNA For Libsodium
-keep class com.goterl.lazysodium.** { *; }

# libscrypt
-keep class com.lambdaworks.codec.** { *; }
-keep class com.lambdaworks.crypto.** { *; }
-keep class com.lambdaworks.jni.** { *; }

-keep class info.guardianproject.** { *; }

# JNA also requires AWT, which Android does not have. So the classes are broken down to filter AWT out
-keep class com.sun.jna.ToNativeConverter { *; }
-keep class com.sun.jna.NativeMapped { *; }
-keep class com.sun.jna.CallbackReference { *; }
-keep class com.sun.jna.ptr.IntByReference { *; }
-keep class com.sun.jna.NativeLong { *; }
-keep class com.sun.jna.Structure { *; }
-keep class com.sun.jna.Structure$* { *; }
-keep class com.sun.jna.Native$ffi_callback { *; }
-keep class * implements com.sun.jna.Structure$* { *; }
-keep class * implements com.sun.jna.Native$* { *; }
-keep class com.sun.jna.Native {
    private static com.sun.jna.NativeMapped fromNative(java.lang.Class, java.lang.Object);
    private static com.sun.jna.NativeMapped fromNative(java.lang.reflect.Method, java.lang.Object);
    private static java.lang.Class nativeType(java.lang.Class);
    private static java.lang.Object toNative(com.sun.jna.ToNativeConverter, java.lang.Object);
    private static java.lang.Object fromNative(com.sun.jna.FromNativeConverter, java.lang.Object, java.lang.reflect.Method);
}

# JSON parsing
-keep class com.vitorpamplona.quartz.** { *; }
-keep class com.vitorpamplona.amethyst.** { *; }

# Room generates *_Impl subclasses instantiated reflectively via no-arg constructor.
# -keepnames preserves the name but R8 still strips the unused <init>().
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# zxing-cpp's JNI boundary. The native library exports name-mangled symbols
# (Java_zxingcpp_BarcodeReader_readYBuffer), so R8 renaming the class or its
# external methods breaks the lookup at runtime with no build error and no
# warning -- the QR scanner simply fails to start in release builds.
#
# This arrived automatically as the io.github.zxing-cpp:android AAR's consumer
# rule. We build that library ourselves now (tools/zxing-cpp-build) and vendor
# its Kotlin half, so the rule is ours to carry.
-keep class zxingcpp.** { *; }
