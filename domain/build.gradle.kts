plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.sumi.domain"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}

ktlint {
    android.set(false)
}

detekt {
    parallel = true
    buildUponDefaultConfig = true
}
