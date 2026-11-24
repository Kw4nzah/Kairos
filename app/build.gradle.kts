plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kairos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kairos.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("vcode") as String?)?.toInt() ?: 16
        // The libraries ship some 80 languages; Kairos has three.
        resourceConfigurations += listOf("en", "tr", "ru")
        versionName = (project.findProperty("vname") as String?) ?: "1.4.7"
    }

    // The release key lives outside the repo (default ~/.kairos-signing/kairos.p12 + password.txt,
    // or -PkairosKeyDir=<dir>). Builds up to 1.4.3 were signed with a debug key; 1.4.4 switched for good.
    val keyDir = file((project.findProperty("kairosKeyDir") as String?) ?: "${System.getProperty("user.home")}/.kairos-signing")
    signingConfigs {
        create("release") {
            storeFile = File(keyDir, "kairos.p12")
            storeType = "PKCS12"
            val pw = File(keyDir, "password.txt").takeIf { it.isFile }?.readText()?.trim()
            storePassword = pw
            keyPassword = pw
            keyAlias = "kairos"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Keep all languages in every split so the in-app language switch works for Play bundles.
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
}
