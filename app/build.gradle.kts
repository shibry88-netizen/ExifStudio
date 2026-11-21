import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lucifer.exifstudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lucifer.exifstudio"
        minSdk = 29
        // Android 16/17 对新安装应用的 targetSdk 有下限要求，这里跟随当年平台
        targetSdk = 36
        versionCode = 7
        versionName = "1.3.2"
        vectorDrawables { useSupportLibrary = true }
    }

    /**
     * 发布签名配置。
     * CI 在构建前把 keystore/release.jks.b64 解码成 keystore/release.jks（见 workflow），
     * 本地构建时只要该文件存在就会自动签名。
     * 证书一旦固定，后续版本可覆盖安装，不需要卸载重装。
     */
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore/signing.properties")
            if (propsFile.exists()) {
                val props = Properties()
                propsFile.inputStream().use { props.load(it) }
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
            val ks = rootProject.file("keystore/release.jks")
            if (ks.exists()) {
                storeFile = ks
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) {
                signingConfig = rel
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
        // Android 15+ 的 16 KB 页大小设备要求原生库按 16 KB 对齐
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    /**
     * 注意：lifecycle 必须与 Compose 版本匹配。
     * lifecycle-runtime-compose 2.8.x 的 collectAsStateWithLifecycle() 依赖
     * Compose 1.7 新增的 androidx.lifecycle.compose.LocalLifecycleOwner，
     * 而 Compose BOM 2024.06 的 compose-ui 是 1.6.8，没人提供那个 CompositionLocal，
     * 一读就抛 "CompositionLocal LocalLifecycleOwner not present"。
     * 因此这里锁定 2.7.0，并在 MainActivity 中改用 collectAsState()。
     */
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
