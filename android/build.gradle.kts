plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.papergrimoire"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.papergrimoire"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resConfigs("en")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("/META-INF/*")
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")
    val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    for (abi in abis) {
        implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-$abi")
    }
}
