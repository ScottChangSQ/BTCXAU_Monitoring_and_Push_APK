plugins {
    alias(libs.plugins.android.application)
}

val mt5GatewayBaseUrl = (
    project.findProperty("MT5_GATEWAY_BASE_URL") as String?
)?.trim()?.takeIf { it.isNotEmpty() } ?: "http://10.0.2.2:8787"
val binanceRestBaseUrl = (
    project.findProperty("BINANCE_REST_BASE_URL") as String?
)?.trim()?.takeIf { it.isNotEmpty() } ?: "https://fapi.binance.com/fapi/v1/klines"
val binanceWsBaseUrl = (
    project.findProperty("BINANCE_WS_BASE_URL") as String?
)?.trim()?.takeIf { it.isNotEmpty() } ?: "wss://fstream.binance.com/ws/"

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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.swiperefreshlayout)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation("junit:junit:4.13.2")
}
