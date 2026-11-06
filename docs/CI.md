# CI (GitHub Actions)

Build, test, and lint **always run in GitHub Actions**. Do not rely on local builds for verification.

- **Build** — `.github/workflows/build.yml` → `./gradlew assembleDebug --no-daemon --stacktrace`, uploads `app-debug.apk`.
- **Test** — `.github/workflows/test.yml` → `:domain:test` + `:app:testDebugUnitTest`, uploads reports.
- **Lint** — `.github/workflows/lint.yml` → `:app:lintDebug`, uploads lint reports.
  - ktlint/detekt Gradle plugins are not used anywhere. Both hook the Android/Kotlin Gradle extensions in ways incompatible with AGP 8.7.3 + Kotlin 2.1.0 (`CommonExtension` / `BaseExtension` / `KotlinProjectExtension` not found in CI), so style is covered by `lintDebug` + `.editorconfig`.

Each workflow runs on `push` to `main` and on `pull_request`, with its own concurrency group.
All use JDK 17 (Temurin) + `gradle/actions/setup-gradle@v4`.

### Local development (informational only)

```sh
# Not a gate — CI is the gate. Use only for fast iteration.
./gradlew :domain:test
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

Android SDK is required locally to assemble; CI images provide it.
