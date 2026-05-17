# Implementation Plan: Chat UI Redesign

## Overview

This plan implements the Messenger-style chat UI redesign for the Where Android app. The work is organized into incremental steps: first extending data models and mappers, then building reusable UI components, redesigning existing screens, creating new info screens, adding navigation wiring, and finally applying visual polish and animations. Each task builds on previous steps to ensure no orphaned code.

## Tasks

- [ ] 1. Extend data models and mapper logic
  - [x] 1.1 Update ConversationUiModel mapper with correct fallback logic
    - Modify the `toUiModel()` mapper to resolve display name from participant metadata when `name` is blank
    - Set fallback to `"Unknown User"` for direct messages and `"Unnamed Group"` for group conversations
    - Ensure the result is never blank
    - _Requirements: 1.1, 1.2, 1.4_

  - [x] 1.2 Add message grouping metadata to MessageUiModel
    - Add `isFirstInGroup`, `isLastInGroup`, `showDateSeparator`, and `dateSeparatorLabel` fields to `MessageUiModel`
    - Implement grouping computation in `ChatViewModel` message mapper: consecutive messages from same sender within 2 minutes form a group
    - Compute date separator flags when `dateKey` differs between consecutive messages
    - Generate separator labels: "Today", "Yesterday", or formatted date
    - _Requirements: 4.6, 4.7, 10.3_

  - [x] 1.3 Add ConversationInfoUiState and GroupInfoUiState data classes
    - Create `ConversationInfoUiState` data class with fields: conversationTitle, photoUrl, isOnline, lastActiveTime, sharedMedia, isMuted, isLoading
    - Create `MediaThumbnail` data class with id, thumbnailUrl, type
    - Create `GroupInfoUiState` data class with fields: groupName, groupPhotoUrl, memberCount, members, inviteLink, isCurrentUserAdmin, sharedMedia, isMuted, isLoading
    - Place in `presentation/model/` package
    - _Requirements: 8.1, 8.2, 9.1, 9.2_

  - [-] 1.4 Write property test for conversation title resolution (Property 1)
    - **Property 1: Conversation title resolution produces correct fallback**
    - Generate random Conversation objects with blank names and varying types/metadata
    - Verify mapper output is never blank, produces "Unknown User" for DM and "Unnamed Group" for group when name is blank
    - Use Kotest property testing module
    - **Validates: Requirements 1.2, 1.4**

  - [-] 1.5 Write property test for blank sender name display (Property 2)
    - **Property 2: Blank sender name displays "Unknown"**
    - Generate random whitespace strings as senderName
    - Verify the displayed sender label is "Unknown" for any blank/whitespace input
    - Use Kotest property testing module
    - **Validates: Requirements 2.4**

  - [-] 1.6 Write property test for message grouping correctness (Property 4)
    - **Property 4: Message grouping correctness**
    - Generate random message sequences with varying senders and timestamps
    - Verify two consecutive messages are in the same group iff same senderId AND timestamp diff ≤ 2 minutes
    - Verify isFirstInGroup and isLastInGroup flags are correct
    - Use Kotest property testing module
    - **Validates: Requirements 4.6, 4.7**

  - [-] 1.7 Write property test for date separator insertion (Property 5)
    - **Property 5: Date separator insertion at day boundaries**
    - Generate random message sequences spanning multiple days
    - Verify date separator is inserted between messages with different dateKey values
    - Verify label is "Today", "Yesterday", or formatted date
    - Use Kotest property testing module
    - **Validates: Requirements 10.3**

- [ ] 2. Build reusable UI components
  - [-] 2.1 Create ConversationAvatar composable
    - Create `chat/components/ConversationAvatar.kt`
    - Implement circular avatar with configurable size (default 56dp)
    - Load remote image with Coil when photoUrl is available, circular clip
    - Show colored circle with initials fallback when no photo
    - Implement initials computation: 1-2 uppercase chars from name, "?" for blank
    - Overlay Online_Indicator (green dot with white border) at bottom-right, configurable size (default 14dp indicator, 2dp border)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 6.1, 6.6_

  - [x] 2.2 Write property test for initials computation (Property 3)
    - **Property 3: Initials computation produces valid uppercase characters**
    - Generate random name strings (single word, multi-word, empty, unicode)
    - Verify initials are 1-2 uppercase characters from first chars of first two words, or "?" for blank
    - Use Kotest property testing module
    - **Validates: Requirements 3.3**

  - [x] 2.3 Create ChatBubble composable
    - Create `chat/components/ChatBubble.kt`
    - Implement sent bubble: primary color background, white text, right-aligned
    - Implement received bubble: surfaceContainerHigh background, onSurface text, left-aligned
    - Apply 18dp corner radius on all corners, 4dp on tail corner (bottom-right for sent, bottom-left for received)
    - Intermediate bubbles in group: 18dp all corners (no tail)
    - Constrain max width to 75% of screen
    - Apply 12dp horizontal, 8dp vertical content padding
    - Display timestamp below text in labelSmall, 0.7 opacity
    - Show 28dp sender avatar to left of first received bubble in group chats
    - Display Sender_Name_Label above received bubbles in group chats (hide for DM)
    - Show "Unknown" when senderName is blank
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 2.2, 2.3, 2.4_

  - [x] 2.4 Create DateSeparator composable
    - Create `chat/components/DateSeparator.kt`
    - Implement rounded chip with surfaceContainerHigh background, onSurfaceVariant text, labelSmall typography
    - Center horizontally with 16dp vertical margin
    - _Requirements: 10.3, 10.4_

  - [x] 2.5 Create UnreadBadge composable
    - Create a small reusable composable for the 20dp filled circular badge with unread count
    - Use primary color background with white text
    - _Requirements: 3.9_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Redesign ChatsScreen (Conversation List)
  - [x] 4.1 Implement new ConversationRow layout in ChatsScreen
    - Replace existing conversation row with Messenger-style layout
    - Use ConversationAvatar (56dp) on leading edge with online indicator
    - Title line: bodyLarge + FontWeight.SemiBold, timestamp (labelSmall) at trailing edge
    - Preview line: bodyMedium + onSurfaceVariant, UnreadBadge at trailing edge
    - Apply 16dp horizontal, 12dp vertical padding
    - Remove HorizontalDivider between rows
    - Apply bold styling for unread conversations (FontWeight.Bold title, FontWeight.Medium preview)
    - Display Conversation_Title from ConversationUiModel (with fallback logic from task 1.1)
    - _Requirements: 1.1, 1.2, 1.3, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11_

  - [x] 4.2 Update ChatsScreen header to Messenger style
    - Replace top bar with "Chats" title in headlineMedium + FontWeight.Bold
    - Match Messenger's screen title style
    - _Requirements: 10.6_

  - [x] 4.3 Write unit tests for ConversationRow
    - Test avatar size (56dp), online indicator visibility
    - Test unread badge display and font weight changes for unread state
    - Test fallback text display for blank names
    - _Requirements: 3.1, 3.4, 3.8, 3.9, 1.2_

- [x] 5. Redesign ChatScreen (Message View)
  - [x] 5.1 Integrate ChatBubble and message grouping into ChatScreen
    - Replace existing message rendering with ChatBubble composable
    - Apply 2dp spacing between grouped messages, 8dp for non-grouped
    - Integrate date separators between messages from different days
    - Show sender avatar on first received bubble in group chats
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 2.2, 2.3, 10.3, 10.4_

  - [x] 5.2 Redesign ChatInputBar to Messenger style
    - Modify `chat/components/ChatInputBar.kt`
    - Apply 24dp corner radius, surfaceContainerHigh background, no border
    - Show circular send button (40dp, primary color) only when text is non-empty
    - Show camera + attachment icons on trailing edge when text is empty
    - Set placeholder text to "Aa" in onSurfaceVariant
    - Support multi-line expansion up to 5 lines, then scroll internally
    - Apply 8dp padding from screen edges and 8dp vertical from message list
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 5.3 Implement empty state for ChatScreen
    - Display centered empty state with illustration and "Say hi!" prompt when conversation has no messages
    - _Requirements: 10.5_

  - [x] 5.4 Write unit tests for ChatBubble and InputBar
    - Test sent/received color schemes and corner radius configuration
    - Test max width constraint (75%)
    - Test send button visibility toggle based on text content
    - Test placeholder text and action icon swap
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.6_

- [x] 6. Redesign ChatHeader
  - [x] 6.1 Implement Messenger-style ChatHeader
    - Modify `chat/ChatHeader.kt`
    - Set compact height (64dp), surface background, 1dp bottom shadow
    - Layout: back arrow (24dp) → 8dp → Avatar (36dp) with 10dp online indicator → 8dp → title column
    - Title: titleSmall + FontWeight.SemiBold
    - Subtitle: "Active now" (green/tertiary) when online, "Offline" (onSurfaceVariant) when offline, "{N} members" for groups
    - Trailing actions: phone, video, info icons (24dp, onSurfaceVariant), max 3 visible
    - Make avatar/title area tappable to navigate to info screen
    - Display Conversation_Title with proper fallback
    - _Requirements: 2.1, 2.5, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11_

  - [x] 6.2 Write unit tests for ChatHeader
    - Test compact height and avatar placement
    - Test subtitle text for online/offline/group states
    - Test action icon visibility and max count
    - _Requirements: 7.1, 7.5, 7.6, 7.7, 7.10, 7.11_

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Create Conversation Info Screen
  - [x] 8.1 Create ConversationInfoViewModel
    - Create `chat/ConversationInfoViewModel.kt`
    - Inject UserRepository and ConversationRepository via Hilt
    - Load conversation details, online status, last active time, shared media
    - Expose `ConversationInfoUiState` as StateFlow
    - Handle error states with retry capability
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

  - [x] 8.2 Create ConversationInfoScreen composable
    - Create `chat/ConversationInfoScreen.kt`
    - Large centered avatar (80dp) + name (headlineSmall, FontWeight.Bold)
    - Online status: "Active now" or "Active Xh ago" in bodyMedium
    - Action button row: Audio Call, Video Call, Profile, Mute, Search — 40dp icon circles with labels
    - "Customize Chat" section: theme color, emoji shortcut, nicknames
    - "More Actions" section: Search in Conversation, View Media & Files, Notification Settings
    - "Privacy & Support" section (DM only): Block, Report in error color
    - Shared media horizontal scrollable row (3 visible) + "See All"
    - Scrollable Column, section headers in labelLarge + FontWeight.SemiBold, 16dp top margin
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

  - [x] 8.3 Write unit tests for ConversationInfoScreen
    - Test section visibility based on conversation type (DM vs group)
    - Test action button row rendering
    - Test error and loading states
    - _Requirements: 8.3, 8.6, 8.7_

- [x] 9. Create Group Info Screen
  - [x] 9.1 Create GroupInfoViewModel
    - Create `chat/GroupInfoViewModel.kt`
    - Inject GroupRepository via Hilt
    - Load group details, member list with online status, shared media, invite link
    - Expose `GroupInfoUiState` as StateFlow
    - Implement admin actions: make admin, remove member, delete group
    - Handle error states with retry capability
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11_

  - [x] 9.2 Create GroupInfoScreen composable
    - Create `chat/GroupInfoScreen.kt`
    - Large centered group avatar (80dp) + group name + member count
    - Action button row: Add Members, Audio Call, Video Call, Mute, Search
    - Members section: 40dp avatar + name (bodyLarge) + "Admin" chip + online indicator
    - Admin actions: overflow menu per non-admin member ("Make Admin", "Remove from Group")
    - "Add Members" row at top of member list for admins (plus icon, primary color)
    - "Customize Chat" section: group name edit, group photo change, theme color
    - "Shared Media" section with horizontal thumbnail row + "See All"
    - "Invite Link" section with copy + share buttons (admin only)
    - "Leave Group" in error color with confirmation dialog
    - "Delete Group" below leave (admin only) with confirmation dialog
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11_

  - [x] 9.3 Write unit tests for GroupInfoScreen
    - Test admin-only options visibility (overflow menu, add members, delete group, invite link)
    - Test member list rendering with role badges and online indicators
    - Test confirmation dialogs for leave/delete
    - _Requirements: 9.4, 9.5, 9.8, 9.9, 9.10, 9.11_

- [x] 10. Navigation wiring and route registration
  - [x] 10.1 Add ConversationInfo route to Screen.kt and AppNavGraph
    - Add `ConversationInfo` route to `Screen.kt` with conversationId parameter
    - Register the route in `AppNavGraph.kt` pointing to `ConversationInfoScreen`
    - Register `GroupInfoScreen` route if not already present
    - Wire ChatHeader avatar/title tap to navigate to appropriate info screen (ConversationInfo for DM, GroupDetails for group)
    - _Requirements: 7.9, 8.1, 9.1_

  - [x] 10.2 Write integration tests for navigation flows
    - Test tapping header avatar navigates to correct info screen (UserProfile for DM, GroupDetails for group)
    - Test back navigation from info screens
    - _Requirements: 7.9_

- [x] 11. Visual polish and animations
  - [x] 11.1 Add message send animation
    - Animate new sent messages sliding in from the bottom with 200ms ease-out animation
    - _Requirements: 10.1_

  - [x] 11.2 Add conversation row reordering animation
    - Animate conversation row reordering with 250ms ease-in-out when new messages change sort order
    - _Requirements: 10.2_

  - [x] 11.3 Write unit tests for visual polish
    - Test date separator pill styling (background, text color, typography)
    - Test empty state rendering
    - _Requirements: 10.3, 10.4, 10.5_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document using Kotest
- Unit tests validate specific examples and edge cases using Compose UI testing
- The implementation uses Kotlin with Jetpack Compose, Material 3, Hilt DI, and Coil for image loading
- All new files go in the existing `presentation/chat/` package structure
- Existing navigation routes (Screen.UserProfile, Screen.GroupDetails) are reused where possible

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4", "1.5", "1.6", "1.7", "2.1", "2.4", "2.5"] },
    { "id": 2, "tasks": ["2.2", "2.3"] },
    { "id": 3, "tasks": ["4.1", "4.2", "5.2", "5.3"] },
    { "id": 4, "tasks": ["4.3", "5.1"] },
    { "id": 5, "tasks": ["5.4", "6.1"] },
    { "id": 6, "tasks": ["6.2", "8.1", "9.1"] },
    { "id": 7, "tasks": ["8.2", "9.2"] },
    { "id": 8, "tasks": ["8.3", "9.3", "10.1"] },
    { "id": 9, "tasks": ["10.2", "11.1", "11.2"] },
    { "id": 10, "tasks": ["11.3"] }
  ]
}
```
