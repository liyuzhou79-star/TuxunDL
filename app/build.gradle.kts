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

    // Tor (1.7.3): the Tor engine and page live in src/tor and are
    // compiled into the phone flavours (alpha, meta) only — code,
    // assets and the tor native binary alike. The TV flavour gets
    // none of it, only a stub entry provider (src/tv).
    sourceSets {
        getByName("alpha") {
            java.srcDir("src/tor/java")
            jniLibs.srcDir("src/tor/jniLibs")
            assets.srcDir("src/tor/assets")
        }
        getByName("meta") {
            java.srcDir("src/tor/java")
            jniLibs.srcDir("src/tor/jniLibs")
            assets.srcDir("src/tor/assets")
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

    // Tor (1.7.3), phone flavours only: IPtProxy 5.8.0 carries the
    // in-process pluggable transports (lyrebird obfs4 / webtunnel /
    // meek-lite + snowflake); the tor daemon itself ships as
    // src/tor/jniLibs/libtor.so (Guardian Project tor-android
    // 0.4.9.17 binary) because the tor-android AAR's bytecode and
    // compileSdk requirements clash with this project's toolchain.
    // androidx.webkit points the Tor page's WebView at Tor's HTTP
    // tunnel port.
    alphaImplementation("com.netzarchitekten:IPtProxy:5.8.0")
    metaImplementation("com.netzarchitekten:IPtProxy:5.8.0")
    alphaImplementation("androidx.webkit:webkit:1.12.0")
    metaImplementation("androidx.webkit:webkit:1.12.0")

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