plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace  = "com.aegispay.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aegispay.android"
        minSdk        = 26      // Android 8.0 — required for EncryptedSharedPreferences
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        // Injected by CI via -P flags or local.properties
        buildConfigField("String", "API_BASE_URL",
            "\"${project.findProperty("API_BASE_URL") ?: "http://10.0.2.2:8080"}\"")
        buildConfigField("String", "WS_BASE_URL",
            "\"${project.findProperty("WS_BASE_URL") ?: "ws://10.0.2.2:8090"}\"")
        buildConfigField("String", "WEB_BASE_URL",
            "\"${project.findProperty("WEB_BASE_URL") ?: "http://10.0.2.2:3000"}\"")
        buildConfigField("String", "KEYCLOAK_ISSUER",
            "\"${project.findProperty("KEYCLOAK_ISSUER") ?: "http://10.0.2.2:8180/realms/aegispay"}\"")
        buildConfigField("String", "OAUTH_CLIENT_ID",
            "\"${project.findProperty("OAUTH_CLIENT_ID") ?: "aegispay-android"}\"")
        // AppAuth redirect scheme — must match intent-filter in AndroidManifest
        buildConfigField("String", "OAUTH_REDIRECT_URI",
            "\"com.aegispay.android://oauth/callback\"")
        // Stripe publishable key — pk_test_* for development, pk_live_* injected by CI
        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY",
            "\"${project.findProperty("STRIPE_PUBLISHABLE_KEY") ?: "pk_test_placeholder_local_dev"}\"")

        manifestPlaceholders["appAuthRedirectScheme"] = "com.aegispay.android"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release signing configured in CI via keystore env vars
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose      = true
        buildConfig  = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Compose BOM — pins all compose versions
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material)              // for pullRefresh (M2 interop)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + OkHttp + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)

    // AppCompat (required for FragmentActivity / BiometricPrompt host)
    implementation(libs.appcompat)

    // Biometric
    implementation(libs.biometric)

    // Splash Screen
    implementation(libs.core.splashscreen)

    // Auth
    implementation(libs.appauth)
    implementation(libs.security.crypto)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Paging 3
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Image loading
    implementation(libs.coil.compose)

    // Firebase / FCM + Phone Auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)

    // WorkManager + Hilt integration for offline queue
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Stripe Android SDK — PaymentSheet for wallet top-up
    implementation(libs.stripe.android)

    // Coroutines
    implementation(libs.coroutines.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
}
