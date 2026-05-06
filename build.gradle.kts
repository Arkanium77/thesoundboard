plugins {
    id("application")
    id("java")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "team.isaz"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.4"
    modules("javafx.controls", "javafx.media")
}

application {
    mainClass = "app.SoundboardLauncher"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.4")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.4")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
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

val packageInputDir = layout.buildDirectory.dir("package/input")
val packageOutputDir = layout.buildDirectory.dir("package/output")
val jpackageName = "soundboard"

val preparePackageInput by tasks.registering(Sync::class) {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packageInputDir)
}

val createAppImage by tasks.registering(Exec::class) {
    dependsOn(preparePackageInput)
    group = "distribution"
    description = "Builds a JavaFX app-image via jpackage."

    val javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }

    val javaHome = javaLauncher.map { it.metadata.installationPath.asFile.absolutePath }
    val jpackageExecutable = javaHome.map { home ->
        val executableName = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "jpackage.exe" else "jpackage"
        file("$home/bin/$executableName").absolutePath
    }

    doFirst {
        delete(packageOutputDir)
        commandLine(
            jpackageExecutable.get(),
            "--type", "app-image",
            "--dest", packageOutputDir.get().asFile.absolutePath,
            "--name", jpackageName,
            "--input", packageInputDir.get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", application.mainClass.get()
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
