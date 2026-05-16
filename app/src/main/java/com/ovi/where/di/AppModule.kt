package com.ovi.where.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.storage.FirebaseStorage
import com.ovi.where.core.config.FeatureFlags
import com.ovi.where.data.local.db.AppDatabase
import com.ovi.where.data.local.dao.InteractionDao
import com.ovi.where.data.local.dao.LinkPreviewCacheDao
import com.ovi.where.data.local.dao.VoiceMessageCacheDao
import com.ovi.where.data.local.prefs.RecentSearchesStore
import com.ovi.where.data.local.prefs.UserPreferences
import com.ovi.where.data.repository.NotificationPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val config = FirebaseRemoteConfig.getInstance()
        val defaults = mapOf(
            FeatureFlags.KEY_USE_NEW_FRIENDSHIP_MODEL to true
        )
        config.setDefaultsAsync(defaults)
        return config
    }

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "nearby_database"
    ).addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
        .build()

    @Provides
    @Singleton
    fun provideLocationDao(database: AppDatabase) = database.locationDao()

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase) = database.messageDao()

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase) = database.conversationDao()

    @Provides
    @Singleton
    fun provideCacheMetadataDao(database: AppDatabase) = database.cacheMetadataDao()

    @Provides
    @Singleton
    fun provideOfflineOperationDao(database: AppDatabase) = database.offlineOperationDao()

    @Provides
    @Singleton
    fun provideInteractionDao(database: AppDatabase) = database.interactionDao()

    @Provides
    @Singleton
    fun provideVoiceMessageCacheDao(database: AppDatabase): VoiceMessageCacheDao = database.voiceMessageCacheDao()

    @Provides
    @Singleton
    fun provideLinkPreviewCacheDao(database: AppDatabase): LinkPreviewCacheDao = database.linkPreviewCacheDao()

    @Provides
    @Singleton
    fun provideOnlineStatusDao(database: AppDatabase) = database.onlineStatusDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideUserPreferences(dataStore: DataStore<Preferences>): UserPreferences = UserPreferences(dataStore)

    @Provides
    @Singleton
    fun provideRecentSearchesStore(dataStore: DataStore<Preferences>): RecentSearchesStore = RecentSearchesStore(dataStore)

    @Provides
    @Singleton
    fun provideNotificationPreferencesRepository(dataStore: DataStore<Preferences>): NotificationPreferencesRepository =
        NotificationPreferencesRepository(dataStore)
}
