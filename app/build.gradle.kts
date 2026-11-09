// AGP 9부터 Kotlin이 내장되어 kotlin-android 플러그인을 따로 쓰지 않는다
plugins {
    id("com.android.application")
}

android {
    namespace = "com.reservevolume"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reservevolume"
        minSdk = 26 // 갤럭시노트9 출시 버전 Android 8.1(API 27)보다 낮게
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
