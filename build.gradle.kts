plugins {
    id("com.android.application") version "9.3.0"
}

android {
    namespace = "com.hellovoid.betterdock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hellovoid.betterdock"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"
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
}

base {
    archivesName.set("BetterDock")
}
