# kotlinx.serialization keeps its generated serializers via reflection on the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.wander.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.wander.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wander.android.**$$serializer { *; }

# Media3 resolves renderers and data sources reflectively.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room generates implementations that are looked up by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated components.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keepclasseswithmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }

# Ktor and OkHttp reference optional platform classes that are absent on Android.
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlinx.coroutines.debug.**

# Release builds keep only warnings and errors in logcat. Verbose, debug and info lines trace
# what the user plays and where their devices sit; bug-report tooling and other apps with log
# access can read logcat, so those calls are stripped rather than trusted to stay harmless.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
