// Top-level build file. Plugin versions live here; library versions are in gradle/libs.versions.toml.
// All build/test/lint execution runs in GitHub Actions (see .github/workflows).
plugins {
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
