# Requirements Document

## Introduction

This specification covers the redesign of the chat UI/UX in the Where Android app to fix the username display bug and align the visual design with professional messaging apps like Facebook Messenger. The redesign targets two screens: the conversation list (ChatsScreen) and the individual chat view (ChatScreen). The goal is a polished, modern messaging experience with proper user identity display, Messenger-style conversation rows, professional chat bubbles, and a refined input bar.

## Glossary

- **ChatsScreen**: The screen displaying the list of all conversations (direct messages and groups) for the current user.
- **ChatScreen**: The screen displaying the message thread within a single conversation.
- **Conversation_Row**: A single list item in the ChatsScreen representing one conversation.
- **Chat_Bubble**: A visual container for a single message in the ChatScreen.
- **Input_Bar**: The message composition area at the bottom of the ChatScreen.
- **Chat_Header**: The top app bar in the ChatScreen showing conversation identity and actions.
- **Avatar**: A circular profile image or initials fallback representing a user or group.
- **Conversation_Title**: The resolved display name shown in the Conversation_Row (partner name for DM, group name for group).
- **Sender_Name_Label**: The text label above a received Chat_Bubble showing who sent the message in group conversations.
- **Message_Preview**: The truncated last message text shown in the Conversation_Row.
- **Timestamp_Label**: The formatted time displayed alongside messages or conversations.
- **Online_Indicator**: A small colored dot overlaid on an Avatar indicating the user is currently online.
- **Conversation_Info_Screen**: The detail screen for a direct message conversation showing user profile, shared media, and conversation settings.
- **Group_Info_Screen**: The detail screen for a group conversation showing group details, member list, and group management actions.
- **Action_Button_Row**: A horizontal row of circular icon buttons with labels used for quick actions on info screens.

## Requirements

### Requirement 1: Username Display Fix in Conversation List

**User Story:** As a user, I want to see the name of the person or group in my conversation list, so that I can identify who I am chatting with.

#### Acceptance Criteria

1. THE ChatsScreen SHALL display the Conversation_Title (resolved partner name for direct messages, group name for group conversations) in each Conversation_Row.
2. WHEN the Conversation_Title is an empty string or blank, THE ChatsScreen SHALL display a fallback text of "Unknown User" for direct messages or "Unnamed Group" for group conversations.
3. THE ChatsScreen SHALL render the Conversation_Title using bold font weight (FontWeight.SemiBold) with titleMedium typography for visual prominence.
4. IF the Conversation domain model returns a blank name field, THEN THE ConversationUiModel mapper SHALL resolve the display name from participant metadata before falling back to the default.

### Requirement 2: Username Display Fix in Chat Screen

**User Story:** As a user, I want to see the sender's name on messages in a conversation, so that I can identify who sent each message.

#### Acceptance Criteria

1. THE Chat_Header SHALL display the Conversation_Title (partner name for DM, group name for group) prominently as the primary title text.
2. WHEN the conversation is a group chat, THE ChatScreen SHALL display the Sender_Name_Label above each received Chat_Bubble showing the sender's display name.
3. WHEN the conversation is a direct message, THE ChatScreen SHALL hide the Sender_Name_Label on received Chat_Bubbles since the sender identity is implicit.
4. IF the senderName field on a MessageUiModel is empty or blank, THEN THE ChatScreen SHALL display "Unknown" as the Sender_Name_Label.
5. THE Chat_Header SHALL display the Conversation_Title with titleMedium typography and FontWeight.SemiBold.

### Requirement 3: Messenger-Style Conversation List Design

**User Story:** As a user, I want the conversation list to look modern and professional like Messenger, so that the app feels polished and easy to scan.

#### Acceptance Criteria

1. THE Conversation_Row SHALL display a 56dp circular Avatar on the leading edge of the row.
2. WHEN the user has a profile photo URL, THE Avatar SHALL load and display the remote image with circular clipping.
3. WHEN the user has no profile photo, THE Avatar SHALL display a colored circle with the user's initials in white text.
4. THE Conversation_Row SHALL overlay a 14dp Online_Indicator (green dot with 2dp white border) on the bottom-right corner of the Avatar when the other user is online.
5. THE Conversation_Row SHALL display the Conversation_Title on the first line with bodyLarge typography and FontWeight.SemiBold.
6. THE Conversation_Row SHALL display the Message_Preview on the second line with bodyMedium typography and onSurfaceVariant color.
7. THE Conversation_Row SHALL display the Timestamp_Label aligned to the trailing edge on the same line as the Conversation_Title with labelSmall typography.
8. WHEN the conversation has unread messages, THE Conversation_Row SHALL display the Conversation_Title and Message_Preview with increased font weight (FontWeight.Bold for title, FontWeight.Medium for preview).
9. WHEN the conversation has unread messages, THE Conversation_Row SHALL display a filled circular badge (20dp) with the unread count on the trailing edge below the timestamp.
10. THE Conversation_Row SHALL use 16dp horizontal padding and 12dp vertical padding to match Messenger density.
11. THE ChatsScreen SHALL remove the HorizontalDivider between conversation rows and rely on vertical spacing for visual separation, matching Messenger's clean list style.

### Requirement 4: Messenger-Style Chat Bubble Design

**User Story:** As a user, I want chat bubbles to look like Messenger with rounded shapes and clear visual distinction between sent and received messages, so that conversations are easy to read.

#### Acceptance Criteria

1. THE ChatScreen SHALL render sent Chat_Bubbles with the primary color background (Messenger blue) and white text, aligned to the trailing edge (right side).
2. THE ChatScreen SHALL render received Chat_Bubbles with surfaceContainerHigh background (light gray) and onSurface text color, aligned to the leading edge (left side).
3. THE Chat_Bubble SHALL use 18dp corner radius on all corners except the tail corner which uses 4dp (bottom-right for sent, bottom-left for received).
4. THE Chat_Bubble SHALL constrain maximum width to 75% of the screen width.
5. THE Chat_Bubble SHALL use 12dp horizontal padding and 8dp vertical padding for message text content.
6. WHEN consecutive messages are from the same sender within 2 minutes, THE ChatScreen SHALL reduce vertical spacing between those bubbles to 2dp (message grouping).
7. WHEN consecutive messages are from the same sender within 2 minutes, THE ChatScreen SHALL apply 18dp radius to all corners on intermediate bubbles (no tail) and only show the tail on the last bubble in the group.
8. WHEN the conversation is a group chat, THE ChatScreen SHALL display a 28dp circular Avatar to the left of received Chat_Bubbles for the first message in each sender group.
9. THE Chat_Bubble SHALL display the Timestamp_Label (HH:mm format) below the bubble text in labelSmall typography with reduced opacity (0.7).

### Requirement 5: Professional Input Bar Design

**User Story:** As a user, I want a clean and modern message input area like Messenger, so that composing messages feels intuitive and polished.

#### Acceptance Criteria

1. THE Input_Bar SHALL display a rounded text field (24dp corner radius) with surfaceContainerHigh background and no visible border in the default state.
2. THE Input_Bar SHALL display a circular send button (40dp) with primary color background on the trailing edge, visible only when the text field contains non-empty text.
3. WHEN the text field is empty, THE Input_Bar SHALL display action icons (camera, attachment) on the trailing edge instead of the send button.
4. THE Input_Bar SHALL use 8dp padding from the screen edges and 8dp vertical padding from the message list above.
5. THE Input_Bar SHALL expand vertically to accommodate multi-line input up to a maximum of 5 visible lines, then scroll internally.
6. THE Input_Bar SHALL display placeholder text "Aa" in onSurfaceVariant color when the text field is empty, matching Messenger's minimal placeholder style.

### Requirement 6: Chat Header Redesign

**User Story:** As a user, I want the chat header to clearly show who I am talking to with their avatar and online status, so that I have context about the conversation.

#### Acceptance Criteria

1. THE Chat_Header SHALL display a 36dp circular Avatar to the left of the title area.
2. THE Chat_Header SHALL display the Conversation_Title as the primary text with titleMedium typography and FontWeight.SemiBold.
3. WHEN the other user in a direct message is online, THE Chat_Header SHALL display "Active now" subtitle text in bodySmall typography with a green color.
4. WHEN the other user in a direct message is offline, THE Chat_Header SHALL hide the subtitle text (no "Offline" label shown).
5. WHEN the conversation is a group, THE Chat_Header SHALL display the member count as subtitle text (e.g., "5 members") in bodySmall typography.
6. THE Chat_Header SHALL overlay a 10dp Online_Indicator (green dot with 1.5dp white border) on the Avatar when the other user is online in a direct message.
7. THE Chat_Header SHALL use a back arrow icon on the leading edge that navigates to the previous screen on tap.

### Requirement 7: Messenger-Style Chat Screen Top Bar

**User Story:** As a user, I want the chat screen top bar to look like Messenger with a compact, tappable header showing the contact's identity, so that I can quickly access conversation info.

#### Acceptance Criteria

1. THE Chat_Header SHALL use a compact height (64dp) with surface background and a subtle bottom elevation shadow (1dp) for visual separation from the message list.
2. THE Chat_Header SHALL display a back arrow (24dp) on the leading edge with 4dp padding from the left screen edge.
3. THE Chat_Header SHALL display a 36dp circular Avatar immediately after the back arrow with 8dp spacing.
4. THE Chat_Header SHALL display the Conversation_Title next to the Avatar with titleSmall typography and FontWeight.SemiBold, with 8dp spacing from the Avatar.
5. WHEN the other user in a direct message is online, THE Chat_Header SHALL display "Active now" text below the title in labelSmall typography with a green (tertiary) color.
6. WHEN the other user in a direct message is offline, THE Chat_Header SHALL display "Offline" text below the title in labelSmall typography with onSurfaceVariant color.
7. WHEN the conversation is a group, THE Chat_Header SHALL display "{memberCount} members" below the title in labelSmall typography with onSurfaceVariant color.
8. THE Chat_Header SHALL overlay a 10dp green Online_Indicator dot (with 1.5dp white border) on the bottom-right of the Avatar when the other user is online in a direct message.
9. WHEN the user taps the Avatar or title area, THE Chat_Header SHALL navigate to the conversation info screen (UserProfileScreen for DM, GroupDetailsScreen for group).
10. THE Chat_Header SHALL display action icons (phone call, video call, info) on the trailing edge with 24dp icon size and onSurfaceVariant tint, matching Messenger's action layout.
11. THE Chat_Header SHALL limit action icons to a maximum of 3 visible icons; additional actions SHALL be placed in an overflow menu.

### Requirement 8: Messenger-Style Conversation Info Screen

**User Story:** As a user, I want a polished conversation info screen like Messenger, so that I can view details about the person or group and access settings in a clean layout.

#### Acceptance Criteria

1. THE Conversation_Info_Screen SHALL display a large centered Avatar (80dp) at the top of the screen with the Conversation_Title below it in headlineSmall typography and FontWeight.Bold.
2. WHEN the conversation is a direct message, THE Conversation_Info_Screen SHALL display the other user's online status ("Active now" or "Active Xh ago") below the name in bodyMedium typography.
3. THE Conversation_Info_Screen SHALL display a horizontal row of circular action buttons (Audio Call, Video Call, Profile, Mute, Search) below the header area, each with a 40dp icon circle and a label below.
4. THE Conversation_Info_Screen SHALL display a "Customize Chat" section with options for theme color, emoji shortcut, and nicknames, each as a tappable list item with leading icon.
5. THE Conversation_Info_Screen SHALL display a "More Actions" section with options for "Search in Conversation", "View Media & Files", "Notification Settings", each as a tappable list item.
6. WHEN the conversation is a direct message, THE Conversation_Info_Screen SHALL display a "Privacy & Support" section with "Block", "Report" options in error color text.
7. THE Conversation_Info_Screen SHALL use a scrollable Column layout with section headers in labelLarge typography and FontWeight.SemiBold with 16dp top margin between sections.
8. THE Conversation_Info_Screen SHALL display shared media thumbnails in a horizontal scrollable row (3 visible items) with a "See All" trailing action.

### Requirement 9: Messenger-Style Group Info and Management

**User Story:** As a user, I want a professional group info screen with clear member management like Messenger, so that I can manage group settings and members efficiently.

#### Acceptance Criteria

1. THE Group_Info_Screen SHALL display a large centered group Avatar (80dp) at the top with the group name below in headlineSmall typography and FontWeight.Bold, and member count in bodyMedium typography.
2. THE Group_Info_Screen SHALL display a horizontal row of circular action buttons (Add Members, Audio Call, Video Call, Mute, Search) below the header, each with a 40dp icon circle and label.
3. THE Group_Info_Screen SHALL display a "Members" section listing all group members with their 40dp Avatar, display name (bodyLarge), and role badge ("Admin" in a small chip) for admin users.
4. WHEN the current user is an admin, THE Group_Info_Screen SHALL display a trailing overflow menu (three-dot icon) on each non-admin member row with options: "Make Admin", "Remove from Group".
5. WHEN the current user is an admin, THE Group_Info_Screen SHALL display an "Add Members" row at the top of the members list with a plus icon and primary color text.
6. THE Group_Info_Screen SHALL display a "Customize Chat" section with options for group name edit, group photo change, and theme color.
7. THE Group_Info_Screen SHALL display a "Shared Media" section with a horizontal thumbnail row and "See All" action.
8. THE Group_Info_Screen SHALL display a "Leave Group" option at the bottom in error color text with a confirmation dialog on tap.
9. WHEN the current user is an admin, THE Group_Info_Screen SHALL display a "Delete Group" option below "Leave Group" in error color text with a confirmation dialog.
10. THE Group_Info_Screen SHALL display an "Invite Link" section with the link text, a copy button, and a share button for admin users.
11. THE Group_Info_Screen member list SHALL display an Online_Indicator on each member's Avatar when that member is currently online.

### Requirement 10: Visual Polish and Consistency

**User Story:** As a user, I want the chat experience to feel smooth and cohesive, so that the app feels professional and trustworthy.

#### Acceptance Criteria

1. THE ChatScreen SHALL animate new sent messages sliding in from the bottom with a 200ms ease-out animation.
2. THE ChatsScreen SHALL animate conversation row reordering with a 250ms ease-in-out animation when new messages arrive and change the sort order.
3. THE ChatScreen SHALL display date separator pills (rounded chip with "Today", "Yesterday", or formatted date) between message groups from different days.
4. THE date separator pill SHALL use surfaceContainerHigh background with onSurfaceVariant text in labelSmall typography, centered horizontally with 16dp vertical margin.
5. WHEN a conversation has no messages, THE ChatScreen SHALL display a centered empty state with an illustration and "Say hi!" prompt text.
6. THE ChatsScreen SHALL use a top header area with "Chats" title in headlineMedium typography and FontWeight.Bold, matching Messenger's screen title style.
