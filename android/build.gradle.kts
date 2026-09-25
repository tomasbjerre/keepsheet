import se.bjurr.violations.gradle.plugin.ViolationsTask
import se.bjurr.violations.lib.model.SEVERITY
import se.bjurr.violations.lib.reports.Parser

// Dependency and plugin versions below are pinned to a known-good baseline rather than
// bleeding edge. Bump them with:
//   ./gradlew showUpdateableDependencies
//   ./gradlew updateDependencies
// (from https://github.com/tomasbjerre/update-versions-gradle-plugin, applied below)

plugins {
    // Gives the root project "build"/"check" lifecycle tasks, which
    // se.bjurr.gradle.update-versions hooks into.
    id("base")
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("com.diffplug.spotless") version "8.10.2"
    id("se.bjurr.violations.violations-gradle-plugin") version "4.4.0"
    id("se.bjurr.gradle.update-versions") version "3.0.1"
}

// Kotlin/.kts formatting and linting is owned by org.jlleitschuh.gradle.ktlint (app
// module), which also feeds the violations report below. Spotless here covers the
// file types ktlint doesn't touch, with plain text hygiene rather than a dedicated
// per-format engine, to keep this dependency-light.
spotless {
    val nonSourceExcludes = listOf("**/build/**", "**/.gradle/**", "**/.idea/**")

    format("xmlResources") {
        target("**/*.xml")
        targetExclude(nonSourceExcludes)
        trimTrailingWhitespace()
        endWithNewline()
        leadingTabsToSpaces(4)
    }
    format("docsAndConfig") {
        target("**/*.md", "**/*.yml", "**/*.yaml")
        targetExclude(nonSourceExcludes)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Aggregates Android Lint, detekt, and ktlint reports from every module into one
// build-log summary. See https://github.com/tomasbjerre/violations-gradle-plugin
tasks.register<ViolationsTask>("violations") {
    // Only ERROR-level findings fail the build here (real bugs, ktlint/detekt style
    // violations). Android Lint's WARN-level suggestions (e.g. "add a monochrome
    // icon") stay visible in its own HTML report (uploaded as a CI artifact) without
    // blocking every PR over cosmetic nits.
    minSeverity.set(SEVERITY.ERROR)
    maxViolations.set(0)
    printViolations.set(true)

    violationConfig()
        .setFolder(projectDir.path)
        .setParser(Parser.ANDROIDLINT)
        .setPattern(".*/reports/lint-results.*\\.xml$")
        .setReporter("AndroidLint")
    violationConfig()
        .setFolder(projectDir.path)
        .setParser(Parser.CHECKSTYLE)
        .setPattern(".*/reports/detekt/.*\\.xml$")
        .setReporter("Detekt")
    violationConfig()
        .setFolder(projectDir.path)
        .setParser(Parser.CHECKSTYLE)
        // "Check" reports only — ktlint's "Format" task reports are transient
        // (rewritten by ktlintFormat) and shouldn't gate the build.
        .setPattern(".*/reports/ktlint/.*Check.*\\.xml$")
        .setReporter("KtLint")
}
