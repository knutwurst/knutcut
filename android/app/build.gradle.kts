plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.knutwurst.knutcut"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.knutwurst.knutcut"
        minSdk = 26
        targetSdk = 34
        versionCode = 48
        versionName = "0.29.4"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        // Debug and release share one application id (de.knutwurst.knutcut) so only a single app
        // is ever installed; no separate ".debug" package.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug-signed so the release APK installs directly for private use.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":svgcore"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Publish the release APK to ../dist with a version in the name, clearing older ones.
val copyReleaseApkToDist = tasks.register("copyReleaseApkToDist") {
    description = "Copy a version-named release APK to ../dist, removing previous ones."
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!apk.exists()) return@doLast
        val distDir = rootDir.parentFile.resolve("dist").apply { mkdirs() }
        distDir.listFiles()
            ?.filter { it.name.startsWith("Knutcut-") && it.name.endsWith("-release.apk") }
            ?.forEach { it.delete() }
        val target = distDir.resolve("Knutcut-v${android.defaultConfig.versionName}-release.apk")
        apk.copyTo(target, overwrite = true)
        logger.lifecycle("Exported ${target.name} to dist/")
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApkToDist) }
