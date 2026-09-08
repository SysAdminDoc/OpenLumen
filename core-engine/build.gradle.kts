plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.openlumen.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // The engine's failure paths log through android.util.Log, which
            // throws "not mocked" in a plain JVM test. Before C253 no test ever
            // reached one of those branches, so a whole class of behaviour was
            // untestable; the probe-inconclusive tests are the first to need
            // it. Defaults are fine here: nothing in core-engine asserts on a
            // framework return value.
            isReturnDefaultValues = true
            // C257 needs a real `filesDir` to prove the kcal_min restore record
            // survives a failed apply, so this module now runs Robolectric the
            // way `app` already does.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    // Robolectric drags in an older bcprov. Test scope only, but a resolved
    // coordinate with a critical advisory is still a resolved coordinate.
    testImplementation(libs.bouncycastle.prov)
}
