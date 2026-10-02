import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Prefer environment variables; Gradle properties may be kept in the user's
// global ~/.gradle/gradle.properties. Never put signing secrets in this project.
val releaseStoreFile = providers.gradleProperty("LISTALOCAL_KEYSTORE_PATH")
    .orElse(providers.environmentVariable("LISTALOCAL_KEYSTORE_PATH"))
val releaseStorePassword = providers.gradleProperty("LISTALOCAL_STORE_PASSWORD")
    .orElse(providers.environmentVariable("LISTALOCAL_STORE_PASSWORD"))
val releaseKeyAlias = providers.gradleProperty("LISTALOCAL_KEY_ALIAS")
    .orElse(providers.environmentVariable("LISTALOCAL_KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("LISTALOCAL_KEY_PASSWORD")
    .orElse(providers.environmentVariable("LISTALOCAL_KEY_PASSWORD"))
val releaseSigningSettings = mapOf(
    "LISTALOCAL_KEYSTORE_PATH" to releaseStoreFile,
    "LISTALOCAL_STORE_PASSWORD" to releaseStorePassword,
    "LISTALOCAL_KEY_ALIAS" to releaseKeyAlias,
    "LISTALOCAL_KEY_PASSWORD" to releaseKeyPassword,
)
val releaseSigningConfigured = releaseSigningSettings.values.all { !it.orNull.isNullOrBlank() }

android {
    namespace = "com.listalocal"
    compileSdk = 35

    // Mesmo pacote e assinatura da versao padrao. A data e imposta no runtime.
    val expira04102026 = providers.gradleProperty("expira04102026")
        .map { it.equals("true", ignoreCase = true) }.getOrElse(false)

    defaultConfig {
        // Pacote proprio: instala ao lado do Lista Local (com.listalocal) e do app do
        // Codex, que usa com.listalocal.instagram. O namespace do codigo continua.
        applicationId = "com.listalocal.instagram.claude"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = if (expira04102026) "0.2.2-expira-2026-10-04" else "0.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "EXPIRA_04102026", expira04102026.toString())
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Checks release signing settings before producing a distributable APK."
    doLast {
        val missing = releaseSigningSettings.filterValues { it.orNull.isNullOrBlank() }.keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured. Set environment variables or global " +
                    "Gradle properties: ${missing.joinToString()}"
            )
        }
        if (!file(releaseStoreFile.get()).isFile) {
            throw GradleException("Release keystore file does not exist at LISTALOCAL_KEYSTORE_PATH")
        }
    }
}

tasks.matching {
    it.name == "packageRelease" || it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Sem as bibliotecas do WhatsApp (Room, libphonenumber, security-crypto), o Gradle
    // escolheria kotlin-stdlib-jdk7/jdk8 1.8.20, que nao esta no cache desta maquina.
    // Fixar a versao ja baixada mantem o build offline (desde o Kotlin 1.8 os dois
    // modulos sao vazios: o conteudo esta no kotlin-stdlib).
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Testes de UI (Compose). Exigem aparelho/emulador para rodar.
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

/**
 * Gate de privacidade: falha o build se a permissao INTERNET (ou qualquer
 * permissao de rede) aparecer no manifesto MESCLADO de qualquer variante.
 * O produto e local-first e nao pode acessar a internet.
 */
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
)

tasks.register("assertNoInternetPermission") {
    description = "Falha se qualquer permissao de rede estiver no manifesto mesclado."
    group = "verification"
    dependsOn("processDebugManifest", "processReleaseManifest")
    doLast {
        val manifests = fileTree(layout.buildDirectory) {
            include("**/AndroidManifest.xml")
            exclude("**/tmp/**/aapt/**")
        }
        val offenders = mutableListOf<String>()
        manifests.forEach { mf ->
            // Ignora comentarios XML (que citam as permissoes so para documentar o gate).
            val text = mf.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            forbiddenPermissions.forEach { perm ->
                val declares = Regex("""uses-permission[^>]*android:name="$perm"""").containsMatchIn(text)
                if (declares) offenders += "${mf.path}: $perm"
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Permissao de rede proibida encontrada no manifesto:\n" +
                    offenders.joinToString("\n")
            )
        }
        logger.lifecycle("OK: nenhuma permissao de rede nos manifestos mesclados.")
    }
}

tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }
    .configureEach { finalizedBy("assertNoInternetPermission") }
