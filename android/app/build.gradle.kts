import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
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

android {
    namespace = "cn.edu.swun.swun_ehall"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "cn.edu.swun.swun_ehall"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
        buildConfigField("String", "BUGLY_APP_ID", "\"$buglyAppId\"")
        manifestPlaceholders["AMAP_KEY"] = amapKey
        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
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
        release {
            val releaseStore = signingConfigs.getByName("release").storeFile
            signingConfig =
                if (releaseStore != null && releaseStore.exists()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("com.amap.api:location:6.5.1")
    implementation("com.tencent.bugly:crashreport:4.1.9")
    implementation("com.tencent.bugly:nativecrashreport:3.9.2")
}
