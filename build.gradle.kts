plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    setCompileSdkVersion(property("compileSdkVersion") as Int)

    defaultConfig {
        setMinSdkVersion(property("minSdkVersion") as Int)
        setTargetSdkVersion(property("targetSdkVersion") as Int)
        versionName = "1.8.0"
        versionNameSuffix = ""
    }

    buildTypes {
        get("release").consumerProguardFile("proguard-rules.pro")
    }
}

dependencies {
    val kotlinVersion = property("kotlinVersion") as String
    api("androidx.annotation:annotation:1.1.0")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
    api("com.otaliastudios.opengl:egloo:0.4.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

