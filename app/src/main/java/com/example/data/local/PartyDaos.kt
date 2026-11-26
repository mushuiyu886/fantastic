package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourcePackDao {
    @Query("SELECT * FROM cached_resource_packs ORDER BY lastAccessedAt DESC")
    fun observeAllPacks(): Flow<List<CachedResourcePackEntity>>

    @Query("SELECT * FROM cached_resource_packs WHERE packId = :packId LIMIT 1")
    suspend fun getPackById(packId: String): CachedResourcePackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: CachedResourcePackEntity)

    @Query("UPDATE cached_resource_packs SET lastAccessedAt = :timestamp WHERE packId = :packId")
    suspend fun touchPack(packId: String, timestamp: Long)

    @Query("DELETE FROM cached_resource_packs WHERE packId = :packId")
    suspend fun deletePack(packId: String)

    @Query("DELETE FROM cached_resource_packs")
    suspend fun clearAllPacks()
}

@Dao
interface SessionSnapshotDao {
    @Query("SELECT * FROM session_snapshots ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestSnapshot(): Flow<SessionSnapshotEntity?>

    @Query("SELECT * FROM session_snapshots ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(): SessionSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(snapshot: SessionSnapshotEntity)

    @Query("DELETE FROM session_snapshots WHERE roomCode = :roomCode")
    suspend fun deleteSnapshot(roomCode: String)

    @Query("DELETE FROM session_snapshots")
    suspend fun clearAllSnapshots()
}

@Dao
interface MatchRecordDao {
    @Query("SELECT * FROM party_match_records ORDER BY playedAt DESC LIMIT 30")
    fun observeRecentMatches(): Flow<List<PartyMatchRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PartyMatchRecordEntity)

    @Query("DELETE FROM party_match_records")
    suspend fun clearRecords()
}
