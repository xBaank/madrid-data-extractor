plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "io.github.xbaank"
version = "1.1.1"

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

tasks {
    val fatJar = register<Jar>("fatJar") {
        dependsOn.addAll(
            listOf(
                "compileJava",
                "compileKotlin",
                "processResources"
            )
        ) // We need this for Gradle optimization to work
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) } // Provided we set it up in the application plugin configuration
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } +
                sourcesMain.output
        from(contents)
    }
    startScripts {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }
    distTar {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }
    distZip {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }
    build {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }
}

application {
    mainClass.set("MainKt")
}