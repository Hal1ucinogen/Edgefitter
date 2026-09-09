plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val versionMajor = 0
val versionMinor = 2
val versionPatch = getGitPatchVersion(versionMajor, versionMinor)

android {
    namespace = "com.hal1ucinogen.systembarsmodernizer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hal1ucinogen.systembarsmodernizer"
        minSdk = 29
        targetSdk = 37
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("sbm.keystore")
            val storePass = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasStr = System.getenv("KEY_ALIAS")
            val keyPass = System.getenv("KEY_PASSWORD") ?: storePass

            if (keystoreFile.exists() && !storePass.isNullOrBlank() && !keyAliasStr.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = keyAliasStr
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { releaseConfig ->
                if (releaseConfig.storeFile != null) {
                    signingConfig = releaseConfig
                }
            }
        }
    }

    applicationVariants.all {
        if (buildType.name == "release") {
            outputs.all {
                (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                    "Edgefitter-v${versionName}.apk"
            }
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.serialization.InternalSerializationApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        lintConfig = file("lint.xml")
    }
}
configurations.configureEach {
    exclude("androidx.appcompat", "appcompat")
}

dependencies {
    implementation(libs.androidX.core)
//    implementation(libs.androidX.appCompat)
    implementation(libs.androidX.fragment)
    implementation(libs.androidX.preference)
    implementation(libs.google.material)
    implementation(libs.coil)
    implementation(libs.bundles.androidX.lifecycle)
    implementation(libs.bundles.androidX.room)
    ksp(libs.androidX.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation(libs.brvah)
    implementation(libs.bundles.rikkax)
    implementation(libs.timber)
    implementation(libs.fastScroll)
    implementation(libs.bundles.zhaobozhen)
}

fun getGitPatchVersion(major: Int, minor: Int): Int {
    return try {
        val baseTag = "v$major.$minor.0"
        val checkProcess = ProcessBuilder("git", "rev-parse", "--verify", "$baseTag^{commit}")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val tagExists = checkProcess.waitFor() == 0

        if (tagExists) {
            val revProcess = ProcessBuilder("git", "rev-list", "$baseTag..HEAD", "--count")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val count = revProcess.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 0
            revProcess.waitFor()
            count
        } else {
            val latestTagProcess = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val latestTag = latestTagProcess.inputStream.bufferedReader().readText().trim()
            latestTagProcess.waitFor()
            if (latestTag.isNotEmpty()) {
                val revProcess = ProcessBuilder("git", "rev-list", "$latestTag..HEAD", "--count")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                val count = revProcess.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 0
                revProcess.waitFor()
                count
            } else {
                0
            }
        }
    } catch (e: Exception) {
        0
    }
}