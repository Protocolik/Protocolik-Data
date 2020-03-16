plugins {
    kotlin("jvm") version "1.3.70"
    `maven-publish`
    maven
}

group = "com.github.protocolik"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenLocal()
    jcenter()
    maven { setUrl("https://jitpack.io/") }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.4")
    api("com.google.code.gson", "gson", "2.8.6")
    api("it.unimi.dsi", "fastutil", "8.3.1")
    api("org.jsoup", "jsoup", "1.13.1")
    api("com.github.protocolik", "protocolik-api", "ab211d67d1")
}