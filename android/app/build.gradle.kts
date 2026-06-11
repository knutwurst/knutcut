import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: read android/keystore.properties (gitignored) when present. A fixed release key
// gives a stable signature, which is required for the self-update flow to install over a previous
// build. If the file is absent (e.g. a fresh clone without the keystore) the build falls back to
// the debug key so it still compiles.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystorePropsFile.exists() &&
    rootProject.file(keystoreProps.getProperty("storeFile", "")).exists()

android {
    namespace = "de.knutwurst.knutcut"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.knutwurst.knutcut"
        minSdk = 26
        targetSdk = 34
        versionCode = 150
        versionName = "0.58.4"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v2 + v3 APK signing. v2 covers every device we target (minSdk 26); v3 adds
                // key-rotation support (Android 9+) via apksigner's signing lineage mechanism.
                // Rotation is performed automatically by scripts/sign_release.sh (invoked by
                // release.sh), which re-signs the built APK with the debug->release lineage stored at
                // android/signing-lineage.bin. No manual `apksigner rotate` step is needed here.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
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
            // Sign with the release key when keystore.properties is present (stable signature
            // for self-update; avoids the debug-cert Play Protect warning). Falls back to the
            // debug key on machines without the keystore so the build still succeeds.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Ship the repo's CHANGELOG.md as an asset so the in-app "Changelog" shows it offline,
    // from the single source of truth (copied in by the copyChangelog task below).
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/changelogAssets"))

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Keep Robolectric's android-all framework jars inside the repo (gitignored), not in the
            // user's global ~/.m2. Robolectric's MavenDependencyResolver honours maven.repo.local for
            // its own downloads; ordinary Gradle dependencies are unaffected.
            all {
                it.systemProperty(
                    "maven.repo.local",
                    rootProject.file(".robolectric-deps").absolutePath,
                )
            }
        }
    }
}

val copyChangelog = tasks.register<Copy>("copyChangelog") {
    from(rootProject.file("../CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/changelogAssets"))
    rename { "changelog.md" }
}
tasks.named("preBuild") { dependsOn(copyChangelog) }

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
    // Robolectric runs the AndroidViewModel cut() guard on the JVM (it needs a real Application
    // plus SharedPreferences-backed settings).
    testImplementation("org.robolectric:robolectric:4.14.1")
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
