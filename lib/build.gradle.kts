version = "0.1.0"
group = "com.parkertenbroeck.generators"

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}



java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}