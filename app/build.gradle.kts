import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.5"
}

android {
    namespace = "com.example.pleosaudioguide"
    useLibrary("android.car")
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleosaudioguide"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 오디(Odii) 인증키는 각 PC의 local.properties에만 둔다. 커밋하지 않는다.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val odiiKey = localProperties.getProperty("ODII_SERVICE_KEY", "").replace("\"", "")
        buildConfigField("String", "ODII_SERVICE_KEY", "\"$odiiKey\"")
        val quizUrl = localProperties.getProperty("QUIZ_API_URL", "http://10.0.2.2:8787/quiz")
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "QUIZ_API_URL", "\"$quizUrl\"")
        // 심사용 공통 토큰은 APK에서 추출 가능. OpenAI 키와 별개이며 서버에서 교체한다.
        val quizToken = localProperties.getProperty("QUIZ_ACCESS_TOKEN", "")
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "QUIZ_ACCESS_TOKEN", "\"$quizToken\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Node 22가 설치된 개발 PC에서 외부 API 호출 없는 중계 서버 테스트를 실행한다.
tasks.register<Exec>("testQuizServer") {
    group = "verification"
    workingDir(rootProject.file("quiz-server"))
    commandLine("node", "--test")
    mustRunAfter("connectedDebugAndroidTest")
}

dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    // 로컬 JVM 테스트에서 Android org.json 스텁 대신 실제 구현을 사용한다.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}