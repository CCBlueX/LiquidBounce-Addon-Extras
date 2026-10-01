plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.grim.ac/snapshots")
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.grim.api)
}

java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get().toInt())

tasks.jar {
    archiveFileName = "paper-probe.jar"
}
