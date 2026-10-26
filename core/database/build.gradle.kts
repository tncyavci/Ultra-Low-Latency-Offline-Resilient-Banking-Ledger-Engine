import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.ledgerengine.database"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sqldelight/code/LedgerEventDatabase/commonMain"))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

sqldelight {
    databases {
        create("LedgerEventDatabase") {
            packageName.set("com.ledgerengine.database.ledger")
        }
    }
}

// Belt-and-suspenders: guarantee SQLDelight's generated LedgerEventDatabase
// sources exist on disk before KSP (Room/Hilt annotation processing) reads
// this module's source set, since DatabaseModule and LocalLedgerDataSource
// reference that generated type directly.
tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn("generateCommonMainLedgerEventDatabaseInterface")
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)

    implementation(libs.tink.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.jvm.driver)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
