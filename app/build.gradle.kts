import org.gradle.api.plugins.quality.Checkstyle

plugins {
    alias(libs.plugins.android.application)
    id("checkstyle")
}

val mt5GatewayBaseUrl = "https://tradeapp.ltd"
val binanceRestBaseUrl = "https://tradeapp.ltd/binance-rest/fapi/v1/klines"
val binanceWsBaseUrl = "wss://tradeapp.ltd/binance-ws/ws/"

android {
    namespace = "com.binance.monitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.binance.monitor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField(
            "String",
            "MT5_GATEWAY_BASE_URL",
            "\"${mt5GatewayBaseUrl.replace("\"", "\\\"")}\""
        )
        buildConfigField(
            "String",
            "BINANCE_REST_BASE_URL",
            "\"${binanceRestBaseUrl.replace("\"", "\\\"")}\""
        )
        buildConfigField(
            "String",
            "BINANCE_WS_BASE_URL",
            "\"${binanceWsBaseUrl.replace("\"", "\\\"")}\""
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

checkstyle {
    toolVersion = "10.17.0"
    isIgnoreFailures = false
}

tasks.register<Checkstyle>("auditCriticalCheckstyle") {
    description = "对本轮审计整改关键链路执行低误报静态检查。"
    group = "verification"
    configFile = rootProject.file("config/checkstyle/audit-critical.xml")
    setSource(
        files(
            "src/main/java/com/binance/monitor/service/MonitorService.java",
            "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
            "src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java",
            "src/main/java/com/binance/monitor/service/account/AccountHistoryRefreshGate.java",
            "src/main/java/com/binance/monitor/service/stream/V2StreamSequenceGuard.java",
            "src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java",
            "src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java",
            "src/main/java/com/binance/monitor/ui/launch/OverlayLaunchBridgeActivity.java",
            "src/main/java/com/binance/monitor/ui/account/AccountDeferredSnapshotRenderHelper.java",
            "src/main/java/com/binance/monitor/ui/account/session/AccountSessionRestoreHelper.java"
        )
    )
    classpath = files()
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named("check") {
    dependsOn("auditCriticalCheckstyle")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.core)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.process)
    implementation(libs.swiperefreshlayout)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
