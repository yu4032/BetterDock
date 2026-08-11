plugins {
    id("com.android.application") version "9.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

android {
    namespace = "com.hellovoid.liquiddock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hellovoid.liquiddock"
        minSdk = 33
        targetSdk = 37
        versionCode = 3
        versionName = "3.0"
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
