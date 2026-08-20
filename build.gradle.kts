import java.util.Properties

plugins {
    id("com.android.application") version "9.3.0"
    id("com.android.library") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

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
        versionCode = 8
        versionName = "2.0.1"
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
        debug {
            // CI/device-test APKs go through the same R8 code + resource optimization
            // path as release, so shrinker regressions are caught before publishing.
            optimization {
                enable = true
            }
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        disable += listOf("BlockedPrivateApi", "SoonBlockedPrivateApi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":prismal"))

    // API 101 is provided by the Xposed framework inside hooked processes.
    compileOnly("io.github.libxposed:api:101.0.1")
    // The module app uses the companion service binder to read/write Remote Preferences.
    implementation("io.github.libxposed:service:101.0.0")

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
