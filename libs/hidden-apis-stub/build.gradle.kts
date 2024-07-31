plugins {
    id("com.android.library")
}

android {
    compileSdk = 31
    namespace = "im.angry.hidden.apis"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("org.jetbrains:annotations:23.0.0")
}