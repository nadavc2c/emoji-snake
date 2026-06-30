plugins {
    application
    // Official OpenJFX Gradle plugin: pulls JavaFX from Maven Central and wires the
    // module-path / --add-modules for compile + run automatically (non-modular project).
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.emojisnake"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        // Latest LTS. Resolves to the JDK running Gradle (Temurin 25) — no download.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

javafx {
    version = "25.0.3"
    // controls -> graphics (Canvas); swing -> SwingFXUtils for the --snapshot self-check.
    modules("javafx.controls", "javafx.swing")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.emojisnake.EmojiSnakeApp"
}

tasks.test {
    useJUnitPlatform()
}

// --- Distribution: a self-contained Windows app-image via the JDK's own `jpackage` --------------
// No third-party plugins. `installDist` (from the `application` plugin) lays out the app jar + all
// JavaFX jars under build/install/emoji-snake/lib; `jpackage` (shipped in JDK 25) wraps that plus a
// trimmed JRE into dist/Emoji Snake/. JavaFX rides on the classpath (its win-native jars carry the
// .dlls); the bundled runtime only needs the JDK modules listed below. Launch via the non-Application
// `Launcher` so JavaFX-on-classpath doesn't trip the "runtime components missing" guard.
//
// The exe icon is the committed art/snake.ico (a 256x256 PNG-embedded ICO derived from art/snake.png;
// regenerate with package.ps1's -Icon helper if the art changes). Kept out of Gradle so the build
// script needn't touch java.awt (not on the Kotlin-DSL script classpath).
tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Build dist/Emoji Snake/ — a self-contained, double-clickable Windows app-image."
    dependsOn("installDist")

    val libDir = layout.buildDirectory.dir("install/${project.name}/lib")
    val icoFile = layout.projectDirectory.file("art/snake.ico")
    val destDir = layout.projectDirectory.dir("dist")
    val ver = project.version.toString()
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val jpackageBin = javaToolchains.launcherFor(java.toolchain).get()
        .metadata.installationPath.file("bin/jpackage" + if (isWindows) ".exe" else "").asFile.absolutePath

    doFirst {
        // jpackage refuses to overwrite an existing app-image, and it writes the runtime as
        // read-only — so clear the read-only flag before deleting (plain delete() fails on it).
        val existing = destDir.dir("Emoji Snake").asFile
        if (existing.exists()) {
            existing.walkBottomUp().forEach { it.setWritable(true); it.delete() }
            if (existing.exists()) {
                throw GradleException(
                    "Could not remove $existing — close 'Emoji Snake.exe' if it's running, then retry.",
                )
            }
        }
        destDir.asFile.mkdirs()
    }

    val args = mutableListOf(
        jpackageBin,
        "--type", "app-image",
        "--name", "Emoji Snake",
        "--app-version", ver,
        "--input", libDir.get().asFile.absolutePath,
        "--main-jar", "${project.name}-$ver.jar",
        "--main-class", "com.emojisnake.Launcher",
        "--dest", destDir.asFile.absolutePath,
        // JavaFX is on the classpath; the trimmed runtime just needs these JDK modules
        // (java.desktop covers AWT/Swing/SwingFXUtils + javax.sound.sampled audio).
        "--add-modules",
        "java.base,java.desktop,java.logging,java.scripting,java.xml,java.naming,java.management,jdk.unsupported",
    )
    if (icoFile.asFile.exists()) {
        args.add("--icon")
        args.add(icoFile.asFile.absolutePath)
    }
    commandLine(args)
}
