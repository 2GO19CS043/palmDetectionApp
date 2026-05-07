# palmDetectionApp
# Palm & Finger Detection Application

A native Android application that captures palm and finger images using custom camera with ML Kit pose detection, validates fingers against palm minutiae records, and saves images with proper metadata.

## 📱 Features

- **Custom Camera Implementation** using CameraX API with auto-focus and exposure control
- **Real-time Palm & Finger Detection** using Google ML Kit Pose Detection
- **Hand Side Detection** (Left/Right) - Requirement m
- **Dorsal Side Detection** for both palm and fingers with error messages - Requirements q, r
- **Finger Validation** against palm minutiae records - Requirement n
- **Cross-Person Detection** - Prevents scanning different person's fingers - Requirement o
- **Wrong Hand Detection** - Validates fingers belong to same hand - Requirement p
- **Luminosity Analysis** with automatic brightness adjustment - Requirement f
- **Blur Detection** before saving images - Requirement l
- **Image Quality Metrics** (brightness, blur score, focus distance) - Requirement t
- **Proper File Naming** as per specification - Requirement u
- **MVVM Architecture** with ViewModel and LiveData

## 🏗️ Architecture

The app follows **MVVM (Model-View-ViewModel)** architecture:
│ View │
│ (CameraFragment, ResultFragment, OverlayView) │
│ LiveData Observation
│ ViewModel │
│ (HandDetectionViewModel) │
│ Model │
│ (BlurDetector, LuminosityAnalyzer, FileStorageManager)│

## 📋 Requirements Covered

| Requirement | Description | Status |
|-------------|-------------|--------|
| a | Base activity with fragments & permissions | ✅ |
| b | Simple responsive UI/UX | ✅ |
| c | Runtime permissions | ✅ |
| d | Lightweight AI/ML Kit | ✅ |
| e | Custom camera with CameraX | ✅ |
| f | Luminosity analyzer | ✅ |
| g | Palm detection overlay | ✅ |
| h | Finger oval overlay | ✅ |
| i | Progress indicator (⅕) | ✅ |
| j | Save to "Finger Data" folder | ✅ |
| k | Auto-focus functionality | ✅ |
| l | Blur detection | ✅ |
| m | Left/right hand detection | ✅ |
| n | Finger validation with toast | ✅ |
| o | Cross-person mismatch | ✅ |
| p | Wrong hand mismatch | ✅ |
| q | Palm dorsal detection | ✅ |
| r | Finger dorsal detection | ✅ |
| s | Save camera metadata | ✅ |
| t | Post-capture metrics | ✅ |
| u | Correct file naming | ✅ |

## 🛠️ Tech Stack

- **Language**: Kotlin
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Architecture**: MVVM
- **Camera**: CameraX (1.3.0)
- **ML/AI**: Google ML Kit Pose Detection
- **Image Processing**: Custom blur detection (no OpenCV required)
- **Async**: Kotlin Coroutines
- **UI**: Material Design Components

## 📦 Dependencies

```gradle
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    
    // ML Kit Pose Detection
    implementation("com.google.mlkit:pose-detection:18.0.0-beta3")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta3")
    
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
