import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.io.File
import java.util.Base64

plugins {
    // com.android.application must be applied before update-versions, since
    // that plugin hooks into the "build" task AGP provides.
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("se.bjurr.gradle.update-versions") version "3.0.1"
    // 3.x, not the latest 4.x, which requires AGP 9 / Gradle 9.1+ — see
    // https://github.com/Triple-T/gradle-play-publisher/releases/tag/4.0.0
    id("com.github.triplet.play") version "3.13.0"
}

fun runGit(vararg args: String): String {
    val builder = ProcessBuilder("git", *args)
    builder.directory(rootDir)
    builder.redirectErrorStream(true)
    val process = builder.start()
    val reader = process.inputStream.bufferedReader()
    val output = reader.readText()
    process.waitFor()
    return output.trim()
}

/** Monotonically increasing, so it never needs manual bumping. */
fun gitCommitCount(): Int {
    val count = runGit("rev-list", "--count", "HEAD")
    return count.toIntOrNull() ?: 1
}

android {
    namespace = "com.github.tomasbjerre.keepsheet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.tomasbjerre.keepsheet"
        minSdk = 26
        targetSdk = 36
        versionCode = gitCommitCount()
        // `version` in gradle.properties for local builds; CI overrides it with
        // -Pversion=<latest tag> — see .github/workflows/release_android.yml.
        versionName = project.version.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Every instrumented test starts from a wiped app (empty database), instead of
        // inheriting whatever the tests before it in the same `connectedDebugAndroidTest`
        // run left behind — see testOptions.execution below.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    signingConfigs {
        // Only present in CI (or a local build with the same env vars set) — see
        // android/README.md#release-signing. A local `assembleRelease` without them
        // just produces an unsigned build rather than failing.
        val keystoreBase64 = System.getenv("ANDROID_KEYSTORE_BASE64")
        if (keystoreBase64 != null) {
            create("release") {
                val decodedKeystore = File.createTempFile("keepsheet-upload-keystore", ".jks")
                decodedKeystore.deleteOnExit()
                decodedKeystore.writeBytes(Base64.getDecoder().decode(keystoreBase64))

                storeFile = decodedKeystore
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // Runs each instrumented test in its own instrumentation, which is what makes
        // clearPackageData (see defaultConfig above) apply between tests.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    kspTest("androidx.room:room-compiler:2.8.5")

    // Camera capture for specs/capture-and-processing.md#multi-page-capture.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Page thumbnails (Page Review, Document Detail) from local content:// / file
    // Uris — no network use, so no INTERNET permission implication.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Still needed: an edge-detection/perspective-correction library (e.g. OpenCV) for
    // specs/capture-and-processing.md#automatic-cropping-and-straightening, an
    // on-device OCR engine (e.g. Tesseract) for
    // specs/capture-and-processing.md#text-recognition-ocr, and a PDF library capable
    // of merging existing documents (not just rendering pages) for specs/merging.md,
    // once those features are implemented.

    // Pure logic: plain JUnit Jupiter, no mocks.
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.assertj:assertj-core:3.27.7")

    // Repository/Room tests run against a real in-memory database via Robolectric
    // (which provides a real SQLite, not a mock DAO) — see specs/data-model.md.
    // Robolectric only has stable JUnit4 support, bridged onto the JUnit
    // Platform test run by the vintage engine below.
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.1.3")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")

    // Instrumented: drives the real app on a device/emulator to capture screenshots
    // for the Play Store listing and README — see android/README.md#screenshots.
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // GrantPermissionRule, for tests exercising Capture's camera permission flow
    // without a system permission dialog blocking a headless emulator run.
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestUtil("androidx.test:orchestrator:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    reports {
        xml.required.set(true)
    }
}

ktlint {
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.PLAIN)
    }
}

// Publishes to Play via the Play Developer API — see android/README.md#play-store-release.
// Auth comes from the ANDROID_PUBLISHER_CREDENTIALS env var (a service account JSON), not
// committed here — see https://github.com/Triple-T/gradle-play-publisher#authenticating.
play {
    track.set("internal")
    defaultToAppBundles.set(true)
    // AUTO would need live Play credentials just to run `bundleRelease` (it resolves the
    // version code against the API as part of the build task itself). versionCode is
    // already monotonic from git history, so a real conflict shouldn't happen in practice.
    resolutionStrategy.set(ResolutionStrategy.IGNORE)
}
