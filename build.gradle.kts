import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import javax.imageio.ImageIO

plugins {
    id("application")
    id("java")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "team.isaz"
version = "0.9.0"
val applicationVersion = version.toString()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.12"
    modules("javafx.controls", "javafx.media")
}

application {
    mainClass = "app.SoundboardLauncher"
}

val applicationName = "TheSoundboard"
val applicationDisplayName = "The Soundboard"
val macosBundleIdentifier = "team.isaz.thesoundboard"
val macosCategory = "public.app-category.music"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.4")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.4")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4") {
        exclude(group = "junit", module = "junit")
    }
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    exclude("emoji/twemoji-17.0.3.zip")
    from(zipTree("src/main/resources/emoji/twemoji-17.0.3.zip")) {
        include("twemoji-17.0.3/assets/72x72/*.png")
        eachFile {
            path = "emoji/twemoji/" + name
        }
        includeEmptyDirs = false
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to applicationDisplayName,
            "Implementation-Version" to applicationVersion
        )
    }
}

fun releasePlatform(osName: String) = when {
    osName.lowercase().contains("windows") -> "windows"
    osName.lowercase().contains("linux") -> "linux"
    osName.lowercase().contains("mac") -> "macos"
    else -> throw GradleException("Unsupported release operating system: $osName")
}

fun releaseArchitecture(osArchitecture: String) = when (osArchitecture.lowercase()) {
    "amd64", "x86_64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> osArchitecture.lowercase().replace(Regex("[^a-z0-9]+"), "-")
}

val releasePlatform = releasePlatform(System.getProperty("os.name"))
val releaseArchitecture = releaseArchitecture(System.getProperty("os.arch"))
val releaseBaseName = "thesoundboard-$applicationVersion-$releasePlatform-$releaseArchitecture"
val packageRootDir = layout.buildDirectory.dir("package/$releasePlatform-$releaseArchitecture")
val packageInputDir = packageRootDir.map { it.dir("input") }
val packageOutputDir = packageRootDir.map { it.dir("output") }
val releaseStagingDir = layout.buildDirectory.dir("release/$releaseBaseName")
val macosIcon = layout.buildDirectory.file("generated/icons/app-icon.icns")
val applicationIcon = when (releasePlatform) {
    "windows" -> file("src/main/resources/icons/app-icon.ico")
    "linux" -> file("src/main/resources/icons/app-icon.png")
    else -> macosIcon.get().asFile
}
val appImageDirectoryName = if (releasePlatform == "macos") "$applicationName.app" else applicationName
val javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}
val javaHome = javaLauncher.map { it.metadata.installationPath.asFile.absolutePath }
val jpackageExecutable = javaHome.map { home ->
    val executableName = if (releasePlatform == "windows") "jpackage.exe" else "jpackage"
    file("$home/bin/$executableName").absolutePath
}

val createMacosIcon by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Generates the macOS ICNS icon from the canonical branding PNG."
    enabled = releasePlatform == "macos"
    inputs.file("assets/branding/tsblogo2.png")
    inputs.file("scripts/macos/create-icon.sh")
    outputs.file(macosIcon)
    commandLine("bash", "scripts/macos/create-icon.sh", "assets/branding/tsblogo2.png", macosIcon.get().asFile.absolutePath)
}

val nightModeExamplePackage by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the editable Night Mode example skin."
    from("examples/skins/Night Mode/source")
    archiveFileName = "Night Mode.tsbs"
    destinationDirectory = file("examples/skins/Night Mode")
}

val sakuraExamplePackage by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the editable Sakura example skin."
    from("examples/skins/Sakura/source")
    archiveFileName = "Sakura.tsbs"
    destinationDirectory = file("examples/skins/Sakura")
}

val packageExampleSkins by tasks.registering {
    dependsOn(nightModeExamplePackage, sakuraExamplePackage)
    group = "distribution"
    description = "Rebuilds all ready-to-install example skin packages."
}

val russianLocalizationExamplePackage by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the editable Russian localization example."
    from("examples/localizations/Russian/source")
    archiveFileName = "Russian.tsbl"
    destinationDirectory = file("examples/localizations/Russian")
}

val preRevolutionaryLocalizationExamplePackage by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the editable Pre-Revolutionary Russian localization example."
    from("examples/localizations/Pre-Revolutionary Russian/source")
    archiveFileName = "Pre-Revolutionary Russian.tsbl"
    destinationDirectory = file("examples/localizations/Pre-Revolutionary Russian")
}

val packageExampleLocalizations by tasks.registering {
    dependsOn(russianLocalizationExamplePackage, preRevolutionaryLocalizationExamplePackage)
    group = "distribution"
    description = "Rebuilds all ready-to-install example localization packages."
}

val packageExamples by tasks.registering {
    dependsOn(packageExampleSkins, packageExampleLocalizations)
    group = "distribution"
    description = "Rebuilds all example skin and localization packages."
}

tasks.named("assemble") {
    dependsOn(packageExamples)
}

val verifyReleaseVersion by tasks.registering {
    group = "verification"
    description = "Verifies that the project version follows Semantic Versioning 2.0.0."
    doLast {
        val semanticVersion = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
        check(semanticVersion.matches(applicationVersion)) {
            "Project version '$applicationVersion' is not a valid Semantic Versioning 2.0.0 version."
        }
    }
}

val preparePackageInput by tasks.registering(Sync::class) {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packageInputDir)
}

val createAppImage by tasks.registering(Exec::class) {
    dependsOn(preparePackageInput)
    if (releasePlatform == "macos") dependsOn(createMacosIcon)
    group = "distribution"
    description = "Builds a self-contained app image for the current operating system."
    doNotTrackState("jpackage app images contain runtime links that Gradle cannot snapshot reliably")

    inputs.property("applicationVersion", applicationVersion)
    inputs.property("releasePlatform", releasePlatform)
    inputs.property("releaseArchitecture", releaseArchitecture)
    inputs.property("applicationName", applicationName)
    inputs.property("applicationDisplayName", applicationDisplayName)
    inputs.property("appImageDirectoryName", appImageDirectoryName)
    inputs.property("macosBundleIdentifier", macosBundleIdentifier)
    inputs.file(applicationIcon)
    inputs.dir(packageInputDir)
    outputs.dir(packageOutputDir)

    doFirst {
        delete(packageOutputDir)
        val jpackageArguments = mutableListOf(
            jpackageExecutable.get(),
            "--type", "app-image",
            "--dest", packageOutputDir.get().asFile.absolutePath,
            "--name", applicationName,
            "--app-version", applicationVersion,
            "--vendor", "Arkanium77 & ISAZ Team",
            "--copyright", "Copyright 2026 Arkanium77 & ISAZ Team",
            "--description", "A desktop soundboard for organizing and playing audio tracks.",
            "--icon", applicationIcon.absolutePath,
            "--input", packageInputDir.get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", application.mainClass.get()
        )
        if (releasePlatform == "macos") {
            jpackageArguments.addAll(listOf(
                "--mac-package-name", applicationName,
                "--mac-package-identifier", macosBundleIdentifier,
                "--mac-app-category", macosCategory
            ))
        }
        commandLine(jpackageArguments)
    }
}

val prepareRelease by tasks.registering(Sync::class) {
    dependsOn(createAppImage)
    group = "distribution"
    description = "Stages the application image and release documentation."
    doFirst {
        delete(releaseStagingDir)
    }
    from(packageOutputDir.map { it.dir(appImageDirectoryName) }) {
        into(appImageDirectoryName)
    }
    from("README.md", "SKIN_AUTHORING.md", "LOCALIZATION_AUTHORING.md", "LICENSE", "NOTICE")
    from(listOf("src/main/resources/emoji/Twemoji-MIT.txt", "src/main/resources/emoji/Twemoji-CC-BY-4.0.txt")) {
        into("THIRD-PARTY-LICENSES")
    }
    into(releaseStagingDir)
}

val releaseZip by tasks.registering(Zip::class) {
    dependsOn(tasks.check, verifyReleaseVersion, prepareRelease)
    group = "distribution"
    description = "Builds the Windows release archive."
    enabled = releasePlatform == "windows"
    from(releaseStagingDir)
    archiveBaseName = releaseBaseName
    archiveVersion = ""
    destinationDirectory = layout.buildDirectory.dir("release")
}

val releaseTar by tasks.registering(Tar::class) {
    dependsOn(tasks.check, verifyReleaseVersion, prepareRelease)
    group = "distribution"
    description = "Builds the Linux release archive."
    enabled = releasePlatform == "linux"
    from(releaseStagingDir)
    archiveBaseName = releaseBaseName
    archiveVersion = ""
    archiveExtension = "tar.gz"
    compression = Compression.GZIP
    destinationDirectory = layout.buildDirectory.dir("release")
}

val releaseDmg by tasks.registering(Exec::class) {
    dependsOn(tasks.check, verifyReleaseVersion, createAppImage)
    group = "distribution"
    description = "Builds the unsigned macOS DMG for the current architecture."
    enabled = releasePlatform == "macos"
    inputs.property("applicationVersion", applicationVersion)
    inputs.property("releasePlatform", releasePlatform)
    inputs.property("releaseArchitecture", releaseArchitecture)
    inputs.property("macosBundleIdentifier", macosBundleIdentifier)
    inputs.file(applicationIcon)
    inputs.dir(packageInputDir)
    outputs.file(layout.buildDirectory.file("release/$releaseBaseName.dmg"))
    doFirst {
        mkdir(layout.buildDirectory.dir("release"))
        delete(layout.buildDirectory.file("release/$releaseBaseName.dmg"))
        commandLine(
            jpackageExecutable.get(),
            "--type", "dmg",
            "--dest", layout.buildDirectory.dir("release").get().asFile.absolutePath,
            "--name", applicationName,
            "--app-version", applicationVersion,
            "--vendor", "Arkanium77 & ISAZ Team",
            "--copyright", "Copyright 2026 Arkanium77 & ISAZ Team",
            "--description", "A desktop soundboard for organizing and playing audio tracks.",
            "--icon", applicationIcon.absolutePath,
            "--input", packageInputDir.get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", application.mainClass.get(),
            "--mac-package-name", applicationName,
            "--mac-package-identifier", macosBundleIdentifier,
            "--mac-app-category", macosCategory
        )
    }
    doLast {
        val generatedDmg = layout.buildDirectory.file("release/$applicationName-$applicationVersion.dmg").get().asFile
        val releaseDmg = layout.buildDirectory.file("release/$releaseBaseName.dmg").get().asFile
        check(generatedDmg.isFile) { "jpackage did not create the expected DMG: $generatedDmg" }
        check(generatedDmg.renameTo(releaseDmg)) { "Could not rename $generatedDmg to $releaseDmg" }
    }
}

val verifyReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Verifies cross-platform release naming and canonical branding inputs."
    inputs.file("assets/branding/tsblogo2.png")
    doLast {
        check(releasePlatform("Windows 11") == "windows")
        check(releasePlatform("Linux") == "linux")
        check(releasePlatform("Mac OS X") == "macos")
        check(releaseArchitecture("amd64") == "x64")
        check(releaseArchitecture("x86_64") == "x64")
        check(releaseArchitecture("aarch64") == "arm64")
        check(releaseArchitecture("arm64") == "arm64")
        check("TheSoundboard.app" == "$applicationName.app")
        check(macosBundleIdentifier == "team.isaz.thesoundboard")
        check("thesoundboard-$applicationVersion-macos-arm64.dmg" == "thesoundboard-$applicationVersion-macos-${releaseArchitecture("arm64")}.dmg")
        check("thesoundboard-$applicationVersion-macos-x64.dmg" == "thesoundboard-$applicationVersion-macos-${releaseArchitecture("amd64")}.dmg")
        val logo = ImageIO.read(file("assets/branding/tsblogo2.png"))
        check(logo != null && logo.width == logo.height && logo.width >= 1024) {
            "Canonical branding PNG must be square and at least 1024x1024."
        }
    }
}

tasks.named("check") {
    dependsOn(verifyReleaseConfiguration)
}

tasks.register("release") {
    dependsOn(packageExamples, when (releasePlatform) {
        "windows" -> releaseZip
        "linux" -> releaseTar
        else -> releaseDmg
    })
    group = "distribution"
    description = "Runs verification and builds a release archive for the current operating system."
}

tasks.test {
    useJUnitPlatform()
}
