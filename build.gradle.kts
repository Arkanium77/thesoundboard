import groovy.json.JsonSlurper
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
version = "1.3.0"
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
    mainClass = "app.SoundboardBootstrap"
}

val applicationName = "TheSoundboard"
val applicationDisplayName = "The Soundboard"
val macosBundleIdentifier = "team.isaz.thesoundboard"
val macosCategory = "public.app-category.music"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.freedesktop.gstreamer:gst1-java-core:1.4.0")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3") {
        exclude(group = "junit", module = "junit")
    }
    implementation("org.jflac:jflac-codec:1.5.2")
    implementation("io.github.jaredmdobson:concentus:1.0.2")
    implementation("org.gagravarr:vorbis-java-core:0.8")
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
val gstreamerRuntimeDirectory = providers.gradleProperty("gstreamerRuntime").orNull?.let { file(it) }
        ?: layout.buildDirectory.dir("gstreamer/$releasePlatform-$releaseArchitecture").get().asFile
val prepareGstreamerRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Assembles the experimental audio runtime from pinned ready-built binaries without native compilation."
    inputs.dir("scripts/gstreamer")
    inputs.property("platform", "$releasePlatform-$releaseArchitecture")
    outputs.dir(gstreamerRuntimeDirectory)
    commandLine(providers.gradleProperty("gstreamerPython").getOrElse(if (releasePlatform == "windows") "python" else "python3"),
            "scripts/gstreamer/prepare-runtime.py", "--platform", "$releasePlatform-$releaseArchitecture",
            "--output", gstreamerRuntimeDirectory.absolutePath,
            "--downloads", layout.buildDirectory.dir("gstreamer/downloads").get().asFile.absolutePath)
}
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

val tacticalCodecExamplePackage by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the editable Tactical Codec example skin."
    from("examples/skins/Tactical Codec/source")
    archiveFileName = "Tactical Codec.tsbs"
    destinationDirectory = file("examples/skins/Tactical Codec")
}

val retroAmpExamplePackage by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the editable Retro Amp example skin."
    from("examples/skins/Retro Amp/source")
    archiveFileName = "Retro Amp.tsbs"
    destinationDirectory = file("examples/skins/Retro Amp")
}

val packageExampleSkins by tasks.registering {
    dependsOn(nightModeExamplePackage, sakuraExamplePackage, tacticalCodecExamplePackage,
            retroAmpExamplePackage)
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

val leetspeakLocalizationExamplePackage by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the editable Leetspeak localization example."
    from("examples/localizations/Leetspeak/source")
    archiveFileName = "Leetspeak.tsbl"
    destinationDirectory = file("examples/localizations/Leetspeak")
}

val packageExampleLocalizations by tasks.registering {
    dependsOn(russianLocalizationExamplePackage, preRevolutionaryLocalizationExamplePackage,
            leetspeakLocalizationExamplePackage)
    group = "distribution"
    description = "Rebuilds all ready-to-install example localization packages."
}

val packageExamples by tasks.registering {
    dependsOn(packageExampleSkins, packageExampleLocalizations)
    group = "distribution"
    description = "Rebuilds all example skin and localization packages."
}

val bundledSkinPackages = fileTree("examples/skins") {
    include("**/*.tsbs")
}
val bundledLocalizationPackages = fileTree("examples/localizations") {
    include("**/*.tsbl")
}
val applicationAssetsDirectory = packageOutputDir.map { outputDirectory ->
    if (releasePlatform == "macos") {
        outputDirectory.dir("$appImageDirectoryName/Contents/assets")
    } else {
        outputDirectory.dir("$appImageDirectoryName/assets")
    }
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
    // The default backend must work in every ordinary release, without an opt-in build property.
    // Only catalog-selected files are shipped; research data and probe registries stay outside the image.
    if (!providers.gradleProperty("gstreamerRuntime").isPresent) dependsOn(prepareGstreamerRuntime)
    val runtime = gstreamerRuntimeDirectory
    val catalogFiles by lazy {
        val manifest = JsonSlurper().parse(file("$runtime/runtime-manifest.json")) as Map<*, *>
        (manifest["files"] as List<*>).map { (it as Map<*, *>)["file"] as String }.toSet()
    }
    from(runtime) {
        into("gstreamer")
        // Copy only the catalog: an old plugin left by an ordinary copy-over update must
        // neither become active nor leak into a newly packaged experimental application.
        include { element ->
            val path = element.relativePath.pathString
            element.isDirectory || path == "native-load-order.txt" || path == "runtime-manifest.json"
                    || path.startsWith("licenses/") || path in catalogFiles
        }
    }
    doFirst {
        check(file("$runtime/lib/gstreamer-1.0").isDirectory) {
            "GStreamer runtime has no private plugin directory: $runtime"
        }
        check(file("$runtime/runtime-manifest.json").isFile && file("$runtime/licenses/NOTICE.txt").isFile) {
            "Packaged GStreamer requires a pinned manifest and native license notices: $runtime"
        }
    }
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
    if (releasePlatform == "linux") inputs.file("scripts/linux/launch.sh")
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
            "--description", applicationDisplayName,
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
    doLast {
        if (releasePlatform == "linux") {
            val image = packageOutputDir.get().dir(appImageDirectoryName).asFile
            val launcher = image.resolve("bin/$applicationName")
            check(launcher.renameTo(image.resolve("bin/$applicationName.bin"))) {
                "Cannot preserve the native Linux launcher"
            }
            image.resolve("lib/app/$applicationName.cfg")
                    .copyTo(image.resolve("lib/app/$applicationName.bin.cfg"))
            // Windows checkouts may use CRLF; the Linux kernel requires an LF shebang.
            launcher.writeText(file("scripts/linux/launch.sh").readText().replace("\r\n", "\n"))
            check(launcher.setExecutable(true, false)) { "Cannot make the Linux launcher executable" }
        }
    }
}

val bundleApplicationAssets by tasks.registering(Sync::class) {
    dependsOn(createAppImage, packageExamples)
    group = "distribution"
    description = "Copies ready-to-use skin and localization packages into the application image."
    from(bundledSkinPackages) {
        eachFile { path = "skins/$name" }
        includeEmptyDirs = false
    }
    from(bundledLocalizationPackages) {
        eachFile { path = "localization/$name" }
        includeEmptyDirs = false
    }
    into(applicationAssetsDirectory)
}

val prepareRelease by tasks.registering(Sync::class) {
    dependsOn(bundleApplicationAssets)
    group = "distribution"
    description = "Stages the application image and release documentation."
    doFirst {
        delete(releaseStagingDir)
    }
    from(packageOutputDir.map { it.dir(appImageDirectoryName) }) {
        into(appImageDirectoryName)
    }
    from("README.md", "SKIN_AUTHORING.md", "LOCALIZATION_AUTHORING.md", "LICENSE", "NOTICE")
    from("src/main/resources/licenses") {
        into("THIRD-PARTY-LICENSES")
    }
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
    inputs.property("linuxExecutablePermissions", "0755")
    // Copy specs can normalize modes when staging on a Windows filesystem. Preserve Linux launchability
    // explicitly for both the shell/native entry points and the bundled JVM's executables/helpers.
    eachFile {
        if (path.contains("/bin/") || path.endsWith("/lib/jspawnhelper") || path.endsWith("/lib/jexec")) {
            permissions { unix("rwxr-xr-x") }
        }
    }
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
    useJUnitPlatform { excludeTags("audio-device", "audio-benchmark") }
}

tasks.register<Test>("audioDeviceTest") {
    description = "Verifies real JavaFX and GStreamer playback; requires a working audio output and graphical session."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("audio-device") }
    maxParallelForks = 1
    outputs.upToDateWhen { false }
}

tasks.register<Test>("audioBenchmark") {
    description = "Measures playback CPU, JVM heap and threads for JavaFX and GStreamer engines; requires audio output and a graphical session."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("audio-benchmark") }
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    testLogging.showStandardStreams = true
}

// Development launches and standard Gradle distributions need the same private runtime as jpackage images.
tasks.named<JavaExec>("run") {
    if (!providers.gradleProperty("gstreamerRuntime").isPresent) dependsOn(prepareGstreamerRuntime)
    doFirst {
        systemProperty("thesoundboard.gstreamer.path", gstreamerRuntimeDirectory.absolutePath)
    }
}

distributions.named("main") {
    contents {
        from(preparePackageInput) {
            include("gstreamer/**")
            into("lib")
        }
    }
}

tasks.register<JavaExec>("runJavaFx") {
    description = "Runs the application with the original JavaFX audio engine."
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args("--audio-engine=javafx")
}

tasks.register<JavaExec>("runGstreamer") {
    description = "Runs the default GStreamer engine with its private native runtime."
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args("--audio-engine=gstreamer")
    if (!providers.gradleProperty("gstreamerRuntime").isPresent) dependsOn(prepareGstreamerRuntime)
    doFirst {
        systemProperty("thesoundboard.gstreamer.path", gstreamerRuntimeDirectory.absolutePath)
    }
}

tasks.register<Test>("gstreamerTest") {
    group = "verification"
    description = "Exercises the real private decoder runtime with synthetic audio, seek, waveform and playback lifecycle."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/GstreamerIntegrationTest.class")
    if (!providers.gradleProperty("gstreamerRuntime").isPresent) dependsOn(prepareGstreamerRuntime)
    inputs.dir(gstreamerRuntimeDirectory)
    environment("THESOUNDBOARD_GSTREAMER_TEST_RUNTIME", gstreamerRuntimeDirectory.absolutePath)
}
