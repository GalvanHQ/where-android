# Add project specific ProGuard rules here.

# Keep domain model classes (Firebase Firestore deserialization)
-keep class com.ovi.where.domain.model.** { *; }

# Keep Room entities
-keep class com.ovi.where.data.local.entity.** { *; }

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
}

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# MapLibre
-keep class org.maplibre.** { *; }

# Kotlin serialization
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
