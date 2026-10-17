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

// The app's version: 0.5.0, or what CI passes with -PappVersion=0.5.<run>. jpackage's MSI wants
// MAJOR.MINOR.BUILD with MAJOR and MINOR at most 255 and BUILD at most 65535 (and a .deb a version starting with a
// digit), so anything else stops the build here rather than half-way through packaging.
val appVersion: String = providers.gradleProperty("appVersion").orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "0.5.0"
run {
    val parts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersion)?.groupValues?.drop(1)?.map { it.toInt() }
    require(parts != null) { "appVersion must be MAJOR.MINOR.BUILD, e.g. 0.5.12 (got '$appVersion')" }
    require(parts[0] <= 255 && parts[1] <= 255 && parts[2] <= 65535) {
        "appVersion '$appVersion': MAJOR and MINOR at most 255, BUILD at most 65535 (MSI rules)"
    }
}
version = appVersion

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
// The version, for Settings → About.
val versionResource by tasks.registering {
    val out = layout.buildDirectory.file("version-resources/version.txt")
    inputs.property("version", appVersion)
    outputs.file(out)
    doLast { out.get().asFile.apply { parentFile.mkdirs(); writeText(appVersion) } }
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("version-resources")) }
tasks.named("processResources") { dependsOn(sharedResources, versionResource) }

// Development runs report MET's 203 (a deprecated API version) on stderr (WeatherClient); packaged apps never do.
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach { systemProperty("nearmosque.debug", "true") }

compose.desktop {
    application {
        mainClass = "sa.zood.nearmosque.desktop.MainKt"
        nativeDistributions {
            // Each format is built on its own system: the MSI on Windows, the .deb and the AppImage folder on Linux
            // (.github/workflows/desktop-release.yml turns that folder into a single .AppImage file).
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "Near Mosque"
            packageVersion = appVersion
            description = "Prayer times, Adhan, Qibla, nearby mosques, Ask and classical books"
            vendor = "Near Mosque"
            // jdk.localedata: times and numbers in the person's language (without it Arabic shows 14:57, not 2:57 م);
            // jdk.unsupported: used by the libraries (from suggestRuntimeModules).
            modules("java.prefs", "java.desktop", "java.net.http", "jdk.localedata", "jdk.unsupported")
            windows {
                menuGroup = "Near Mosque"
                upgradeUuid = "3f6a2a8e-6d1b-4c55-9a7e-2b8c1f4d9e10"
                iconFile.set(project.file("src/main/icon/app.ico"))
            }
            linux {
                // The Debian package and its folder (/opt/near-mosque); the menu shows "Near Mosque".
                packageName = "near-mosque"
                debMaintainer = "app@vision.bz"
                // Categories in the menu entry (freedesktop), and the Debian section.
                menuGroup = "Utility"
                appCategory = "utils"
                iconFile.set(project.file("src/main/icon/app.png"))
                shortcut = true
            }
        }
    }
}

// jpackage's .deb registers its menu entry with xdg-desktop-menu under `set -e`, so on a system without the
// freedesktop menu folders (a container, a minimal or server install) the package can be neither installed nor
// removed. The package is repacked with those two calls allowed to fail (the menu entry is then simply absent),
// and its menu entry is given StartupWMClass so the desktop matches the running window to it.
fun run(vararg cmd: String) {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    check(p.waitFor() == 0) { "${cmd.joinToString(" ")} failed:\n$out" }
}
tasks.matching { it.name == "packageDeb" || it.name == "packageReleaseDeb" }.configureEach {
    doLast {
        val dir = layout.buildDirectory.dir(if (name == "packageDeb") "compose/binaries/main/deb" else "compose/binaries/main-release/deb").get().asFile
        dir.listFiles { f -> f.extension == "deb" }.orEmpty().forEach { deb ->
            val root = layout.buildDirectory.dir("tmp/deb-repack").get().asFile.apply { deleteRecursively(); mkdirs() }
            run("dpkg-deb", "-R", deb.path, root.path)
            listOf("postinst", "prerm").map { File(root, "DEBIAN/$it") }.filter { it.isFile }.forEach { script ->
                script.writeText(script.readText().lines().joinToString("\n") { line ->
                    if (line.trimStart().startsWith("xdg-desktop-menu ") && !line.contains("|| true")) "$line || true" else line
                })
            }
            fileTree(root) { include("opt/*/lib/*.desktop") }.forEach { entry ->
                val text = entry.readText()
                if ("StartupWMClass=" !in text) entry.writeText(text.trimEnd() + "\nStartupWMClass=sa-zood-nearmosque-desktop-MainKt\n")
            }
            run("dpkg-deb", "--root-owner-group", "-Zxz", "--build", root.path, deb.path)
            root.deleteRecursively()
        }
    }
}
