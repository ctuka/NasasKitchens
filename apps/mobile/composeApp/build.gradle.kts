import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework { baseName = "NanasKitchensMobile"; isStatic = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.3.1")
            implementation("io.ktor:ktor-client-content-negotiation:3.3.1")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("io.ktor:ktor-client-okhttp:3.3.1")
        }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.3.1") }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-cio:3.3.1")
            }
        }
    }
}

android {
    namespace = "com.nanaskitchens.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nanaskitchens.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

compose.desktop {
    application {
        mainClass = "com.nanaskitchens.mobile.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Nanas Kitchens Mobile"
            packageVersion = "1.0.0"
        }
    }
}
