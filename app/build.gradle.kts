plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.cluster.facelabs.clusterface"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cluster.facelabs.clusterface"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes.add("META-INF/proguard/androidx-annotations.pro")
        }
    }

    androidResources {
        noCompress.add("tflite")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.mlkit.face)
    implementation(libs.tflite)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    implementation(libs.commons.io)
    implementation(libs.commons.math3)
}