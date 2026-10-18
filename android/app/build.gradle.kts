plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Release signing comes only from the environment (CI decodes the upload keystore from repository secrets;
// docs/12-google-play.md). With any of the four unset, the release build is left unsigned and debug builds
// are unaffected.
val releaseKeystore = listOf("ANDROID_KEYSTORE_FILE", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .map { System.getenv(it)?.takeIf(String::isNotBlank) }
    .takeIf { values -> values.all { it != null } }
    ?.map { it!! }

// CI passes -PversionCode=<number> (or ANDROID_VERSION_CODE) so every upload to Google Play is higher than the last.
val appVersionCode = (findProperty("versionCode") as String? ?: System.getenv("ANDROID_VERSION_CODE"))
    ?.takeIf(String::isNotBlank)?.toInt() ?: 1

android {
    namespace = "sa.zood.nearmosque"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.AVA.nearmosque"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                val (path, storePass, alias, keyPass) = releaseKeystore
                storeFile = file(path)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Only the seven reviewed interface languages are packaged; see res/xml/locales_config.xml.
        localeFilters += listOf("en", "ar", "ur", "tr", "in", "fr", "es", "ru", "bn", "fa", "fa-rAF", "b+zh+Hans", "hi", "pt", "ha", "sw", "b+fil", "vi", "th", "km", "ug", "b+ckb", "bs", "sr", "mk", "hu", "nl", "ka", "si", "te", "kn", "ml", "mr", "gu", "pa", "as")
    }

    bundle {
        // The in-app language picker offers every language; with language splits Play would install only the phone's.
        language { enableSplit = false }
    }

    sourceSets {
        // Data packs are shared with iOS and live outside the Gradle project.
        getByName("main").assets.srcDirs("../../packs", "../../shared/content", "../../shared/semantic")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("nm.root", rootProject.projectDir.parentFile.absolutePath)
                // The Google Play screenshots (StoreScreenshotTest, 8 screens in every app language) run only when asked:
                // -PstoreShots=true, optionally -PstoreLangs="en ar", -PstoreDevices="phone seven ten desktop xr cover inner-land tablet" and -PstoreClock=12|24 (.github/workflows/store-play-assets.yml).
                if (findProperty("storeShots") == "true") {
                    it.systemProperty("nm.storeLangs", findProperty("storeLangs") as String? ?: "")
                    it.systemProperty("nm.storeClock", findProperty("storeClock") as String? ?: "")
                    it.systemProperty("nm.storeDevices", findProperty("storeDevices") as String? ?: "")
                    it.maxHeapSize = "4g"
                    // The PNGs are not declared outputs: always run, never from the build cache.
                    it.outputs.upToDateWhen { false }
                    it.outputs.cacheIf { false }
                } else {
                    it.exclude("**/StoreScreenshotTest*")
                }
            }
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

composeCompiler {
    // The Compose group mapping merged into the R8 mapping needs org.jetbrains.kotlin:compose-group-mapping, which
    // could not be resolved for Kotlin 2.4.0 and made :app:bundleRelease fail. mapping.txt still deobfuscates
    // crash stacks; only Compose group keys stay unmapped.
    includeComposeMappingFile.set(false)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
