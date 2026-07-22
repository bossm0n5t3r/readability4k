import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.kotest)
    `maven-publish`
    signing
}

group = "com.m0n5t3r.boss"

version = "1.0.1"

val centralStagingRepository = layout.buildDirectory.dir("central-staging")
val centralPublicationPath =
    "${project.group.toString().replace('.', '/')}/${project.name}/${project.version}"
val centralUsername = providers.environmentVariable("CENTRAL_USERNAME")
val centralPassword = providers.environmentVariable("CENTRAL_PASSWORD")
val signingKey = providers.environmentVariable("SIGNING_KEY")
val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")

java { withSourcesJar() }

val javadocJar =
    tasks.register<Jar>("javadocJar") {
        description = "Assembles the Maven Central Javadoc placeholder JAR."
        archiveClassifier = "javadoc"
        from(layout.projectDirectory.file("README.md")) { into("META-INF") }
    }

repositories { mavenCentral() }

dependencies {
    testImplementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.logback.classic)
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
    inputs.dir("readability/test/test-pages")
}

kotlin { jvmToolchain(libs.versions.jdk.version.get().toInt()) }

ktfmt { kotlinLangStyle() }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(javadocJar)

            pom {
                name.set("readability4k")
                description.set(
                    "A Kotlin port of Mozilla Readability for extracting readable article content from HTML."
                )
                url.set("https://codeberg.org/bossm0n5t3r/readability4k")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("bossm0n5t3r")
                        name.set("bossm0n5t3r")
                        email.set("bossm0n5t3r@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:https://codeberg.org/bossm0n5t3r/readability4k.git")
                    developerConnection.set(
                        "scm:git:ssh://git@codeberg.org/bossm0n5t3r/readability4k.git"
                    )
                    url.set("https://codeberg.org/bossm0n5t3r/readability4k")
                }
            }
        }
    }

    repositories {
        maven {
            name = "centralStaging"
            url = uri(centralStagingRepository)
        }
    }
}

signing {
    if (signingKey.isPresent && signingPassword.isPresent) {
        useInMemoryPgpKeys(signingKey.get().replace("\\n", "\n"), signingPassword.get())
        sign(publishing.publications)
    }
}

val requireCentralSigning =
    tasks.register("requireCentralSigning") {
        group = "publishing"
        description = "Verifies the signing credentials required by Maven Central."

        doLast {
            check(signingKey.isPresent) {
                "SIGNING_KEY must contain an ASCII-armored PGP private key."
            }
            check(signingPassword.isPresent) {
                "SIGNING_PASSWORD must contain the PGP key passphrase."
            }
        }
    }

tasks.named("publishMavenJavaPublicationToCentralStagingRepository") {
    dependsOn(requireCentralSigning)
}

val prepareCentralBundle =
    tasks.register<Zip>("prepareCentralBundle") {
        group = "publishing"
        description = "Builds a signed Maven Central Portal upload bundle."
        dependsOn("publishMavenJavaPublicationToCentralStagingRepository")
        archiveFileName.set("${project.name}-${project.version}-central.zip")
        destinationDirectory.set(layout.buildDirectory.dir("central-bundle"))
        from(centralStagingRepository) { include("$centralPublicationPath/**") }

        doLast {
            val publicationDirectory =
                centralStagingRepository.get().dir(centralPublicationPath).asFile
            val primaryArtifacts =
                publicationDirectory
                    .walkTopDown()
                    .filter {
                        it.isFile &&
                            it.extension !in setOf("asc", "md5", "sha1", "sha256", "sha512")
                    }
                    .toList()
            check(primaryArtifacts.isNotEmpty()) {
                "No artifacts were published to the Central staging repository."
            }

            val missingAccompaniments = primaryArtifacts.flatMap { artifact ->
                listOf(".asc", ".md5", ".sha1")
                    .map { suffix -> artifact.resolveSibling("${artifact.name}$suffix") }
                    .filterNot { it.isFile }
            }
            check(missingAccompaniments.isEmpty()) {
                "Maven Central requires signatures and checksums. Missing: ${missingAccompaniments.joinToString()}"
            }
        }
    }

tasks.register("publishCentralBundle") {
    group = "publishing"
    description = "Uploads a signed bundle to the Maven Central Portal for manual release."
    dependsOn(prepareCentralBundle)

    doLast {
        val username =
            centralUsername.orNull
                ?: error("CENTRAL_USERNAME must contain the Central Portal user token username.")
        val password =
            centralPassword.orNull
                ?: error("CENTRAL_PASSWORD must contain the Central Portal user token password.")
        val bundle = prepareCentralBundle.get().archiveFile.get().asFile
        val boundary = "----gradle-central-${UUID.randomUUID()}"
        val deploymentName =
            URLEncoder.encode(
                "${project.group}:${project.name}:${project.version}",
                StandardCharsets.UTF_8,
            )
        val connection =
            URI(
                    "https://central.sonatype.com/api/v1/publisher/upload?name=$deploymentName&publishingType=USER_MANAGED"
                )
                .toURL()
                .openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty(
                "Authorization",
                "Bearer ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}",
            )
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        connection.outputStream.buffered().use { output ->
            output.write(
                ("--$boundary\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"${bundle.name}\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n")
                    .toByteArray(StandardCharsets.UTF_8)
            )
            Files.copy(bundle.toPath(), output)
            output.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }

        val response =
            (if (connection.responseCode in 200..299) connection.inputStream
                else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        check(connection.responseCode in 200..299) {
            "Central Portal upload failed with HTTP ${connection.responseCode}: $response"
        }
        logger.lifecycle("Central Portal deployment uploaded for manual release: $response")
    }
}
