# 📍 WHERE

## 🧠 Project Overview
The **WHERE** is a mobile application designed to simplify real-time coordination among friends, teams, and groups. It enables users to create private groups and share their live location with selected members for a specific duration or continuously.

The primary goal is to eliminate the common problem of repeatedly calling or messaging others to ask about their whereabouts when planning meetups.

---

## 🎯 Objectives
- Enable seamless real-time location sharing within private groups
- Reduce communication overhead during meetups
- Provide accurate ETA and arrival insights
- Ensure user privacy and control over location visibility
- Optimize battery usage for continuous background tracking

---

## 🚀 Key Features

### 👥 Group Management
- Create and manage groups
- Invite members via link or username
- Role-based access (Admin / Member)
- group chatting or individual chatting

### 📍 Live Location Sharing
- Share live location with group members
- Custom sharing duration:
  - 15 minutes
  - 1 hour
  - Custom duration
  - Continuous (manual stop)
- Automatic expiration of sharing

### 🗺️ Real-Time Map View
- Display all group members on a map
- Real-time location updates
- Interactive markers for each user

### 🧭 ETA Calculation
- Estimate arrival time based on:
  - Distance
  - Movement speed
- Display ETA for each member

### 🔔 Notifications
- Arrival notifications when a user reaches the destination
- Alerts when someone starts/stops sharing location

### 🎯 Meetup Destination
- Set a common destination point
- Show distance of each member from the destination

### 🔋 Battery Optimization
- Adaptive location update intervals
- Reduced tracking when user is idle
- Efficient background processing

---

## 🧰 Tech Stack

### 📱 Mobile Application (Android)
- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose
- **Architecture:** MVVM (Model-View-ViewModel)
- **Maps & Location:** Google Maps SDK, Fused Location Provider API
- **Asynchronous Programming:** Kotlin Coroutines, Flow

### 🔥 Backend & Cloud
- **Authentication:** Firebase Authentication
- **Database:** Cloud Firestore (real-time sync)
- **Notifications:** Firebase Cloud Messaging (FCM)

### 🛠 Development Tools
- Android Studio
- Gradle (Build System)
- Git & GitHub (Version Control)

---

## 🏗️ Architecture Pattern (MVVM)

The application follows the **MVVM (Model-View-ViewModel)** architecture to ensure separation of concerns, scalability, and maintainability.

### 🔹 Model
- Handles data logic and sources
- Interacts with Firebase (Firestore, Auth)
- Represents location, user, and group data

### 🔹 View
- Built using Jetpack Compose
- Observes state from ViewModel
- Displays UI (maps, markers, group info)

### 🔹 ViewModel
- Acts as a bridge between View and Model
- Manages UI state using StateFlow / LiveData
- Handles business logic (location updates, ETA calculation)

---

## 🔄 Data Flow

1. User logs in via Firebase Authentication  
2. Creates or joins a group  
3. Starts location sharing  
4. Location data is sent to Firestore in real-time  
5. ViewModel listens to updates via Flow/LiveData  
6. UI (Compose) updates map dynamically  

---

## 🔐 Privacy & Security
- Location sharing is strictly group-based
- Only group members can view shared locations
- Users have full control:
  - Start/stop sharing anytime
  - Set sharing duration
- Secure authentication and encrypted data transfer

---

## ⚠️ Challenges & Solutions

### 🔋 Battery Consumption
**Challenge:** Continuous location tracking drains battery  
**Solution:** Adaptive update intervals and smart tracking logic  

### 📵 Background Execution Limits
**Challenge:** Android restricts background services  
**Solution:** Use foreground services with proper permissions  

### 🔐 Privacy Concerns
**Challenge:** Users may hesitate to share location  
**Solution:** Full control over sharing duration and visibility  

---

## 📈 Future Enhancements

- 🧑‍🤝‍🧑 Close Friends Mode (persistent sharing)
- 📍 Geo-fencing alerts (enter/exit zones)
- 🚗 Route tracking and navigation integration
- 🤖 AI-based delay prediction
- 📊 Movement analytics

---

## 🧪 Use Cases

- Friends meeting at a location  
- Event coordination  
- Family safety tracking  
- Group travel monitoring  
- Campus coordination (e.g., university students)  

---

## 🏁 Conclusion

This system provides a practical and scalable solution for real-time group coordination. By combining location services, real-time data synchronization, and user-centric privacy controls, the application addresses a common everyday problem effectively.

It is designed to be lightweight, efficient, and user-friendly, making it suitable for both small friend groups and larger communities.

---

## 👨‍💻 Author
**Ismam Hasan Ovi**  
Software Engineering Student  
Android Developer | Kotlin Enthusiast  

---