// Lumina — standalone Gradle project that builds the glass UI as a real, installable demo APK.
// The browser fork (duckduckgo/Android + scripts/p0-fork.sh) consumes the same :lumina-ui module.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lumina"

// The standalone demo project is retired (see docs/PROGRESS.md): it pinned AGP 8 / Kotlin 2.0,
// while the glass stack (skydoves/Cloudy 1.0.0-alpha01) and the browser fork both need
// Kotlin 2.4 metadata. `lumina-ui/` is now compiled exactly once — inside the fork, by
// scripts/p3_wire_ntp.py — so there is a single toolchain and a single APK to trust.
// include(":lumina-ui")
// include(":lumina-demo")
