-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,JavascriptInterface,InnerClasses,Signature,EnclosingMethod

# Shrink unused libraries, but keep all DoVimu classes so release does not crash.
-keep class com.dovimu.** { *; }
-keepclassmembers class com.dovimu.** { *; }

-keep class com.dovimu.platform.DovimuJsBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class dovimu.shared.generated.** { *; }
-keep class org.jetbrains.compose.resources.** { *; }

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn kotlinx.serialization.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn coil3.**
-dontwarn org.jetbrains.compose.**
