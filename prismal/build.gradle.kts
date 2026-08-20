plugins {
    id("com.android.library") version "9.3.0"
}

android {
    namespace = "com.hellovoid.prismal"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
