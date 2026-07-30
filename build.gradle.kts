plugins {
    id("com.android.application")
}

android {
    namespace = "com.kiminonawa.betterdock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kiminonawa.betterdock"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))
}
