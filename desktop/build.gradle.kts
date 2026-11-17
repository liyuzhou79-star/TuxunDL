import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI

// Kotlin 2.1.0 matches the Android side of this repo; Compose
// Multiplatform 1.7.3 is the release paired with it.
plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.tuxundl.windows"
version = "1.1.4"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.tuxundl.windows.MainKt"

        nativeDistributions {
            packageName = "TuxunDL"
            packageVersion = "1.1.4"
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

// The mihomo core ships inside the app so users install one thing.
// The binaries are downloaded at build time from the upstream
// releases and are never committed to this repository (see
// .gitignore). CoreBridge unpacks them on first run.
val mihomoVersion = "v1.19.16"
val coreResDir = layout.projectDirectory.dir("src/main/resources/core")

tasks.register("fetchCore") {
    val exeFile = coreResDir.file("mihomo.exe").asFile
    val dllFile = coreResDir.file("wintun.dll").asFile
    outputs.files(exeFile, dllFile)
    onlyIf { !exeFile.exists() || !dllFile.exists() }
    doLast {
        coreResDir.asFile.mkdirs()
        if (!exeFile.exists()) {
            val zip = File(temporaryDir, "mihomo.zip")
            URI(
                "https://github.com/MetaCubeX/mihomo/releases/download/" +
                    "$mihomoVersion/mihomo-windows-amd64-compatible-$mihomoVersion.zip",
            ).toURL().openStream().use { input ->
                zip.outputStream().use { output -> input.copyTo(output) }
            }
            copy {
                from(zipTree(zip))
                into(coreResDir)
                include("*.exe")
            }
            // The archive holds one exe with a versioned name.
            coreResDir.asFile.listFiles { f -> f.extension == "exe" }
                ?.firstOrNull { it.name != "mihomo.exe" }
                ?.renameTo(exeFile)
            check(exeFile.exists()) { "mihomo.exe missing after fetchCore" }
        }
        if (!dllFile.exists()) {
            val zip = File(temporaryDir, "wintun.zip")
            URI("https://www.wintun.net/builds/wintun-0.14.1.zip")
                .toURL().openStream().use { input ->
                    zip.outputStream().use { output -> input.copyTo(output) }
                }
            copy {
                from(zipTree(zip))
                into(coreResDir)
                // The archive nests everything under a wintun/ folder.
                include("wintun/bin/amd64/wintun.dll")
                eachFile { path = name }
            }
            check(dllFile.exists()) { "wintun.dll missing after fetchCore" }
        }
    }
}

tasks.named("processResources") {
    dependsOn("fetchCore")
}

// Off-screen render of the home page (see PreviewMain.kt). The
// preview-windows-ui workflow runs this and uploads the PNG, so the
// UI can be eyeballed without a Windows machine. The classpath is
// borrowed from the Compose plugin's own run task, which already
// carries the full desktop runtime including the Skiko natives.
tasks.register<JavaExec>("previewHome") {
    dependsOn(tasks.named("compileKotlin"))
    classpath = tasks.named<JavaExec>("run").get().classpath
    mainClass.set("com.tuxundl.windows.PreviewMainKt")
    args = listOf("tuxundl-home.png")
}
