import java.security.KeyStore as JavaKeyStore
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

plugins {
    id("com.android.application") version "9.2.1"
}

providers.environmentVariable("ARRIVAL_BUILD_DIR").orNull?.let {
    layout.buildDirectory.set(file(it))
}

val amapWebKey = providers.environmentVariable("ARRIVAL_AMAP_WEB_KEY").orElse("").get()
require(amapWebKey.matches(Regex("^[A-Za-z0-9_-]*$"))) {
    "ARRIVAL_AMAP_WEB_KEY contains unsupported characters"
}

val signingEnvironment = mapOf(
    "keystore" to providers.environmentVariable("ARRIVAL_UPLOAD_KEYSTORE").orNull,
    "storePassword" to providers.environmentVariable("ARRIVAL_UPLOAD_STORE_PASSWORD").orNull,
    "keyAlias" to providers.environmentVariable("ARRIVAL_UPLOAD_KEY_ALIAS").orNull,
    "keyPassword" to providers.environmentVariable("ARRIVAL_UPLOAD_KEY_PASSWORD").orNull,
)
val configuredSigningValues = signingEnvironment.values.count { !it.isNullOrBlank() }
require(configuredSigningValues == 0 || configuredSigningValues == signingEnvironment.size) {
    "Release signing is only partly configured. Set all four ARRIVAL_UPLOAD_* environment variables or none of them."
}
val hasReleaseSigning = configuredSigningValues == signingEnvironment.size

android {
    namespace = "com.example.arrivalalarm"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.navobell.arrivalalarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.4.3"
        buildConfigField("String", "AMAP_WEB_KEY", "\"$amapWebKey\"")
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.directories.clear()
            java.directories.add("src")
            res.directories.clear()
            res.directories.add("res")
        }
        getByName("test") {
            java.directories.clear()
            java.directories.add("test")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("releaseUpload") {
                storeFile = file(signingEnvironment.getValue("keystore")!!)
                storePassword = signingEnvironment.getValue("storePassword")
                keyAlias = signingEnvironment.getValue("keyAlias")
                keyPassword = signingEnvironment.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        htmlReport = true
        sarifReport = true
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events("failed", "skipped", "passed")
            }
        }
    }
}

val bundletool by configurations.creating

dependencies {
    testImplementation("junit:junit:4.13.2")
    bundletool("com.android.tools.build:bundletool:1.18.3")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.register<JavaExec>("validateReleaseBundle") {
    group = "verification"
    description = "Validates the generated release Android App Bundle with bundletool."
    dependsOn("bundleRelease")
    classpath = bundletool
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    doFirst {
        val bundles = fileTree(layout.buildDirectory.dir("outputs/bundle/release")) {
            include("*.aab")
        }.files
        require(bundles.size == 1) {
            "Expected one release AAB, found ${bundles.size}."
        }
        args("validate", "--bundle=${bundles.single().absolutePath}")
    }
}

tasks.register<Exec>("verifyReleaseBundleSignature") {
    group = "verification"
    description = "Fails unless the release bundle has a valid JAR signature."
    dependsOn("bundleRelease")
    doFirst {
        require(hasReleaseSigning) {
            "Release signing is not configured. Set the four ARRIVAL_UPLOAD_* environment variables."
        }
        val bundles = fileTree(layout.buildDirectory.dir("outputs/bundle/release")) {
            include("*.aab")
        }.files
        require(bundles.size == 1) {
            "Expected one release AAB, found ${bundles.size}."
        }

        val keyStore = JavaKeyStore.getInstance(
            file(signingEnvironment.getValue("keystore")!!),
            signingEnvironment.getValue("storePassword")!!.toCharArray(),
        )
        val certificate = keyStore.getCertificate(signingEnvironment.getValue("keyAlias")!!)
            as? X509Certificate
            ?: error("Upload key alias does not contain an X.509 certificate.")
        require(!certificate.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true)) {
            "The release bundle must not use an Android Debug certificate."
        }

        val entryNames = ZipFile(bundles.single()).use { zip ->
            zip.entries().asSequence().map { it.name.uppercase() }.toList()
        }
        require(entryNames.any { it.startsWith("META-INF/") && it.endsWith(".SF") }) {
            "The release AAB does not contain a signature file."
        }
        require(entryNames.any {
            it.startsWith("META-INF/") &&
                (it.endsWith(".RSA") || it.endsWith(".DSA") || it.endsWith(".EC"))
        }) {
            "The release AAB does not contain a cryptographic signature block."
        }
        commandLine("jarsigner", "-verify", "-certs", bundles.single().absolutePath)
    }
}

tasks.register("verifyPlayBundle") {
    group = "verification"
    description = "Validates a signed AAB before a human uploads it to Google Play."
    dependsOn("validateReleaseBundle", "verifyReleaseBundleSignature")
}
