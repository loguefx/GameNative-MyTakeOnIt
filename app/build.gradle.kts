import java.util.Properties
import java.io.File
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.serialization)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.ksp)
    alias(libs.plugins.secrets.gradle)
    alias(libs.plugins.room)
}

val keystorePropertiesFile = rootProject.file("app/keystores/keystore.properties")
val keystoreProperties: Properties? = if (keystorePropertiesFile.exists()) {
    Properties().apply {
        load(FileInputStream(keystorePropertiesFile))
    }
} else null

// Add PostHog API key and host as build-time variables
val posthogApiKey: String = project.findProperty("POSTHOG_API_KEY") as String? ?: System.getenv("POSTHOG_API_KEY") ?: ""
val posthogHost: String = project.findProperty("POSTHOG_HOST") as String? ?: System.getenv("POSTHOG_HOST") ?: "https://us.i.posthog.com"

// Add Supabase URL and key as build-time variables
val supabaseUrl: String = project.findProperty("SUPABASE_URL") as String? ?: System.getenv("SUPABASE_URL") ?: "https://your-project.supabase.co"
val supabaseKey: String = project.findProperty("SUPABASE_KEY") as String? ?: System.getenv("SUPABASE_KEY") ?: ""

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "app.gamenative"
    compileSdk = 35

    // https://developer.android.com/ndk/downloads
    ndkVersion = "22.1.7171670"

    signingConfigs {
        create("pluvia") {
            if (keystoreProperties != null) {
                storeFile = file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    defaultConfig {
        applicationId = "app.gamenative"

        minSdk = 26
        targetSdk = 28

        versionCode = 11
        versionName = "0.7.2"

        buildConfigField("boolean", "GOLD", "false")
        // #region agent log
        val debugLog = File(project.rootProject.layout.projectDirectory.asFile, "debug-6ad579.log")
        debugLog.writeText("") // ensure file exists and is cleared at config start
        debugLog.appendText("""{"sessionId":"6ad579","runId":"buildConfig","hypothesisId":"H0","location":"app/build.gradle.kts","message":"defaultConfig block entered","data":{"rootDir":"${project.rootProject.layout.projectDirectory.asFile.absolutePath}"},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        fun logBuildConfig(key: String, passedValue: String, hypothesisId: String) {
            val preview = passedValue.take(80).replace("\\", "\\\\").replace("\"", "\\\"")
            val entry = """{"sessionId":"6ad579","runId":"buildConfig","hypothesisId":"$hypothesisId","location":"app/build.gradle.kts","message":"buildConfigField value","data":{"key":"$key","passedLength":${passedValue.length},"passedPreview":"$preview","rawFromFindProperty":${project.findProperty(key) != null},"rawFromEnv":${System.getenv(key) != null}},"timestamp":${System.currentTimeMillis()}}""" + "\n"
            debugLog.appendText(entry)
        }
        // #endregion
        fun secret(name: String): String {
            val raw = project.findProperty(name) as String? ?: System.getenv(name) ?: ""
            return when (name) {
                "POSTHOG_HOST" -> if (raw.isBlank()) "https://us.i.posthog.com" else raw
                "SUPABASE_URL", "SUPABASE_KEY" -> raw
                else -> raw
            }
        }
        fun escapeForBuildConfig(s: String): String = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
        run {
            val v = "\"${escapeForBuildConfig(secret("POSTHOG_API_KEY"))}\""
            logBuildConfig("POSTHOG_API_KEY", v, "H1")
            buildConfigField("String", "POSTHOG_API_KEY", v)
        }
        run {
            val v = "\"${escapeForBuildConfig(secret("POSTHOG_HOST"))}\""
            logBuildConfig("POSTHOG_HOST", v, "H1")
            buildConfigField("String", "POSTHOG_HOST", v)
        }
        run {
            val v = "\"${escapeForBuildConfig(secret("SUPABASE_URL"))}\""
            logBuildConfig("SUPABASE_URL", v, "H2")
            buildConfigField("String", "SUPABASE_URL", v)
        }
        run {
            val v = "\"${escapeForBuildConfig(secret("SUPABASE_KEY"))}\""
            logBuildConfig("SUPABASE_KEY", v, "H2")
            buildConfigField("String", "SUPABASE_KEY", v)
        }
        run {
            val v = "\"${escapeForBuildConfig(secret("STEAMGRIDDB_API_KEY"))}\""
            logBuildConfig("STEAMGRIDDB_API_KEY", v, "H1")
            buildConfigField("String", "STEAMGRIDDB_API_KEY", v)
        }
        val iconValue = "@mipmap/ic_launcher"
        val iconRoundValue = "@mipmap/ic_launcher_round"
        manifestPlaceholders.putAll(
            mapOf(
                "icon" to iconValue,
                "roundIcon" to iconRoundValue,
            ),
        )

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        // Localization support - specify which languages to include
        resourceConfigurations += listOf(
            "en",      // English (default)
            "es",      // Spanish
            "da",      // Danish
            "pt-rBR",  // Portuguese (Brazilian)
            "zh-rTW",  // Traditional Chinese
            "zh-rCN",  // Simplified Chinese
            "fr",      // French
            "de",      // German
            "uk",      // Ukrainian
            "it",      // Italian
            "ro",      // Română
            "pl",      // Polish
            // TODO: Add more languages here using the ISO 639-1 locale code with regional qualifiers (e.g., "pt-rPT" for European Portuguese)
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        proguardFiles(
            // getDefaultProguardFile("proguard-android-optimize.txt"),
            getDefaultProguardFile("proguard-android.txt"),
            "proguard-rules.pro",
        )
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
        create("release-signed") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("pluvia")
        }
        create("release-gold") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("pluvia")
            applicationIdSuffix = ".gold"
            buildConfigField("boolean", "GOLD", "true")
            val iconValue = "@mipmap/ic_launcher_gold"
            val iconRoundValue = "@mipmap/ic_launcher_gold_round"
            manifestPlaceholders.putAll(
                mapOf(
                    "icon" to iconValue,
                    "roundIcon" to iconRoundValue,
                ),
            )
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/DebugProbesKt.bin"
            excludes += "/junit/runner/smalllogo.gif"
            excludes += "/junit/runner/logo.gif"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            // 'extractNativeLibs' was not enough to keep the jniLibs and
            // the libs went missing after adding on-demand feature delivery
            useLegacyPackaging = true
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    dynamicFeatures += setOf(":ubuntufs")

    kotlinter {
        ignoreFormatFailures  = false
    }

    // build extras needed in libwinlator_bionic.so
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/extras/CMakeLists.txt")   // the file shown above
    //         version = "3.22.1"
    //     }
    // }

    // cmake on release builds a proot that fails to process ld-2.31.so
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }

    // (For now) Uncomment for LeakCanary to work.
    // configurations {
    //     debugImplementation {
    //         exclude(group = "junit", module = "junit")
    //     }
    // }
}

dependencies {
    implementation(libs.material)

    // Chrome Custom Tabs for GOG OAuth
    implementation("androidx.browser:browser:1.8.0")

    // JavaSteam
    val localBuild = false // Change to 'true' needed when building JavaSteam manually
    if (localBuild) {
        implementation(files("../../JavaSteam/build/libs/javasteam-1.8.0-11-SNAPSHOT.jar"))
        implementation(files("../../JavaSteam/javasteam-depotdownloader/build/libs/javasteam-depotdownloader-1.8.0-11-SNAPSHOT.jar"))
        implementation(libs.bundles.javasteam.dev)
    } else {
        implementation(libs.javasteam) {
            isChanging = version?.contains("SNAPSHOT") ?: false
        }
        implementation(libs.javasteam.depotdownloader) {
            isChanging = version?.contains("SNAPSHOT") ?: false
        }
    }
    implementation(libs.spongycastle)

    // Split Modules
    implementation(libs.bundles.google)

    // Winlator
    implementation(libs.bundles.winlator)
    implementation(libs.zstd.jni) { artifact { type = "aar" } }
    implementation(libs.xz)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.landscapist.coil)
    debugImplementation(libs.androidx.ui.tooling)

    // Support
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.apng)
    implementation(libs.datastore.preferences)
    implementation(libs.jetbrains.kotlinx.json)
    implementation(libs.kotlin.coroutines)
    implementation(libs.timber)
    implementation(libs.zxing)

    // Google Protobufs
    implementation(libs.protobuf.java)

    // Hilt
    implementation(libs.bundles.hilt)

    // KSP (Hilt, Room)
    ksp(libs.bundles.ksp)

    // Room Database
    implementation(libs.bundles.room)

    // Memory Leak Detection
    // debugImplementation("com.squareup.leakcanary:leakcanary-android:3.0-alpha-8")

    // Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.zstd.jni)
    testImplementation(libs.orgJson)
    testImplementation(libs.mockwebserver)

    // Add PostHog Android SDK dependency
    implementation("com.posthog:posthog-android:3.8.0")

    // 1) import the platform – it pins *every* Supabase + Ktor module
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))

    // 2) add whichever supabase-kt modules you actually use
    implementation("io.github.jan-tennert.supabase:postgrest-kt")   // PostgREST
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    implementation("io.ktor:ktor-client-android:3.1.3")

    implementation("com.auth0.android:jwtdecode:2.0.2")
}

// #region agent log – task so CI can run it and get debug-6ad579.log in workspace
tasks.register("writeBuildConfigDebugLog") {
    doLast {
        val outputDir = (project.findProperty("debugLogDir") as String?)?.let { File(it) }
            ?: project.rootProject.layout.projectDirectory.asFile
        val logFile = File(outputDir, "debug-6ad579.log")
        fun secret(name: String): String {
            val raw = project.findProperty(name) as String? ?: System.getenv(name) ?: ""
            return when (name) {
                "POSTHOG_HOST" -> if (raw.isBlank()) "https://us.i.posthog.com" else raw
                "SUPABASE_URL", "SUPABASE_KEY" -> raw
                else -> raw
            }
        }
        fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
        val keys = listOf("POSTHOG_API_KEY", "POSTHOG_HOST", "SUPABASE_URL", "SUPABASE_KEY", "STEAMGRIDDB_API_KEY")
        logFile.writeText("")
        logFile.appendText("""{"sessionId":"6ad579","runId":"task","hypothesisId":"H0","message":"writeBuildConfigDebugLog task ran","data":{"outputDir":"${logFile.parentFile.absolutePath}"},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        for (key in keys) {
            val v = "\"${escape(secret(key))}\""
            val preview = v.take(80).replace("\\", "\\\\").replace("\"", "\\\"")
            logFile.appendText("""{"sessionId":"6ad579","runId":"task","hypothesisId":"H2","message":"buildConfigField value","data":{"key":"$key","passedLength":${v.length},"passedPreview":"$preview","rawFromFindProperty":${project.findProperty(key) != null},"rawFromEnv":${System.getenv(key) != null}},"timestamp":${System.currentTimeMillis()}}""" + "\n")
        }
    }
}
// #endregion
