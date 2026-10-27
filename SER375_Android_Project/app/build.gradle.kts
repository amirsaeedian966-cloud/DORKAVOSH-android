plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ser375.drilling"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ser375.drilling"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Apache POI enables writing .xlsx files based on the bundled company template.
    implementation("org.apache.poi:poi-ooxml:5.2.5")
}
