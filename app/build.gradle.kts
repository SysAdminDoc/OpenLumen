import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.screenshot)
    alias(libs.plugins.roborazzi)
}

@DisableCachingByDefault(because = "Only validates local release signing inputs.")
abstract class CheckReleaseSigningEnvironment : DefaultTask() {
    @get:Input
    abstract val allowUnsignedRelease: Property<Boolean>

    @get:Input
    abstract val openLumenKeystore: Property<String>

    @get:Input
    abstract val openLumenKeystorePassword: Property<String>

    @get:Input
    abstract val openLumenKeyAlias: Property<String>

    @get:Input
    abstract val openLumenKeyPassword: Property<String>

    @TaskAction
    fun check() {
        if (allowUnsignedRelease.get()) {
            logger.lifecycle(
                "Unsigned release output explicitly allowed by -Popenlumen.allowUnsignedRelease=true"
            )
            return
        }

        val required = mapOf(
            "OPENLUMEN_KEYSTORE" to openLumenKeystore.get(),
            "OPENLUMEN_KEYSTORE_PASSWORD" to openLumenKeystorePassword.get(),
            "OPENLUMEN_KEY_ALIAS" to openLumenKeyAlias.get(),
            "OPENLUMEN_KEY_PASSWORD" to openLumenKeyPassword.get()
        )
        val missing = required.filterValues { it.isBlank() }.keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Signed release builds require ${missing.joinToString()}. " +
                    "Set the full OPENLUMEN_* signing environment or pass " +
                    "-Popenlumen.allowUnsignedRelease=true for local/F-Droid reproducibility builds."
            )
        }

        val keystore = File(openLumenKeystore.get())
        if (!keystore.isFile) {
            throw GradleException(
                "OPENLUMEN_KEYSTORE does not point to a readable file: ${keystore.absolutePath}"
            )
        }
    }
}

val releaseSigningEnvVars = listOf(
    "OPENLUMEN_KEYSTORE",
    "OPENLUMEN_KEYSTORE_PASSWORD",
    "OPENLUMEN_KEY_ALIAS",
    "OPENLUMEN_KEY_PASSWORD"
)

val allowUnsignedReleaseBuild = providers.gradleProperty("openlumen.allowUnsignedRelease")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

fun releaseSigningEnv(): Map<String, String?> =
    releaseSigningEnvVars.associateWith { providers.environmentVariable(it).orNull }

fun hasCompleteReleaseSigningEnv(): Boolean =
    releaseSigningEnv().values.all { !it.isNullOrBlank() }

android {
    namespace = "com.openlumen"
    compileSdk = 37
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.openlumen"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.6.2"
    }

    signingConfigs {
        create("release") {
            val signingEnv = releaseSigningEnv()
            val ksPath = signingEnv["OPENLUMEN_KEYSTORE"]
            if (hasCompleteReleaseSigningEnv() && ksPath != null) {
                storeFile = file(ksPath)
                storePassword = signingEnv["OPENLUMEN_KEYSTORE_PASSWORD"]
                keyAlias = signingEnv["OPENLUMEN_KEY_ALIAS"]
                keyPassword = signingEnv["OPENLUMEN_KEY_PASSWORD"]
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
            // reference APK comparisons drift. Release provenance stays
            // external via Git tags and local release-gate artifacts.
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasCompleteReleaseSigningEnv()) {
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

val checkReleaseSigningEnvironment = tasks.register<CheckReleaseSigningEnvironment>("checkReleaseSigningEnvironment") {
    group = "verification"
    description = "Fails release builds unless signing is configured or unsigned release output is explicitly allowed."
    allowUnsignedRelease.set(allowUnsignedReleaseBuild)
    openLumenKeystore.set(providers.environmentVariable("OPENLUMEN_KEYSTORE").orElse(""))
    openLumenKeystorePassword.set(providers.environmentVariable("OPENLUMEN_KEYSTORE_PASSWORD").orElse(""))
    openLumenKeyAlias.set(providers.environmentVariable("OPENLUMEN_KEY_ALIAS").orElse(""))
    openLumenKeyPassword.set(providers.environmentVariable("OPENLUMEN_KEY_PASSWORD").orElse(""))
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkReleaseSigningEnvironment)
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance.appwidget)
    // Keep WorkManager explicit because OpenLumenApp supplies its lazy-init
    // Configuration.Provider while the startup initializer stays disabled.
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
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
