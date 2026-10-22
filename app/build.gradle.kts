plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledOcrLanguages = listOf(
    "eng", "hin", "ben", "ara", "urd", "fra",
    "deu", "spa", "por", "rus", "chi_sim", "jpn"
)
val generatedOcrAssets = layout.buildDirectory.dir("generated/ocrAssets")

android {
    namespace = "com.amresalehin.emregallery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aistudio.emregallery.xytkpq"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main").assets.srcDir(generatedOcrAssets)

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

val prepareBundledOcrData by tasks.registering {
    val outputDir = generatedOcrAssets.map { it.dir("tessdata") }
    outputs.dir(outputDir)
    doLast {
        val tessdataDir = outputDir.get().asFile
        if (!tessdataDir.exists() && !tessdataDir.mkdirs()) {
            error("Could not create generated OCR assets directory: $tessdataDir")
        }
        bundledOcrLanguages.forEach { code ->
            val target = tessdataDir.resolve("$code.traineddata")
            if (!target.exists() || target.length() < 1024) {
                val temporary = tessdataDir.resolve("$code.traineddata.download")
                try {
                    val connection = java.net.URL(
                        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$code.traineddata"
                    ).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 20000
                    connection.readTimeout = 120000
                    connection.instanceFollowRedirects = true
                    try {
                        connection.connect()
                        if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                            error("Could not fetch OCR data for $code (HTTP ${connection.responseCode}).")
                        }
                        connection.inputStream.use { input ->
                            temporary.outputStream().use { output -> input.copyTo(output) }
                        }
                    } finally {
                        connection.disconnect()
                    }
                    if (temporary.length() < 1024) error("OCR data for $code downloaded incompletely.")
                    if (!temporary.renameTo(target)) {
                        target.delete()
                        if (!temporary.renameTo(target)) error("Could not install OCR data for $code.")
                    }
                } finally {
                    temporary.delete()
                }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareBundledOcrData)
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
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Native Tesseract engine and its JNI libraries are packaged in the APK.
    implementation("com.rmtheis:tess-two:9.1.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
