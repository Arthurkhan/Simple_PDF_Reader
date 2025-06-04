# Simple PDF Reader - Phase 1 Implementation Guide

## Phase 1: Foundation Modernization - Detailed Implementation

This document provides step-by-step implementation details for Phase 1 of the improvement roadmap.

### 1.1 Dependency Updates

#### Step 1: Update Project-Level build.gradle

```gradle
// build.gradle (Project level)
buildscript {
    ext.kotlin_version = '2.0.21'
}

plugins {
    id 'com.android.application' version '8.7.0' apply false
    id 'org.jetbrains.kotlin.android' version '2.0.21' apply false
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

#### Step 2: Update App-Level build.gradle

```gradle
// app/build.gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.simplepdf.reader'
    compileSdk 35

    defaultConfig {
        applicationId "com.simplepdf.reader"
        minSdk 28
        targetSdk 35
        versionCode 2
        versionName "1.1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            minifyEnabled false
            debuggable true
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = '17'
    }
    
    buildFeatures {
        viewBinding true
        buildConfig true
    }
}

dependencies {
    // AndroidX Core
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.2.0'
    
    // Lifecycle & ViewModel
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.7'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.8.7'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
    
    // Document handling
    implementation 'androidx.documentfile:documentfile:1.0.1'
    
    // PDF Viewer (temporary - will be replaced in 1.2)
    implementation 'com.github.barteksc:android-pdf-viewer:3.2.0-beta.1'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
}
```

### 1.2 PDF Library Migration Preparation

#### Research Summary: PDF Library Options

1. **Pdf-Viewer by afreakyelf** (Recommended)
   - Size: ~80KB (vs 16MB for current)
   - Features: Native PdfRenderer wrapper, Jetpack Compose support
   - Pros: Extremely lightweight, actively maintained, good performance
   - Cons: Less features than AndroidPdfViewer (no clickable links initially)

2. **Native PdfRenderer**
   - Size: 0KB (part of Android framework)
   - Features: Basic PDF rendering
   - Pros: No external dependencies, full control
   - Cons: More implementation work, no built-in UI

#### Migration Strategy

```kotlin
// Create abstraction layer for PDF rendering
interface PdfRenderer {
    suspend fun openPdf(uri: Uri): PdfDocument
    suspend fun renderPage(pageIndex: Int): Bitmap
    fun getPageCount(): Int
    fun close()
}

// Current implementation wrapper
class AndroidPdfViewerRenderer : PdfRenderer {
    // Wrap current AndroidPdfViewer logic
}

// Future implementation
class LightweightPdfRenderer : PdfRenderer {
    // Implement using Pdf-Viewer library
}
```

### 1.3 Build Configuration Optimization

#### ProGuard Rules Update

```pro
# app/proguard-rules.pro

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# PDF Libraries (temporary)
-keep class com.shockwave.**
-keep class com.github.barteksc.pdfviewer.** { *; }

# App specific
-keep class com.simplepdf.reader.** { *; }
```

#### Enable R8 Full Mode

```gradle
// gradle.properties
android.enableR8.fullMode=true
android.enableJetifier=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
kotlin.code.style=official
```

### 1.4 Code Quality Setup

#### Add Detekt for Static Analysis

```gradle
// build.gradle (Project level)
plugins {
    id 'io.gitlab.arturbosch.detekt' version '1.23.7' apply false
}

// app/build.gradle
apply plugin: 'io.gitlab.arturbosch.detekt'

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/detekt-config.yml")
}
```

#### Create detekt-config.yml

```yaml
# app/detekt-config.yml
build:
  maxIssues: 0
  excludeCorrectable: false

processors:
  active: true

console-reports:
  active: true

complexity:
  active: true
  LongMethod:
    threshold: 60
  ComplexMethod:
    threshold: 15
  TooManyFunctions:
    thresholdInFiles: 20
    thresholdInClasses: 20

style:
  active: true
  MagicNumber:
    active: false
  MaxLineLength:
    maxLineLength: 120
```

### 1.5 Gradle Wrapper Update

```bash
# Update Gradle wrapper
./gradlew wrapper --gradle-version 8.4.2
```

### 1.6 Version Catalog Setup (Optional but Recommended)

Create `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
coroutines = "1.8.1"
androidx-core = "1.13.1"
androidx-appcompat = "1.7.0"
material = "1.12.0"
lifecycle = "2.8.7"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
androidx-core = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "androidx-appcompat" }
material = { module = "com.google.android.material:material", version.ref = "material" }
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### 1.7 Initial Code Improvements

#### Update MainActivity for Better Lifecycle Handling

```kotlin
class MainActivity : AppCompatActivity() {
    
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        // Use lifecycle-aware components
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                hideSystemUI()
            }
        })
    }
    
    // Use repeatOnLifecycle for coroutines
    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe StateFlows here
            }
        }
    }
}
```

### Testing the Updates

1. Clean and rebuild the project
2. Run on minimum SDK device (API 28)
3. Run on latest SDK device (API 35)
4. Check APK size reduction
5. Verify all features work as before

### Rollback Plan

If issues arise:
1. Git revert to previous commit
2. Gradual dependency updates (one at a time)
3. Keep old build.gradle files as backup

### Next Steps

After completing Phase 1:
1. Create feature branches for Phase 2
2. Set up CI/CD pipeline
3. Begin architecture refactoring
4. Start PDF library migration tests

### Success Criteria

- [ ] All dependencies updated to latest stable
- [ ] Build completes without warnings
- [ ] APK size not increased
- [ ] All existing features working
- [ ] No performance degradation
- [ ] Code passes Detekt analysis

This completes the detailed implementation guide for Phase 1. Each step should be tested thoroughly before moving to the next phase.