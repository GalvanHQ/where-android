package com.ovi.where.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ovi.where.data.local.entity.UserCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCacheDao {

    /** Reactive stream — UI re-renders when the row updates. */
    @Query("SELECT * FROM user_cache WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<UserCacheEntity?>

    /** Reactive stream of every cached user. Used by VMs that need to
     *  hydrate an in-memory map at process start. */
    @Query("SELECT * FROM user_cache")
    fun observeAll(): Flow<List<UserCacheEntity>>

    /** Reactive stream for several uids at once — used by the meetup sheet
     *  and the map's group-preview avatars. */
    @Query("SELECT * FROM user_cache WHERE id IN (:ids)")
    fun observeAll(ids: List<String>): Flow<List<UserCacheEntity>>

    /** One-shot read; returns null when not cached. */
    @Query("SELECT * FROM user_cache WHERE id = :id LIMIT 1")
    suspend fun get(id: String): UserCacheEntity?

    @Query("SELECT * FROM user_cache WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<UserCacheEntity>

    /** Returns the set of [ids] that already have a cached row. */
    @Query("SELECT id FROM user_cache WHERE id IN (:ids)")
    suspend fun cachedIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserCacheEntity>)

    @Query("DELETE FROM user_cache WHERE cachedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    @Query("DELETE FROM user_cache")
    suspend fun clearAll()
}
