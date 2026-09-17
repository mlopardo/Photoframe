plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.zambiotica.photoframe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zambiotica.photoframe"   // PERMANENTE (ADR-002)
        minSdk = 25                                   // Galaxy Tab 2 / LineageOS 14.1 = Android 7.1.2
        targetSdk = 36                                // requisito de Play desde el 31-ago-2026
        // En CI se pisa con el numero de build: -PversionCode=$GITHUB_RUN_NUMBER
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false   // MVP: lint reporta pero no frena el CI
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
