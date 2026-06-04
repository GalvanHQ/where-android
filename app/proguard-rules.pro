# Add project specific ProGuard rules here.

# ── Preserve line numbers for crash reporting ─────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firebase ──────────────────────────────────────────────────────────────────
# Keep all Firebase classes (Auth, Firestore, Messaging, Storage, Functions, etc.)
-keep class com.google.firebase.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
}
-dontwarn com.google.firebase.**

# ── Room (@Entity and @Dao annotated classes) ─────────────────────────────────
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
# Keep Room entity and DAO classes in the project
-keep class com.ovi.where.data.local.entity.** { *; }
-keep class com.ovi.where.data.local.dao.** { *; }

# ── Hilt / Dagger (@HiltViewModel and @Module annotated classes) ──────────────
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.Module class * { *; }
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-dontwarn dagger.hilt.**

# ── Kotlin Serialization (@Serializable annotated classes) ────────────────────
-keepattributes *Annotation*
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
# Keep generated serializers
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Domain model classes (Firebase Firestore deserialization) ──────────────────
-keep class com.ovi.where.domain.model.** { *; }

# ── OkHttp / Retrofit ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ── Coroutines ────────────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# ── Socket.IO ────────────────────────────────────────────────────────────────
-keep class io.socket.** { *; }
-keep interface io.socket.** { *; }
-dontwarn io.socket.**
-dontwarn org.json.**
