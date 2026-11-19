import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("android")
    kotlin("kapt")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }

    // Tor spike: bundled native binaries (libtor.so) must be extracted
    // to disk so the app can execute them as a daemon process; with the
    // modern compressed packaging they would only be mmap-able.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.biometric)

    // Tor connectivity spike (tor-spike branch only): in-process
    // pluggable transports (IPtProxy 5.8.0: lyrebird
    // obfs4/webtunnel/meek-lite + snowflake + dnstt). The tor daemon
    // itself ships as jniLibs/libtor.so (extracted from Guardian
    // Project's tor-android 0.4.9.17 AAR) because that AAR's Java 24
    // bytecode and compileSdk 37 requirement clash with this
    // project's toolchain; only its tor binary is used.
    implementation("com.netzarchitekten:IPtProxy:5.8.0")

    // Tor page phase 2: androidx.webkit's ProxyController points the
    // in-app browser's WebView at Tor's HTTPTunnelPort, so Duck-bar
    // browsing rides the Tor circuit instead of going direct.
    implementation("androidx.webkit:webkit:1.12.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val geoFilesDownloadDir = "src/main/assets"

task("downloadGeoFiles") {

    val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        // "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb" to "country.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z" to "BundleMRS.7z",
    )

    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val url = URL(downloadUrl)
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()
            url.openStream().use { input ->
                Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("$outputFileName downloaded to $outputPath")
            }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file(geoFilesDownloadDir))
}