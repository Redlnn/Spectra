plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

android {
    namespace = "top.redlnn.spectra"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "top.redlnn.spectra"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notClass"] = "top.redlnn.spectra.AnalyzerEnduranceTest"
    }

    buildTypes {
        release {
            // Locally installable preview. Replace with an owned release key before publication.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.androidx.datastore)
    implementation(libs.miuix)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val modules =
        variant.runtimeConfiguration.incoming.resolutionResult.rootComponent.map { root ->
            val visited = mutableSetOf<org.gradle.api.artifacts.component.ComponentIdentifier>()
            val pending = ArrayDeque<org.gradle.api.artifacts.result.ResolvedComponentResult>()
            val coordinates = mutableListOf<String>()
            pending.add(root)
            while (pending.isNotEmpty()) {
                val component = pending.removeFirst()
                if (!visited.add(component.id)) continue
                (component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                    ?.let {
                        coordinates.add("${it.group}\t${it.module}\t${it.version}")
                    }
                component.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                    .forEach {
                        pending.add(it.selected)
                    }
            }
            coordinates.sorted()
        }
    tasks.register("writeRuntimeModules") {
        val destination = layout.buildDirectory.file("reports/runtime-modules.tsv")
        inputs.property("modules", modules)
        outputs.file(destination)
        doLast {
            destination.get().asFile.apply {
                parentFile.mkdirs()
                writeText(modules.get().joinToString("\n"))
            }
        }
    }
}
