import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Near Mosque for Windows: Compose for Desktop on the same Kotlin core as the Android app (prayer times, Qibla,
// reminders planner, library search, books), compiled straight from android/core so both always agree.
plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.compose") version "1.12.1"
}

group = "sa.zood.nearmosque"
version = "0.5.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

sourceSets {
    main {
        kotlin.srcDir("../android/core/src/main/kotlin")
        // The city table, the classical books catalog and the app's strings, read from the shared folders.
        resources.srcDir("build/shared-resources")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.batoulapps.adhan:adhan2:0.0.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // MP3 playback for the Adhan and reminders (LGPL, unmodified).
    implementation("javazoom:jlayer:1.0.1")
    testImplementation(kotlin("test"))
}

val sharedResources by tasks.registering(Copy::class) {
    from("../packs/cities/cities.tsv")
    from("../shared/content/books-catalog.json")
    from("../shared/i18n/strings.json")
    from("../shared/i18n/languages.json")
    from("src/main/sounds") { into("sounds") }
    // Ask: the Qur'an, the HadeethEnc hadith in Arabic and English, and the common questions (searched on the PC).
    from("../packs/sources/quran-tanzil-pickthall/chunks.jsonl") { into("books"); rename { "quran.jsonl" } }
    from("../packs/sources/quran-tanzil-pickthall/common-questions.json") { into("books") }
    from("../packs/sources/hadeethenc-en/chunks.jsonl") { into("books"); rename { "hadith-en.jsonl" } }
    from("../packs/sources/hadeethenc-ar/chunks.jsonl") { into("books"); rename { "hadith-ar.jsonl" } }
    from("../shared/content/stopwords.json") { into("books") }
    // Mosques that come with the app.
    listOf("eg-cairo", "gb-london", "za-cape-town").forEach { pack ->
        from("../packs/mosques/$pack/mosques.jsonl") { into("mosques"); rename { "$pack.jsonl" } }
    }
    into(layout.buildDirectory.dir("shared-resources"))
}
tasks.named("processResources") { dependsOn(sharedResources) }

compose.desktop {
    application {
        mainClass = "sa.zood.nearmosque.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.AppImage)
            packageName = "Near Mosque"
            packageVersion = "0.5.0"
            description = "Prayer times, Adhan, Qibla, nearby mosques, Ask and classical books"
            vendor = "Near Mosque"
            modules("java.prefs", "java.desktop", "java.net.http")
            windows {
                menuGroup = "Near Mosque"
                upgradeUuid = "3f6a2a8e-6d1b-4c55-9a7e-2b8c1f4d9e10"
                iconFile.set(project.file("src/main/icon/app.ico"))
            }
        }
    }
}
