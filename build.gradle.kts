import java.util.Properties

plugins {
    id("com.android.application") version "9.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

// Release signing: keystore + passwords are git-ignored locally; a backup copy
// lives in the private yu4032/liquiddock-keys repository.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.hellovoid.liquiddock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hellovoid.liquiddock"
        minSdk = 33
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "liquiddock-release.keystore"))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "liquiddock")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // AGP 9.3: R8 code shrinking + optimized resource shrinking.
            optimization.enable = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/liquiddock.keep")
        }
    }

    buildFeatures { compose = true }

    lint {
        // LSPosed module: reflective access to hidden framework APIs is the whole point.
        disable += listOf("BlockedPrivateApi", "SoonBlockedPrivateApi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    testImplementation("junit:junit:4.13.2")
}

base {
    archivesName.set("LiquidDock")
}
