import java.net.URL
import java.net.HttpURLConnection
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
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

    buildTypes {
        // Use Android Gradle Plugin's generated debug keystore in CI and local builds.
        debug {
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

val prepareBundledOcrData by tasks.registering {
    val outputDir = generatedOcrAssets.map { it.dir("tessdata") }
    outputs.dir(outputDir)
    // The output directory may exist even when one or more trained-data files
    // were deleted or left incomplete. Re-run the task unless every required
    // language asset is present and non-trivial.
    outputs.upToDateWhen {
        bundledOcrLanguages.all { code ->
            outputDir.get().asFile.resolve("$code.traineddata").let { file ->
                file.isFile && file.length() >= 1024
            }
        }
    }
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
                    val connection = URL(
                        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$code.traineddata"
                    ).openConnection() as HttpURLConnection
                    connection.connectTimeout = 20000
                    connection.readTimeout = 120000
                    connection.instanceFollowRedirects = true
                    try {
                        connection.connect()
                        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
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

val bundledFaceModelNames = listOf(
    "det_10g.onnx", "w600k_r50.onnx", "2d106det.onnx", "1k3d68.onnx", "genderage.onnx"
)
val generatedFaceModelDir = generatedOcrAssets.map { it.dir("face-models/buffalo_l") }
val prepareBundledFaceModel by tasks.registering {
    outputs.dir(generatedFaceModelDir)
    outputs.upToDateWhen {
        bundledFaceModelNames.all { name ->
            generatedFaceModelDir.get().asFile.resolve(name).let { it.isFile && it.length() >= 1024 * 1024 }
        }
    }
    doLast {
        val modelDir = generatedFaceModelDir.get().asFile
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            error("Could not create generated buffalo_l assets directory: $modelDir")
        }
        val missing = bundledFaceModelNames.filter { name ->
            modelDir.resolve(name).let { !it.isFile || it.length() < 1024 * 1024 }
        }
        if (missing.isNotEmpty()) {
            val archive = layout.buildDirectory.file("tmp/buffalo_l.zip").get().asFile
            archive.parentFile.mkdirs()
            val temporary = layout.buildDirectory.file("tmp/buffalo_l.zip.download").get().asFile
            try {
                val connection = URL(
                    "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip"
                ).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 180000
                connection.instanceFollowRedirects = true
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        error("Could not download buffalo_l model pack (HTTP ${connection.responseCode}).")
                    }
                    connection.inputStream.use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }

                if (temporary.length() < 100 * 1024 * 1024) {
                    error("buffalo_l archive download incomplete (${temporary.length()} bytes).")
                }
                if (archive.exists()) archive.delete()
                if (!temporary.renameTo(archive)) error("Could not move downloaded buffalo_l archive to $archive.")

                ZipFile(archive).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name.substringAfterLast('/')
                        if (name in bundledFaceModelNames || name.contains("LICENSE", ignoreCase = true)) {
                            zip.getInputStream(entry).use { input ->
                                modelDir.resolve(name).outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
            } finally {
                temporary.delete()
                archive.delete()
            }
        }
        val invalid = bundledFaceModelNames.filter { name ->
            modelDir.resolve(name).let { !it.isFile || it.length() < 1024 * 1024 }
        }
        if (invalid.isNotEmpty()) {
            error("Incomplete buffalo_l model pack; missing or invalid: ${invalid.joinToString()}")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareBundledOcrData, prepareBundledFaceModel)
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
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // On-device InsightFace buffalo_l SCRFD detector and ArcFace inference.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // Native Tesseract engine and its JNI libraries are packaged in the APK.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
