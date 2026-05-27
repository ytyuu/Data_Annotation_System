plugins {
    kotlin("jvm") version "1.9.10"
    application
}

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("io.javalin:javalin:5.6.1")
    implementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

application {
    // main class for the application plugin. Kotlin top-level main in App.kt -> AppKt
    mainClass.set("app.AppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "25"
}

tasks.test {
    useJUnitPlatform()
}

