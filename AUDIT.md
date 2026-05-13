# Codebase Audit Report

Generated: 2026-05-13 19:54:52

Source directory: `app\src\main\java`

Total Kotlin files scanned: 210

---

## Screen States

| Screen | Loading | Error | Empty | Content |
|--------|---------|-------|-------|---------|
| AboutScreen | ❌ missing | ❌ missing | ❌ missing | ❌ missing |
| AppearanceScreen | ❌ missing | ❌ missing | ❌ missing | ❌ missing |
| ChatScreen | ✅ present | ❌ missing | ✅ present | ✅ present |
| ChatsScreen | ✅ present | ❌ missing | ✅ present | ✅ present |
| CompleteProfileScreen | ❌ missing | ✅ present | ❌ missing | ✅ present |
| CreateGroupScreen | ✅ present | ✅ present | ❌ missing | ✅ present |
| DataStorageScreen | ✅ present | ✅ present | ❌ missing | ❌ missing |
| EditGroupScreen | ✅ present | ✅ present | ✅ present | ✅ present |
| EditProfileScreen | ✅ present | ✅ present | ❌ missing | ✅ present |
| EmailVerificationScreen | ✅ present | ❌ missing | ❌ missing | ❌ missing |
| ForgotPasswordScreen | ❌ missing | ✅ present | ❌ missing | ✅ present |
| FriendRequestsScreen | ✅ present | ❌ missing | ✅ present | ✅ present |
| GlobalMapScreen | ❌ missing | ❌ missing | ❌ missing | ❌ missing |
| GroupDetailsScreen | ❌ missing | ✅ present | ❌ missing | ✅ present |
| HelpScreen | ❌ missing | ❌ missing | ❌ missing | ✅ present |
| JoinGroupScreen | ❌ missing | ✅ present | ❌ missing | ❌ missing |
| LoginScreen | ❌ missing | ❌ missing | ❌ missing | ✅ present |
| MapScreen | ❌ missing | ❌ missing | ✅ present | ❌ missing |
| NotificationPreferencesScreen | ❌ missing | ❌ missing | ❌ missing | ❌ missing |
| OnboardingScreen | ❌ missing | ❌ missing | ❌ missing | ✅ present |
| PeopleScreen | ✅ present | ✅ present | ✅ present | ✅ present |
| PrivacyScreen | ❌ missing | ❌ missing | ❌ missing | ❌ missing |
| ProfileScreen | ❌ missing | ❌ missing | ❌ missing | ✅ present |
| SearchUsersScreen | ✅ present | ✅ present | ✅ present | ✅ present |
| SecurityScreen | ❌ missing | ✅ present | ❌ missing | ✅ present |
| SettingsScreen | ❌ missing | ❌ missing | ❌ missing | ❌ missing |
| SignUpScreen | ✅ present | ✅ present | ❌ missing | ❌ missing |
| UserProfileScreen | ✅ present | ✅ present | ❌ missing | ❌ missing |

---

## TODO/FIXME/HACK/STUB Markers

Total: 0

| File | Line | Marker | Text |
|------|------|--------|------|
| _None found_ | - | - | - |

---

## Unused Code

### Unused Imports

Total: 87

| File | Line | Import |
|------|------|--------|
| `app/src/main/java/com/ovi/where/core/utils/ImageCompressor.kt` | 10 | `java.io.ByteArrayOutputStream` |
| `app/src/main/java/com/ovi/where/core/utils/IntentUtils.kt` | 6 | `androidx.core.content.ContextCompat` |
| `app/src/main/java/com/ovi/where/data/repository/ConversationRepositoryImpl.kt` | 35 | `com.ovi.where.data.util.Resource as DataResource` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 22 | `kotlinx.coroutines.flow.emitAll` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 25 | `kotlinx.coroutines.flow.map` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 33 | `com.ovi.where.data.util.Resource as DataResource` |
| `app/src/main/java/com/ovi/where/data/repository/MessageRepositoryImpl.kt` | 46 | `com.ovi.where.data.util.Resource as DataResource` |
| `app/src/main/java/com/ovi/where/DeepLinkManager.kt` | 4 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/DeepLinkManager.kt` | 6 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/domain/repository/ConversationRepository.kt` | 6 | `com.ovi.where.data.util.Resource as DataResource` |
| `app/src/main/java/com/ovi/where/domain/repository/GroupRepository.kt` | 7 | `com.ovi.where.data.util.Resource as DataResource` |
| `app/src/main/java/com/ovi/where/domain/repository/MessageRepository.kt` | 9 | `com.ovi.where.data.util.Resource as DataResource` |
| `app/src/main/java/com/ovi/where/domain/usecase/auth/ObserveCurrentUserUseCase.kt` | 3 | `com.ovi.where.core.common.Resource` |
| `app/src/main/java/com/ovi/where/domain/usecase/auth/ResetPasswordUseCase.kt` | 3 | `com.ovi.where.core.common.Resource` |
| `app/src/main/java/com/ovi/where/MainActivity.kt` | 11 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/auth/complete/CompleteProfileScreen.kt` | 44 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/auth/forgotpassword/ForgotPasswordScreen.kt` | 43 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/auth/login/LoginScreen.kt` | 21 | `androidx.compose.foundation.layout.width` |
| `app/src/main/java/com/ovi/where/presentation/auth/login/LoginScreen.kt` | 25 | `androidx.compose.material.icons.Icons` |
| `app/src/main/java/com/ovi/where/presentation/auth/login/LoginScreen.kt` | 26 | `androidx.compose.material.icons.rounded.LocationOn` |
| `app/src/main/java/com/ovi/where/presentation/auth/login/LoginScreen.kt` | 37 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/auth/login/LoginScreen.kt` | 52 | `androidx.compose.ui.tooling.preview.Preview` |
| `app/src/main/java/com/ovi/where/presentation/auth/signup/SignUpScreen.kt` | 35 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/auth/verification/EmailVerificationScreen.kt` | 35 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/chat/ChatScreen.kt` | 53 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/chat/ChatsScreen.kt` | 53 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/chat/ChatsViewModel.kt` | 23 | `com.ovi.where.data.util.Resource as DataResource` |
| `app/src/main/java/com/ovi/where/presentation/chat/ChatViewModel.kt` | 14 | `com.ovi.where.domain.model.MessageStatus` |
| `app/src/main/java/com/ovi/where/presentation/chat/components/ReactionPicker.kt` | 14 | `androidx.compose.foundation.layout.Column` |
| `app/src/main/java/com/ovi/where/presentation/chat/components/ReactionPicker.kt` | 31 | `androidx.compose.ui.text.style.TextAlign` |
| `app/src/main/java/com/ovi/where/presentation/chat/components/ReconnectionBanner.kt` | 7 | `androidx.compose.foundation.background` |
| `app/src/main/java/com/ovi/where/presentation/chat/components/ReconnectionBanner.kt` | 10 | `androidx.compose.foundation.layout.Box` |
| `app/src/main/java/com/ovi/where/presentation/chat/components/ReconnectionBanner.kt` | 11 | `androidx.compose.foundation.layout.Column` |
| `app/src/main/java/com/ovi/where/presentation/chat/components/ReconnectionBanner.kt` | 15 | `androidx.compose.foundation.layout.height` |
| `app/src/main/java/com/ovi/where/presentation/chat/components/ReconnectionBanner.kt` | 22 | `androidx.compose.material.icons.filled.Refresh` |
| `app/src/main/java/com/ovi/where/presentation/common/Components.kt` | 64 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/common/PressAnimation.kt` | 9 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/common/PressAnimation.kt` | 15 | `com.ovi.where.core.utils.REDUCED_MOTION_MAX_DURATION_MS` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 22 | `androidx.compose.foundation.layout.width` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 23 | `androidx.compose.foundation.lazy.LazyColumn` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 24 | `androidx.compose.foundation.lazy.items` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 35 | `androidx.compose.material3.AssistChip` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 44 | `androidx.compose.material3.IconButton` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 57 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 60 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/group/create/CreateGroupScreen.kt` | 70 | `androidx.compose.ui.unit.dp` |
| `app/src/main/java/com/ovi/where/presentation/group/details/GroupDetailsScreen.kt` | 54 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/group/details/GroupDetailsScreen.kt` | 57 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/group/edit/EditGroupScreen.kt` | 25 | `androidx.compose.foundation.layout.width` |
| `app/src/main/java/com/ovi/where/presentation/group/edit/EditGroupScreen.kt` | 50 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/group/edit/EditGroupScreen.kt` | 61 | `androidx.compose.ui.text.style.TextAlign` |
| `app/src/main/java/com/ovi/where/presentation/group/JoinGroupScreen.kt` | 48 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/group/JoinGroupScreen.kt` | 51 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/group/JoinGroupViewModel.kt` | 7 | `com.ovi.where.core.common.UiText` |
| `app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt` | 91 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/map/GlobalMapViewModel.kt` | 8 | `android.location.LocationManager as SystemLocationManager` |
| `app/src/main/java/com/ovi/where/presentation/map/MapScreen.kt` | 48 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/map/MapScreen.kt` | 53 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/navigation/AppNavGraph.kt` | 13 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/navigation/MainScaffold.kt` | 21 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/onboarding/OnboardingScreen.kt` | 41 | `androidx.compose.ui.text.font.FontWeight` |
| `app/src/main/java/com/ovi/where/presentation/people/components/ProfileActions.kt` | 10 | `androidx.compose.foundation.layout.width` |
| `app/src/main/java/com/ovi/where/presentation/people/FriendRequestsScreen.kt` | 5 | `androidx.compose.foundation.clickable` |
| `app/src/main/java/com/ovi/where/presentation/people/FriendRequestsScreen.kt` | 32 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/people/FriendRequestsScreen.kt` | 37 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/people/PeopleScreen.kt` | 29 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/people/PeopleScreen.kt` | 33 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/people/SearchUsersScreen.kt` | 22 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/people/SearchUsersViewModel.kt` | 8 | `com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase` |
| `app/src/main/java/com/ovi/where/presentation/people/SearchUsersViewModel.kt` | 15 | `com.ovi.where.presentation.model.toSearchUiModel` |
| `app/src/main/java/com/ovi/where/presentation/people/UserProfileScreen.kt` | 16 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/people/UserProfileViewModel.kt` | 6 | `com.google.firebase.functions.FirebaseFunctionsException` |
| `app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt` | 18 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt` | 21 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/profile/edit/EditProfileScreen.kt` | 47 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/profile/ProfileScreen.kt` | 31 | `androidx.compose.material.icons.filled.Search` |
| `app/src/main/java/com/ovi/where/presentation/profile/ProfileScreen.kt` | 43 | `androidx.compose.material3.IconButton` |
| `app/src/main/java/com/ovi/where/presentation/settings/AppearanceScreen.kt` | 27 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/settings/DataStorageScreen.kt` | 32 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/settings/DataStorageScreen.kt` | 35 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/settings/NotificationPreferencesScreen.kt` | 31 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/settings/PrivacyScreen.kt` | 30 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/settings/PrivacyViewModel.kt` | 12 | `kotlinx.coroutines.flow.combine` |
| `app/src/main/java/com/ovi/where/presentation/settings/SecurityScreen.kt` | 38 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/presentation/settings/SecurityScreen.kt` | 41 | `androidx.compose.runtime.setValue` |
| `app/src/main/java/com/ovi/where/presentation/settings/SettingsScreen.kt` | 45 | `androidx.compose.runtime.getValue` |
| `app/src/main/java/com/ovi/where/service/LocationTrackingService.kt` | 12 | `com.google.firebase.firestore.FirebaseFirestore` |

### Commented-Out Code Blocks (3+ consecutive lines)

Total: 0

| File | Start Line | End Line | Lines |
|------|------------|----------|-------|
| _None found_ | - | - | - |

---

## Debug Log Statements

Total: 14

| File | Line | Statement |
|------|------|-----------|
| `app/src/main/java/com/ovi/where/core/utils/ImageCompressor.kt` | 123 | `Log.d(TAG, "Compressed image: ${finalWidth}x${finalHeight}, size: ${compressedFile.length()} bytes")` |
| `app/src/main/java/com/ovi/where/core/utils/ImageUploadUtil.kt` | 234 | `Log.d(TAG, "Compressed with quality $quality, size: ${tempFile.length()} bytes")` |
| `app/src/main/java/com/ovi/where/core/utils/ImageUploadUtil.kt` | 237 | `Log.d(TAG, "Achieved target size with quality: $quality")` |
| `app/src/main/java/com/ovi/where/core/utils/ImageUploadUtil.kt` | 265 | `Log.d(TAG, "ByteArray compression with quality $quality, size: ${compressedData.size} bytes")` |
| `app/src/main/java/com/ovi/where/core/utils/ImageUploadUtil.kt` | 273 | `Log.d(TAG, "Final compression successful with quality: $quality")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 83 | `Log.d(TAG, "Socket.IO connected")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 89 | `Log.d(TAG, "Socket.IO disconnected")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 284 | `Log.d(TAG, "Reconnection attempt ${reconnectAttempt + 1}/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 326 | `Log.d(TAG, "Socket.IO reconnected")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 334 | `Log.d(TAG, "Socket.IO disconnected during reconnection")` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 184 | `Timber.d("Write throttled — skipping Firestore update (${now - lastWriteTimestamp}ms since last)")` |
| `app/src/main/java/com/ovi/where/service/LocationTrackingService.kt` | 81 | `Timber.d("Location tracking session expired")` |
| `app/src/main/java/com/ovi/where/service/LocationTrackingService.kt` | 89 | `Timber.d("Location update: lat=${location.latitude}, lng=${location.longitude}")` |
| `app/src/main/java/com/ovi/where/WhereApplication.kt` | 74 | `Timber.d("FCM token saved on app start")` |

---

## Permission Mappings

| Permission | Manifest | Code Usages |
|------------|----------|-------------|
| `android.permission.INTERNET` | ✅ Declared | `app/src/main/java/com/ovi/where/data/network/NetworkConnectivityObserver.kt:44`<br>`app/src/main/java/com/ovi/where/data/network/NetworkConnectivityObserver.kt:51`<br>`app/src/main/java/com/ovi/where/data/network/NetworkConnectivityObserver.kt:59` |
| `android.permission.ACCESS_NETWORK_STATE` | ✅ Declared | _No code references found_ |
| `android.permission.ACCESS_FINE_LOCATION` | ✅ Declared | `app/src/main/java/com/ovi/where/core/utils/PermissionUtils.kt:10`<br>`app/src/main/java/com/ovi/where/presentation/chat/ChatScreen.kt:113`<br>`app/src/main/java/com/ovi/where/presentation/chat/ChatScreen.kt:302`<br>`app/src/main/java/com/ovi/where/presentation/chat/ChatScreen.kt:308`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:154`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:180`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:490`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:547`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:555`<br>`app/src/main/java/com/ovi/where/presentation/map/MapScreen.kt:111`<br>`app/src/main/java/com/ovi/where/presentation/map/MapScreen.kt:202`<br>`app/src/main/java/com/ovi/where/presentation/map/MapScreen.kt:207`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:39`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:47`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:61`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:202` |
| `android.permission.ACCESS_COARSE_LOCATION` | ✅ Declared | `app/src/main/java/com/ovi/where/core/utils/PermissionUtils.kt:11`<br>`app/src/main/java/com/ovi/where/presentation/chat/ChatScreen.kt:114`<br>`app/src/main/java/com/ovi/where/presentation/chat/ChatScreen.kt:308`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:158`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:181`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:491`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:550`<br>`app/src/main/java/com/ovi/where/presentation/map/GlobalMapScreen.kt:556`<br>`app/src/main/java/com/ovi/where/presentation/map/MapScreen.kt:112`<br>`app/src/main/java/com/ovi/where/presentation/map/MapScreen.kt:208`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:39`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:61`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:203` |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | ✅ Declared | `app/src/main/java/com/ovi/where/core/utils/PermissionUtils.kt:14`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:40`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:60`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:151`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:212`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:218`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:268` |
| `android.permission.FOREGROUND_SERVICE` | ✅ Declared | _No code references found_ |
| `android.permission.FOREGROUND_SERVICE_LOCATION` | ✅ Declared | _No code references found_ |
| `android.permission.POST_NOTIFICATIONS` | ✅ Declared | `app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:26`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:114`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:133`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:156`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:168`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:174`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:185`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:277`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:386`<br>`app/src/main/java/com/ovi/where/core/notification/NotificationHelper.kt:393`<br>`app/src/main/java/com/ovi/where/core/utils/PermissionUtils.kt:16`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:40`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:73`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:92`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:233`<br>`app/src/main/java/com/ovi/where/presentation/permission/PermissionManager.kt:239` |
| `android.permission.VIBRATE` | ✅ Declared | _No code references found_ |

---

## Network Call Patterns

### Retrofit Interfaces

Total: 8

| File | Line | Method |
|------|------|--------|
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 12 | `@GET("/api/conversations/{conversationId}/messages")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 18 | `@GET("/api/conversations/{conversationId}/messages")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 30 | `@GET("/api/conversations/{conversationId}/messages")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 38 | `@POST("/api/conversations/direct")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 44 | `@POST("/api/conversations/group")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 50 | `@PATCH("/api/conversations/{conversationId}/read")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 60 | `@GET("/api/conversations/unread-counts")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatApiService.kt` | 69 | `@GET("/api/conversations")` |

### Firestore Queries

Total: 152

| File | Line | Query |
|------|------|-------|
| `app/src/main/java/com/ovi/where/data/remote/FcmMessagingService.kt` | 51 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/remote/FcmMessagingService.kt` | 52 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 46 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 47 | `.document(firebaseUser.uid)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 124 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 125 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 162 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 163 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 207 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 208 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 266 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 267 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 280 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 281 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 297 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 298 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 311 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 342 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 343 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 382 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 383 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 411 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/AuthRepositoryImpl.kt` | 412 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/ConversationRepositoryImpl.kt` | 240 | `firestoreListener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_CONVERSATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 53 | `val reg = firestore.collection("users").document(uid).collection("friends")` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 68 | `val reg = firestore.collection("users").document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 69 | `.collection("inbox").document("friendRequests")` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 83 | `val reg = firestore.collection("users").document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 84 | `.collection("outbox").document("friendRequests")` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 99 | `val reg = firestore.collection("users").document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 100 | `.collection("summary").document("social")` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 113 | `val reg = firestore.collection("users").document(uid).collection("blocks")` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 129 | `val doc = firestore.collection("friendships").document(pairId).get().await()` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 144 | `val doc = firestore.collection("friendships").document(pairId).get().await()` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 203 | `val groupsListener = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 216 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 217 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/FriendshipRepositoryImpl.kt` | 218 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 70 | `val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 71 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 81 | `val memberRef = groupRef.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 82 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 96 | `val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 97 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 112 | `val groups = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 127 | `val query = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 154 | `val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 155 | `.document(group.id)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 156 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 157 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 160 | `val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 161 | `.document(group.id)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 179 | `val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 180 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 181 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 182 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 185 | `val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 186 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 201 | `val membersSnapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 202 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 203 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 210 | `val locationsSnapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 211 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 212 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 219 | `val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 220 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 236 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 237 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 240 | `val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 241 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 253 | `val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 254 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 255 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 279 | `val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 280 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 281 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 282 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 285 | `val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 286 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 301 | `val memberRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 302 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 303 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 304 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 307 | `val groupRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 308 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 321 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 322 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 323 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 324 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 367 | `val members = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 368 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/GroupRepositoryImpl.kt` | 369 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 42 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_DIRECT_LOCATION_SHARES)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 43 | `.document(directShareId(uid, friendId))` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 82 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 83 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 105 | `shareDoc.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 106 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 110 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 111 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 112 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 113 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 132 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 133 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 149 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 150 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 154 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 155 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 156 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 157 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 199 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 200 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 219 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 220 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 224 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 225 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 226 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 227 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 244 | `.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 278 | `val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 279 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 280 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 311 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 312 | `.document(friendId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 336 | `val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 337 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 338 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 339 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 371 | `val activeDoc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_ACTIVE_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 372 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 392 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 393 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 397 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 398 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 399 | `.collection(AppConstants.FIRESTORE_COLLECTION_LOCATIONS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 400 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 424 | `val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_GROUPS)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 425 | `.document(groupId)` |
| `app/src/main/java/com/ovi/where/data/repository/LocationRepositoryImpl.kt` | 426 | `.collection(AppConstants.FIRESTORE_COLLECTION_MEMBERS)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 28 | `val doc = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 29 | `.document(userId).get().await()` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 47 | `val snapshot = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 59 | `val listener: ListenerRegistration = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 60 | `.document(userId)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 71 | `firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 72 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 91 | `val byName = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/data/repository/UserRepositoryImpl.kt` | 96 | `val byUsername = firestore.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/WhereApplication.kt` | 50 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/WhereApplication.kt` | 51 | `.document(uid)` |
| `app/src/main/java/com/ovi/where/WhereApplication.kt` | 70 | `.collection(AppConstants.FIRESTORE_COLLECTION_USERS)` |
| `app/src/main/java/com/ovi/where/WhereApplication.kt` | 71 | `.document(uid)` |

### Socket.IO Events

Total: 29

| File | Line | Event |
|------|------|-------|
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 82 | `socket?.on(Socket.EVENT_CONNECT) {` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 88 | `socket?.on(Socket.EVENT_DISCONNECT) {` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 96 | `socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 104 | `socket?.on("connected") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 114 | `socket?.on("message") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 124 | `socket?.on("ack") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 134 | `socket?.on("typing") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 144 | `socket?.on("reaction_update") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 154 | `socket?.on("read_receipt") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 164 | `socket?.on("presence") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 174 | `socket?.on("error") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 212 | `socket?.emit("message", payload)` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 222 | `socket?.emit("location_message", payload)` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 231 | `socket?.emit("image_message", payload)` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 239 | `socket?.emit("typing", payload)` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 244 | `socket?.emit("read")` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 253 | `socket?.emit("reaction", payload)` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 262 | `socket?.emit("remove_reaction", payload)` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 325 | `socket?.on(Socket.EVENT_CONNECT) {` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 333 | `socket?.on(Socket.EVENT_DISCONNECT) {` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 338 | `socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 354 | `socket?.on("connected") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 364 | `socket?.on("message") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 374 | `socket?.on("ack") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 384 | `socket?.on("typing") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 394 | `socket?.on("reaction_update") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 404 | `socket?.on("read_receipt") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 414 | `socket?.on("presence") { args ->` |
| `app/src/main/java/com/ovi/where/data/remote/chat/ChatSocketIoClient.kt` | 424 | `socket?.on("error") { args ->` |

---

## Notification Channels

Total: 5

| Channel ID | Name | Importance |
|------------|------|------------|
| `messages` | Messages | High |
| `social` | Friends & Social | Default |
| `location_updates` | Location Updates | High |
| `group_activity` | Group Activity | Default |
| `general` | General | Default |

---

## Parse Errors

| File | Error |
|------|-------|
| _No parse errors_ | - |

