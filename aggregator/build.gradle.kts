plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("de.noonoo.aggregator.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core"))

    // kotlinx
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Datenbank (PostgreSQL + HikariCP + Flyway)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // DI
    implementation(libs.koin.core)

    // Discord
    implementation(libs.discord.webhooks)
    implementation(libs.jda) {
        exclude(module = "opus-java")
    }

    // Sonstige
    implementation(libs.dotenv.kotlin)
    implementation(libs.jsoup)
    implementation(libs.playwright)
    implementation(libs.anthropic.java)
    implementation(libs.kaml)
    implementation(libs.logback)
    implementation(libs.kotlin.logging)
}
