# KrishiVision AI 🌱
> **Smart Crop Assistance for Indian Farmers Powered by Gemma 4**

[![Category](https://img.shields.io/badge/MLH_Hackathon-Best_Use_of_Gemma_4-19A875?style=for-the-badge)](https://mlh.io)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-D4AF37?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-080D0B?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)

---

## 🌾 Overview

**KrishiVision AI** is a premium, AI-powered agricultural application designed to help farmers in India identify crop diseases, understand pest issues, and receive safe, actionable recommendations in **natural Hinglish**.

Built using **Kotlin** and **Jetpack Compose** with a luxury agricultural dark theme, the app directly integrates **Google's Gemma 4 model (`gemma-4-26b-a4b-it`)** to provide instant diagnostic insights across four key cards:
1. **Pehchani Gayi Samasya (Problem)**
2. **Sambhavit Karan (Symptoms & Causes)**
3. **Surakshit Upay (Safe Actions)**
4. **Agla Kadam (Recommended Next Steps)**

---

## 🎨 Design System & Luxury UI
- **Dark Forest Background**: `#080D0B`
- **Surface Cards**: `#111C17` & `#19271F`
- **Emerald Green Accent**: `#19A875`
- **Gold Accent**: `#D4AF37`
- **Typography**: `#F5F5F0` Off-White Primary, `#A7B5AA` Secondary.

---

## ✨ Features
- **Crop Selector**: Wheat (Gehun), Rice (Chawal), Tomato (Tamatar), Potato (Aloo), and Other Crops.
- **Android Photo Picker**: Native `PickVisualMedia` integration with instant image preview & removal options.
- **Natural Hinglish AI Engine**: Uses Roman-script Hindi tailored for Indian farmers.
- **Agricultural Safety Guardrails**: Avoids unverified chemical dosage recommendations and emphasizes safe observation and local extension consultation (KVK).
- **Error Resilient**: Bounded retry with exponential backoff for transient server errors (HTTP 500, 503, 429).
- **Report Sharing**: Instant share option for analysis reports via Android Intent chooser.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.2.10
- **UI Toolkit**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Kotlin Coroutines (`StateFlow`)
- **Networking**: OkHttp 4.12.0
- **Image Rendering**: Coil Compose 2.7.0
- **AI Model**: Gemma 4 (`gemma-4-26b-a4b-it`) via Google AI Studio REST API

```
com.krishivision.ai
├── data
│   ├── api          # GemmaApiService (OkHttp REST Client & JSON Parser)
│   ├── model        # CropType, CropAnalysisResult & UiState
│   └── repository   # CropRepository (Coroutine execution)
├── ui
│   ├── screens      # HomeScreen (Compose UI & Result Cards)
│   ├── theme        # Color, Theme & Typography
│   └── viewmodel    # KrishiVisionViewModel (State Management)
└── MainActivity.kt
```

---

## 🔑 Setup & Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/KrishiVisionAI.git
   cd KrishiVisionAI
   ```

2. **Configure API Key**:
   Open `local.properties` in the project root and add your Google AI Studio Gemma API key:
   ```properties
   GEMMA_API_KEY=your_google_ai_studio_api_key_here
   ```
   *Note: `local.properties` is ignored by Git to prevent API key exposure.*

3. **Build and Run**:
   - Open the project in **Android Studio**.
   - Select your Android device or emulator.
   - Press **Run ▶ (Shift + F10)**.

---

## 🔒 Security & Privacy
- **Zero API Key Leakage**: API key is injected at compile-time into `BuildConfig.GEMMA_API_KEY` through Gradle configuration (`app/build.gradle.kts`) and never committed to source control.
- **Safe Network Logging**: All network logs omit query parameters containing API tokens.
