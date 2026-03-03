# Add project specific ProGuard rules here.

# Keep Room entities and DAOs
-keep class com.ebooks.reader.data.db.** { *; }

# Keep ViewModel classes
-keep class com.ebooks.reader.viewmodel.** { *; }

# Keep JavaScript interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Coil
-dontwarn coil.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
