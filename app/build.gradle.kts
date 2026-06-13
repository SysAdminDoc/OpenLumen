plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.screenshot)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.openlumen"
    compileSdk = 36
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.openlumen"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.5.1"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("OPENLUMEN_KEYSTORE")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("OPENLUMEN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OPENLUMEN_KEY_ALIAS")
                keyPassword = System.getenv("OPENLUMEN_KEY_PASSWORD")
                // v1 = legacy JAR; useful only for API < 24, but we keep it for any future
                // minSdk lowering. v2 + v3 cover Android 7+ with key-rotation support.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // F-Droid rebuilds from the release tag; embedding the local Git
            // revision in META-INF/version-control-info.textproto can make
            // reference APK comparisons drift. GitHub release provenance stays
            // external via actions/attest.
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("OPENLUMEN_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            }
        }
    }
}

roborazzi {
    outputDir.set(file("src/test/roborazzi"))
}

dependencies {
    implementation(project(":core-engine"))
    implementation(project(":core-schedule"))
    implementation(project(":core-prefs"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance.appwidget)
    // Pinned to the version Glance 1.1.1 already pulls in transitively, so
    // the dependency-verification metadata stays valid. We need direct API
    // access for Configuration.Provider in OpenLumenApp (see issue #5).
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
