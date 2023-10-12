plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "io.github.xbaank"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.1") // for JVM platform
    implementation("io.github.xbaank:simpleJson-core:2.1.3")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("ru.gildor.coroutines:kotlin-coroutines-okhttp:1.0")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    implementation("io.arrow-kt:arrow-core:1.1.5")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}