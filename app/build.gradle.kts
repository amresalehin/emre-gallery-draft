plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

// Give every GitHub Actions debug build a unique install identity. Local debug
// builds keep the base application ID; release builds are unchanged.
val isGitHubActions = providers.environmentVariable("GITHUB_ACTIONS").orNull == "true"
val ciRunId = providers.environmentVariable("GITHUB_RUN_ID").orNull
val ciRunAttempt = providers.environmentVariable("GITHUB_RUN_ATTEMPT").orNull
val ciRunIdentity = if (isGitHubActions) {
    val runId = requireNotNull(ciRunId) {
        "GITHUB_RUN_ID must be set for GitHub Actions debug builds"
    }
    val runAttempt = requireNotNull(ciRunAttempt) {
        "GITHUB_RUN_ATTEMPT must be set for GitHub Actions debug builds"
    }
    require(runId.isNotEmpty() && runId.all { it in '0'..'9' }) {
        "GITHUB_RUN_ID must contain only digits"
    }
    require(runAttempt.isNotEmpty() && runAttempt.all { it in '0'..'9' }) {
        "GITHUB_RUN_ATTEMPT must contain only digits"
    }
    runId to runAttempt
} else {
    null
}
val debugApplicationIdSuffix = ciRunIdentity?.let { (runId, runAttempt) ->
    ".ci${runId}a${runAttempt}"
}.orEmpty()
val debugApplicationLabel = ciRunIdentity?.let { (runId, runAttempt) ->
    "Emre Gallery CI ${runId}/${runAttempt}"
} ?: "@string/app_name"

android {
    namespace = "com.amresalehin.emregallery"
    compileSdk = 35

    defaultConfig {
        // Keep source namespace stable. CI identity is applied to debug only.
        applicationId = "com.amresalehin.emregallery"
        manifestPlaceholders["applicationLabel"] = "@string/app_name"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = debugApplicationIdSuffix
            manifestPlaceholders["applicationLabel"] = debugApplicationLabel
        }

        // Debug uses Android Gradle Plugin's standard debug signing key.
        // Release keeps its normal application ID and existing build settings.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.version"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Native FFmpeg/FFprobe runtime used to mux separate yt-dlp video and audio tracks
    // and to verify the resulting container before a download is marked complete.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // On-device InsightFace buffalo_l SCRFD detector and ArcFace inference.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // Native Tesseract engine and its JNI libraries are packaged in the APK.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // On-device Google ML Kit Text Recognition with multiple language script support
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

chaquopy {
    defaultConfig {
        version = "3.10"
        pip {
            install("gallery-dl")
            install("yt-dlp")
        }
    }
}
