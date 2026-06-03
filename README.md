# 📍 Where - Android

> **⚠️ PRIVATE REPOSITORY**  
> This is a proprietary, closed-source project. Unauthorized copying, distribution, or use of this repository or its contents, via any medium, is strictly prohibited.

## 🧠 Project Overview
**Where** is a mobile application designed to simplify real-time coordination among friends, teams, and groups. It enables users to create private groups and share their live location with selected members for a specific duration or continuously.

The primary goal is to eliminate the common problem of repeatedly calling or messaging others to ask about their whereabouts when planning meetups.

## 🚀 Key Features
- **Group Management:** Create groups, invite members, role-based access, and group/individual chatting.
- **Live Location Sharing:** Share location in real-time with customizable durations (15m, 1h, custom, or continuous) and automatic expiration.
- **Real-Time Map View:** Display group members dynamically on a map with interactive markers.
- **ETA Calculation:** Automatic arrival time estimates based on distance and movement speed.
- **Notifications:** Alerts for arrivals, location sharing start/stop.
- **Meetup Destination:** Set a common point and monitor everyone's distance.
- **Battery Optimization:** Adaptive tracking and update intervals to minimize power consumption.

## 🧰 Tech Stack
- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose
- **Architecture:** MVVM (Model-View-ViewModel)
- **Dependency Injection:** Dagger Hilt
- **Maps & Location:** Google Maps SDK, Fused Location Provider API
- **Asynchronous Programming:** Kotlin Coroutines & Flow
- **Backend & Cloud:** Firebase Authentication, Cloud Firestore (Real-time DB), Firebase Cloud Messaging (FCM)

## 🏗️ Architecture
The app follows the **MVVM** architecture for clear separation of concerns:
- **Model:** Interacts with Firebase and local data, managing real-time location sync.
- **ViewModel:** Manages state using `StateFlow` and handles business logic (e.g., ETA calculations).
- **View:** Built purely with Jetpack Compose, observing ViewModel state to dynamically render the map and UI.

## 🛠️ Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17+
- A valid Google Maps API Key
- Firebase `google-services.json` file for the project environment

### Project Configuration
1. Clone this repository.
2. Place the `google-services.json` file in the `app/` directory.
3. Open or create `local.properties` in the root of the project and add your Google Maps API Key:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key_here
   ```
4. Sync the Gradle project in Android Studio.

### Build and Run
- **Debug:** Run the application directly from Android Studio onto an emulator (with Google Play Services) or a physical device.
- **Release:** The release build requires keystore properties. You can configure them via environment variables (`KEYSTORE_PASSWORD`, `KEY_PASSWORD`) or modify `app/build.gradle.kts` temporarily.

## 🔐 Privacy & Security
- Location sharing is strictly group-bound.
- Secure Firebase Authentication ensures encrypted data transfer.
- Users retain full control over tracking with granular sharing limits.

---
*Developed by NeoTechDev. All rights reserved.*
