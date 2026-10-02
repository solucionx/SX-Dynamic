plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.solucionx.sxdynamic"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.solucionx.sxdynamic"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
}
