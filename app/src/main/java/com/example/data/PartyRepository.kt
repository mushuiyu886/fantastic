package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.CachedResourcePackEntity
import com.example.data.local.PartyDatabase
import com.example.data.local.PartyMatchRecordEntity
import com.example.data.local.SessionSnapshotEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.partyPrefsDataStore by preferencesDataStore(name = "bump_party_prefs")

data class LocalUserProfile(
    val nickname: String,
    val avatarEmoji: String,
    val colorIndex: Int
)

class PartyRepository(private val context: Context) {
    private val database = PartyDatabase.getInstance(context)
    private val snapshotDao = database.sessionSnapshotDao()
    private val matchRecordDao = database.matchRecordDao()
    val resourcePackManager = DynamicResourcePackManager(database.resourcePackDao())

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val playerListType = Types.newParameterizedType(List::class.java, PartyPlayer::class.java)
    private val playerListAdapter = moshi.adapter<List<PartyPlayer>>(playerListType)

    private object PrefKeys {
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR = stringPreferencesKey("avatar_emoji")
        val COLOR_IDX = intPreferencesKey("color_index")
    }

    val userProfileFlow: Flow<LocalUserProfile> = context.partyPrefsDataStore.data.map { prefs ->
        LocalUserProfile(
            nickname = prefs[PrefKeys.NICKNAME] ?: "派对玩家",
            avatarEmoji = prefs[PrefKeys.AVATAR] ?: "🦊",
            colorIndex = prefs[PrefKeys.COLOR_IDX] ?: 0
        )
    }

    val cachedPacksFlow: Flow<List<CachedResourcePackEntity>> =
        resourcePackManager.observeCachedPacks()

    val latestSnapshotFlow: Flow<SessionSnapshotEntity?> =
        snapshotDao.observeLatestSnapshot()

    val recentMatchesFlow: Flow<List<PartyMatchRecordEntity>> =
        matchRecordDao.observeRecentMatches()

    suspend fun updateProfile(nickname: String, emoji: String, colorIndex: Int) {
        val cleanName = nickname.trim().ifEmpty { "派对玩家" }.take(12)
        context.partyPrefsDataStore.edit { prefs ->
            prefs[PrefKeys.NICKNAME] = cleanName
            prefs[PrefKeys.AVATAR] = emoji
            prefs[PrefKeys.COLOR_IDX] = colorIndex
        }
    }

    suspend fun saveSessionSnapshot(
        roomCode: String,
        connectionMode: ConnectionMode,
        hostId: String,
        activeGameId: GameId?,
        players: List<PartyPlayer>,
        gameStateJson: String,
        seqNumber: Long
    ) {
        snapshotDao.saveSnapshot(
            SessionSnapshotEntity(
                roomCode = roomCode,
                connectionMode = connectionMode.name,
                hostId = hostId,
                activeGameId = activeGameId?.name,
                playersJson = playerListAdapter.toJson(players),
                gameStateJson = gameStateJson,
                seqNumber = seqNumber,
                playerCount = players.size,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getLatestSnapshot(): SessionSnapshotEntity? = snapshotDao.getLatestSnapshot()

    fun decodePlayers(playersJson: String): List<PartyPlayer> {
        return runCatching { playerListAdapter.fromJson(playersJson).orEmpty() }.getOrDefault(emptyList())
    }

    suspend fun clearSnapshots() = snapshotDao.clearAllSnapshots()

    suspend fun recordMatchResult(summary: MatchResultSummary) {
        matchRecordDao.insertRecord(
            PartyMatchRecordEntity(
                gameId = summary.gameId.name,
                gameTitle = summary.gameTitle,
                playerCount = summary.playerCount,
                mvpName = summary.mvpName,
                mvpEmoji = summary.mvpEmoji,
                mvpScore = summary.mvpScore,
                localWon = summary.localWon,
                summaryNote = summary.note,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearMatchRecords() = matchRecordDao.clearRecords()
}
