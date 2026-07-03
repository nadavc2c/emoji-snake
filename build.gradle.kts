import java.nio.file.Files // `java.*` can't be used inline: `java` resolves to the Java plugin extension

plugins {
    application
    // Official OpenJFX Gradle plugin: pulls JavaFX from Maven Central and wires the
    // module-path / --add-modules for compile + run automatically (non-modular project).
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.emojisnake"

// Single source of truth for the version: derived from git so the app, the jar name, the jpackage
// app-version, the GitHub release, and the Drive filenames all share ONE number. MAJOR.MINOR come from
// the latest `vX.Y` tag; PATCH = commits since it - so it advances on every push (v1.3 -> 1.3.0, then
// 1.3.1, 1.3.2, ...). Falls back to 0.0.0 if git/tags are unavailable (jpackage needs numeric X.Y.Z).
// providers.exec (not a raw ProcessBuilder) keeps this configuration-cache-safe: Gradle tracks the
// git call as a build-configuration input instead of flagging an external process at config time.
val gitDescribe: Provider<String> = providers.exec {
    commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--always")
    workingDir = projectDir // pin to the repo root - the daemon's cwd is not guaranteed to be it
    isIgnoreExitValue = true // not-a-repo exits non-zero; empty stdout falls through to 0.0.0 below
}.standardOutput.asText
version = runCatching {
    val raw = gitDescribe.get().trim().removePrefix("v")
    val m = Regex("""^(\d+)\.(\d+)(?:-(\d+)-g[0-9a-f]+)?$""").find(raw)
    if (m != null) "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3].ifEmpty { "0" }}" else "0.0.0"
}.getOrDefault("0.0.0") // also covers a missing git binary (the exec provider throws at .get())

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
// regenerate with package.ps1's -RegenIcon switch if the art changes). Kept out of Gradle so the build
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

    // Incremental: jpackage re-runs only when the staged app/jars or the icon change, so a no-op
    // build skips it (and the delete below) entirely.
    inputs.dir(libDir).withPropertyName("appLib")
    inputs.files(icoFile).withPropertyName("icon").optional()
    outputs.dir(destDir.dir("Emoji Snake")).withPropertyName("appImage")

    doFirst {
        // jpackage won't overwrite an existing app-image, so delete the previous one first. jpackage on
        // JDK 25+ writes the launcher exe with the DOS read-only attribute (JDK-8363926), and JDK 25
        // changed File.delete to fail on read-only files (JDK-8355954). Per the official guidance
        // (inside.java 2025-06-16), clear the read-only attribute before deleting: Files.setAttribute
        // "dos:readonly" false, then delete bottom-up. A file that still refuses to go is genuinely in
        // use (the game is running from dist\), so stop with a clear message.
        val existing = destDir.dir("Emoji Snake").asFile
        if (existing.exists()) {
            existing.walkBottomUp().forEach { f ->
                val p = f.toPath()
                runCatching { Files.setAttribute(p, "dos:readonly", false) }
                runCatching { Files.delete(p) }
            }
        }
        if (existing.exists()) {
            throw GradleException(
                "Could not remove $existing — it's in use. Close the running game / 'Emoji Snake.exe' and re-run.",
            )
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

// --- Cross-platform distribution: ONE zip that runs on Windows, macOS AND Linux ------------------
// Java is portable, so this needs no per-OS build and no CI - it's produced from any single machine.
// The only per-OS pieces are JavaFX's native libraries, which live in javafx-graphics's platform
// classifier jars (win / linux / linux-aarch64 / mac / mac-aarch64). The OpenJFX plugin already
// bundles the build host's set on the runtime classpath; here we ADD the other platforms' graphics
// jars, and JavaFX loads whichever matches the machine it's launched on. (Only javafx-graphics
// carries natives; base/controls/swing are pure Java, so the host jars already cover them.)
//
// Unlike the jpackage app-image (self-contained, no Java needed, but Windows-only from a Windows
// box), this archive needs a JDK/JRE 25 on the target - but a single build serves every OS. Launch
// via the classpath + non-Application Launcher (same trick as jpackage) so JavaFX-on-classpath
// doesn't trip the "runtime components missing" guard.
// One configuration PER platform: JavaFX's Gradle Module Metadata gives every platform variant the
// same capability ('org.openjfx:javafx-graphics'), so putting several in one configuration is
// "rejected" as a capability conflict. Isolating each classifier in its own configuration sidesteps
// that. The host's own graphics jar also arrives via runtimeClasspath; the zip's EXCLUDE
// duplicates-strategy drops that second copy.
val jfxNativePlatforms = listOf("win", "mac", "mac-aarch64", "linux", "linux-aarch64")
val javafxNativeConfigs = jfxNativePlatforms.map { p ->
    configurations.create("javafxNatives_" + p.replace('-', '_')) { isTransitive = false }
}
dependencies {
    jfxNativePlatforms.forEach { p ->
        add("javafxNatives_" + p.replace('-', '_'), "org.openjfx:javafx-graphics:25.0.3:$p")
    }
}

tasks.register<Zip>("crossPlatformZip") {
    group = "distribution"
    description = "One archive that runs on Windows/macOS/Linux (needs a JDK 25 on the target)."
    dependsOn("jar")
    destinationDirectory.set(layout.projectDirectory.dir("dist"))
    archiveFileName.set("emoji-snake-crossplatform.zip")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // host graphics jar arrives from both sources
    val appDir = "emoji-snake"
    into("$appDir/lib") {
        from(tasks.named("jar"))                       // the game's own jar
        from(configurations.named("runtimeClasspath")) // JavaFX (+ any runtime deps) for the build host
        javafxNativeConfigs.forEach { from(it) }       // + JavaFX graphics natives for every OS
    }
    into("$appDir/bin") {
        from(layout.projectDirectory.file("packaging/emoji-snake.bat"))
    }
    into("$appDir/bin") {
        from(layout.projectDirectory.file("packaging/emoji-snake"))
        filePermissions { unix("0755") } // keep the Unix launcher executable inside the zip
    }
    doLast {
        logger.lifecycle("Cross-platform zip: ${archiveFile.get().asFile} " +
            "(unzip, then run bin/emoji-snake on macOS/Linux or bin\\emoji-snake.bat on Windows; needs Java 25)")
    }
}
