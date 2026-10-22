plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Set here rather than in .editorconfig, where Spotless doesn't pick them up: keep trailing commas
// (one-line diffs when a list grows), and don't force blank lines between a `when`'s branches.
val ktlintOverrides = mapOf(
    "ij_kotlin_allow_trailing_comma" to true,
    "ij_kotlin_allow_trailing_comma_on_call_site" to true,
    "ktlint_standard_blank-line-between-when-conditions" to "disabled",
)

// One formatter for all Kotlin: `./gradlew spotlessApply` fixes, `spotlessCheck` (in CI) checks.
// The rules live in the repo's .editorconfig.
spotless {
    kotlin {
        target("app/src/**/*.kt", "photoprep/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootDir.resolve("../.editorconfig"))
            .editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "photoprep/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootDir.resolve("../.editorconfig"))
            .editorConfigOverride(ktlintOverrides)
    }
}

// Static analysis (code smells, complexity, likely bugs) on top of the formatter: `./gradlew detekt`.
// Rules are detekt's defaults, adjusted in config/detekt.yml.
detekt {
    source.setFrom(
        "app/src/main/java",
        "app/src/test/java",
        "photoprep/src/main/kotlin",
        "photoprep/src/test/kotlin",
    )
    config.setFrom("config/detekt.yml")
    buildUponDefaultConfig = true
    parallel = true
}
