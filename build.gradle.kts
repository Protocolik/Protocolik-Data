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
    api("com.google.code.gson", "gson", "2.8.6")
    api("it.unimi.dsi", "fastutil", "8.3.1")
}