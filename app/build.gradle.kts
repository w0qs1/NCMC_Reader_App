import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.w0qs1.ncmcreader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.w0qs1.ncmcreader"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    base.archivesName.set("ncmc_reader")

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val downloadNcmcDbTask = tasks.register("downloadNcmcDb") {
    description = "Downloads the latest ncmc.db from GitHub repository"
    group = "build"

    val targetFile = file("src/main/assets/ncmc.db")
    val dbUrl = "https://raw.githubusercontent.com/w0qs1/NCMC_IDs/main/data/ncmc.db"

    outputs.file(targetFile)

    doLast {
        logger.lifecycle("Fetching latest ncmc.db from $dbUrl...")
        try {
            targetFile.parentFile.mkdirs()
            val url = URI(dbUrl).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                logger.lifecycle("Successfully updated ncmc.db (${targetFile.length()} bytes)")
            } else {
                logger.warn("Failed to download ncmc.db from GitHub: HTTP ${connection.responseCode}. Keeping local copy.")
            }
        } catch (e: Exception) {
            logger.warn("Could not fetch ncmc.db from GitHub (${e.message}). Keeping local copy.")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadNcmcDbTask)
}