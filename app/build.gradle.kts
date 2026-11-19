plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mc.fuwuqi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mc.fuwuqi"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        // versionName 带 build 号（与 release tag v1.0-<run_number> 一致），安装后一眼确认是不是最新版
        versionName = "1.0-${System.getenv("GITHUB_RUN_NUMBER") ?: "1"}"
    }

    signingConfigs {
        create("lime") {
            storeFile = file("lime.keystore")
            storePassword = "lime123456"
            keyAlias = "lime"
            keyPassword = "lime123456"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lime")
        }
        release {
            signingConfig = signingConfigs.getByName("lime")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    androidResources {
        localeFilters += listOf("zh", "en")
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    // Compose Compiler 2.4.20 要求 classpath 上存在 Compose Runtime 才能完成编译阶段，
    // 即使本项目 UI 基于 View 系统、无 @Composable 代码。
    implementation("androidx.compose.runtime:runtime:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.json:json:20240303")
    implementation("org.apache.commons:commons-compress:1.26.2")
}
