// Standalone Gradle build for the Windows edition.
//
// This build is deliberately NOT a subproject of the Android root build:
// the root build.gradle.kts applies an Android plugin to every
// subproject, which a plain JVM desktop module cannot use. Keeping the
// desktop build standalone also means the Android build can never be
// broken by desktop changes, and CI builds each side on its own runner.
//
// Build from the repo root with:
//   gradlew -p desktop run            (dev run)
//   gradlew -p desktop packageMsi     (installer, on Windows)
//   gradlew -p desktop packageExe     (installer, on Windows)

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "TuxunDL-Windows"
