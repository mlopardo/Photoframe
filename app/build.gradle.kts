plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Firma: la clave NUNCA vive en el repositorio.
 *
 * En el CI llega como secreto de GitHub (el keystore va en base64) y se materializa en un
 * archivo temporal del runner. En una máquina de desarrollo se lee de keystore.properties,
 * que está en .gitignore. Si no hay ninguna de las dos, el build sigue con la firma de
 * depuración: así cualquiera puede clonar y compilar sin tener la clave.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyName: String, environmentName: String): String? =
    keystoreProperties.getProperty(propertyName) ?: System.getenv(environmentName)

android {
    namespace = "com.zambiotica.photoframe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zambiotica.photoframe"   // PERMANENTE (ADR-002)
        minSdk = 25                                   // Galaxy Tab 2 / LineageOS 14.1 = Android 7.1.2
        targetSdk = 36                                // requisito de Play desde el 31-ago-2026
        // En CI se pisa con el numero de build: -PversionCode=$GITHUB_RUN_NUMBER
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.2.0"
    }

    signingConfigs {
        create("zambiotica") {
            val storePath = signingValue("storeFile", "PHOTOFRAME_KEYSTORE_FILE")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "PHOTOFRAME_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "PHOTOFRAME_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "PHOTOFRAME_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            val zambiotica = signingConfigs.getByName("zambiotica")
            if (zambiotica.storeFile != null) signingConfig = zambiotica
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            // Con la clave disponible, hasta el APK de prueba se firma igual siempre: así
            // actualizar en la tablet no obliga a desinstalar (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
            val zambiotica = signingConfigs.getByName("zambiotica")
            if (zambiotica.storeFile != null) signingConfig = zambiotica
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
