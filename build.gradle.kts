plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.kotest)
}

group = "me.bossm0n5t3r"

version = "1.0.0"

repositories { mavenCentral() }

dependencies {
    testImplementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.mockk)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(libs.versions.jdk.version.get().toInt()) }

ktfmt { kotlinLangStyle() }

tasks.register<Copy>("copyTestPages") {
    group = "test"
    description = "Copy readability test page fixtures into src/test/resources/test-pages"

    from("readability/test/test-pages")
    into("src/test/resources/test-pages")
    outputs.upToDateWhen { false }
}
