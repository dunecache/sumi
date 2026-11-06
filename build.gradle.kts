// Top-level build file. Plugin versions live here; library versions are in gradle/libs.versions.toml.
// All build/test/lint execution runs in GitHub Actions (see .github/workflows).
// NOTE: ktlint/detekt Gradle plugins are intentionally not used anywhere. Both hook the
// Android/Kotlin Gradle extensions in ways incompatible with AGP 8.7.3 + Kotlin 2.1.0
// (`CommonExtension` / `BaseExtension` / `KotlinProjectExtension` not found in CI).
// Style/lint is covered by Android `lintDebug` + `.editorconfig` (see docs/CI.md).
plugins {
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
