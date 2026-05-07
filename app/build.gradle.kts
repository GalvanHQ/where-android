plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ovi.where"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ovi.where"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Chat server URLs – defaults (overridden per build type below)
        buildConfigField("String", "CHAT_SERVER_HTTP_URL", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "CHAT_SERVER_WS_URL",   "\"http://10.0.2.2:8080\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "CHANGE_ME"
            keyAlias = "where-app"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "CHANGE_ME"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            // Cloud Run server URL
            buildConfigField("String", "CHAT_SERVER_HTTP_URL", "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
            buildConfigField("String", "CHAT_SERVER_WS_URL",   "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
        }
        release {
            isMinifyEnabled = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            // Cloud Run server URL
            buildConfigField("String", "CHAT_SERVER_HTTP_URL", "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
            buildConfigField("String", "CHAT_SERVER_WS_URL", "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)


    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    // TODO: Re-enable after fixing build ID issue
    // implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Play Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)

    // Google Maps
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.androidx.ui.text.google.fonts)

    // Timber
    implementation(libs.timber)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // ── HTTP + WebSocket Clients ─────────────────────────────────────
    implementation(libs.socketio.client)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    
    // OkHttp logging
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
