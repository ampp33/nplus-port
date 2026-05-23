plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project
val controllersVersion: String by project

val natives: Configuration by configurations.creating

android {
    namespace = "com.nplus"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nplus.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        assets.srcDirs("../assets")
        jniLibs.srcDirs("libs")
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86_64")
    implementation("com.badlogicgames.gdx-controllers:gdx-controllers-android:$controllersVersion")
}

tasks.register("copyAndroidNatives") {
    doFirst {
        val libsDir = file("libs")
        natives.files.forEach { jar ->
            // jar name: gdx-platform-1.12.1-natives-arm64-v8a.jar → arm64-v8a
            val abi = jar.nameWithoutExtension.substringAfter("natives-")
            val outputDir = File(libsDir, abi)
            outputDir.mkdirs()
            copy {
                from(zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}

tasks.configureEach {
    if (name == "preBuild") dependsOn("copyAndroidNatives")
}
