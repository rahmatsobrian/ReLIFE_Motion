// Root build file — plugin versions are declared in gradle/libs.versions.toml
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 memakai "built-in Kotlin"; KGP dinaikkan agar cocok dengan plugin Compose.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
