import dev.detekt.gradle.extensions.FailOnSeverity

val officialReleaseVersion = providers.environmentVariable("OFFICIAL_RELEASE_VERSION").orElse("---")

plugins {
    id("com.android.application")
    id("dev.detekt")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.github.panlelapin.simplercal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.panlelapin.simplercal"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "OFFICIAL_RELEASE_VERSION",
            "\"${officialReleaseVersion.get()}\"",
        )

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            // Bootstrap signing only: installable, reproducible, and not for production distribution.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
        warningsAsErrors = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("com.google.android.material:material:1.14.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.2")
}

detekt {
    toolVersion = "2.0.0-alpha.5"
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    ignoreFailures = false
    failOnSeverity = FailOnSeverity.Warning
    basePath.set(rootProject.projectDir)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("17")
    exclude("**/build/**", "**/generated/**")
    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        markdown.required.set(true)
        sarif.required.set(true)
    }
}
