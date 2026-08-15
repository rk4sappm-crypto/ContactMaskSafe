plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.ucpl.makookidslanguages"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.ucpl.makookidslanguages"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}
