import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Kotlin 2.1.0 matches the Android side of this repo; Compose
// Multiplatform 1.7.3 is the release paired with it.
plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.tuxundl.windows"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.tuxundl.windows.MainKt"

        nativeDistributions {
            packageName = "TuxunDL"
            packageVersion = "1.0.0"
            description = "TuxunDL for Windows"
            vendor = "TuxunDL"

            targetFormats(TargetFormat.Msi, TargetFormat.Exe)

            windows {
                menuGroup = "TuxunDL"
                // Stable upgrade UUID so installers upgrade in place.
                upgradeUuid = "7e2b8f4a-3c1d-4a5e-9b6f-2d8a1c5e7f90"
            }
        }
    }
}
