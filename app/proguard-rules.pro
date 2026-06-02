# ─────────────────────────────────────────────────────────────
# SmartStudy — ProGuard Rules (Production)
# ─────────────────────────────────────────────────────────────

# ── Kotlin ──
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-dontwarn kotlin.**

# ── Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Data models (Gson) ──
-keep class com.hanif.smartstudy.data.model.** { *; }
-keepclassmembers class com.hanif.smartstudy.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# ── BuildConfig ──
-keep class com.hanif.smartstudy.BuildConfig { *; }

# ── Firebase ──
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.hanif.smartstudy.service.SmartStudyFirebaseService { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── WorkManager ──
-keep class androidx.work.** { *; }
-keep class com.hanif.smartstudy.worker.** { *; }
-keepclassmembers class com.hanif.smartstudy.worker.** { *; }

# ── OkHttp + Okio ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Gson ──
-keep class com.google.gson.** { *; }
-dontwarn sun.misc.Unsafe

# ── Jetpack Compose ──
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Receivers & Services ──
-keep class com.hanif.smartstudy.receiver.** { *; }
-keep class com.hanif.smartstudy.service.**  { *; }

# ── WebView (Privacy Policy) ──
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, android.webkit.WebResourceRequest);
}

# ── DataStore ──
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── Strip debug logs in release ──
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ── Keep source line numbers for crash reports ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
