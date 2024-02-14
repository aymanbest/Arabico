import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.0.4")
        // Cloudstream gradle plugin which makes everything work and builds plugin
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
    }
}


allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
    }

    android {
        compileSdkVersion(30)

        defaultConfig {
            minSdk = 21
            targetSdk = 30
        }

        lintOptions {
            isAbortOnError = false
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8" // Required
                // Disables some unnecessary features
                freeCompilerArgs = freeCompilerArgs +
                        "-Xno-call-assertions" +
                        "-Xno-param-assertions" +
                        "-Xno-receiver-assertions" +
                        "-Xskip-metadata-version-check"
            }
        }
    }

    dependencies {

        val apkTasks = listOf("deployWithAdb", "build")
        val useApk = gradle.startParameter.taskNames.any { taskName ->
            apkTasks.any { apkTask ->
                taskName.contains(apkTask, ignoreCase = true)
            }
        }

        val apk by configurations
        val implementation by configurations

        // Stubs for all Cloudstream classes
       //apk("com.lagradost:cloudstream3:pre-release")

        // these dependencies can include any of those which are added by the app,
        // but you dont need to include any of them if you dont need them
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle
        if (useApk) {
            // Stubs for all Cloudstream classes
            apk("com.lagradost:cloudstream3:pre-release")
        } else {
            // For running locally
           implementation("com.github.Blatzar:CloudstreamApi:0.1.6")
        }
        implementation(kotlin("stdlib")) // adds standard kotlin features, like listOf, mapOf etc
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // http library
        implementation("org.jsoup:jsoup:1.17.2") // html parser
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        implementation("io.ktor:ktor-client-cio:2.3.7")
		implementation("org.mozilla:rhino:1.7.14")
        implementation("commons-codec:commons-codec:1.15")
        implementation("org.bouncycastle:bcpkix-jdk15on:1.68")
        implementation("org.graalvm.js:js:21.3.0")
        implementation("org.graalvm.js:js-scriptengine:21.3.0")
        implementation("org.json:json:20231013")
        implementation("com.google.code.gson:gson:2.10")
        dependencies {
            implementation("org.jsoup:jsoup:1.17.2")
            implementation("org.bouncycastle:bcpkix-jdk15on:1.68")
        }
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
