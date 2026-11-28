import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 업로드 키스토어(Play App Signing) — 저장소 밖(projects/keystores)에 두고 keystore.properties 로만 읽는다.
// 파일이 없으면 release 빌드는 서명 없이 만들어지고(스토어 업로드불가), 저장소에는 아무 키도 들어가지 않는다.
val keystorePropertiesFile: File? = listOf(
    rootProject.file("../keystores/keystore.properties"),
    rootProject.file("keystore.properties")
).firstOrNull { it.exists() }
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile != null) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.youngsu.scanpdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.youngsu.scanpdf"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.0"

        // AdMob 앱 ID — 기본(debug)은 Google 공식 테스트 ID, release 는 buildTypes 에서 실제 ID 로 바꾼다.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"

        // AdMob 광고 단위 ID — debug 는 Google 공식 테스트 단위, release 는 buildTypes 에서 실제 단위로 바꾼다.
        // (release dex 에 테스트 ID 문자열이 남지 않도록 상수가 아니라 빌드 설정으로 넣는다.)
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystorePropertiesFile != null) {
            create("release") {
                val storeFileName = keystoreProperties.getProperty("storeFile")
                val candidates = listOf(
                    File(storeFileName),
                    rootProject.file(storeFileName),
                    keystorePropertiesFile.parentFile.resolve(storeFileName)
                )
                storeFile = candidates.firstOrNull { it.exists() }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 배포 전 테스트용 설치 파일 — 패키지명은 그대로 둔다 (나중에 정식 버전과 같은 앱으로 인식)
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Play 스토어 업로드용 서명 — keystore.properties 가 있을 때만 붙는다.
            signingConfigs.findByName("release")?.let { signingConfig = it }

            // release 빌드는 실제 AdMob ID (스캔 PDF) 사용
            manifestPlaceholders["admobAppId"] = "ca-app-pub-4832055633016363~6067607403"
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-4832055633016363/7197369596\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"ca-app-pub-4832055633016363/2192454922\"")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric 이 받은 Android 런타임을 둘 홈 폴더 (없으면 "?" 로 잡혀 실패한다)
        unitTests.all { test ->
            test.systemProperty("user.home", "/workspace/.robolectric")
            test.systemProperty("robolectric.logging", "stdout")
            // Maven Central 이 이 환경에서 429 로 막혀 있어 Google 미러에서 받는다
            test.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)

    implementation(libs.play.services.ads)

    // 온디바이스 OCR — 한글·영문(라틴)·중국어·일본어 모델을 앱에 포함
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
