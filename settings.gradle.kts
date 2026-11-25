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

// Retired: the Compose demo project pinned AGP 8 / Kotlin 2.0. The product is the browser fork,
// and the home page is now native views (lumina-android/), compiled there.
// include(":lumina-ui")
// include(":lumina-demo")
