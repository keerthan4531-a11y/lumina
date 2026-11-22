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

include(":lumina-ui")
include(":lumina-demo")
