import java.time.Duration

plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

val jdk = libs.versions.jdk.get().toInt()
val minecraft = libs.versions.minecraft.get()

base {
    archivesName = property("archives_base_name") as String
    version = "${property("mod_version")}+$minecraft"
    group = property("maven_group") as String
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.ccbluex.net/releases")
    maven("https://maven.ccbluex.net/snapshots")
    maven("https://maven.fabricmc.net/")
}

loom {
    accessWidenerPath = file("src/main/resources/liquidbounce-extras.accesswidener")
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "liquidbounce-extras-gametest"
        enableGameTests = false
    }
}

// Shared with LiquidBounce's own game tests, so the browser is downloaded once per machine.
val gameTestLibraries = gradle.gradleUserHomeDir.resolve("liquidbounce-gametest")
val paperDirectory = layout.buildDirectory.dir("paper").get().asFile
val probeJar = project(":paper-probe").layout.buildDirectory.file("libs/paper-probe.jar").get().asFile

loom.runs.named("clientGameTest") {
    systemProperties.putAll(
        mapOf(
            "net.ccbluex.liquidbounce.ui.basicMode" to "true",
            "net.ccbluex.liquidbounce.browser.libraries" to gameTestLibraries.resolve("mcef").path,
            "net.ccbluex.liquidbounce.deeplearning.engines" to gameTestLibraries.resolve("djl").path,
            // Chromium's accelerated paint crashes natively on llvmpipe, which is all CI has
            "net.ccbluex.liquidbounce.browser.disableAcceleration" to "true",
            "extras.paper.directory" to paperDirectory.path,
            "extras.paper.probe" to probeJar.path,
            "extras.paper.minecraft" to minecraft,
            "fabric.noGui" to "true",
        )
    )
    // Loader and client errors go to the log instead of a dialog nobody sees
    environmentVars.put("CI", "true")
    mapOf(
        "gametest.only" to "extras.gametest.only",
        "paper.build" to "extras.paper.build",
        "grim.version" to "extras.grim.version",
    ).forEach { (gradleProperty, systemProperty) ->
        providers.gradleProperty(gradleProperty).orNull?.let { systemProperties.put(systemProperty, it) }
    }
}

tasks.named<JavaExec>("runClientGameTest") {
    dependsOn(":paper-probe:jar")
    // A client that cannot start may wait on an error dialog forever
    timeout = Duration.ofMinutes(45)
}

// No `mappings(...)` and no `mod*` configurations: like LiquidBounce itself, this Loom version runs on
// Mojang's official names without a remapping step. Another mapping set compiles and then fails on
// every Minecraft call.
dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.kotlin)
    implementation(libs.liquidbounce)
}

// The client publishes a snapshot on every push to nextgen.
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to minecraft,
        "loader_version" to libs.versions.fabric.loader.get(),
        "fabric_kotlin_version" to libs.versions.fabric.kotlin.get(),
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(jdk)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = jdk
}

kotlin {
    jvmToolchain(jdk)
    compilerOptions {
        freeCompilerArgs.addAll(
            // LiquidBounce is built with pre-release language features
            "-Xskip-prerelease-check",
            "-Xcollection-literals",
            "-Xcompanion-blocks-and-extensions",
            "-Xcontext-sensitive-resolution",
            "-Xreturn-value-checker=full",
        )
    }
}

// The same rules as LiquidBounce
detekt {
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    source.from("src/gametest/kotlin")
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}
