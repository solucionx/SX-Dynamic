plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.solucionx.sxdynamic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.solucionx.sxdynamic"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = file("debug.keystore")
            storePassword = "sxdebug"
            keyAlias = "sxdebug"
            keyPassword = "sxdebug"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}
