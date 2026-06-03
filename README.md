# 📍 Where - Android & Backend Platform

> **⚠️ PRIVATE REPOSITORY**  
> This is a proprietary, closed-source project. Unauthorized copying, distribution, or use of this repository or its contents, via any medium, is strictly prohibited.

## 🧠 Platform Overview

**Where** is a robust, real-time coordination and communication platform designed for friends, teams, and groups. It eliminates the friction of meetups by providing real-time location sharing, integrated chatting, and automated ETA calculation. 

The platform is designed for scale and consists of three primary subsystems:
1. **Android Client:** A modern, Jetpack Compose-based mobile application.
2. **Cloud Functions:** Serverless functions handling the social graph and friendship mutations.
3. **Relay Server:** A high-performance Express + Socket.IO server handling real-time chat and high-frequency location updates.

---

## 🚀 Key Features

### 📍 Live Location Sharing & Maps
- **Granular Sharing:** Share location for 15 minutes, 1 hour, custom durations, or continuously.
- **Dynamic Map View:** Real-time map rendering of all active group members.
- **Automated ETA:** Distance and speed-based arrival estimates to shared destinations.
- **Battery Optimization:** Adaptive intervals that adjust based on user movement state and battery level.

### 💬 Real-Time Chat & Messaging
- **Group & Direct Chats:** Full-featured messaging capabilities natively integrated.
- **Rich Media:** Support for text, images, and voice messages.
- **Socket.IO Integration:** Low-latency delivery, real-time typing indicators, and read receipts.
- **Offline Support:** Local caching of messages via Room DB with background synchronization.

### 👥 Social Graph & Group Management
- **Role-Based Access:** Granular group permissions (Admin/Member).
- **Friendship Mutations:** Robust friend request, accept, decline, block, and unfriend flows powered by secure Cloud Functions.
- **Profile Fan-Out:** Automatic synchronization of profile updates across the denormalized social graph.

---

## 🧰 Tech Stack & Architecture

### 📱 Android Application (`app/`)
- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose (Pure Compose architecture)
- **Architecture:** MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Dependency Injection:** Dagger Hilt
- **Local Storage:** Room Database (caching chats and locations)
- **Maps & Location:** Google Maps SDK, Fused Location Provider API
- **Networking/Async:** Kotlin Coroutines, Flow, Retrofit, Socket.IO Client

### 🔥 Firebase Cloud Functions (`functions/`)
- **Language:** TypeScript / Node.js
- **Purpose:** Manages the social graph (Friendships, Blocks). Ensures transactional consistency across denormalized documents.
- **Key Mechanics:** Rate limiting, strict server-side validation, and background Firestore triggers (e.g., profile data fan-out).

### ⚡ Relay Server (`server/`)
- **Language:** Node.js (Express)
- **Purpose:** Chat message relay and high-frequency location brokering.
- **Tech:** Socket.IO for WebSocket connections, LRU caching for Auth tokens.
- **Production Hardening:** 
  - Helmet security headers & CORS allowlisting
  - gzip compression
  - Rate limiting & IP trusting (Cloud Run compatibility)
  - Graceful SIGTERM shutdowns to prevent dropped messages

---

## 🛠️ Developer Setup & Deployment

### 1. Prerequisites
- **Android Studio Ladybug** (or newer) & JDK 17+
- **Node.js** v20+ (for backend services)
- **Firebase CLI** (`npm install -g firebase-tools`)
- Required API Keys: Google Maps API Key, Firebase `google-services.json`

### 2. Android Client Setup
1. Place the `google-services.json` file in the `app/` directory.
2. Create `local.properties` in the project root:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key_here
   ```
3. Sync Gradle and run on a device or emulator with Google Play Services.

### 3. Cloud Functions Setup
1. Navigate to the functions directory: `cd functions`
2. Install dependencies: `npm install`
3. Test locally using the emulator: `npm run test:emulator`
4. Deploy: `firebase deploy --only functions`

### 4. Relay Server Setup
1. Navigate to the server directory: `cd server`
2. Install dependencies: `npm install`
3. Configure environment variables in an `.env` file (e.g., Firebase Admin credentials, CORS origins).
4. Run locally: `npm start`
5. **Deployment:** Designed for deployment on **Google Cloud Run**, utilizing the built-in HTTP keep-alive and load balancing configurations.

---

## 🔐 Privacy & Security
- **Authentication:** Managed securely via Firebase Authentication. All server connections authenticate via cached ******
- **Data Access:** Firestore rules enforce that users only read authorized social and group data. All write mutations to the social graph route through strict Cloud Functions.
- **Location Privacy:** Location data is ephemeral and sharing is strictly governed by user-defined timers and group boundaries.

---
*Developed by NeoTechDev. All rights reserved.*
