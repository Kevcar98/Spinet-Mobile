import java.util.Properties
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("dev.spinet:extension-api")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.media3.common)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)

            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)

            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

val appVersion = "0.0.3"

val appVersionCode = appVersion.split('.').map { it.toInt() }
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

val generateAppVersion = tasks.register("generateAppVersion") {
    val outDir = layout.buildDirectory.dir("generated/appversion")
    val version = appVersion
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("dev/spinet/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package dev.spinet

            /** Generated from build.gradle.kts - do not edit. */
            const val APP_VERSION: String = "$version"
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateAppVersion)
}

val keystorePropertiesFile = (providers.environmentVariable("SPINET_KEYSTORE_PROPERTIES").orNull
    ?.let { File(it) }
    ?: rootProject.file("keystore.properties"))
    .takeIf { it.isFile }

val keystoreProperties = keystorePropertiesFile?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

val releaseKeystore = keystoreProperties?.getProperty("storeFile")
    ?.let { path ->
        val named = File(path)
        if (named.isAbsolute) named else File(keystorePropertiesFile!!.parentFile, path)
    }

if (keystorePropertiesFile != null) {
    val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .filter { keystoreProperties?.getProperty(it).isNullOrBlank() }
    check(missing.isEmpty()) {
        "${keystorePropertiesFile.absolutePath} is missing: ${missing.joinToString(", ")}"
    }
    check(releaseKeystore?.isFile == true) {
        "Signing key not found at ${releaseKeystore?.absolutePath}\n" +
            "  (storeFile in ${keystorePropertiesFile.absolutePath})\n" +
            "  Create it, or correct the path. Relative paths resolve against " +
            "that file's folder."
    }
}

android {
    namespace = "dev.spinet"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.spinet"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties?.getProperty("storePassword")
                keyAlias = keystoreProperties?.getProperty("keyAlias")
                keyPassword = keystoreProperties?.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        getByName("release") {
            isMinifyEnabled = false

            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

val signedRelease = releaseKeystore != null
tasks.register("prepareRelease") {
    group = "build"
    description = "APK + SHA256SUMS in build/release, ready to upload"
    dependsOn(if (signedRelease) "assembleRelease" else "assembleDebug")

    val apkDir = layout.buildDirectory.dir(
        if (signedRelease) "outputs/apk/release" else "outputs/apk/debug"
    )
    val outDir = layout.buildDirectory.dir("release")
    val version = appVersion
    val signed = signedRelease
    val allowDebug = providers.gradleProperty("allowDebugRelease").isPresent

    inputs.dir(apkDir)
    outputs.dir(outDir)

    doFirst {
        check(signed || allowDebug) {
            "No signing key, so this would publish a debug-signed APK.\n" +
                "  Point SPINET_KEYSTORE_PROPERTIES at your keystore.properties, " +
                "or put one in the project root.\n" +
                "  To publish debug-signed anyway (testing only): " +
                "./gradlew prepareRelease -PallowDebugRelease"
        }
    }

    doLast {
        val dest = outDir.get().asFile

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        dest.deleteRecursively()
        dest.mkdirs()
        val src = File(
            apkDir.get().asFile,
            if (signed) "composeApp-release.apk" else "composeApp-debug.apk"
        )
        check(src.isFile) { "expected ${src.name}: it was not built" }

        val copy = File(dest, "Spinet-$version.apk")
        src.copyTo(copy, overwrite = true)
        File(dest, "SHA256SUMS").writeText("${sha256(copy)} *${copy.name}\n")
        logger.lifecycle("Release files in ${dest.absolutePath}")
    }
}

compose.resources {
    packageOfResClass = "dev.spinet.resources"
    publicResClass = false
}
