import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun localOrEnv(fileKey: String, envKey: String): String {
    val fromEnv = System.getenv(envKey)?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    val f = rootProject.file("local.properties")
    if (!f.exists()) return ""
    return f.readLines()
        .firstOrNull { it.trim().startsWith("$fileKey=") }
        ?.substringAfter("=")
        ?.trim()
        ?.trim('"')
        ?: ""
}

val amapKey = localOrEnv("amap.key", "AMAP_KEY")
val buglyAppId = localOrEnv("bugly.appId", "BUGLY_APP_ID")
val miAppId = localOrEnv("mipush.appId", "MIPUSH_APP_ID")
val miAppKey = localOrEnv("mipush.appKey", "MIPUSH_APP_KEY")
val oppoAppKey = localOrEnv("oppo.appKey", "OPPO_APP_KEY")
val oppoAppSecret = localOrEnv("oppo.appSecret", "OPPO_APP_SECRET")
val vivoAppId = localOrEnv("vivo.appId", "VIVO_APP_ID")
val vivoAppKey = localOrEnv("vivo.appKey", "VIVO_APP_KEY")
val meizuAppId = localOrEnv("meizu.appId", "MEIZU_APP_ID")
val meizuAppKey = localOrEnv("meizu.appKey", "MEIZU_APP_KEY")
val huaweiAppId = localOrEnv("huawei.appId", "HUAWEI_APP_ID")
val honorAppId = localOrEnv("honor.appId", "HONOR_APP_ID")

android {
    namespace = "cn.edu.swun.swun_ehall"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "cn.edu.swun.swun_ehall"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
        buildConfigField("String", "BUGLY_APP_ID", "\"$buglyAppId\"")
        buildConfigField("String", "MI_APP_ID", "\"$miAppId\"")
        buildConfigField("String", "MI_APP_KEY", "\"$miAppKey\"")
        buildConfigField("String", "OPPO_APP_KEY", "\"$oppoAppKey\"")
        buildConfigField("String", "OPPO_APP_SECRET", "\"$oppoAppSecret\"")
        buildConfigField("String", "VIVO_APP_ID", "\"$vivoAppId\"")
        buildConfigField("String", "VIVO_APP_KEY", "\"$vivoAppKey\"")
        buildConfigField("String", "MEIZU_APP_ID", "\"$meizuAppId\"")
        buildConfigField("String", "MEIZU_APP_KEY", "\"$meizuAppKey\"")
        buildConfigField("String", "HUAWEI_APP_ID", "\"$huaweiAppId\"")
        buildConfigField("String", "HONOR_APP_ID", "\"$honorAppId\"")
        manifestPlaceholders["AMAP_KEY"] = amapKey
        manifestPlaceholders["VIVO_APP_ID"] = vivoAppId.ifEmpty { "0" }
        manifestPlaceholders["VIVO_APP_KEY"] = vivoAppKey.ifEmpty { "0" }
        manifestPlaceholders["HUAWEI_APP_ID"] = if (huaweiAppId.isEmpty()) "appid=0" else "appid=$huaweiAppId"
        manifestPlaceholders["HONOR_APP_ID"] = honorAppId.ifEmpty { "0" }
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        val upload = signingConfigs.getByName("release")
        val useUpload = upload.storeFile != null && upload.storeFile!!.exists()
        debug {
            if (useUpload) signingConfig = upload
        }
        release {
            signingConfig =
                if (useUpload) upload else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.8")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4-rc01")
    implementation("dev.chrisbanes.haze:haze:2.0.0-rc02")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.0-rc02")
    implementation("dev.chrisbanes.haze:haze-glass:2.0.0-rc02")
    // Latest combined map SDK. It already contains location classes, so do not add com.amap.api:location beside it.
    implementation("com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1")
    implementation(files("libs/MiPush_SDK_Client_6_0_1-C.jar"))
    implementation(files("libs/com.heytap.msp_3.1.0.aar"))
    implementation(files("libs/vivo-push-open.jar", "libs/vivo-push-open-build.jar"))
    implementation("com.huawei.hms:push:6.13.0.301")
    implementation("com.hihonor.mcs:push:10.0.39.302")
    implementation("com.meizu.flyme.internet:push-internal:5.0.3")
    implementation("com.tencent.bugly:crashreport:4.1.9")
    implementation("com.tencent.bugly:nativecrashreport:3.9.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
