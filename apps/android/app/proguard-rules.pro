# AegisPay ProGuard/R8 rules
# https://www.guardsquare.com/manual/configuration/examples

# ── Retrofit ──────────────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# ── Moshi ─────────────────────────────────────────────────────────────────────
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── AppAuth ───────────────────────────────────────────────────────────────────
-keep class net.openid.appauth.** { *; }

# ── Hilt ─────────────────────────────────────────────────────────────────────
-dontwarn dagger.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ── Kotlin coroutines ──────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Application domain models ─────────────────────────────────────────────────
-keep class com.aegispay.android.network.** { *; }
-keep class com.aegispay.android.auth.** { *; }

# ── UI state data classes (used by ViewModel StateFlow) ───────────────────────
# Kotlin data classes used as StateFlow values must keep component functions
# and copy() so reflection-based serialisation and copy patterns still work.
-keep class com.aegispay.android.ui.**.** {
    public <fields>;
    public <methods>;
}

# ── WorkManager (PaymentSyncWorker + HiltWorkerFactory) ───────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# Hilt WorkerFactory — must survive obfuscation
-keep class dagger.hilt.android.internal.managers.** { *; }
-keepclassmembers class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory {
    <init>(...);
}

# ── Room (OfflinePaymentEntity + TypeConverters) ─────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.TypeConverters class * { *; }

# ── Biometric (keep BiometricPrompt callbacks intact) ─────────────────────────
-keep class androidx.biometric.** { *; }

# ── Stripe SDK ────────────────────────────────────────────────────────────────
-keep class com.stripe.android.** { *; }
-dontwarn com.stripe.android.**

# ── Remove logging in release ─────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
