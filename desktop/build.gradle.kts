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

// One source tree, two desktop editions. The build follows the OS
// of the machine running it: Windows CI builds the Windows edition,
// Linux CI builds the Linux edition. Versions are independent from
// here on — the Windows edition is at 1.6.1, the Linux edition
// starts at 1.0.0 — and each edition's update checker only follows
// its own release tags (win-v* / linux-v*).
val hostOsName = System.getProperty("os.name").lowercase()
val linuxBuild = hostOsName.contains("linux")
val editionVersion = if (linuxBuild) "1.0.0" else "1.6.1"

group = if (linuxBuild) "com.tuxundl.linux" else "com.tuxundl.windows"
version = editionVersion

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
            packageVersion = editionVersion
            description = if (linuxBuild) {
                "TuxunDL for Linux"
            } else {
                "TuxunDL for Windows"
            }
            vendor = "TuxunDL"

            if (linuxBuild) {
                targetFormats(TargetFormat.Deb, TargetFormat.AppImage)

                linux {
                    // The application identifier doubles as the deb
                    // package name, mirroring how the Windows
                    // edition is identified by com.tuxundl.windows.
                    packageName = "com.tuxundl.linux"
                }
            } else {
                targetFormats(TargetFormat.Msi, TargetFormat.Exe)

                windows {
                    menuGroup = "TuxunDL"
                    // Stable upgrade UUID so installers upgrade in place.
                    upgradeUuid = "7e2b8f4a-3c1d-4a5e-9b6f-2d8a1c5e7f90"
                }
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
    val binFile = coreResDir.file("mihomo").asFile
    if (linuxBuild) {
        outputs.file(binFile)
        onlyIf { !binFile.exists() }
    } else {
        outputs.files(exeFile, dllFile)
        onlyIf { !exeFile.exists() || !dllFile.exists() }
    }
    doLast {
        coreResDir.asFile.mkdirs()
        if (linuxBuild) {
            // Linux ships a single gzipped binary; there is no
            // wintun — the kernel provides /dev/net/tun natively.
            if (!binFile.exists()) {
                val gz = File(temporaryDir, "mihomo.gz")
                URI(
                    "https://github.com/MetaCubeX/mihomo/releases/download/" +
                        "$mihomoVersion/mihomo-linux-amd64-compatible-$mihomoVersion.gz",
                ).toURL().openStream().use { input ->
                    gz.outputStream().use { output -> input.copyTo(output) }
                }
                java.util.zip.GZIPInputStream(gz.inputStream()).use { input ->
                    binFile.outputStream().use { output -> input.copyTo(output) }
                }
                binFile.setExecutable(true)
                check(binFile.exists() && binFile.length() > 0) {
                    "mihomo missing after fetchCore"
                }
            }
            return@doLast
        }
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
