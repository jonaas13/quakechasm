plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.github.polyzium"
version = "1.0.0-beta1-spigot"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-bukkit-core:11.0.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.18")
    implementation("fr.mrmicky:fastboard:2.1.5")
    implementation("net.kyori:adventure-platform-bukkit:4.4.1")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    shadowJar {
        archiveClassifier.set("")
//        relocate("org.apache.commons.lang3", "com.github.polyzium.quakechasm.libs.commons.lang3")
//        relocate("fr.mrmicky.fastboard", "com.github.polyzium.quakechasm.libs.fastboard")
//        relocate("net.kyori", "com.github.polyzium.quakechasm.libs.kyori")
    }
    
    build {
        dependsOn(shadowJar)
    }
}