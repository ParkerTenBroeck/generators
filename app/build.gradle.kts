plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

tasks.register<JavaExec>("lexer"){
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    jvmArgs("--enable-preview")
    group = "Demos"
    mainClass = "lexer.Main"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("sockets"){
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    group = "Demos"
    mainClass = "sockets.Main"
    classpath = sourceSets["main"].runtimeClasspath
}