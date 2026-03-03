# Add project specific ProGuard rules here.

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class com.ebooks.reader.data.db.** { *; }
-keep enum com.ebooks.reader.data.db.entities.** { *; }

# ── ViewModels ────────────────────────────────────────────────────────────────
-keep class com.ebooks.reader.viewmodel.** { *; }

# ── UI data / state classes ──────────────────────────────────────────────────
-keep class com.ebooks.reader.data.parser.** { *; }

# ── WebView JavaScript interface ─────────────────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── XML Pull Parser (used by EpubParser) ─────────────────────────────────────
-keep class org.xmlpull.v1.** { *; }

# ── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }
-keepclassmembers class * extends coil.fetch.Fetcher { *; }
-keepclassmembers class * extends coil.decode.Decoder { *; }

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Jetpack Compose ───────────────────────────────────────────────────────────
-dontwarn androidx.compose.**
-keepclassmembers class * {
    *** *(...);
}
