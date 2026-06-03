plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    implementation(libs.koin.core)
    implementation(libs.discord.webhooks)
    implementation(libs.jda) { exclude(module = "opus-java") }
    implementation(libs.dotenv)
    implementation(libs.jsoup)
    implementation(libs.playwright)
    implementation(libs.anthropic)
    implementation(libs.kaml)
    implementation(libs.logback)
    implementation(libs.kotlin.logging)
}

application {
    mainClass.set("de.noonoo.aggregator.ApplicationKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
