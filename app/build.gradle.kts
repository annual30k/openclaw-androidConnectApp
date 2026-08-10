plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFilePath = providers.environmentVariable("CLAWLINK_RELEASE_STORE_FILE").orNull
val releaseStorePasswordValue = providers.environmentVariable("CLAWLINK_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAliasValue = providers.environmentVariable("CLAWLINK_RELEASE_KEY_ALIAS").orNull
val releaseKeyPasswordValue = providers.environmentVariable("CLAWLINK_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePasswordValue,
    releaseKeyAliasValue,
    releaseKeyPasswordValue
).all { !it.isNullOrBlank() }

android {
    namespace = "com.rethinkingstudio.clawlink"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rethinkingstudio.clawlink"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.0.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                // 发布签名凭据只允许从环境变量注入，避免把 keystore 密码写入仓库。
                storeFile = file(checkNotNull(releaseStoreFilePath))
                storePassword = releaseStorePasswordValue
                keyAlias = releaseKeyAliasValue
                keyPassword = releaseKeyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            // 真机调试包与正式签名包并存，避免为安装本地代码而卸载并清空用户数据。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    // AndroidX Core
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Security / encrypted preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Process lifecycle for foreground/background checks
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Accompanist for permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // CameraX
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")

    // DOCX parsing for native document preview
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
