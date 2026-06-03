plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.dotenv)
    implementation(libs.kaml)
    implementation(libs.logback)
}

application {
    mainClass.set("de.noonoo.web.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
