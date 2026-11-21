// lumina-ui — glass design system module.
// Rename this folder to `lumina-ui` after copying it into the forked app;
// the upstream settings.gradle auto-discovers any top-level dir with a build file.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.lumina.ui.glass"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // glass engine — pick ONE of these two lines
    implementation("io.github.kyant0:backdrop:0.1.0")     // Maven Central (check latest)
    // implementation(project(":backdrop"))                // vendored copy

    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.webkit:webkit:1.14.0")        // document-start JS for the SERP skin
}
