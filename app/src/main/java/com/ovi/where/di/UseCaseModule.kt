package com.ovi.where.di

import com.ovi.where.domain.repository.AuthRepository
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.repository.MessageRepository
import com.ovi.where.domain.repository.UserRepository
import com.ovi.where.domain.usecase.auth.CheckUsernameAvailableUseCase
import com.ovi.where.domain.usecase.auth.CompleteProfileUseCase
import com.ovi.where.domain.usecase.auth.GoogleSignInUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.auth.RegisterUseCase
import com.ovi.where.domain.usecase.auth.ReloadUserUseCase
import com.ovi.where.domain.usecase.auth.ResetPasswordUseCase
import com.ovi.where.domain.usecase.auth.SendEmailVerificationUseCase
import com.ovi.where.domain.usecase.auth.SignInUseCase
import com.ovi.where.domain.usecase.auth.SignOutUseCase
import com.ovi.where.domain.usecase.auth.UpdateBioUseCase
import com.ovi.where.domain.usecase.auth.UpdateUsernameUseCase
import com.ovi.where.domain.usecase.auth.UpdateProfileUseCase
import com.ovi.where.domain.usecase.chat.CreateGroupConversationUseCase
import com.ovi.where.domain.usecase.chat.GetOrCreateDirectConversationUseCase
import com.ovi.where.domain.usecase.chat.MarkConversationReadUseCase
import com.ovi.where.domain.usecase.chat.ObserveConversationsUseCase
import com.ovi.where.domain.usecase.chat.ObserveMessagesUseCase
import com.ovi.where.domain.usecase.chat.SendLocationMessageUseCase
import com.ovi.where.domain.usecase.chat.SendMessageUseCase
import com.ovi.where.domain.usecase.friend.AcceptFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.BlockUserUseCase
import com.ovi.where.domain.usecase.friend.CancelFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.DeclineFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.GetFriendshipStatusUseCase
import com.ovi.where.domain.usecase.friend.ObserveAllFriendLocationsUseCase
import com.ovi.where.domain.usecase.friend.ObserveBlockedUsersUseCase
import com.ovi.where.domain.usecase.friend.ObserveFriendsUseCase
import com.ovi.where.domain.usecase.friend.ObserveIncomingRequestsUseCase
import com.ovi.where.domain.usecase.friend.ObserveOutgoingRequestsUseCase
import com.ovi.where.domain.usecase.friend.ObserveSocialSummaryUseCase
import com.ovi.where.domain.usecase.friend.RemoveFriendUseCase
import com.ovi.where.domain.usecase.friend.SendFriendRequestUseCase
import com.ovi.where.domain.usecase.friend.UnblockUserUseCase
import com.ovi.where.domain.usecase.group.CreateGroupUseCase
import com.ovi.where.domain.usecase.group.DeleteGroupUseCase
import com.ovi.where.domain.usecase.group.GetGroupUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.domain.usecase.group.JoinGroupUseCase
import com.ovi.where.domain.usecase.group.KickMemberUseCase
import com.ovi.where.domain.usecase.group.LeaveGroupUseCase
import com.ovi.where.domain.usecase.group.ObserveGroupMembersUseCase
import com.ovi.where.domain.usecase.group.PromoteMemberUseCase
import com.ovi.where.domain.usecase.group.UpdateGroupUseCase
import com.ovi.where.domain.usecase.location.ObserveGroupLocationsUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import com.ovi.where.domain.usecase.user.GetUsersUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    // ── Auth ─────────────────────────────────────────────────────────────────
    @Provides fun provideSignInUseCase(r: AuthRepository) = SignInUseCase(r)
    @Provides fun provideRegisterUseCase(r: AuthRepository) = RegisterUseCase(r)
    @Provides fun provideObserveCurrentUserUseCase(r: AuthRepository) = ObserveCurrentUserUseCase(r)
    @Provides fun provideSignOutUseCase(r: AuthRepository) = SignOutUseCase(r)
    @Provides fun provideGoogleSignInUseCase(r: AuthRepository) = GoogleSignInUseCase(r)
    @Provides fun provideResetPasswordUseCase(r: AuthRepository) = ResetPasswordUseCase(r)
    @Provides fun provideUpdateProfileUseCase(r: AuthRepository) = UpdateProfileUseCase(r)
    @Provides fun provideUpdateBioUseCase(r: AuthRepository) = UpdateBioUseCase(r)
    @Provides fun provideUpdateUsernameUseCase(r: AuthRepository) = UpdateUsernameUseCase(r)
    @Provides fun provideCheckUsernameAvailableUseCase(r: AuthRepository) = CheckUsernameAvailableUseCase(r)
    @Provides fun provideSendEmailVerificationUseCase(r: AuthRepository) = SendEmailVerificationUseCase(r)
    @Provides fun provideReloadUserUseCase(r: AuthRepository) = ReloadUserUseCase(r)
    @Provides fun provideCompleteProfileUseCase(r: AuthRepository) = CompleteProfileUseCase(r)

    // ── Group ────────────────────────────────────────────────────────────────
    @Provides fun provideCreateGroupUseCase(r: GroupRepository) = CreateGroupUseCase(r)
    @Provides fun provideGetUserGroupsUseCase(r: GroupRepository) = GetUserGroupsUseCase(r)
    @Provides fun provideGetGroupUseCase(r: GroupRepository) = GetGroupUseCase(r)
    @Provides fun provideJoinGroupUseCase(r: GroupRepository) = JoinGroupUseCase(r)
    @Provides fun provideLeaveGroupUseCase(r: GroupRepository) = LeaveGroupUseCase(r)
    @Provides fun provideObserveGroupMembersUseCase(r: GroupRepository) = ObserveGroupMembersUseCase(r)
    @Provides fun provideKickMemberUseCase(r: GroupRepository) = KickMemberUseCase(r)
    @Provides fun providePromoteMemberUseCase(r: GroupRepository) = PromoteMemberUseCase(r)
    @Provides fun provideDeleteGroupUseCase(r: GroupRepository) = DeleteGroupUseCase(r)
    @Provides fun provideUpdateGroupUseCase(r: GroupRepository) = UpdateGroupUseCase(r)

    // ── Location ─────────────────────────────────────────────────────────────
    @Provides fun provideStartLocationSharingUseCase(r: LocationRepository) = StartLocationSharingUseCase(r)
    @Provides fun provideStopLocationSharingUseCase(r: LocationRepository) = StopLocationSharingUseCase(r)
    @Provides fun provideObserveGroupLocationsUseCase(r: LocationRepository) = ObserveGroupLocationsUseCase(r)

    // ── User ─────────────────────────────────────────────────────────────────
    @Provides fun provideGetUsersUseCase(r: UserRepository) = GetUsersUseCase(r)

    // ── Friends ──────────────────────────────────────────────────────────────
    @Provides fun provideSendFriendRequestUseCase(r: FriendshipRepository) = SendFriendRequestUseCase(r)
    @Provides fun provideAcceptFriendRequestUseCase(r: FriendshipRepository) = AcceptFriendRequestUseCase(r)
    @Provides fun provideDeclineFriendRequestUseCase(r: FriendshipRepository) = DeclineFriendRequestUseCase(r)
    @Provides fun provideRemoveFriendUseCase(r: FriendshipRepository) = RemoveFriendUseCase(r)
    @Provides fun provideObserveFriendsUseCase(r: FriendshipRepository) = ObserveFriendsUseCase(r)
    @Provides fun provideGetFriendshipStatusUseCase(r: FriendshipRepository) = GetFriendshipStatusUseCase(r)
    @Provides fun provideObserveAllFriendLocationsUseCase(r: FriendshipRepository) = ObserveAllFriendLocationsUseCase(r)
    @Provides fun provideCancelFriendRequestUseCase(r: FriendshipRepository) = CancelFriendRequestUseCase(r)
    @Provides fun provideBlockUserUseCase(r: FriendshipRepository) = BlockUserUseCase(r)
    @Provides fun provideUnblockUserUseCase(r: FriendshipRepository) = UnblockUserUseCase(r)
    @Provides fun provideObserveIncomingRequestsUseCase(r: FriendshipRepository) = ObserveIncomingRequestsUseCase(r)
    @Provides fun provideObserveOutgoingRequestsUseCase(r: FriendshipRepository) = ObserveOutgoingRequestsUseCase(r)
    @Provides fun provideObserveSocialSummaryUseCase(r: FriendshipRepository) = ObserveSocialSummaryUseCase(r)
    @Provides fun provideObserveBlockedUsersUseCase(r: FriendshipRepository) = ObserveBlockedUsersUseCase(r)

    // ── Chat ─────────────────────────────────────────────────────────────────
    @Provides fun provideObserveConversationsUseCase(r: ConversationRepository) = ObserveConversationsUseCase(r)
    @Provides fun provideObserveMessagesUseCase(r: MessageRepository) = ObserveMessagesUseCase(r)
    @Provides fun provideGetOrCreateDirectConversationUseCase(r: ConversationRepository) = GetOrCreateDirectConversationUseCase(r)
    @Provides fun provideCreateGroupConversationUseCase(r: ConversationRepository) = CreateGroupConversationUseCase(r)
    @Provides fun provideSendMessageUseCase(r: MessageRepository) = SendMessageUseCase(r)
    @Provides fun provideSendLocationMessageUseCase(r: MessageRepository) = SendLocationMessageUseCase(r)
    @Provides fun provideMarkConversationReadUseCase(r: ConversationRepository) = MarkConversationReadUseCase(r)
}
