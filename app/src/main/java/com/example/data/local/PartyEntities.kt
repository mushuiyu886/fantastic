package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline cache for 4P+ dynamically loaded game resource packs (word pairs, charades prompts,
 * player-count role balance tables). Pure structured JSON data — zero executable code.
 */
@Entity(tableName = "cached_resource_packs")
data class CachedResourcePackEntity(
    @PrimaryKey val packId: String,
    val title: String,
    val category: String,
    val targetGameId: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val version: Int,
    val payloadJson: String,
    val sizeBytes: Int,
    val itemCount: Int,
    val lastAccessedAt: Long
)

/**
 * Offline state snapshot for instantaneous recovery during network fluctuations or app resume.
 */
@Entity(tableName = "session_snapshots")
data class SessionSnapshotEntity(
    @PrimaryKey val roomCode: String,
    val connectionMode: String,
    val hostId: String,
    val activeGameId: String?,
    val playersJson: String,
    val gameStateJson: String,
    val seqNumber: Long,
    val playerCount: Int,
    val updatedAt: Long
)

/**
 * Local party leaderboard & match history records.
 */
@Entity(tableName = "party_match_records")
data class PartyMatchRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val gameTitle: String,
    val playerCount: Int,
    val mvpName: String,
    val mvpEmoji: String,
    val mvpScore: Int,
    val localWon: Boolean,
    val summaryNote: String,
    val playedAt: Long
)
