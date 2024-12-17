import org.lineageos.generatebp.GenerateBpPlugin
import org.lineageos.generatebp.GenerateBpPluginExtension
import org.lineageos.generatebp.models.Module

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply {
    plugin<GenerateBpPlugin>()
}

android {
    namespace = "im.angry.openeuicc_deps"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api("androidx.core:core-ktx:1.15.0")
    api("androidx.appcompat:appcompat:1.7.0")
    api("com.google.android.material:material:1.12.0")
    api("androidx.constraintlayout:constraintlayout:2.2.1")
    //noinspection KtxExtensionAvailable
    api("androidx.preference:preference:1.2.1")
    api("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    api("androidx.lifecycle:lifecycle-service:2.8.7")
    api("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    api("androidx.cardview:cardview:1.0.0")
    api("androidx.viewpager2:viewpager2:1.1.0")
    api("androidx.datastore:datastore-preferences:1.1.3")
    api("com.journeyapps:zxing-android-embedded:4.3.0")
    api("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

configure<GenerateBpPluginExtension> {
    targetSdk.set(android.compileSdk!!)
    availableInAOSP.set { module: Module ->
        when {
            module.group == "androidx.datastore" -> false
            module.group.startsWith("androidx") -> true
            module.group == "com.google.android.material" -> true
            module.group.startsWith("org.jetbrains") -> true
            else -> false
        }
    }
}