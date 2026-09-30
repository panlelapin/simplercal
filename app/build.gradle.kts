import dev.detekt.gradle.extensions.FailOnSeverity
import com.android.build.api.variant.HostTestBuilder

val officialReleaseVersion = providers.environmentVariable("OFFICIAL_RELEASE_VERSION").orElse("---")
val signingStorePath = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(signingStorePath, signingStorePassword, signingKeyAlias, signingKeyPassword).all { !it.isNullOrBlank() }

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
        resValue("string", "official_release_version", officialReleaseVersion.get())

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildFeatures {
        resValues = true
        compose = true
    }

    buildTypes {
        debug {
            // Device tests run on the CI x86_64 emulator; release remains arm64-only.
            ndk.abiFilters += "x86_64"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.create("stableRelease") {
                    storeFile = file(requireNotNull(signingStorePath))
                    storePassword = signingStorePassword
                    keyAlias = signingKeyAlias
                    keyPassword = signingKeyPassword
                }
            }
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

tasks.matching { it.name == "packageRelease" }.configureEach {
    inputs.property("releaseSigningConfigured", hasReleaseSigning)
    doFirst {
        check(inputs.properties["releaseSigningConfigured"] == true) { "Stable release signing is required. Configure GitHub signing secrets with scripts/configure-signing." }
    }
}

// AGP 9 enables host tests only for the tested build type by default.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { builder ->
        builder.hostTests.getValue(HostTestBuilder.UNIT_TEST_TYPE).enable = true
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
