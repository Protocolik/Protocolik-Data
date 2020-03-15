plugins {
    kotlin("jvm") version "1.3.70"
    `maven-publish`
}

group = "com.github.protocolik"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenLocal()
    jcenter()
    maven { setUrl("https://jitpack.io/") }
}