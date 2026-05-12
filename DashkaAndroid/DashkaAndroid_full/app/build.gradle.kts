plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.solar.dashka"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.solar.dashka"
        minSdk = 26                  // Decision 1: stable modern MVP — not chasing legacy devices
        targetSdk = 36
        versionCode = 10
        versionName = "0.3.2-share-ux"

        // Backend host — points to the post-REC-001…005 deployment.
        // Change to "http://10.0.2.2:3000/" for local Next.js (emulator loopback).
        buildConfigField(
            "String",
            "DASHKA_BASE_URL",
            "\"https://dashka-chatpl.vercel.app/\""
        )

        // REC-001 compatibility — build-time token from gradle.properties.
        // See gradle.properties for the security note.
        buildConfigField(
            "String",
            "DASHKA_API_TOKEN",
            "\"${(project.findProperty("DASHKA_API_TOKEN") as String?).orEmpty()}\""
        )

        // Decision 2: Sprint 1 is PL only. PARTNER_LANG mirrors web /config.ts.
        // Multi-partner-language support deferred to a later sprint.
        buildConfigField("String", "PARTNER_LANG", "\"PL\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false   // Sprint 1: skip R8/ProGuard. Re-enable in Store-ready phase.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin compiles to JVM bytecode 17 (matches Java sourceCompat above).
// Modern Kotlin 2.1+ syntax via the kotlin {} extension at the project level.
// We deliberately do NOT call jvmToolchain(N) — Gradle 9.0 stopped auto-provisioning
// toolchains, and we want the build to use whichever JDK is launching it (Studio's
// bundled JBR 21 by default, which is forward-compatible with target 17).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network — Retrofit 3 + OkHttp + kotlinx.serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore Preferences — Sprint 4: persists voice + autoplay across app restarts.
    // Pinned version: stable, compatible with kotlin 2.1, well-supported.
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Java 8+ APIs on minSdk 26 — needed for things like Instant/Duration if introduced.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlinx.coroutines.test)
}
