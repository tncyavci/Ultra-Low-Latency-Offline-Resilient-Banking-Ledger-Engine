plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:domain"))
    // Pure-JVM Tink: :core:crypto stays Android-agnostic and unit-testable off-device.
    // The Android Keystore-backed keyset provider (tink-android) lives in the `app`
    // module, which implements the SigningKeyProvider/VerificationKeyProvider ports
    // declared here.
    implementation(libs.tink)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}
