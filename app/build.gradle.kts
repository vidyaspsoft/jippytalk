import org.gradle.kotlin.dsl.androidTestImplementation
import org.gradle.kotlin.dsl.coreLibraryDesugaring
import org.gradle.kotlin.dsl.implementation
import org.gradle.kotlin.dsl.testImplementation

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jippytalk"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.jippytalk"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Ship English-only resources. Adding more languages later is just a
    // matter of appending them here ("hi", "es", ...). Drops a couple of MB
    // of bundled translations from AppCompat / Material / etc. (AGP 9+ moved
    // this from defaultConfig.resourceConfigurations to androidResources.)
    androidResources {
        localeFilters += listOf("en")
    }
    dataBinding{
        enable=true
    }

   viewBinding{
       enable=true
   }


    buildTypes {
        release {
            // R8 strips unused classes / methods / fields from the app and
            // every library. Combined with isShrinkResources this typically
            // halves APK size for an app with libsignal + Firebase + Maps.
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign release builds with the debug key so the test APK
            // installs on the client's phone without needing a release
            // keystore. Replace with a real signingConfigs.release before
            // shipping to the Play Store.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep debug builds fast — no shrinking.
            isMinifyEnabled = false
        }
    }

    // Per-ABI APK splits. Produces a separate, smaller APK per architecture
    // (arm64-v8a-release.apk + armeabi-v7a-release.apk). Hand the arm64-v8a
    // file to the client — it works on essentially every modern phone.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    // Drop META-INF noise that some libraries duplicate across JARs and
    // that bloats the APK without serving the runtime.
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment:2.9.6")
    implementation("androidx.navigation:navigation-ui:2.9.6")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.activity:activity:1.12.2")
    implementation("com.google.firebase:firebase-crashlytics:20.0.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.1.20"))

    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.work:work-runtime:2.11.0")

    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.22")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    implementation("com.google.android.gms:play-services-maps:20.0.0")

    implementation("org.signal:libsignal-android:0.86.5")

    implementation("com.android.volley:volley:1.2.1")

    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("com.google.firebase:firebase-messaging:25.0.1")

    implementation(platform("com.google.firebase:firebase-bom:34.8.0"))

    implementation("androidx.lifecycle:lifecycle-process:2.10.0")


    implementation("com.github.yalantis:ucrop:2.2.11")

    // Add the dependencies for the Crashlytics and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    // For Paging 3 (Jetpack Paging)

    implementation("net.zetetic:sqlcipher-android:4.12.0")
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.google.android.gms:play-services-auth:21.5.0")
}